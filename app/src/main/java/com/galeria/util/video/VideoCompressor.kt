package com.galeria.util.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.nio.ByteBuffer
import java.util.concurrent.Executors

data class VideoCompressResult(val uri: Uri?, val error: String? = null)

// Chave usada pelo MediaExtractor pra indicar que o vídeo tem uma rotação embutida (comum em
// vídeos gravados na vertical). Usamos a string literal em vez de MediaFormat.KEY_ROTATION
// pra não depender de uma constante só oficialmente documentada a partir da API 30 -- o valor
// em si ("rotation-degrees") é estável desde muito antes disso.
private const val KEY_ROTATION = "rotation-degrees"

object VideoCompressor {

    private const val TIMEOUT_US = 10_000L
    private const val IFRAME_INTERVAL_SECONDS = 2
    private const val MAX_PROCESSING_MS = 10 * 60_000L // salvaguarda: nunca deixa rodar pra sempre

    /**
     * Recodifica o vídeo em [sourceUri] com o maior lado limitado a [targetMaxDimension]
     * pixels e o bitrate de vídeo em [videoBitrate] bits/s, mantendo a proporção original.
     * O áudio é copiado sem recodificar (reduz a superfície de risco, já que raramente é o
     * áudio que pesa no tamanho do arquivo). NUNCA toca no arquivo de origem -- grava sempre
     * um arquivo novo (via MediaStore), e se qualquer etapa falhar, apaga o que já tinha
     * sido escrito e devolve o motivo em [VideoCompressResult.error].
     *
     * Por baixo dos panos: decodifica o vídeo original pra uma Surface, desenha cada quadro
     * via OpenGL direto na entrada de um encoder novo (nunca copia pixel pra CPU) e escreve
     * tudo com MediaMuxer. Nada disso é opcional pra "só reduzir o tamanho" -- é o caminho
     * mínimo que o Android oferece pra recodificar vídeo sem depender de bibliotecas
     * externas (tipo FFmpeg).
     *
     * As variáveis "*Holder" abaixo existem só pra permitir liberar os recursos no `finally`
     * mesmo se algo falhar no meio do processo; o corpo da função usa sempre os `val` locais
     * (não-nuláveis), criados logo após cada recurso ser aberto.
     */
    suspend fun compress(
        context: Context,
        sourceUri: Uri,
        targetMaxDimension: Int,
        videoBitrate: Int,
        onProgress: (Float) -> Unit
    ): VideoCompressResult {
        // Todo o trabalho roda numa única thread de segundo plano fixa (nunca a thread
        // principal, e nunca um pool de threads variável) -- por dois motivos:
        // 1) O laço de decodificação/codificação faz chamadas bloqueantes; rodar isso na
        //    thread principal trava a UI e, pior, impede o aviso de "novo quadro pronto" do
        //    decoder (que depende da fila de mensagens da thread principal) de ser entregue.
        // 2) O contexto gráfico EGL usado pra desenhar cada quadro só é válido na thread
        //    física em que foi criado -- um pool de threads (como Dispatchers.Default) pode
        //    retomar a corrotina numa thread diferente a cada pausa, corrompendo esse
        //    contexto. Uma thread fixa dedicada evita isso.
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "galeria-video-compress") }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            return withContext(dispatcher) {
                compressOnCurrentThread(context, sourceUri, targetMaxDimension, videoBitrate, onProgress)
            }
        } finally {
            dispatcher.close()
        }
    }

    private suspend fun compressOnCurrentThread(
        context: Context,
        sourceUri: Uri,
        targetMaxDimension: Int,
        videoBitrate: Int,
        onProgress: (Float) -> Unit
    ): VideoCompressResult {
        var outputUri: Uri? = null
        var pfdHolder: ParcelFileDescriptor? = null
        var muxerHolder: MediaMuxer? = null
        var muxerStarted = false
        var videoExtractorHolder: MediaExtractor? = null
        var audioExtractorHolder: MediaExtractor? = null
        var decoderHolder: MediaCodec? = null
        var encoderHolder: MediaCodec? = null
        var inputSurfaceHolder: InputSurface? = null
        var outputSurfaceHolder: OutputSurface? = null

        try {
            val videoExtractor = MediaExtractor().apply { setDataSource(context, sourceUri, null) }
            videoExtractorHolder = videoExtractor
            val videoTrackIndex = selectTrack(videoExtractor, "video/")
                ?: return VideoCompressResult(null, "Esse arquivo não parece ter uma faixa de vídeo legível")
            videoExtractor.selectTrack(videoTrackIndex)
            val sourceVideoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
            val sourceMime = sourceVideoFormat.getString(MediaFormat.KEY_MIME)
                ?: return VideoCompressResult(null, "Não foi possível identificar o formato do vídeo")

            val srcWidth = sourceVideoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val srcHeight = sourceVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val durationUs = if (sourceVideoFormat.containsKey(MediaFormat.KEY_DURATION)) {
                sourceVideoFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            val rotation = if (sourceVideoFormat.containsKey(KEY_ROTATION)) {
                sourceVideoFormat.getInteger(KEY_ROTATION)
            } else 0

            val (dstWidth, dstHeight) = scaledDimensions(srcWidth, srcHeight, targetMaxDimension)

            val outputFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, dstWidth, dstHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL_SECONDS)
                if (rotation != 0) setInteger(KEY_ROTATION, rotation)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoderHolder = encoder
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = InputSurface(encoder.createInputSurface())
            inputSurfaceHolder = inputSurface
            inputSurface.makeCurrent()
            encoder.start()

            val outputSurface = OutputSurface()
            outputSurfaceHolder = outputSurface
            val decoder = MediaCodec.createDecoderByType(sourceMime)
            decoderHolder = decoder
            decoder.configure(sourceVideoFormat, outputSurface.surface, null, 0)
            decoder.start()

            val displayName = "galeria_compress_${System.currentTimeMillis()}.mp4"
            val uri = createPendingVideoEntry(context, displayName)
                ?: return VideoCompressResult(null, "Não foi possível criar o arquivo de saída")
            outputUri = uri
            val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
                ?: return VideoCompressResult(null, "Não foi possível abrir o arquivo de saída")
            pfdHolder = pfd
            val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxerHolder = muxer

            // O áudio original é copiado à parte, amostra por amostra, sem recodificar --
            // só depois que o muxer já estiver de pé (ele só começa quando o encoder de
            // vídeo informa seu formato final, dentro do laço abaixo).
            val audioExtractor = MediaExtractor().apply { setDataSource(context, sourceUri, null) }
            audioExtractorHolder = audioExtractor
            val audioTrackIndex = selectTrack(audioExtractor, "audio/")
            val audioFormat = audioTrackIndex?.let { idx ->
                audioExtractor.selectTrack(idx)
                audioExtractor.getTrackFormat(idx)
            }

            var outVideoTrackIndex = -1
            var outAudioTrackIndex = -1

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var encoderDone = false
            val startTime = System.currentTimeMillis()

            while (!encoderDone) {
                yield()
                if (System.currentTimeMillis() - startTime > MAX_PROCESSING_MS) {
                    throw IllegalStateException("Tempo limite excedido ao comprimir o vídeo")
                }

                if (!inputDone) {
                    val inputBufferId = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferId >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferId)!!
                        val sampleSize = videoExtractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val presentationTime = videoExtractor.sampleTime
                            decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTime, 0)
                            videoExtractor.advance()
                            if (durationUs > 0) onProgress((presentationTime.toFloat() / durationUs).coerceIn(0f, 0.98f))
                        }
                    }
                }

                if (!decoderDone) {
                    val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outputBufferId >= 0) {
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val shouldRender = bufferInfo.size > 0
                        decoder.releaseOutputBuffer(outputBufferId, shouldRender)
                        if (shouldRender) {
                            outputSurface.awaitNewImage()
                            inputSurface.makeCurrent()
                            outputSurface.drawImage()
                            inputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000)
                            inputSurface.swapBuffers()
                        }
                        if (isEos) {
                            decoderDone = true
                            encoder.signalEndOfInputStream()
                        }
                    }
                }

                val encoderOutputId = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    encoderOutputId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outVideoTrackIndex = muxer.addTrack(encoder.outputFormat)
                        if (audioFormat != null) outAudioTrackIndex = muxer.addTrack(audioFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    encoderOutputId >= 0 -> {
                        val encodedData = encoder.getOutputBuffer(encoderOutputId)!!
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(outVideoTrackIndex, encodedData, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(encoderOutputId, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encoderDone = true
                        }
                    }
                }
            }

            if (audioTrackIndex != null && audioFormat != null && outAudioTrackIndex >= 0 && muxerStarted) {
                copyAudioTrack(audioExtractor, outAudioTrackIndex, muxer)
            }

            muxer.stop()
            muxer.release()
            muxerHolder = null
            pfd.close()
            pfdHolder = null

            finalizePendingVideoEntry(context, uri)
            onProgress(1f)
            return VideoCompressResult(uri)
        } catch (e: CancellationException) {
            outputUri?.let { safeDeletePendingEntry(context, it) }
            throw e
        } catch (e: Exception) {
            outputUri?.let { safeDeletePendingEntry(context, it) }
            return VideoCompressResult(null, e.message ?: "Falha desconhecida ao comprimir o vídeo")
        } finally {
            try { decoderHolder?.stop() } catch (_: Exception) {}
            try { decoderHolder?.release() } catch (_: Exception) {}
            try { encoderHolder?.stop() } catch (_: Exception) {}
            try { encoderHolder?.release() } catch (_: Exception) {}
            try { inputSurfaceHolder?.release() } catch (_: Exception) {}
            try { outputSurfaceHolder?.release() } catch (_: Exception) {}
            try {
                muxerHolder?.let { m ->
                    if (muxerStarted) try { m.stop() } catch (_: Exception) {}
                    m.release()
                }
            } catch (_: Exception) {}
            try { pfdHolder?.close() } catch (_: Exception) {}
            try { videoExtractorHolder?.release() } catch (_: Exception) {}
            try { audioExtractorHolder?.release() } catch (_: Exception) {}
        }
    }

    private fun copyAudioTrack(extractor: MediaExtractor, outTrackIndex: Int, muxer: MediaMuxer) {
        val bufferInfo = MediaCodec.BufferInfo()
        val buffer = ByteBuffer.allocate(1 shl 20)
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            bufferInfo.offset = 0
            bufferInfo.size = size
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(outTrackIndex, buffer, bufferInfo)
            extractor.advance()
        }
    }

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return null
    }

    // "targetMaxDimension" sempre se refere ao MAIOR lado do vídeo, funcionando igual pra
    // vídeos na horizontal ou na vertical -- a mesma convenção já usada pro redimensionamento
    // de imagens na aba de compressão do editor. As dimensões finais são sempre pares
    // (exigência comum de encoders H.264).
    private fun scaledDimensions(srcWidth: Int, srcHeight: Int, targetMaxDimension: Int): Pair<Int, Int> {
        val largestSide = maxOf(srcWidth, srcHeight)
        if (largestSide <= targetMaxDimension) {
            return evenize(srcWidth) to evenize(srcHeight)
        }
        val scale = targetMaxDimension.toFloat() / largestSide
        return evenize((srcWidth * scale).toInt()) to evenize((srcHeight * scale).toInt())
    }

    private fun evenize(value: Int): Int {
        val v = value.coerceAtLeast(2)
        return if (v % 2 == 0) v else v - 1
    }

    private fun createPendingVideoEntry(context: Context, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Galeria")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        return context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun finalizePendingVideoEntry(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    private fun safeDeletePendingEntry(context: Context, uri: Uri) {
        try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
    }
}

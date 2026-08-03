package com.galeria.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import com.galeria.data.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SafUtils {

    // Limites de segurança pra não deixar o app preso escaneando uma pasta gigante (ou a raiz
    // do armazenamento inteiro, caso o usuário escolha por engano) por tempo indefinido.
    private const val MAX_DEPTH = 8
    private const val MAX_ITEMS = 4000

    /**
     * Lê as imagens e vídeos de uma árvore de pastas concedida via Storage Access Framework.
     * Útil para pastas que o Android não indexa no MediaStore (ex.: com .nomedia),
     * como certas pastas de apps de mensagens.
     *
     * Usa DocumentsContract.buildChildDocumentsUriUsingTree + uma única consulta por pasta
     * (retornando nome/tipo/tamanho/data de todos os filhos de uma vez). A API DocumentFile
     * (usada antes) parece simples mas cada propriedade lida (isDirectory, type, name,
     * lastModified, length) de cada arquivo dispara uma consulta separada ao provedor -- numa
     * pasta com centenas ou milhares de arquivos (comum em pastas de apps de mensagens), isso
     * virava milhares de idas e vindas e travava o app "carregando" sem nunca terminar.
     */
    suspend fun loadImagesFromTree(context: Context, treeUri: Uri, bucketLabel: String): List<Photo> =
        withContext(Dispatchers.IO) {
            val rootDocumentId = try {
                DocumentsContract.getTreeDocumentId(treeUri)
            } catch (_: Exception) {
                return@withContext emptyList()
            }

            val result = mutableListOf<Photo>()
            collectImages(context, treeUri, rootDocumentId, bucketLabel, result, depth = 0)
            result
        }

    private fun collectImages(
        context: Context,
        treeUri: Uri,
        parentDocumentId: String,
        bucketLabel: String,
        out: MutableList<Photo>,
        depth: Int
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_ITEMS) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE
        )

        val cursor = try {
            context.contentResolver.query(childrenUri, projection, null, null, null)
        } catch (_: Exception) {
            null
        } ?: return

        cursor.use { c ->
            val idIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modifiedIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeIdx = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            if (idIdx < 0) return

            while (c.moveToNext() && out.size < MAX_ITEMS) {
                val docId = c.getString(idIdx) ?: continue
                val mimeType = (if (mimeIdx >= 0) c.getString(mimeIdx) else null) ?: ""
                val name = (if (nameIdx >= 0) c.getString(nameIdx) else null) ?: "sem_nome"
                val lastModified = if (modifiedIdx >= 0) c.getLong(modifiedIdx) else 0L
                val size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L

                when {
                    mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> {
                        collectImages(context, treeUri, docId, bucketLabel, out, depth + 1)
                    }
                    mimeType.startsWith("image/") -> {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        out.add(
                            Photo(
                                id = docUri.toString().hashCode().toLong(),
                                uri = docUri,
                                displayName = name,
                                dateTakenMillis = lastModified,
                                dateAddedMillis = lastModified,
                                width = 0,
                                height = 0,
                                sizeBytes = size,
                                mimeType = mimeType,
                                bucketName = bucketLabel,
                                path = docUri.toString(),
                                isVideo = false
                            )
                        )
                    }
                    mimeType.startsWith("video/") -> {
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val duration = readVideoDuration(context, docUri)
                        out.add(
                            Photo(
                                id = docUri.toString().hashCode().toLong(),
                                uri = docUri,
                                displayName = name,
                                dateTakenMillis = lastModified,
                                dateAddedMillis = lastModified,
                                width = 0,
                                height = 0,
                                sizeBytes = size,
                                mimeType = mimeType,
                                bucketName = bucketLabel,
                                path = docUri.toString(),
                                isVideo = true,
                                durationMs = duration
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readVideoDuration(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}

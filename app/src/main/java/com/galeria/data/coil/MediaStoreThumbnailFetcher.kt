package com.galeria.data.coil

import android.content.ContentResolver
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.pxOrElse

/**
 * Busca miniaturas de fotos e vídeos usando o cache de thumbnails do próprio Android
 * (ContentResolver#loadThumbnail, disponível a partir do Android 10/API 29), em vez de o
 * Coil precisar abrir e decodificar o arquivo de mídia original inteiro a cada célula da
 * grade. Essa é a mesma via rápida usada pelo Google Fotos e outros apps de galeria — o
 * sistema já mantém essas miniaturas geradas e em cache, então buscar por aqui é muito mais
 * leve que decodificar um JPEG de 12MP (ou pior, extrair um frame de vídeo) só para exibir
 * numa célula pequena da grade. É o principal motivo de travamentos ao rolar rápido.
 *
 * Em versões mais antigas que o Android 10 (API < 29), essa API não existe: o Fetcher.Factory
 * retorna null nesse caso e o Coil segue com seu comportamento padrão (decodificar o arquivo
 * inteiro e reduzir a amostragem), um pouco mais pesado mas ainda funcional.
 */
class MediaStoreThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // Usa o tamanho alvo que o Coil calculou a partir das constraints do Composable
        // (ex.: a célula da grade), com um teto razoável para não pedir um bitmap enorme
        // quando o alvo for "sem restrição" (ex.: no visualizador em tela cheia).
        val targetWidth = options.size.width.pxOrElse { 1024 }.coerceIn(1, 2048)
        val targetHeight = options.size.height.pxOrElse { 1024 }.coerceIn(1, 2048)

        val bitmap = context.contentResolver.loadThumbnail(
            uri,
            Size(targetWidth, targetHeight),
            null
        )

        return DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val appContext: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            if (data.scheme != ContentResolver.SCHEME_CONTENT) return null
            if (data.authority != "media") return null

            // Só usa o atalho de thumbnail do sistema para pedidos de tamanho pequeno (células
            // da grade). O visualizador em tela cheia (onde dá pra dar zoom/pinça) pede um
            // tamanho maior — nesse caso deixamos o Coil decodificar o arquivo original, para
            // não servir uma miniatura de baixa resolução quando o usuário amplia a foto.
            val requestedWidth = options.size.width.pxOrElse { Int.MAX_VALUE }
            val requestedHeight = options.size.height.pxOrElse { Int.MAX_VALUE }
            if (requestedWidth > GRID_THUMBNAIL_MAX_PX || requestedHeight > GRID_THUMBNAIL_MAX_PX) {
                return null
            }

            return MediaStoreThumbnailFetcher(appContext, data, options)
        }
    }

    private companion object {
        // Bem acima do que qualquer célula de grade (mesmo em telas grandes) vai pedir, mas
        // bem abaixo do que o visualizador em tela cheia pede — separa os dois casos de uso.
        const val GRID_THUMBNAIL_MAX_PX = 720
    }
}

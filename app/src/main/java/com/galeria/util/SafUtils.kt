package com.galeria.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.galeria.data.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SafUtils {

    /**
     * Lê as imagens de uma árvore de pastas concedida via Storage Access Framework.
     * Útil para pastas que o Android não indexa no MediaStore (ex.: com .nomedia),
     * como certas pastas de apps de mensagens.
     */
    suspend fun loadImagesFromTree(context: Context, treeUri: Uri, bucketLabel: String): List<Photo> =
        withContext(Dispatchers.IO) {
            val tree = try {
                DocumentFile.fromTreeUri(context, treeUri)
            } catch (_: Exception) {
                null
            } ?: return@withContext emptyList()

            val result = mutableListOf<Photo>()
            collectImages(tree, bucketLabel, result)
            result
        }

    private fun collectImages(dir: DocumentFile, bucketLabel: String, out: MutableList<Photo>) {
        val children = try { dir.listFiles() } catch (_: Exception) { emptyArray() }
        for (doc in children) {
            if (doc.isDirectory) {
                collectImages(doc, bucketLabel, out)
            } else if (doc.isFile && (doc.type?.startsWith("image/") == true)) {
                out.add(
                    Photo(
                        id = doc.uri.toString().hashCode().toLong(),
                        uri = doc.uri,
                        displayName = doc.name ?: "imagem",
                        dateTakenMillis = doc.lastModified(),
                        dateAddedMillis = doc.lastModified(),
                        width = 0,
                        height = 0,
                        sizeBytes = doc.length(),
                        mimeType = doc.type ?: "image/*",
                        bucketName = bucketLabel,
                        path = doc.uri.toString()
                    )
                )
            }
        }
    }
}

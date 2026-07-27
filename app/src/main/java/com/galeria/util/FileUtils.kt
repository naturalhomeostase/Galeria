package com.galeria.util

object FileUtils {
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }
        return "%.1f %s".format(value, units[unitIndex])
    }
}

fun shareMultiplePhotos(context: android.content.Context, uriStrings: List<String>) {
    if (uriStrings.isEmpty()) return
    val uris = ArrayList(uriStrings.map { android.net.Uri.parse(it) })
    val intent = if (uris.size == 1) {
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uris.first())
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(android.content.Intent.createChooser(intent, null))
}

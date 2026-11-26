package com.pulldock.data.download

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Filesystem/MediaStore URI operations shared by the download and library repositories.
 * Centralizes the `file`/`content` scheme handling and private-URL redaction so the two
 * repositories cannot drift apart.
 */
internal object UriFileOps {

    /** Never persist raw private URLs (data-model.md §4.7): keep only scheme/host. */
    fun sanitizeSource(source: String): String = runCatching {
        val uri = Uri.parse(source)
        if (uri.scheme == null) "<local>" else "${uri.scheme}://${uri.host ?: "<unknown>"}"
    }.getOrDefault("<local>")

    fun exists(context: Context, uriString: String): Boolean = try {
        val uri = Uri.parse(uriString)
        when (uri.scheme) {
            "file" -> File(uri.path ?: return false).exists()
            "content" -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            else -> false
        }
    } catch (_: Exception) {
        false
    }

    fun deleteUri(context: Context, uriString: String): Boolean = try {
        val uri = Uri.parse(uriString)
        when (uri.scheme) {
            "file" -> File(uri.path ?: return false).delete()
            "content" -> android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
            else -> false
        }
    } catch (_: Exception) {
        false
    }
}
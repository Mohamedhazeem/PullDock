package com.pulldock.core.designsystem.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Displays a locally generated thumbnail (video frame, audio artwork, or image file).
 * Falls back to [fallback] while loading or when no thumbnail exists.
 */
@Composable
fun ThumbnailImage(
    uri: String?,
    fallback: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    // Stored thumbnails use `File.toURI()` (single-slash `file:/…`), so parse the Uri
    // instead of stripping a literal "file://" prefix (which never matched and left the
    // bitmap null, showing only the fallback icon).
    val file = remember(uri) {
        uri?.let { value ->
            val parsed = Uri.parse(value)
            if (parsed.scheme == "file") parsed.path?.let(::File) else null
        }
    }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            file?.let {
                runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(
                imageVector = fallback,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
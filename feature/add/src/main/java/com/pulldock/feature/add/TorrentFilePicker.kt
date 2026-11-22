package com.pulldock.feature.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * System file picker for `.torrent` documents (T065, FR-001). SAF only — no broad
 * storage permission; the returned content URI is handed to the add flow, which reads
 * it through the engine adapter with a bounded read.
 */
class TorrentFilePicker internal constructor(
    private val launcher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, android.net.Uri?>,
) {
    fun launch() = launcher.launch(arrayOf("application/x-bittorrent", "application/octet-stream"))
}

@Composable
fun rememberTorrentFilePicker(onPicked: (String) -> Unit): TorrentFilePicker {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { picked ->
            // Take a persistable grant when offered so the engine can re-read later.
            onPicked(picked.toString())
        }
    }
    return remember(launcher) { TorrentFilePicker(launcher) }
}

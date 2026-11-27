package com.pulldock.data.download

import android.content.Context
import android.os.Environment
import com.pulldock.domain.download.repository.MediaStorageDirectory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves the download/extraction root: shared /PullDock when granted, else app storage. */
@Singleton
class MediaStorageDirectoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaStorageDirectory {

    override suspend fun downloadsDir(): String? {
        val shared = File(Environment.getExternalStorageDirectory(), "PullDock")
        val sharedReady = Environment.isExternalStorageManager() &&
            (shared.exists() || shared.mkdirs())
        val base = if (sharedReady) {
            shared
        } else {
            val appBase = context.getExternalFilesDir(null) ?: return null
            File(appBase, "PullDock").apply { mkdirs() }
        }
        return base.absolutePath
    }
}
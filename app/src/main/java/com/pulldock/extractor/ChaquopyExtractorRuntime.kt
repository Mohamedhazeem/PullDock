package com.pulldock.extractor

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.domain.extractor.ExtractorRuntime
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.MediaFormat
import com.pulldock.domain.extractor.model.RuntimeVerificationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import org.json.JSONObject

/**
 * Chaquopy-backed [ExtractorRuntime] (ADR-0012). Python modules are baked into the APK
 * at build time; no remote executable code is fetched at runtime. All values crossing
 * this boundary are plain Kotlin types parsed from JSON.
 */
@Singleton
class ChaquopyExtractorRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExtractorRuntime {

    private val version = "2026.11.23"

    private fun module() = try {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        Python.getInstance().getModule("pulldock_extractor")
    } catch (_: Throwable) {
        null
    }

    override suspend fun info(url: String, providerId: ProviderId): ExtractorResult<ExtractorRuntime.MediaInfo> {
        val mod = module() ?: return ExtractorResult.Failure(
            DownloadError.ExtractorEngine(DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE),
        )
        return try {
            val json = JSONObject(withContext(kotlinx.coroutines.Dispatchers.IO) {
                mod.callAttr("probe", url).toString()
            })
            if (json.has("error")) return ExtractorResult.Failure(mapError(json.optString("error", "")))
            val formats = mutableListOf<MediaFormat>()
            val array = json.optJSONArray("formats")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val f = array.getJSONObject(i)
                    formats += MediaFormat(
                        id = f.optString("id"),
                        resolution = f.optString("resolution").ifBlank { null },
                        container = f.optString("container").ifBlank { "mp4" },
                        videoCodec = f.optString("videoCodec").ifBlank { null },
                        audioCodec = f.optString("audioCodec").ifBlank { null },
                        estimatedBytes = f.optLong("estimatedBytes", 0L).takeIf { it > 0 },
                        mergeRequired = f.optBoolean("mergeRequired", false),
                        processingEstimateSeconds = null,
                    )
                }
            }
            ExtractorResult.Success(
                ExtractorRuntime.MediaInfo(
                    sourceUrl = url,
                    providerId = providerId,
                    title = json.optString("title").ifBlank { "Media" },
                    durationSeconds = json.optLong("durationSeconds", 0L).takeIf { it > 0 },
                    thumbnailUrl = json.optString("thumbnailUrl").ifBlank { null },
                    formats = formats,
                ),
            )
        } catch (t: Throwable) {
            ExtractorResult.Failure(mapThrowable(t))
        }
    }

    override suspend fun download(
        url: String,
        formatId: String,
        outputPath: String,
        onProgress: (Long, Long?) -> Unit,
    ): ExtractorResult<String> {
        val mod = module() ?: return ExtractorResult.Failure(
            DownloadError.ExtractorEngine(DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE),
        )
        val poller: Job? = launchPoller(outputPath, onProgress)
        return try {
            val json = JSONObject(withContext(kotlinx.coroutines.Dispatchers.IO) {
                mod.callAttr("download", url, formatId, outputPath).toString()
            })
            if (json.has("error")) ExtractorResult.Failure(mapError(json.optString("error", "")))
            else ExtractorResult.Success(json.optString("path"))
        } catch (t: Throwable) {
            ExtractorResult.Failure(mapThrowable(t))
        } finally {
            poller?.cancel()
        }
    }

    private suspend fun launchPoller(outputPath: String, onProgress: (Long, Long?) -> Unit): Job? {
        val scope = coroutineContext[Job] ?: return null
        return kotlinx.coroutines.CoroutineScope(scope).launch {
            val prefix = File(outputPath).name
            val dir = File(outputPath).parentFile ?: return@launch
            while (isActive) {
                delay(300)
                val bytes = dir.listFiles()?.filter { it.name.startsWith(prefix + ".") }?.sumOf { it.length() } ?: 0L
                onProgress(bytes, null)
            }
        }
    }

    override fun runtimeInfo(): ExtractorRuntimeInfo = ExtractorRuntimeInfo(
        component = "yt-dlp",
        installedVersion = version,
        requiredRange = "pinned:$version",
        runtimeCompatible = module() != null,
        channel = "stable",
        verificationStatus = if (module() != null) RuntimeVerificationStatus.VERIFIED else RuntimeVerificationStatus.UNAVAILABLE,
        knownLimitations = listOf(
            "Progressive formats only; merge-required formats need a bundled FFmpeg (ADR-0012).",
            "Provider anti-bot challenges may fail with typed provider errors.",
        ),
        checkedAt = Instant.now(),
        inventoryId = "yt-dlp-$version",
    )

    private fun mapError(code: String): DownloadError = when {
        code.contains("Network", true) || code.contains("Timeout", true) || code.contains("HTTP", true) ->
            DownloadError.Network(com.pulldock.core.model.DownloadError.NetworkReason.OFFLINE)
        code.contains("Format", true) -> DownloadError.ExtractorEngine(DownloadError.ExtractorReason.NO_FORMATS)
        else -> DownloadError.ExtractorEngine(DownloadError.ExtractorReason.PROVIDER_CHANGED)
    }

    private fun mapThrowable(t: Throwable): DownloadError = when {
        t is kotlinx.coroutines.CancellationException -> throw t
        else -> DownloadError.ExtractorEngine(DownloadError.ExtractorReason.UNSUPPORTED)
    }
}

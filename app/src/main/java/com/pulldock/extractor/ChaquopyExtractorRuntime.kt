package com.pulldock.extractor

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.pulldock.core.model.DownloadError
import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.ExtractorResult
import com.pulldock.core.model.AudioFormat
import com.pulldock.domain.extractor.AudioExtractor
import com.pulldock.domain.extractor.ExtractorRuntime
import com.pulldock.domain.extractor.model.ExtractorRuntimeInfo
import com.pulldock.domain.extractor.model.MediaFormat
import com.pulldock.domain.extractor.model.PlaylistEntry
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
import java.nio.ByteBuffer
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
    private val audioExtractor: AudioExtractor,
) : ExtractorRuntime {

    private val version = "2026.11.23"

    private fun module() = try {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        Python.getInstance().getModule("pulldock_extractor")
    } catch (_: Throwable) {
        null
    }

    /**
     * The first call can race Chaquopy's one-time startup (the module is still loading), which
     * otherwise surfaces as "Extractor runtime unavailable". Retry briefly until it is ready.
     */
    private fun moduleReady() = run {
        var result = module()
        var attempts = 0
        while (result == null && attempts < 5) {
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                break
            }
            result = module()
            attempts++
        }
        result
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    override suspend fun info(url: String, providerId: ProviderId): ExtractorResult<ExtractorRuntime.MediaInfo> {
        val mod = withContext(kotlinx.coroutines.Dispatchers.IO) { moduleReady() } ?: return ExtractorResult.Failure(
            DownloadError.ExtractorEngine(DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE),
        )
        return try {
            val json = JSONObject(withContext(kotlinx.coroutines.Dispatchers.IO) {
                mod.callAttr("probe", url).toString()
            })
            if (json.has("error")) {
                return ExtractorResult.Failure(mapError(json.optString("error", "")))
            }
            val formats = mutableListOf<MediaFormat>()
            val array = json.optJSONArray("formats")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val f = array.getJSONObject(i)
                    val id = f.stringOrNull("id")
                    if (id == null) continue
                    formats += MediaFormat(
                        id = id,
                        resolution = f.stringOrNull("resolution"),
                        container = f.stringOrNull("container") ?: "mp4",
                        videoCodec = f.stringOrNull("videoCodec"),
                        audioCodec = f.stringOrNull("audioCodec"),
                        estimatedBytes = f.optLong("estimatedBytes", 0L).takeIf { it > 0 },
                        mergeRequired = f.optBoolean("mergeRequired", false),
                        processingEstimateSeconds = null,
                        language = f.stringOrNull("language"),
                        languagePreference = if (f.isNull("languagePreference")) null else f.optInt("languagePreference"),
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

    override suspend fun playlist(
        url: String,
        providerId: ProviderId,
    ): ExtractorResult<ExtractorRuntime.PlaylistInfo> {
        val mod = withContext(kotlinx.coroutines.Dispatchers.IO) { moduleReady() } ?: return ExtractorResult.Failure(
            DownloadError.ExtractorEngine(DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE),
        )
        return try {
            val json = JSONObject(withContext(kotlinx.coroutines.Dispatchers.IO) {
                mod.callAttr("playlist", url).toString()
            })
            if (json.has("error")) {
                val code = json.optString("error", "")
                if (code == "NOT_PLAYLIST") {
                    return ExtractorResult.Failure(
                        DownloadError.ExtractorEngine(
                            DownloadError.ExtractorReason.UNSUPPORTED,
                            "not a playlist",
                        ),
                    )
                }
                return ExtractorResult.Failure(mapError(code))
            }
            val entries = mutableListOf<PlaylistEntry>()
            val array = json.optJSONArray("entries")
            if (array != null) {
                for (i in 0 until array.length()) {
                    val e = array.getJSONObject(i)
                    val entryUrl = e.stringOrNull("url") ?: continue
                    entries += PlaylistEntry(
                        index = e.optInt("index", i),
                        title = e.optString("title").ifBlank { "Entry ${i + 1}" },
                        url = entryUrl,
                        durationSeconds = e.optLong("durationSeconds", 0L).takeIf { it > 0 },
                        thumbnailUrl = e.stringOrNull("thumbnailUrl"),
                    )
                }
            }
            ExtractorResult.Success(
                ExtractorRuntime.PlaylistInfo(
                    sourceUrl = url,
                    providerId = providerId,
                    title = json.optString("title").ifBlank { "Playlist" },
                    entries = entries,
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
        height: Int?,
    ): ExtractorResult<String> {
        if (formatId.startsWith("audio:")) {
            return downloadAudio(url, formatId, outputPath, onProgress)
        }
        val mod = withContext(kotlinx.coroutines.Dispatchers.IO) { moduleReady() } ?: return ExtractorResult.Failure(
            DownloadError.ExtractorEngine(DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE),
        )
        val poller: Job? = launchPoller(outputPath, onProgress)
        return try {
            val json = JSONObject(withContext(kotlinx.coroutines.Dispatchers.IO) {
                mod.callAttr("download", url, formatId, outputPath, height ?: 0).toString()
            })
            if (json.has("error")) {
                android.util.Log.i("PullDock", "dl err " + json.optString("error") + " fmt=" + formatId)
                ExtractorResult.Failure(mapError(json.optString("error", "")))
            } else {
                val path = json.optString("path")
                if (path.isBlank()) {
                    ExtractorResult.Failure(
                        DownloadError.ExtractorEngine(DownloadError.ExtractorReason.UNSUPPORTED, "no output"),
                    )
                } else if (json.optBoolean("raw", false)) {
                    // Fallback HLS path produced a raw .ts/.m4s; remux to .mp4 (ADR-0013).
                    remuxToMp4(path, outputPath)
                } else {
                    ExtractorResult.Success(path)
                }
            }
        } catch (t: Throwable) {
            ExtractorResult.Failure(mapThrowable(t))
        } finally {
            poller?.cancel()
        }
    }

    override suspend fun downloadMuxed(
        url: String,
        videoFormatId: String,
        audioFormatId: String,
        videoCodec: String?,
        audioCodec: String?,
        container: String,
        outputPath: String,
        onProgress: (Long, Long?) -> Unit,
        height: Int?,
    ): ExtractorResult<String> {
        val mod = withContext(kotlinx.coroutines.Dispatchers.IO) { moduleReady() } ?: return ExtractorResult.Failure(
            DownloadError.ExtractorEngine(DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE),
        )
        val output = File(outputPath).absoluteFile
        val videoPath = File(output.parentFile, output.name + ".video").absolutePath
        val audioPath = File(output.parentFile, output.name + ".audio").absolutePath
        val poller = launchPoller(listOf(videoPath, audioPath), onProgress)
        return try {
            val json = JSONObject(withContext(kotlinx.coroutines.Dispatchers.IO) {
                mod.callAttr(
                    "download_muxed",
                    url,
                    videoFormatId,
                    audioFormatId,
                    videoPath,
                    audioPath,
                    height ?: 0,
                ).toString()
            })
            if (json.has("error")) {
                ExtractorResult.Failure(mapError(json.optString("error", "")))
            } else {
                val video = json.optString("video")
                val audio = json.optString("audio")
                if (video.isBlank() || audio.isBlank()) {
                    ExtractorResult.Failure(
                        DownloadError.ExtractorEngine(DownloadError.ExtractorReason.UNSUPPORTED, "no muxed output"),
                    )
                } else {
                    mux(video, audio, outputPath, videoCodec, audioCodec, container)
                }
            }
        } catch (t: Throwable) {
            ExtractorResult.Failure(mapThrowable(t))
        } finally {
            poller?.cancel()
        }
    }

    /**
     * Audio-only download: id is `audio:<target>:<sourceId>`. Downloads the source (native
     * audio format, or the muxed video for derived audio) to a temp, then extracts the target
     * format with FFmpegKit and deletes the source temp.
     */
    private suspend fun downloadAudio(
        url: String,
        formatId: String,
        outputPath: String,
        onProgress: (Long, Long?) -> Unit,
    ): ExtractorResult<String> {
        val rest = formatId.removePrefix("audio:")
        val split = rest.indexOf(':')
        if (split <= 0 || split >= rest.length - 1) {
            return ExtractorResult.Failure(
                DownloadError.ExtractorEngine(
                    DownloadError.ExtractorReason.UNSUPPORTED,
                    "invalid audio format id",
                ),
            )
        }
        val target = AudioFormat.fromId(rest.substring(0, split))
        val sourceId = rest.substring(split + 1)
        val mod = withContext(kotlinx.coroutines.Dispatchers.IO) { moduleReady() } ?: return ExtractorResult.Failure(
            DownloadError.ExtractorEngine(DownloadError.ExtractorReason.RUNTIME_INCOMPATIBLE),
        )
        val srcBase = outputPath + ".src"
        val poller = launchPoller(listOf(srcBase), onProgress)
        var sourcePath: String? = null
        try {
            val json = JSONObject(withContext(kotlinx.coroutines.Dispatchers.IO) {
                mod.callAttr("download", url, sourceId, srcBase, 0).toString()
            })
            if (json.has("error")) return ExtractorResult.Failure(mapError(json.optString("error", "")))
            sourcePath = json.optString("path").takeIf { it.isNotBlank() }
                ?: return ExtractorResult.Failure(
                    DownloadError.ExtractorEngine(
                        DownloadError.ExtractorReason.UNSUPPORTED,
                        "no source audio",
                    ),
                )
            return audioExtractor.extract(sourcePath, target, outputPath)
        } finally {
            poller?.cancel()
            sourcePath?.let { runCatching { File(it).delete() } }
            runCatching { File(srcBase + ".total").delete() }
            runCatching { File(srcBase + ".progress").delete() }
        }
    }

    private suspend fun remuxToMp4(rawPath: String, outputPath: String): ExtractorResult<String> =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val raw = File(rawPath)
            if (!raw.isFile || raw.length() <= 0L) {
                runCatching { raw.delete() }
                return@withContext ExtractorResult.Failure(
                    DownloadError.ExtractorEngine(
                        DownloadError.ExtractorReason.UNSUPPORTED,
                        "no raw output",
                    ),
                )
            }
            // Fallback: the raw HLS file is a valid MPEG-TS that ExoPlayer plays natively.
            // If FFmpeg cannot remux (codec/timestamp quirks), publish the raw .ts instead
            // of failing the whole task.
            fun publishRaw(): ExtractorResult<String> = ExtractorResult.Success(rawPath)
            try {
                val session = FFmpegKit.execute(
                    "-y -fflags +genpts -i \"$rawPath\" -c copy " +
                        "-avoid_negative_ts make_zero \"$outputPath\"",
                )
                val out = File(outputPath)
                val ok = ReturnCode.isSuccess(session.returnCode) && out.isFile && out.length() > 0L
                if (ok) {
                    runCatching { raw.delete() }
                    ExtractorResult.Success(outputPath)
                } else {
                    publishRaw()
                }
            } catch (t: Throwable) {
                publishRaw()
            }
        }

    private fun mux(
        videoPath: String,
        audioPath: String,
        outputPath: String,
        videoCodec: String?,
        audioCodec: String?,
        container: String,
    ): ExtractorResult<String> {
        // FFmpegKit mux (fast, native) replaces the slow MediaMuxer sample-copy loop, which
        // could stall for minutes or throw on non-monotonic DTS for large DASH streams.
        // Container choice matters: VP8/VP9 normally live in WebM, but WebM cannot carry AAC
        // audio (e.g. Instagram's mp4a track), so a VP9+AAC pair must stay in MP4 (both are
        // valid there). Only retarget to WebM when the audio is WebM-compatible (Opus/Vorbis).
        val vp = videoCodec?.contains("vp8", true) == true || videoCodec?.contains("vp9", true) == true
        val aac = audioCodec?.contains("mp4a", true) == true || audioCodec?.contains("aac", true) == true
        val target = when {
            vp && aac -> if (outputPath.endsWith(".webm", true)) {
                outputPath.substringBeforeLast('.') + ".mp4"
            } else {
                outputPath
            }
            vp && outputPath.endsWith(".mp4", true) ->
                outputPath.substringBeforeLast('.') + ".webm"
            else -> outputPath
        }
        return try {
            val session = FFmpegKit.execute(
                "-y -i \"$videoPath\" -i \"$audioPath\" -map 0:v:0 -map 1:a:0 -c copy \"$target\"",
            )
            val out = File(target)
            if (ReturnCode.isSuccess(session.returnCode) && out.isFile && out.length() > 0L) {
                ExtractorResult.Success(target)
            } else {
                ExtractorResult.Failure(
                    DownloadError.ExtractorEngine(
                        DownloadError.ExtractorReason.UNSUPPORTED,
                        "mux failed",
                    ),
                )
            }
        } catch (t: Throwable) {
            ExtractorResult.Failure(mapThrowable(t))
        }
    }

    private suspend fun launchPoller(outputPath: String, onProgress: (Long, Long?) -> Unit): Job? =
        launchPoller(listOf(outputPath), onProgress)

    private suspend fun launchPoller(paths: List<String>, onProgress: (Long, Long?) -> Unit): Job? {
        val scope = coroutineContext[Job] ?: return null
        val names = paths.map { File(it).name }
        val dir = paths.firstNotNullOfOrNull { File(it).parentFile } ?: return null
        val totalFile = File(paths.first() + ".total")
        val progressFile = File(paths.first() + ".progress")
        return kotlinx.coroutines.CoroutineScope(scope).launch {
            while (isActive) {
                delay(300)
                val fileBytes = dir.listFiles()
                    ?.filter { file -> names.any { file.name.startsWith(it + ".") } }
                    ?.sumOf { it.length() } ?: 0L
                // The Python downloader publishes cumulative bytes to a ".progress" sidecar
                // (hlsfetch writes the output only at the end) and the total to ".total".
                val bytes = if (progressFile.isFile) {
                    progressFile.readText().trim().toLongOrNull() ?: fileBytes
                } else {
                    fileBytes
                }
                val total = if (totalFile.isFile) {
                    totalFile.readText().trim().toLongOrNull()?.takeIf { it > 0L }
                } else {
                    null
                }
                onProgress(bytes, total)
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
            "Video-only formats are merged with a separate audio stream using Android MediaMuxer.",
            "Provider anti-bot challenges may fail with typed provider errors.",
        ),
        checkedAt = Instant.now(),
        inventoryId = "yt-dlp-$version",
    )

    private fun mapError(code: String): DownloadError = when {
        code.contains("Network", true) || code.contains("Timeout", true) || code.contains("HTTP", true) ->
            DownloadError.Network(com.pulldock.core.model.DownloadError.NetworkReason.OFFLINE)
        code.contains("Format", true) -> DownloadError.ExtractorEngine(DownloadError.ExtractorReason.NO_FORMATS)
        code.contains("NO_OUTPUT", true) -> DownloadError.ExtractorEngine(
            DownloadError.ExtractorReason.UNSUPPORTED,
            "no output",
        )
        code.contains("NO_AUDIO", true) -> DownloadError.ExtractorEngine(
            DownloadError.ExtractorReason.UNSUPPORTED,
            "no audio stream",
        )
        code.contains("SNIFF_FAILED", true) -> DownloadError.ExtractorEngine(
            DownloadError.ExtractorReason.UNSUPPORTED,
            "no media link found",
        )
        code.contains("HLS_FAILED", true) -> DownloadError.ExtractorEngine(
            DownloadError.ExtractorReason.UNSUPPORTED,
            "hls download failed",
        )
        else -> DownloadError.ExtractorEngine(DownloadError.ExtractorReason.PROVIDER_CHANGED)
    }

    private fun mapThrowable(t: Throwable): DownloadError = when {
        t is kotlinx.coroutines.CancellationException -> throw t
        else -> DownloadError.ExtractorEngine(DownloadError.ExtractorReason.UNSUPPORTED)
    }
}

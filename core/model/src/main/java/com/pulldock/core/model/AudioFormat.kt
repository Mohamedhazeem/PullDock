package com.pulldock.core.model

/**
 * Target container/codec for an audio-only download or extraction (FR-016 extension).
 * The ffmpeg arguments are passed to FFmpegKit; the source is always re-encoded to the
 * target so a raw ADTS AAC or any source container is handled uniformly.
 *
 * FLAC is intentionally excluded: the Android `ffmpeg-kit-full` AAR does not ship the
 * `flac` encoder. Playback still supports FLAC; only extraction targets are listed here.
 */
enum class AudioFormat(
    val id: String,
    val extension: String,
    val ffmpegArgs: String,
) {
    MP3("mp3", "mp3", "-c:a libmp3lame -b:a 192k"),
    M4A("m4a", "m4a", "-c:a aac -b:a 192k"),
    OPUS("opus", "opus", "-c:a libopus -b:a 128k"),
    OGG("ogg", "ogg", "-c:a libvorbis -q:a 5"),
    WAV("wav", "wav", "-c:a pcm_s16le"),
    ;

    companion object {
        val DEFAULT = MP3

        fun fromId(id: String?): AudioFormat =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
package com.pulldock.core.model

/**
 * The kind of content a library item's file holds, derived from its file extension.
 *
 * This is the single source of truth for classifying downloaded files. The library UI,
 * the section filter, and the content icon all rely on it so that video/audio open in the
 * in-app player while every other kind (PDF, books, images, archives, …) is handed to an
 * external app.
 */
enum class MediaKind {
    /** Video files the in-app player can render. */
    VIDEO,

    /** Audio files the in-app player can render. */
    AUDIO,

    /** Still images (JPEG, PNG, HEIC, …). */
    IMAGE,

    /** Documents and books (PDF, EPUB, comics, archives, …). */
    DOCUMENT,

    /** Anything not recognised; opened externally. */
    OTHER;

    /** True when the in-app media player can play this content. */
    val playable: Boolean
        get() = this == VIDEO || this == AUDIO
}

/**
 * Classify a file name, path, or URI by its extension.
 *
 * Content URIs without a recognizable extension fall back to [MediaKind.OTHER] so the
 * caller opens them externally rather than forcing them through the media player.
 */
fun mediaKindOf(fileNameOrUri: String): MediaKind {
    val extension = fileNameOrUri.substringAfterLast('.', "").lowercase()
    return when (extension) {
        in VIDEO_EXTENSIONS -> MediaKind.VIDEO
        in AUDIO_EXTENSIONS -> MediaKind.AUDIO
        in IMAGE_EXTENSIONS -> MediaKind.IMAGE
        in DOCUMENT_EXTENSIONS -> MediaKind.DOCUMENT
        else -> MediaKind.OTHER
    }
}

private val VIDEO_EXTENSIONS = setOf(
    "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "3g2", "ts", "m2ts", "mpeg", "mpg",
)

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "aac", "flac", "ogg", "oga", "wav", "opus", "wma", "mid", "midi",
)

private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "tif", "tiff", "avif",
)

private val DOCUMENT_EXTENSIONS = setOf(
    "pdf", "epub", "mobi", "azw", "azw3", "djvu", "cbz", "cbr", "cb7", "txt", "md",
    "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "zip",
)
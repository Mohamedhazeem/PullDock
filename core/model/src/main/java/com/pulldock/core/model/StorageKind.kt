package com.pulldock.core.model

/**
 * The kind of storage location a download output is published to (spec FR-037).
 * Shared by the domain model (`StorageDestination`) and the platform storage contract in
 * `core:storage`; scoped storage only — no broad external storage access.
 */
enum class StorageKind {
    /** App-specific storage used for temporary and intermediate files. */
    APP_SPECIFIC,

    /** System media collection for completed public media (MediaStore). */
    MEDIA_STORE,

    /** User-selected destination via the Storage Access Framework. */
    SAF,
}

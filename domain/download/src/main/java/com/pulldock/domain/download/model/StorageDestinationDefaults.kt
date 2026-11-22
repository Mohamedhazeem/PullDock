package com.pulldock.domain.download.model

import com.pulldock.core.model.StorageKind

/**
 * Default destinations used before the user picks one (FR-037: app-specific storage
 * for temporary/intermediate output; SAF/MediaStore destination selection is wired in
 * the add flow once the storage implementation lands, T150). The default is the only
 * choice that never requires a permission prompt.
 */
object StorageDestinationDefaults {
    fun appSpecific(): StorageDestination = StorageDestination(
        kind = StorageKind.APP_SPECIFIC,
        uri = null,
        displayName = "App storage",
        freeSpaceBytes = null,
    )
}

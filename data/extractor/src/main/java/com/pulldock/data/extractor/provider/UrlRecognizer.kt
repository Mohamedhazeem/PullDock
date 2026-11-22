package com.pulldock.data.extractor.provider

import com.pulldock.core.model.ProviderId
import com.pulldock.data.extractor.runtime.BundledRuntimeCatalog

/**
 * URL recognition over the v1 provider set (FR-014, T085). Pure and dependency-free;
 * an unknown host is `UNSUPPORTED_PROVIDER` — never a bypass attempt (FR-020).
 */
object UrlRecognizer {

    /** The provider whose URL patterns match, or null when unsupported. */
    fun recognize(url: String): ProviderId? =
        BundledRuntimeCatalog.providerPatterns.entries
            .firstOrNull { (_, patterns) -> patterns.any { it.matches(url.trim()) } }
            ?.key
}

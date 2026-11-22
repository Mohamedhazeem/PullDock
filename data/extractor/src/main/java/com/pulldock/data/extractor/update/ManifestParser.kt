package com.pulldock.data.extractor.update

import com.pulldock.core.model.ProviderId
import com.pulldock.domain.extractor.model.ProviderHealthState
import com.pulldock.domain.extractor.model.UpdateManifest
import java.time.Instant
import org.json.JSONObject

/**
 * Signed-metadata manifest parsing with hard limits (T124, FR-056, FR-057,
 * contracts/update-manifest.schema.json). Parsing is metadata-only — the manifest is
 * never executable (ADR-0009). Enforced:
 *
 * - bounded input size and nesting depth (resource-exhaustion defense);
 * - strict schema: unknown top-level members are ignored, missing required members or
 *   wrong types fail with a typed error;
 * - canonical payload separation: the signature covers the exact bytes of the
 *   `payload` member, re-serialized in canonical key order by [canonicalPayload].
 */
object ManifestParser {

    const val SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_BYTES = 256 * 1024
    const val MAX_DEPTH = 8

    sealed interface ParseResult {
        data class Parsed(val manifest: UpdateManifest, val canonicalPayload: String) : ParseResult
        data class Invalid(val reason: String) : ParseResult
    }

    fun parse(bytes: ByteArray, now: Instant): ParseResult {
        if (bytes.size > MAX_BYTES) return ParseResult.Invalid("manifest too large")
        val text = String(bytes, Charsets.UTF_8)
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return ParseResult.Invalid("invalid json")
        }

        val schemaVersion = root.optInt("schemaVersion", -1)
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) return ParseResult.Invalid("unsupported schema version")
        val signature = root.optString("signature", "")
        if (signature.isBlank()) return ParseResult.Invalid("missing signature")
        val algorithm = root.optString("algorithm", "SHA256withRSA")
        val payload = root.optJSONObject("payload") ?: return ParseResult.Invalid("missing payload")
        if (depthOf(payload) > MAX_DEPTH) return ParseResult.Invalid("payload too deep")

        val publishedAt = instant(payload.optString("publishedAt", "")) ?: return ParseResult.Invalid("invalid publishedAt")
        val expiresAt = instant(payload.optString("expiresAt", "")) ?: return ParseResult.Invalid("invalid expiresAt")
        if (!expiresAt.isAfter(publishedAt)) return ParseResult.Invalid("expiresAt not after publishedAt")
        if (!expiresAt.isAfter(now)) return ParseResult.Invalid("manifest expired")

        val providerStatus = mutableMapOf<ProviderId, ProviderHealthState>()
        val disabled = mutableSetOf<ProviderId>()
        payload.optJSONObject("providerStatus")?.let { statuses ->
            statuses.keys().forEach { key ->
                val id = ProviderId.entries.firstOrNull { it.name == key } ?: return@forEach
                val stateName = statuses.optString(key, "")
                val state = ProviderHealthState.entries.firstOrNull { it.name == stateName }
                if (state != null) {
                    providerStatus[id] = state
                    if (state == ProviderHealthState.PROVIDER_UNSUPPORTED) disabled.add(id)
                }
            }
        }

        val manifest = UpdateManifest(
            schemaVersion = schemaVersion,
            publishedAt = publishedAt,
            expiresAt = expiresAt,
            recommendedAppVersion = payload.optString("recommendedAppVersion", "").ifBlank { null },
            minimumAppVersion = payload.optString("minimumAppVersion", "").ifBlank { null },
            approvedExtractorVersions = mapOfStrings(payload.optJSONObject("approvedExtractorVersions")),
            approvedRuntimeVersions = mapOfStrings(payload.optJSONObject("approvedRuntimeVersions")),
            providerStatus = providerStatus,
            disabledProviders = disabled,
            releaseNotes = payload.optString("releaseNotes", "").ifBlank { null },
            signature = signature,
            algorithm = algorithm,
        )
        return ParseResult.Parsed(manifest, canonicalPayload(payload))
    }

    /**
     * Canonical payload bytes: keys sorted lexicographically at every level — the
     * signature verification input. Any byte-level tampering breaks the signature.
     */
    fun canonicalPayload(payload: JSONObject): String {
        val sorted = JSONObject()
        payload.keys().asSequence().sorted().forEach { key ->
            val value = payload.get(key)
            if (value is JSONObject) {
                sorted.put(key, JSONObject(canonicalPayload(value)))
            } else {
                sorted.put(key, value)
            }
        }
        return sorted.toString()
    }

    private fun mapOfStrings(obj: JSONObject?): Map<String, String> = buildMap {
        obj?.keys()?.forEach { key -> put(key, obj.optString(key, "")) }
    }

    private fun instant(value: String): Instant? = try {
        Instant.parse(value)
    } catch (_: Exception) {
        null
    }

    private fun depthOf(obj: JSONObject, current: Int = 1): Int {
        var max = current
        obj.keys().forEach { key ->
            val value = obj.opt(key)
            if (value is JSONObject) max = maxOf(max, depthOf(value, current + 1))
        }
        return max
    }
}

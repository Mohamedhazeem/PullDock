# Extractor Lifecycle and Update Security

**Scope**: how PullDock manages the media-extraction runtime, its compatibility
model, and update metadata. Status: implemented core (T121–T126, T128–T129, T131–T141);
the on-device runtime PoC (T080) remains open.

## Pinned runtime

- The extractor stack (yt-dlp, yt-dlp-ejs, CPython 3.13, JS runtime, FFmpeg/FFprobe)
  is bundled, pinned, and immutable for an app release. It is never downloaded,
  replaced, or extended at runtime (FR-055, FR-058, ADR-0009).
- 64-bit only: arm64-v8a and x86_64 (FR-076).
- `BundledRuntimeCatalog` is the single inventory (FR-053): component, version,
  required range, verification status, documented limitations.

## Compatibility validation

`CompatibilityValidator` validates the combination, not just versions (data-model.md
§5.2): yt-dlp ↔ yt-dlp-ejs ↔ Python ↔ JS runtime ↔ FFmpeg/FFprobe must all be present,
runtime-compatible, and VERIFIED. `RUNTIME_INCOMPATIBLE` is reported as a typed,
actionable state — never a crash (FR-054). Until the T080 device PoC flips
`RUNTIME_VERIFIED`, resolution attempts honestly report incompatibility.

## Update metadata (never executable)

The remote signed manifest carries: schema version, publish/expiry timestamps,
recommended/minimum app versions, approved component versions, per-provider health
and disablement notices, release notes. Admission pipeline (`UpdateChannel`):

1. **Parse** (`ManifestParser`): bounded size/depth, strict schema, canonical payload
   re-serialization. Expired or malformed manifests are rejected (FR-057).
2. **Verify** (`SignatureVerifier`): SHA256withRSA over the canonical payload against
   the app-pinned public key. Any mismatch rejects outright.
3. **Admit** (`ManifestGuard`): expiration + rollback protection (publishedAt older
   than the newest accepted manifest is rejected — downgrade defense). On any failure
   the cached last-known-good manifest stays authoritative and the existing
   installation and user data are untouched (FR-060).

The pinned key is the **public half only**. Production must provision the real key at
build time; rotation ships with the app release.

## Staleness vs network failure (FR-061)

`StalenessDetector` never equates "could not check" with "outdated": network failures
surface `VERSION_CHECK_UNAVAILABLE`/`PROVIDER_TEMPORARILY_UNAVAILABLE`; only verified
manifest signals or validated provider failures produce `UPDATE_*` /
`PLATFORM_CHANGE_SUSPECTED` states.

## Provider degradation (FR-062)

One provider's failure disables only that provider (`ProviderRegistry.isolated`);
others remain usable (SC-007). Disablement is safe and reversible: a provider marked
`PROVIDER_UNSUPPORTED` is skipped at recognition time without bypass attempts (FR-020).

## Distribution

Extractor changes ship only inside complete, signed application releases through the
platform installer (FR-059). See `docs/EXTRACTOR_UPDATE_STRATEGY.md`.

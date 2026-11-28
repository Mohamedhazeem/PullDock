# ADR-0015: HTTP client for discovery providers

**Status**: Accepted | **Date**: 2026-11-28

## Context

The repository had no HTTP client (only `ConnectivityMonitor`). Feature 002 must query
external provider REST/XML APIs from the data layer, with per-request timeouts, bounded
responses, and offline unit tests.

## Decision

Use a single **Ktor Client** with the **OkHttp** engine, **kotlinx-serialization** JSON, and
Ktor **ContentNegotiation**, confined to `:data:discovery`. Versions are pinned in
`gradle/libs.versions.toml` (Ktor 3.x, kotlinx-serialization 1.9.x) and verified compatible
with Kotlin 2.4.20 / AGP 9.4.1 / minSdk 26. Torznab XML is parsed with the platform
`android.util.XmlPullParser` (no dependency).

## Alternatives

- Raw `HttpURLConnection` — no mocking, verbose; rejected.
- Retrofit + serialization — heavier and would duplicate the stack; rejected.
- OkHttp alone with manual JSON — loses coroutine ergonomics and `MockEngine`; rejected.

## Consequences

- Provider unit tests use Ktor `MockEngine` with fixtures (offline, deterministic).
- One serialization/HTTP stack only; no Retrofit.
- License (Apache-2.0) recorded in `docs/THIRD_PARTY_LICENSES.md`; pure JVM/Kotlin, no ABI
  impact.
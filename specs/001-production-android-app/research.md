# Research & Clarification Record: PullDock v1

**Feature**: `001-production-android-app`
**Date**: 2026-11-22
**Purpose**: Feasibility findings, clarification decisions, specification gaps, and the
unresolved-decision register used to prepare `/speckit-plan`.

## 1. Evidence Sources

Verified 2026-11-22. Distinguish verified facts from assumptions.

| Topic | Source | Finding |
|---|---|---|
| libtorrent4j versions/ABIs | https://libraries.io/maven/org.libtorrent4j:libtorrent4j (artifact list) | Current releases up to 2.1.0-39; platform artifacts `libtorrent4j-android-<arch>` for armeabi-v7a, arm64-v8a, x86, x86_64; Android binaries buildable via Docker. |
| libtorrent4j maintenance | https://github.com/nzh63/libretorrentEE/blob/master/NEWS.md | Libretorrent 4.1.1 (2026-07-24) temporarily downgraded libtorrent4j to keep Android 8.1-and-below support; 4.0 used libtorrent4j 2.1.0-36. Indicates newer libtorrent4j may raise the minimum supported Android version. |
| yt-dlp on Android | https://github.com/ffmpegkit-maintained/yt-dlp-android | Maintained replacement for `youtubedl-android`. Embeds CPython 3.13 via Chaquopy; AAR ~60-80 MB; arm64-v8a + x86_64 only; yt-dlp pinned at library build time (no in-app update); free tier has no TLS fingerprint impersonation. |
| yt-dlp pro variant | https://github.com/ffmpegkit-maintained/yt-dlp-android/wiki/Pro-Version | Adds `curl-cffi` TLS impersonation; arm64-v8a only; paid. |
| Android foreground services | https://developer.android.com/develop/background-work/services/fgs/changes | Android 16 (API 36): background jobs started from a foreground service must respect runtime quotas; user-initiated data transfer jobs are exempt. Android 15 (API 35): `dataSync` foreground services get 6 hours per 24 hours, then `Service.onTimeout`. |
| Android 15 dataSync budget | https://developer.android.google.cn/about/versions/15/behavior-changes-15 | 6-hour budget shared across an app's `dataSync` services; bringing the app to the foreground resets it; exceeding it throws `ForegroundServiceStartNotAllowedException`. |
| User-Initiated Data Transfer (UIDT) | https://android-developers.googleblog.com/2024/09/google-maps-improved-download-reliability-user-initiated-data-transfer-api.html | Android 14+ `setUserInitiated` JobScheduler jobs: long-duration (>10 min), user-initiated, exempt from ordinary job quotas, constraint support, progress notification. |
| Boot restrictions | https://dev.to/sivavishnu_r_1ee8971ed42d/who-gets-to-run-when-the-screen-goes-dark-android-services-under-modern-limits-1a6m | Android 15 blocks starting several foreground-service types (including `dataSync`) from `BOOT_COMPLETED`; route through WorkManager. |
| Dynamic code loading | https://developer.android.com/privacy-and-security/risks/dynamic-code-loading | Android strongly discourages dynamic code loading; remote sources violate Play policy; integrity checks and trusted local storage recommended. |
| Android 14 DCL | https://source.android.com/docs/security/enhancements | Dynamically loaded files must be marked read-only on Android 14+; avoid DCL where possible. |

## 2. Dependency Feasibility Summary

### Torrent engine

- **Feasible.** libtorrent4j is maintained and publishes Android artifacts for four ABIs.
- **Open risk:** newer libtorrent4j releases may drop support for older Android versions
  (Libretorrent downgraded to keep Android 8.1 support). With minSdk 26 and 64-bit-only
  packaging, the exact libtorrent4j version and its minimum API level MUST be verified in the
  plan's proof of concept.
- **License:** verify libtorrent license and redistribution terms before pinning.

### Media extractor

- **Feasible only with a bundled runtime.** The maintained path is Chaquopy CPython 3.13
  embedded in the app, with yt-dlp pinned at library build time.
- **Costs:** ~60-80 MB AAR, arm64-v8a + x86_64 only, no in-app yt-dlp update, and (free tier)
  no TLS fingerprint impersonation. Protected sites may require cookies or the paid variant.
- **JS runtime:** yt-dlp-ejs requires a JavaScript engine; the Android-compatible choice and
  its licensing MUST be verified before pinning.
- **Media processing:** an Android-compatible FFmpeg/FFprobe build is required for merging.
  The original FFmpegKit was retired; a maintained fork or self-built binaries must be
  selected and licensed.

### Background execution

- **No single mechanism fits all transfers.** UIDT jobs (Android 14+) are the recommended
  route for long user-initiated transfers; `dataSync` foreground services carry a 6 h/24 h
  budget on Android 15; WorkManager suits deferrable work. A per-OS-version strategy and a
  boot-recovery path that does not auto-start a restricted service are required.

### Security

- The specification's prohibition on remote dynamic code execution aligns with current
  Android guidance and Play policy. A signature on remote code is necessary but not
  sufficient; the plan MUST keep executable updates inside signed application releases.

## 3. Clarification Decisions (this session)

| # | Question | Decision |
|---|---|---|
| 1 | v1 provider set | All five: YouTube, X/Twitter, Instagram, Facebook, TikTok. |
| 2 | Extractor runtime | Bundle in-process Python (Chaquopy) with pinned yt-dlp; arm64-v8a + x86_64; update via signed app releases. |
| 3 | Device/ABI coverage | 64-bit only (arm64-v8a, x86_64); 32-bit unsupported. |
| 4 | Torrent streaming in v1 | Included in v1 with a loopback-only bridge. |
| 5 | Remote signed metadata check | Included in v1; non-blocking; pinned key; cached last-known-good. |

## 4. Specification Gap Report

Addressed in `spec.md` during this session:

- Provider set made explicit (FR-014, Assumptions).
- Extractor runtime, ABIs, and update path made explicit (FR-053, FR-054, Assumptions).
- Device/ABI policy added (FR-076, Assumptions).
- Streaming moved from P3/deferred to P2/in-scope (User Story 6, Assumptions, SC-015).
- Remote signed metadata check moved from optional to required (FR-056, Assumptions).

Deferred to `/speckit-plan` (implementation-level, not blocking clarity):

- Exact foreground-service type matrix per Android version and per transfer type.
- Exact queue concurrency defaults, rate-limit defaults, and retry/backoff parameters.
- Exact filename-collision algorithm and storage volume selection rules.
- Exact Room schema versions and migration list.
- Crash-reporting / diagnostics policy (spec currently forbids analytics by default).
- OEM-specific background limitation list.
- Exact provider URL patterns and per-provider authorization assessment.

## 5. Unresolved-Decision Register

| ID | Decision | Class | Status |
|---|---|---|---|
| D1 | libtorrent4j exact version and minimum API level with minSdk 26 / 64-bit | BLOCKING for torrent slice | Open - verify in plan PoC |
| D2 | Android-compatible FFmpeg/FFprobe build and license | BLOCKING for extractor slice | Open - verify in plan |
| D3 | JavaScript runtime for yt-dlp-ejs and its license | IMPORTANT | Open - verify in plan |
| D4 | Whether to use the paid TLS-impersonation extractor variant | IMPORTANT | Open - product/cost decision; free tier uses cookies |
| D5 | Foreground-service type matrix and boot recovery mechanism | IMPORTANT | Open - plan/architecture |
| D6 | Provider authorization assessment for all five providers | IMPORTANT | Open - legal/policy, not implementation |
| D7 | Store distribution eligibility | NON-BLOCKING for v1 | Resolved as "unresolved/sideloaded v1" |
| D8 | Crash reporting policy | NON-BLOCKING | Open - default off |

## 6. Newly Identified Acceptance Criteria

- **FR-076**: 64-bit only packaging (arm64-v8a, x86_64); CI verifies native ABI packaging.
- **SC-015**: Streaming an eligible downloading torrent, seeking within available data,
  loopback-only bridge, terminated on stop.
- **FR-014 (expanded)**: v1 provider set enumerated with per-provider documentation.
- **FR-053/FR-054 (expanded)**: in-process pinned extractor runtime; unsupported ABI/runtime
  reported as a typed "provider unavailable" state.
- **FR-056 (tightened)**: remote signed manifest check is required in v1, non-blocking.

## 7. Phase 0 Decisions (plan)

Resolved during `/speckit-plan`; recorded here so the plan has an audit trail.

| Unknown / risk | Decision | Status |
|---|---|---|
| Torrent engine choice | libtorrent4j behind `TorrentEngine` (ADR-0001) | Decided; exact version locked by PoC |
| libtorrent4j minimum API vs minSdk 26 | Verify in PoC; 64-bit only | Open verification |
| Extractor runtime | In-process Chaquopy CPython 3.13 + pinned yt-dlp (ADR-0002) | Decided |
| JS runtime for yt-dlp-ejs | Verify Android-compatible runtime and license before pinning | Open verification |
| FFmpeg/FFprobe | Maintained Android build, per-ABI, argument-array invocation (ADR-0003) | Decided; build/license verify |
| TLS impersonation (free vs paid extractor) | Free tier + cookie path for v1; revisit as D4 | Deferred product/cost |
| ABI coverage | 64-bit only: arm64-v8a, x86_64 | Decided |
| Background execution | API-level matrix: UIDT (34+), `dataSync` FGS (<34), WorkManager (deferrable) (ADR-0005) | Decided |
| Queue scheduling | Single `QueueCoordinator`, Room as source of truth (ADR-0004) | Decided |
| File finalization | Temp + verify + atomic publish; `name (n).ext` collisions (ADR-0006) | Decided |
| Streaming | Loopback-only bridge with token and byte-range (ADR-0007) | Decided |
| Module boundaries | 25-module Clean Architecture graph (ADR-0008) | Decided |
| Extractor updates | Metadata-only signed manifest; executable updates via app releases (ADR-0009) | Decided |
| Security boundaries | UI→domain→data→engines; no remote code (ADR-0010) | Decided |

No NEEDS CLARIFICATION markers remain in the plan's Technical Context.


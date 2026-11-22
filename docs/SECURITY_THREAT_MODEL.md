# Security Threat Model (T170/T181)

**Boundaries**: UI → domain → data → engines; engines are the only native/process
surface. Review status: static review complete; dynamic/device verification is open.

## Checkpoints

| Threat | Defense | Status |
|---|---|---|
| SSRF via media URLs | Scheme allowlist (http/https), provider URL-pattern recognition, unknown providers are `UNSUPPORTED_PROVIDER` — never bypassed (FR-020) | Implemented (domain URL validator + UrlRecognizer) |
| Torrent filename traversal | `PathValidator` rejects absolute/`..`/illegal paths; `PathValidator.confine` canonicalizes against the destination root (symlink escapes) (FR-047) | Implemented + tested |
| Unsafe/oversized filenames | `FilenameSanitizer` (NFC, control/bidi strip, reserved names, 200-char cap) + collision strategy (FR-040) | Implemented + tested |
| Partial-file publication | `FileFinalizer` verify→atomic publish; failed verify keeps temp for recovery (FR-030/FR-039) | Implemented + tested |
| Streaming exposure | Loopback-only bind + per-connection loopback check, SecureRandom token, range bounds, traversal 404, 416 for unwritten ranges (FR-045) | Implemented + tested |
| Subprocess injection | `FfmpegProcess` argument arrays only, bounded output, timeouts, forced reaping (FR-048/FR-049) | Implemented + tested |
| Log/diagnostic leakage | Error details sanitized (URLs redacted, 200-char cap); engine text never raw (FR-049) | Implemented + tested |
| Remote code execution | No DCL anywhere — `GuardTests` scan production sources (FR-058); manifest metadata-only with pinned-key signatures (FR-056/FR-057) | Implemented + tested |
| Watermark removal | No path exists — guard test (FR-021) | Implemented + tested |
| Malicious update manifest | Expiration + rollback protection + signature verification + cached last-known-good (FR-056/FR-057/FR-060) | Implemented + tested |
| Secrets in repo | CI secret-scan job; no credentials committed; signing config excluded from VCS (FR-073) | Implemented |

## Permission audit (T181)

Declared in `app/src/main/AndroidManifest.xml`, each justified inline (FR-051):

- `INTERNET` — transfers themselves (torrent peers, media providers).
- `POST_NOTIFICATIONS` — transfer progress/controls on Android 13+; optional, graceful fallback (in-app controls remain).
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` — `dataSync` FGS for transfers on API < 34 (ADR-0005); correct type declared.
- `RECEIVE_BOOT_COMPLETED` — restore persisted queue markers only; never auto-starts a restricted service (FR-034).

Not requested (deliberate): storage permissions (SAF/scoped storage only), location,
contacts, clipboard, camera, microphone, account/auth permissions.

## Privacy verification (T182, FR-050)

Guard test asserts: no analytics/advertising SDK, no account system, no clipboard
read, no upload path. Local-first is structural: the only network clients are the
torrent engine and the extractor/provider resolution; nothing uploads user content.

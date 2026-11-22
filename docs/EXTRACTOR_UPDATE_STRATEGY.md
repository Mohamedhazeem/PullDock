# Extractor Update Strategy

**Decision**: updates arrive exclusively through signed application releases.
No in-app executable updates (ADR-0009, FR-058/FR-059).

## Why

- Android guidance and Play policy discourage dynamic code loading; remote-supplied
  executable code is an unacceptable supply-chain risk (constitution: Extractor
  Updates and Executable-Code Integrity).
- The extractor is the highest-risk dependency: provider sites change constantly, and
  an automatically fetched "fix" would be untrusted code with app-level permissions.
- The pinned combination is verifiable: one app version maps to one tested runtime set
  recorded in the dependency assessment (AGENTS.md §17).

## Mechanics

1. Upstream yt-dlp releases are evaluated by the maintainer: compatibility with the
   pinned Python/JS runtime, license, ABI, and provider regression results.
2. A new app release pins the validated combination and updates
   `BundledRuntimeCatalog` + `docs` version records.
3. The remote signed manifest (metadata only) communicates: "an update is
   recommended/required", approved version ranges, and per-provider disablement
   notices. Users see the honest state in Settings; the action is a normal app
   update — never an in-app swap.
4. `SignatureVerifier` (app-pinned public key), `ManifestGuard` (expiry + rollback
   protection), and the cached last-known-good guarantee a failed or offline check
   leaves the install intact (FR-060).

## If an update check fails

Nothing happens to the installation: cache retained, queue state retained,
`VERSION_CHECK_UNAVAILABLE` surfaced. Recovery is automatic on the next successful
check; the retry path is the WorkManager maintenance window (T109).

## Test-key disclosure

The repository pins a placeholder development key. Release builds MUST provision the
real public key from the release infrastructure. A placeholder key still enforces the
verification path in CI (tests sign with a locally generated keypair), so no unsigned
manifest can pass while the placeholder is in place.

# Feature Specification: PullDock — Production-Grade Android Download & Media Application

**Feature Branch**: `001-production-android-app`

**Created**: 2026-11-22

**Status**: Draft

**Input**: User description: "Production-grade native Android application unifying authorized
media downloads and BitTorrent transfers, with a unified queue, local media library, in-app
playback, privacy-first design, and reliable background execution."

> Naming note: the source brief used the placeholder name "FlowFetch". The project is named
> **PullDock**; this specification uses PullDock throughout.

## Product Summary

PullDock is a local-first native Android application that lets a user:

- Add torrent downloads from magnet links or `.torrent` files.
- Resolve and download authorized online media from supported providers.
- Manage both download types through one unified queue.
- Pause, resume, retry, cancel, and remove downloads.
- Play completed media in-app and stream eligible torrent media while downloading.
- Organize completed content in one library.
- Control network, battery, storage, and upload/seeding behavior.

The application MUST be reliable, private by default, honest about its behavior, and safe
when things go wrong (network loss, process death, storage exhaustion, provider changes,
platform background restrictions).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — First launch: consent, privacy, and capability explanation (Priority: P1)

A first-time user opens PullDock and is told, in plain language, what the app does: that it
is local-first, that torrent transfers expose the device IP address to peers, that the user
is responsible for only downloading content they have rights to, and which permissions are
requested and why. The user must acknowledge this before using download features.

**Why this priority**: Legal transparency, privacy, and informed consent are foundational.
Without them the product cannot be shipped responsibly.

**Independent Test**: Install on a clean device, launch, and verify the consent flow blocks
download actions until acknowledged, and that re-consent is requested when legal text changes.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the user opens the app, **Then** an onboarding flow
   explains capabilities, local-first behavior, network behavior, torrent peer-IP exposure,
   copyright responsibility, and permission usage.
2. **Given** the user has not acknowledged the current legal version, **When** they attempt
   to add a download, **Then** the app requires acknowledgement first.
3. **Given** the legal/consent text version has changed, **When** the user opens the app,
   **Then** the updated information is presented again.
4. **Given** the user acknowledges, **When** they proceed, **Then** the acknowledgement is
   persisted and not shown again until the version changes.

---

### User Story 2 — Add and manage a torrent download (Priority: P1)

A user adds a magnet link or selects a `.torrent` file, reviews the torrent name, total size,
and file list, selects which files to download, chooses a destination, and starts the
download. They can later pause, resume, recheck, change priorities, limit speeds, and remove
the torrent.

**Why this priority**: Torrent transfer is one of the two core product capabilities.

**Independent Test**: Add a valid magnet link and a valid `.torrent` file; verify metadata
fetch, file selection, start/pause/resume, and removal.

**Acceptance Scenarios**:

1. **Given** a valid magnet URI, **When** the user adds it, **Then** the app fetches metadata
   asynchronously and shows name, total size, and file list.
2. **Given** a valid `.torrent` file selected through the system file picker, **When** the
   user confirms, **Then** the app loads it and presents the file list.
3. **Given** a torrent with multiple files, **When** the user selects a subset, **Then** only
   selected files are downloaded.
4. **Given** a download is running, **When** the user pauses and later resumes, **Then**
   progress is preserved and no duplicate session is created.
5. **Given** a completed torrent, **When** the user views details, **Then** seeding status and
   policy are visible and controllable.
6. **Given** a malformed magnet URI or damaged torrent file, **When** the user adds it,
   **Then** the app shows a clear, actionable error and does not start a transfer.

---

### User Story 3 — Add and manage an authorized media download (Priority: P1)

A user pastes or shares a supported media URL, the app resolves it, shows available formats
with resolution, container, codecs, and estimated size, and the user chooses one and
downloads. Audio-only sources and separate audio/video streams are handled.

**Why this priority**: Media extraction is the second core capability.

**Independent Test**: Resolve a supported authorized URL, choose a format, download, and
verify the output plays.

**Acceptance Scenarios**:

1. **Given** a supported, authorized URL, **When** the user adds it, **Then** the app resolves
   it and presents available formats with accurate metadata.
2. **Given** multiple formats, **When** the user selects one, **Then** the selection is
   remembered and used for the download.
3. **Given** a source with separate video and audio streams, **When** the user downloads,
   **Then** the streams are merged and the result is validated before being published.
4. **Given** an unsupported or invalid URL, **When** the user adds it, **Then** the app shows
   an understandable unsupported/error state.
5. **Given** a provider failure, **When** resolution or download fails, **Then** the app does
   not crash and reports a typed, actionable error.

---

### User Story 4 — Unified queue management (Priority: P1)

A user sees torrent and media downloads together in one queue with filters (all, active,
queued, paused, completed, failed), can reorder/prioritize, and can act on any task
independently without affecting others.

**Why this priority**: The unified queue is the product's central differentiator.

**Independent Test**: Have at least one torrent and one media task in different states;
verify they appear together, filters work, and per-task actions are independent.

**Acceptance Scenarios**:

1. **Given** both download types exist, **When** the user opens the queue, **Then** both are
   listed with accurate status, progress, speed, and type-specific details.
2. **Given** concurrency limits are configured, **When** more tasks are queued, **Then** only
   the allowed number run concurrently and the rest wait.
3. **Given** a task is paused, **When** the user resumes it, **Then** it re-enters the queue
   without duplicating work.
4. **Given** a failed task, **When** the user retries, **Then** the retry follows the
   configured backoff and does not loop forever.
5. **Given** a duplicate add request, **When** the same source is added again, **Then** the
   app handles it safely (deduplicates or clearly rejects) without two identical transfers.

---

### User Story 5 — Local media library and playback (Priority: P2)

A user browses completed media in a library with video/audio/other sections, search, sort,
and filter, sees thumbnails, size, duration, source, and completion date, and plays media in
a polished in-app player.

**Why this priority**: Turns downloads into a usable product, but depends on downloads
existing first.

**Independent Test**: Complete a download, verify it appears in the library, and play it.

**Acceptance Scenarios**:

1. **Given** completed media exists, **When** the user opens the library, **Then** items are
   grouped and searchable with metadata.
2. **Given** a completed item, **When** the user plays it, **Then** playback supports
   play/pause, seek, skip, speed, volume, and fullscreen, and resumes from last position.
3. **Given** a file was deleted outside the app, **When** the library refreshes, **Then** the
   app reconciles and does not show a broken entry as playable.
4. **Given** an empty library, **When** the user opens it, **Then** a clear empty state is
   shown.

---

### User Story 6 — Stream eligible torrent media while downloading (Priority: P3)

A user can start playback of a downloading torrent's media at a playable point.

**Why this priority**: Valuable but depends on engine capabilities and is not required for
the core promise.

**Independent Test**: Start a torrent with a media file, begin playback before completion,
verify seeking and buffering behavior.

**Acceptance Scenarios**:

1. **Given** a torrent with a media file is downloading, **When** the user chooses to stream,
   **Then** playback starts from available data and handles buffering and missing pieces.
2. **Given** the streaming bridge is active, **When** the user stops playback or leaves,
   **Then** the bridge is shut down and not left running.
3. **Given** the bridge is running, **When** the device network changes, **Then** the bridge
   remains local-only and is not exposed beyond the device.

---

### User Story 7 — Network, battery, and storage controls (Priority: P2)

A user configures Wi-Fi-only downloads, metered-network restrictions, battery thresholds,
active download limits, rate limits, and connection limits, and can stop all activity.

**Why this priority**: User control and resource respect are core product principles.

**Independent Test**: Set Wi-Fi-only and a rate limit; verify behavior on network change.

**Acceptance Scenarios**:

1. **Given** Wi-Fi-only is enabled, **When** the device is on a metered network, **Then** new
   downloads do not start and the user is informed.
2. **Given** a rate limit is set, **When** a transfer runs, **Then** the effective speed
   respects the limit.
3. **Given** a battery threshold is set, **When** battery falls below it, **Then** active
   downloads are paused and the user is informed.
4. **Given** storage is low, **When** a download cannot complete, **Then** the app reports it
   clearly and does not publish an incomplete file as complete.

---

### User Story 8 — Extractor health and provider status (Priority: P2)

A user can see, in Settings and on error screens, whether each provider is working, whether
an update is recommended/required, whether a provider is temporarily unavailable, or whether
it is unsupported — with a clear next action.

**Why this priority**: Extractor rot is the main long-term reliability risk; honest
communication is mandatory.

**Independent Test**: Simulate a stale extractor and a provider failure; verify distinct,
accurate states and no crash.

**Acceptance Scenarios**:

1. **Given** the installed extractor is older than recommended, **When** the user opens
   provider status, **Then** an "update recommended" state is shown with a next action.
2. **Given** a provider has changed and cannot resolve content, **When** the user attempts a
   download, **Then** a "compatibility problem" state is shown and other providers remain
   usable.
3. **Given** a network failure, **When** the app checks provider health, **Then** the state is
   reported as unavailable/unknown, not as an outdated extractor.
4. **Given** a provider is unsupported, **When** the user adds its URL, **Then** an
   unsupported state is shown without attempting a bypass.

---

### User Story 9 — Interruption, process death, and reboot recovery (Priority: P1)

A user's downloads survive app backgrounding, swipe dismissal, process death, reboot, and OS
termination, and are either resumed safely or clearly marked as needing recovery.

**Why this priority**: Reliability under interruption is a core promise.

**Independent Test**: Start a download, force-stop the app, relaunch, and verify state is
restored and the task is either resumed or marked for recovery.

**Acceptance Scenarios**:

1. **Given** an active download, **When** the process is killed, **Then** on relaunch the
   queue state is restored and no duplicate engine session is created.
2. **Given** a task whose engine state cannot be reconstructed, **When** recovery runs,
   **Then** the task is marked for recovery rather than silently dropped.
3. **Given** a device reboot, **When** the app next runs, **Then** persisted queue information
   is restored and interrupted tasks are surfaced to the user.
4. **Given** recovery is not possible automatically, **When** the user views the task, **Then**
   a clear resume/retry path is offered.

---

### User Story 10 — Accessibility and adaptive layouts (Priority: P2)

A user with assistive technology, large font settings, RTL locale, or a large screen can use
all primary flows.

**Why this priority**: Production quality and inclusivity, but not a first-slice blocker.

**Independent Test**: Enable TalkBack and large fonts; navigate add-download, queue, and
player.

**Acceptance Scenarios**:

1. **Given** TalkBack is enabled, **When** the user navigates primary screens, **Then**
   interactive elements and download states have meaningful descriptions.
2. **Given** large system font settings, **When** the user opens each screen, **Then** layout
   remains usable without truncation or overlap.
3. **Given** an RTL locale, **When** the user navigates, **Then** layout mirrors correctly.
4. **Given** a tablet or foldable, **When** the user opens the app, **Then** navigation adapts
   and content is not constrained to a phone-width column.

---

### Edge Cases

- Malformed magnet URI, unsupported scheme, damaged `.torrent` file, oversized metadata.
- Torrent with zero peers, no healthy trackers, DHT unavailable, unreachable sources.
- Torrent filenames containing path traversal, absolute paths, reserved names, Unicode
  normalization collisions, case-insensitive collisions.
- Duplicate infohash added twice.
- Changing torrent file selection after download has begun.
- Media source with missing metadata, no available formats, audio-only, separate streams,
  expired URL, removed content, rate limiting, unsupported codec.
- Provider HTML/interface change causing resolution failure.
- Low disk space, file already exists, duplicate filename, unsupported extension, missing
  storage volume, removable storage unmounted, SAF permission revocation.
- Database/file inconsistency: output present but record missing, record present but file
  missing, crash during merge or finalization.
- Network switching, metered network, roaming, airplane mode, captive portal, VPN, DNS
  failure, TLS failure, intermittent connectivity.
- Notification permission denied; OEM battery restrictions; Doze; Battery Saver.
- Update check with no internet, invalid signature, expired metadata, unsupported manifest
  version, insufficient storage, cancelled install, signing mismatch.
- Concurrent pause/resume requests; out-of-order progress events; duplicate queue events.

## Requirements *(mandatory)*

### Functional Requirements

#### Torrent ingestion and metadata

- **FR-001**: The app MUST allow adding a torrent from a magnet URI, a `.torrent` document
  selected via the system file picker, or a share intent containing either.
- **FR-002**: The app MUST validate magnet URIs and torrent files and reject malformed or
  unsupported inputs with a clear error.
- **FR-003**: The app MUST fetch torrent metadata asynchronously and present name, total
  size, file list with sizes, and destination before downloading.
- **FR-004**: The app MUST let the user select individual files and change selection safely.
- **FR-005**: The app MUST handle metadata timeout, unavailable trackers, and torrents that
  never obtain metadata without crashing or misleading the user.
- **FR-006**: The app MUST handle duplicate infohashes without creating duplicate transfers.

#### Torrent transfer and seeding

- **FR-007**: The app MUST support start, pause, resume, cancel, remove, recheck, and retry.
- **FR-008**: The app MUST support file priority, download/upload rate limits, connection
  limits, and queue priority.
- **FR-009**: The app MUST support piece verification and report corrupted pieces safely.
- **FR-010**: The app MUST persist engine resume data so a torrent can be restored after
  interruption.
- **FR-011**: The app MUST provide explicit, user-configurable seeding controls: seed after
  completion, max upload rate, stop ratio, time limit, per-torrent control, and stop-all.
- **FR-012**: The app MUST NOT seed indefinitely without user consent.
- **FR-013**: The app MUST explain torrent peer-IP visibility before first use and in a
  accessible privacy explanation.

#### Media extraction

- **FR-014**: The app MUST resolve supported, authorized media URLs through a provider
  registry with isolated provider implementations.
- **FR-015**: The app MUST present available formats with resolution, container, video/audio
  codecs, estimated size, and whether merging is required.
- **FR-016**: The app MUST support audio-only sources and separate audio/video streams.
- **FR-017**: The app MUST classify provider failures into typed errors and map them to
  understandable user messages.
- **FR-018**: The app MUST support cancellation, timeouts, bounded retries, and exponential
  backoff with jitter for extraction.
- **FR-019**: The app MUST NOT claim successful resolution until a valid media descriptor
  exists.
- **FR-020**: The app MUST NOT bypass DRM, paywalls, authentication, private-account
  restrictions, age restrictions, encryption, or platform download restrictions.
- **FR-021**: The app MUST NOT implement watermark removal. A watermark-free variant may be
  offered only when the source itself exposes it, the integration is authorized, access is
  permitted, and the app preserves the source-provided variant unmodified.
- **FR-022**: The app MUST use an Android-compatible media processing path for merging and
  MUST validate output before publishing it.

#### Unified queue and state

- **FR-023**: The app MUST present torrent and media tasks in one queue with filters for
  all, active, queued, paused, completed, and failed.
- **FR-024**: The app MUST define and enforce a download state machine with persisted,
  legal transitions. Required states include at least: created, validating, resolving,
  fetching metadata, awaiting file selection, awaiting format selection, queued, starting,
  running, pausing, paused, retry wait, verifying, post-processing, completed, failed,
  cancelled, removing, needs permission, needs user action, blocked.
- **FR-025**: The app MUST enforce that a completed download cannot be resumed, a cancelled
  task cannot silently restart, and a task awaiting selection cannot run.
- **FR-026**: The app MUST make pause idempotent and repeated start requests free of
  duplicate engine sessions.
- **FR-027**: The app MUST separate "remove task" from "delete file" as distinct
  user-controlled operations.
- **FR-028**: The app MUST coordinate concurrency across both engine types with global and
  per-engine limits, priority ordering, retries, and backoff.
- **FR-029**: The app MUST persist queue state and engine recovery information so state
  survives process death.
- **FR-030**: The app MUST NOT report a file as completed before successful verification
  and finalization.

#### Background execution and recovery

- **FR-031**: The app MUST choose execution mechanisms per transfer type and Android version,
  and MUST respect foreground-service type restrictions and time limits.
- **FR-032**: The app MUST provide meaningful ongoing notifications with pause and stop
  controls where a foreground service is used.
- **FR-033**: The app MUST handle process recreation, OS-initiated termination, and service
  disconnection without corrupting downloads.
- **FR-034**: The app MUST restore persisted queue state on reboot, validate resume data,
  mark interrupted tasks for recovery, avoid duplicate engine sessions, and offer a
  user-visible resume path.
- **FR-035**: The app MUST NOT promise unlimited background execution or seamless
  continuation that has not been verified.

#### Storage

- **FR-036**: The app MUST use scoped storage and MUST NOT request broad external storage
  access for convenience.
- **FR-037**: The app MUST support app-specific storage for temporary files, the system
  media collection for completed public media, and user-selected destinations via the
  storage access framework.
- **FR-038**: The app MUST handle low disk space, existing files, duplicate filenames,
  unsupported extensions, invalid/oversized filenames, missing volumes, unmounted removable
  storage, revoked SAF permission, and interrupted media-store writes.
- **FR-039**: The app MUST use temporary output and safe finalization so incomplete files are
  never published as completed downloads.
- **FR-040**: The app MUST define a predictable, documented filename collision strategy.
- **FR-041**: The app MUST require confirmation for destructive file operations.

#### Library and playback

- **FR-042**: The app MUST provide a library with video, audio, and other sections, search,
  sort, filter, thumbnails, size, duration, source, and completion date.
- **FR-043**: The app MUST reconcile library records with actual files rather than assuming
  every file still exists.
- **FR-044**: The app MUST provide playback with play/pause, seek, skip, speed, volume,
  fullscreen, orientation handling, resume position, track selection where available, and
  error handling.
- **FR-045**: If torrent streaming is provided, the app MUST bind any local bridge to
  loopback only, use an unpredictable access token, support byte-range requests, prevent
  directory traversal, and terminate the bridge when no longer needed.

#### Security and privacy

- **FR-046**: The app MUST treat all external URLs, torrent metadata, filenames, network
  responses, and process output as untrusted.
- **FR-047**: The app MUST prevent path traversal and unsafe destination access, and MUST
  never allow a torrent to write outside the selected destination.
- **FR-048**: The app MUST NOT build shell commands from raw user input and MUST pass
  process arguments safely.
- **FR-049**: The app MUST NOT log private URLs, tokens, cookies, or unredacted secrets, and
  MUST bound and sanitize subprocess diagnostics.
- **FR-050**: The app MUST NOT include analytics or advertising SDKs by default, MUST NOT
  require an account, MUST NOT upload user files, and MUST NOT read the clipboard silently.
- **FR-051**: The app MUST request the minimum permissions and justify each one.
- **FR-052**: The app MUST document third-party licenses and pinned dependency versions.

#### Extractor lifecycle and updates

- **FR-053**: The app MUST maintain an explicit, immutable extractor runtime inventory
  covering extractor, companion JS runtime, Python runtime, media processor/prober, native
  libraries and ABIs, and provider adapters, each with version, required compatibility,
  runtime compatibility, channel, verification status, and known limitations.
- **FR-054**: The app MUST validate the extractor environment during initialization and
  produce a typed, actionable error on failure without crashing.
- **FR-055**: The app MUST use a pinned, validated dependency combination in production and
  MUST NOT automatically install nightly or unvalidated components.
- **FR-056**: The app MAY check a remote signed metadata manifest for update and provider
  compatibility information. It MUST verify signatures with an app-pinned key, validate
  schema and expiration, prevent rollback, use secure transport, and handle unavailable
  servers and cached last-known-good data.
- **FR-057**: The app MUST reject invalid, expired, malformed, or incorrectly signed
  manifests.
- **FR-058**: The app MUST NOT automatically download, replace, import, load, or execute
  arbitrary executable components from a remote server, including scripts, native
  libraries, dynamic modules, or binaries.
- **FR-059**: The app MUST distribute extractor changes as part of a complete, signed
  application release using the platform package installation flow.
- **FR-060**: The app MUST keep the existing installation and user data intact when an
  update check or install fails or is cancelled.
- **FR-061**: The app MUST expose distinct extractor health states (at least: current,
  update recommended, update required, platform change suspected, provider temporarily
  unavailable, provider unsupported, authorization required, version check unavailable,
  runtime incompatible) and MUST NOT confuse network errors with staleness.
- **FR-062**: The app MUST keep providers independently manageable so one provider's failure
  does not break others, and MUST support safe provider disablement and graceful
  degradation.
- **FR-063**: The app MUST maintain a provider compatibility matrix with URL patterns,
  capabilities, versions, last verified date, method, result, limitations, and policy
  assessment, using authorized test fixtures rather than arbitrary live content.
- **FR-064**: The app MUST persist durable queue state and torrent resume data before an
  application update and MUST mark tasks for recovery when engine state cannot be safely
  reconstructed.

#### UI/UX and accessibility

- **FR-065**: The app MUST provide a consistent Material 3 design system with shared tokens
  for color, typography, spacing, radius, elevation, iconography, and status colors, in
  light and dark themes.
- **FR-066**: The app MUST provide main navigation covering Home, Downloads, Library, and
  Settings, with a prominent add-download action, adapted for larger screens.
- **FR-067**: Every screen MUST handle loading, refreshing, empty, populated, partial,
  offline, error, permission-denied, recoverable-failure, and restored states.
- **FR-068**: Every interactive component MUST have default, pressed, disabled, loading, and
  feedback states, with accessibility semantics.
- **FR-069**: The app MUST support TalkBack, 48dp touch targets, contrast, scalable type,
  large fonts, keyboard navigation where relevant, reduced motion, and RTL.
- **FR-070**: The app MUST NOT ship placeholder screens, dead buttons, fake interactions, or
  misleading progress indicators.
- **FR-071**: The app MUST provide undo for reversible destructive actions and confirmation
  dialogs for genuinely destructive ones.

#### Quality and release

- **FR-072**: The app MUST include unit, integration, database, engine adapter, UI,
  instrumentation, lifecycle/recovery, and migration tests appropriate to each critical use
  case, verifying failure and boundary conditions rather than only mocked success.
- **FR-073**: The app MUST produce debug and release builds, keep signing credentials outside
  source control, and document versioning, licensing, and a release checklist.
- **FR-074**: The app MUST run automated build, lint, static analysis, formatting checks,
  tests, dependency verification, license reporting, ABI packaging checks, and secret
  scanning in CI.
- **FR-075**: The app MUST document distribution and policy risk honestly, including that
  store eligibility is unresolved until a policy assessment is completed, and MUST NOT
  market the app as enabling unauthorized downloads.

### Key Entities

- **DownloadTask**: The unified task. Common attributes: unique id, type (torrent/media),
  original source, display name, output filename, status, progress, total/downloaded bytes,
  download/upload speed, ETA, created/updated timestamps, output location, error code and
  details, retry count, destination, user limits.
- **TorrentTaskDetail**: Engine-specific detail: infohash, trackers, peers, selected files
  and priorities, piece state, resume data, seeding policy and stats.
- **MediaTaskDetail**: Engine-specific detail: provider, original URL, selected format,
  container/codecs, merge requirement, processing status, provider error info.
- **DownloadState**: A named state in the download state machine plus legal transitions.
- **Provider**: A supported media source with URL patterns, capabilities, auth requirements,
  restrictions, and compatibility metadata.
- **ExtractorRuntimeInfo**: Immutable inventory of extractor components, versions,
  compatibility, channel, verification status, and limitations.
- **ProviderCompatibilityRecord**: Provider id, URL patterns, capabilities, versions, last
  verified date, method, result, limitations, policy assessment.
- **UpdateManifest**: Signed remote metadata: schema version, timestamps, recommended and
  minimum app versions, approved extractor/runtime versions, provider status, disablement
  notices, release notes.
- **LibraryItem**: A completed media record: type, title, thumbnail, size, duration, source,
  completion date, file location.
- **PlaybackState**: Current media, position, duration, speed, volume, track selection.
- **NetworkPolicy**: Wi-Fi-only, metered restrictions, roaming, rate and connection limits.
- **SeedingPolicy**: Seed-after-completion, max upload rate, stop ratio, time limit,
  per-torrent override.
- **RecoveryMarker**: Persistent marker indicating a task interrupted and awaiting recovery.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can add a valid magnet link and a `.torrent` file, select files, and
  complete a download, with no manual retry caused by app defects.
- **SC-002**: A user can resolve a supported authorized media URL, choose a format, and
  obtain a playable file whose contents match the chosen format.
- **SC-003**: 100% of download state transitions are persisted; after process death, the
  queue is restored with no duplicate active engine sessions.
- **SC-004**: No incomplete file is ever presented to the user as completed.
- **SC-005**: Torrent and media tasks appear together in one queue, and per-task actions
  complete without affecting unrelated tasks.
- **SC-006**: Concurrent transfers respect the configured active-download and rate limits.
- **SC-007**: A provider failure does not crash the app and does not prevent other verified
  providers from working.
- **SC-008**: Extractor environment incompatibilities (extractor/JS runtime/media
  processor/Python) are detected and reported as typed, actionable errors, not crashes.
- **SC-009**: Invalid, expired, or incorrectly signed update metadata is rejected, and a
  failed update check or install leaves the existing installation and user data intact.
- **SC-010**: All primary screens render correct loading, empty, success, and error states,
  in light and dark themes.
- **SC-011**: Primary flows are completable with TalkBack, large system fonts, and RTL
  locales, with no truncated or overlapping controls.
- **SC-012**: Critical unit and integration tests pass, release build succeeds, and actual
  test outcomes are documented for each release.
- **SC-013**: A forced-stop followed by relaunch recovers or clearly marks interrupted
  tasks, with no data loss for completed files.
- **SC-014**: Storage exhaustion, missing files, and database/file inconsistency are
  handled without corrupting existing downloads.

## Assumptions

- The product name is **PullDock**; the source brief's "FlowFetch" is a legacy placeholder.
- Initial distribution is private/sideloaded. Store distribution is an unresolved decision
  pending a policy assessment.
- No-watermark transformation/removal is disabled for v1, as specified.
- Torrent streaming is a P3 capability and may be deferred if the engine cannot support it
  safely.
- Provider support is limited to a documented set; unsupported providers are surfaced as
  unsupported rather than bypassed.
- Automated tests use owned, licensed, synthetic, or authorized fixtures; live social-media
  URLs are not the sole test dependency.
- Minimum supported Android version is 26, subject to dependency compatibility.
- Target/compile SDK and all dependency versions are the latest stable versions verified at
  implementation time and pinned in the version catalog.
- The implementation proceeds in dependency-ordered vertical slices (foundation, domain and
  persistence, core UX, torrent engine, extractor engine, execution and recovery, library
  and playback, hardening), each buildable and testable on its own.
- No user account, developer-operated download backend, cloud upload, or analytics SDK is
  required.

## Out of Scope (v1)

- Built-in torrent search, indexing, discovery, or pirated-content catalogs.
- Watermark removal or bypassing DRM, paywalls, authentication, private-account, or age
  restrictions.
- Automatic download and execution of remotely supplied code.
- Store distribution claims prior to a policy assessment.

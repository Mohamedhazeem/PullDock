# PullDock Constitution

## Core Principles

### I. Product and Engineering Identity

PullDock is a unified Android application for managing authorized media downloads and
BitTorrent transfers. It MUST be built as a reliable, privacy-conscious, modular native
Android application.

Engineering priorities, in descending order:

1. Correctness.
2. Reliability.
3. Security and privacy.
4. User experience.
5. Maintainability.
6. Scalability.
7. Performance.
8. Testability.

Implementation speed MUST NEVER be prioritized over fundamental correctness.

### II. Platform and Technology Standards

- Kotlin is the primary development language.
- Jetpack Compose is mandatory for the Android UI.
- Material 3 is the design foundation.
- Use stable, compatible Android libraries and pin dependency versions.
- Use Kotlin Coroutines and Flow for asynchronous operations.
- Use Hilt for dependency injection.
- Use Room for durable structured application data.
- Use DataStore for application preferences.
- Use AndroidX Media3 for media playback.
- Respect Android lifecycle, scoped storage, permissions, and background-execution
  restrictions.
- Verify every native dependency's Android and ABI compatibility.

New technology MUST NOT be introduced without a concrete engineering justification.

### III. Clean Architecture and Module Boundaries

- Follow Clean Architecture, SOLID, separation of concerns, dependency inversion, and
  unidirectional data flow.
- Maintain clear boundaries between Presentation, Domain, Data, Platform infrastructure,
  and Native download engines.
- The domain layer MUST remain independent of Android framework implementations and
  concrete third-party engines.
- The presentation layer MUST NOT directly access databases, native engines, or storage
  implementations.
- Keep modules cohesive and dependencies directional.
- Do not create abstractions without a meaningful purpose.

MUST avoid: god classes, god ViewModels, circular dependencies, business logic in
composables, global mutable state, tight coupling between feature modules, duplicated
business rules, and premature micro-modularization.

### IV. Kotlin Coding Standards

- Use immutable data structures wherever practical.
- Prefer explicit types for public contracts.
- Use sealed types for meaningful state and error hierarchies.
- Handle nullable values deliberately.
- Use structured concurrency and respect coroutine cancellation.
- Never block the main thread.
- Never use `GlobalScope`.
- Avoid unnecessary scope creation.
- Prevent memory leaks and unowned coroutine jobs.
- Avoid broad exception catching without a recovery strategy.
- Never silently swallow exceptions.
- Use clear naming and small, cohesive functions.

All public APIs and important architectural boundaries MUST be documented.

### V. Compose and UI Standards

The application MUST have a consistent, polished, production-quality design system.

- Use Material 3 and build reusable components.
- Keep composables focused and testable.
- Use immutable UI state and `StateFlow` for screen state where appropriate.
- Follow unidirectional event handling.
- Avoid expensive computations in composables and avoid unnecessary recomposition.
- Keep UI independent of engine implementation details.
- Implement light and dark themes.
- Support accessibility, large font sizes, TalkBack, RTL, and adaptive layouts.

Every screen MUST handle loading, empty, success, and error states where applicable.
Dead buttons, fake interactions, and misleading progress indicators MUST NOT ship.

### VI. Download-Engine Isolation

- Torrent and media-extraction engines MUST remain independent.
- Expose engines through explicit interfaces and typed result models.
- Do not leak JNI, libtorrent, yt-dlp, FFmpeg, process, or native data types into the domain
  or UI layers.
- Engine callbacks and progress events MUST be handled safely across thread boundaries.
- Native resources MUST have deterministic ownership and cleanup.
- Cancellation and shutdown MUST be designed explicitly.

Never assume a desktop executable or native library is Android-compatible without
verification.

### VII. Reliability and Persistence

- Room is the durable source of application state.
- Persist important state transitions.
- Use database transactions where consistency requires them.
- Make operations idempotent where possible.
- Design deterministic recovery and persist engine-specific recovery information.
- Handle process death and interrupted transfers.
- Implement safe database migrations.
- Keep database and actual file state reconcilable.

Storing progress percentages alone MUST NEVER be treated as making a download recoverable.
A file MUST NOT be reported complete before successful verification and finalization.

### VIII. Android Lifecycle and Background Execution

- All background work MUST comply with the Android version and current platform
  restrictions.
- Use appropriate execution mechanisms and declare correct foreground-service types and
  permissions.
- Avoid unnecessary long-running services and never misuse foreground-service types.
- Respect Doze, Battery Saver, service timeouts, notification permissions, and process
  termination.
- Do not promise unlimited background execution.
- Recover interrupted work safely.
- Avoid duplicate engine instances after process recreation or reboot.

Platform limitations MUST be documented rather than hidden.

### IX. Security and Privacy

Treat user-provided URLs, torrent metadata, filenames, network responses, and external
process output as untrusted.

- Validate inputs.
- Prevent path traversal and unsafe destination access.
- Do not build shell commands through string concatenation.
- Do not leak URLs, tokens, or private information through logs.
- Do not hardcode credentials or embed private signing keys.
- Pin and verify dependencies and document third-party licenses.
- Minimize permissions and avoid unnecessary analytics or tracking.
- Do not silently read clipboard contents.
- Protect local services and temporary files.

All media integrations MUST respect applicable rights, platform restrictions, and legal
requirements.

### X. Testing

Testing is part of implementation, not an optional cleanup activity.

Require appropriate unit, integration, database, engine adapter, Compose UI,
instrumentation, lifecycle and recovery, and migration tests.

- Tests MUST verify meaningful behavior, including failure and boundary conditions.
- Superficial tests that merely verify mocked success responses MUST NOT be accepted.
- Every critical use case MUST have an appropriate verification strategy.

### XI. Error Handling

- Use centralized, typed error handling.
- Distinguish input, network, engine, storage, permission, processing, persistence, and
  lifecycle errors.
- User-facing errors MUST be understandable and actionable.
- Internal diagnostics MUST be useful without exposing sensitive information.

Failure MUST NOT be hidden behind default values or success-shaped results.

### XII. Dependency and Architectural Decisions

Before introducing a library or framework, verify maintenance status, Android compatibility,
supported ABIs, licensing, transitive dependencies, security and size impact, and explain
why it is needed.

- Significant architectural choices MUST be recorded as Architecture Decision Records.
- Approved architectural decisions MUST NOT be changed without explaining the trade-offs
  and updating the relevant documentation.

## Additional Constraints

### Extractor Updates and Executable-Code Integrity

- Third-party extractor updates MUST follow a controlled, reproducible release process.
- Remotely downloaded code MUST NOT be dynamically executed by default.
- Prefer pinned, validated dependencies distributed through signed application releases.
- Any exception requires an approved ADR, security review, integrity verification,
  compatibility testing, and distribution-policy assessment.

Application security MUST NOT be sacrificed merely to make provider updates faster.

## Development Workflow, Review, and Quality Gates

### Development Workflow

Each implementation slice MUST:

1. Have explicit scope.
2. Follow approved architecture.
3. Have acceptance criteria.
4. Include appropriate tests.
5. Compile successfully.
6. Pass relevant quality checks.
7. Update affected documentation.
8. Be reviewed before dependent work proceeds.

Never implement unrelated future slices opportunistically. Never mark incomplete work as
finished. Never disable tests, lint rules, or compiler checks merely to obtain a successful
build.

### Definition of Done

A feature is done only when:

- Its requirements are implemented.
- Its architectural boundaries are respected.
- Its relevant tests pass.
- Relevant edge cases are addressed.
- Error handling is implemented.
- User-visible states are complete.
- No known critical regression remains.
- Documentation is updated where necessary.

A successful compilation alone does not constitute completion.

## Governance

- This constitution supersedes all other engineering practices and guidance.
- All specifications, plans, tasks, code changes, and architectural decisions MUST verify
  compliance with these principles.
- Amendments require a documented rationale, explicit approval, a migration plan for
  affected work, and a semantic version bump: MAJOR for principle removal or redefinition,
  MINOR for new principles or materially expanded guidance, PATCH for clarifications.
- If a later specification or implementation request conflicts with this constitution:
  1. Identify the conflict.
  2. Explain its impact.
  3. Recommend a compliant alternative.
  4. Request an explicit decision when a genuine trade-off is unavoidable.
  5. Never silently weaken a permanent engineering principle.
- Compliance is reviewed as part of every implementation-slice review; violations block
  dependent work.
- Runtime development guidance MUST stay consistent with this constitution; where it
  conflicts, this constitution prevails.

**Version**: 1.0.1 | **Ratified**: 2026-11-22 | **Last Amended**: 2026-11-22

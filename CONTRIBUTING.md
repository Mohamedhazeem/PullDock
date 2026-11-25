# Contributing to PullDock

Thanks for your interest in improving PullDock. This project is maintained by Mohamed Hazeem and welcomes bug reports, documentation fixes, tests, accessibility improvements, and focused feature work.

By participating, you agree to be respectful and constructive. Do not submit abusive, discriminatory, or harassing content.

## Ways to contribute

- Report reproducible bugs with device, Android version, app version, and steps.
- Improve documentation, examples, or error messages.
- Add or fix tests.
- Improve accessibility, RTL behavior, and adaptive layouts.
- Implement a scoped feature or fix from the issue tracker.
- Review third-party dependency and license implications.

## Before you start

1. Read [AGENTS.md](AGENTS.md).
2. Read the engineering constitution at [`.specify/memory/constitution.md`](.specify/memory/constitution.md).
3. Read the relevant feature documents under `specs/<feature>/` (`spec.md`, `plan.md`, `tasks.md`).
4. Check the issue tracker or open an issue before large changes.

PullDock uses Spec Kit for spec-driven development. For a new feature, start with `/speckit-specify`, then `/speckit-plan`, `/speckit-tasks`, and `/speckit-implement`.

## Development setup

Requirements:

- Android Studio with a recent Android SDK Platform
- JDK 17+
- Android SDK Build Tools and Platform Tools
- A device or emulator running Android 8.0+ (API 26+)

Build:

```bash
./gradlew :app:assembleDebug
```

Install on a connected arm64 device:

```bash
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Run checks before opening a pull request:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

If you change a specific module, run that module’s tests too.

## Pull request checklist

- [ ] The change is focused and has a clear title.
- [ ] `./gradlew :app:assembleDebug` succeeds.
- [ ] Relevant unit tests and `:app:lintDebug` pass.
- [ ] New or changed behavior has tests.
- [ ] No secrets, credentials, or signing keys are committed.
- [ ] No unrelated files or generated build outputs are included.
- [ ] Architecture boundaries are respected.
- [ ] User-visible strings are in `strings.xml` and support accessibility.
- [ ] README/AGENTS/docs are updated when behavior or structure changes.

## Architecture rules

- Keep `:domain:*` pure Kotlin. No Android, Room, Compose, or engine classes.
- Presentation must not access Room, native engines, or storage directly.
- Define interfaces at real architectural boundaries; avoid speculative abstractions.
- Use unidirectional data flow: immutable state down, events up.
- Use `AppDispatchers`; never reference `Dispatchers` directly in features or domain.
- Never use `GlobalScope`, `!!`, or unowned coroutines.
- Room schema changes require a tested migration.
- Native engines must be isolated behind typed interfaces and cleaned up deterministically.
- Do not add a new module without a real boundary.

## Security and legal

- Treat URLs, filenames, torrent metadata, and external process output as untrusted.
- Never build shell commands through string concatenation.
- Do not log URLs, tokens, or private information.
- Do not commit credentials or signing material.
- Only download content you own or have permission to download.
- Do not change the distribution policy for yt-dlp, FFmpeg, or native binaries without an approved ADR and security review.

## Commit and review style

- Use clear, imperative commit subjects, for example `fix: clear stale format sheet on URL change`.
- Keep unrelated changes in separate commits.
- Explain why a change is needed, not just what changed.
- Respond to review feedback with focused follow-up commits.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
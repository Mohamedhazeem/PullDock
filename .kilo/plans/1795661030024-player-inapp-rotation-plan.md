# Player In-App Rotation + Portrait Shell

## Goal
Fix rotation so the app shell stays portrait (bottom nav bar never becomes a side rail / never rotates), and the player rotates only its content in-app. The nav bar is hidden while the player is open and returns when it is closed.

## Root cause (verified)
- `MainActivity` sets no orientation and passes `useRail = widthSizeClass >= Medium` to `RootNavHost`. Rotating to landscape makes width `>= Medium`, so `MainScaffold` swaps the bottom `NavigationBar` for a left `NavigationRail` (`MainScaffold.kt:48`).
- The player rotate button calls `setOrientation()` which sets `activity.requestedOrientation = LANDSCAPE` (`PlayerScreen.kt:600-607`), rotating the whole activity (and triggering the system auto-rotate behavior) instead of the video.
- The player route is rendered **inside** `MainScaffold` (`RootNavHost.kt:73-145`), so the nav bar is visible on the player.

## Decisions
1. Lock the activity to portrait (shell never rotates; bottom bar stays at the bottom).
2. Player rotate = in-app 90° rotation of the player content only (no `requestedOrientation` changes).
3. Hide the nav bar/rail while the player route is active; show it on all other routes.

## Tasks (ordered)

1. **Lock portrait** — `app/src/main/AndroidManifest.xml`
   - Add `android:screenOrientation="portrait"` to the `.MainActivity` `<activity>`.

2. **Force bottom bar** — `app/src/main/java/com/pulldock/MainActivity.kt`
   - Pass `useRail = false` to `RootNavHost` (portrait lock already implies Compact, but make it explicit so the rail can never appear).

3. **Hide nav chrome on the player** — `app/src/main/java/com/pulldock/navigation/MainScaffold.kt`
   - After `currentRoute` is computed, early-return a full-screen content when `currentRoute == Routes.PLAYER`:
     `if (currentRoute == Routes.PLAYER) { content(Modifier.fillMaxSize()); return }`
   - This removes the `NavigationBar`/`NavigationRail` (and its padding) only for the player; all other routes keep the bottom bar.

4. **In-app player rotation** — `feature/player/src/main/java/com/pulldock/feature/player/PlayerScreen.kt`
   - Remove `setOrientation(...)` and the `DisposableEffect(Unit) { onDispose { setOrientation(context, false) } }` (lines ~180-182) and the `setOrientation` helper (lines ~600-607). Remove the now-unused `activityOf` helper if nothing else uses it.
   - Rotate button (lines ~453-459): keep toggling `landscape`, but drop the `setOrientation(context, landscape)` call.
   - Wrap the player `Box` (the black `Box(fillMaxSize)` at ~line 228) in `BoxWithConstraints` so the content can be rotated:
     - Read `maxWidth`/`maxHeight`.
     - When `landscape` is true, render the inner content with `Modifier.width(maxHeight).height(maxWidth).graphicsLayer { rotationZ = 90f }`; otherwise `Modifier.fillMaxSize()`.
     - Center the inner content in the outer `Box` (`contentAlignment = Alignment.Center`) so the rotated box fits.
   - Add imports: `androidx.compose.foundation.layout.BoxWithConstraints`, `androidx.compose.ui.graphics.graphicsLayer` (and `width`/`height` if not present).
   - Keep `PlayerView` `RESIZE_MODE_FIT`; the rotated container yields a correct landscape layout. Compose transforms pointer events through `graphicsLayer`, so tap/seek/volume gestures remain mapped.

5. **Cleanup**
   - Remove now-unused imports (`nestedScroll`/`TopAppBarDefaults` were already removed from Home/Library/Settings; do not reintroduce).
   - Do not change unrelated files.

## Validation
- `./gradlew :app:assembleDebug` succeeds.
- Install on device.
- Home/Library/Settings: nav bar is a bottom bar; rotating the device does not rotate the app or move the bar.
- Open a video: nav bar is hidden; press the rotate (ScreenRotation) button → video + controls rotate 90° in-app, activity stays portrait, no system auto-rotate prompt.
- Rotate back → video returns to portrait; close player → nav bar reappears.
- Audio items: rotate button is irrelevant (no video surface) but must not crash.

## Risks / notes
- In-app rotation relies on Compose transforming pointer coordinates through `graphicsLayer`; verify seek/volume gestures in landscape on-device.
- Portrait lock means tablets/foldables no longer get the rail; this matches the requested bottom-bar behavior.
- Out of scope: making the player a separate full-screen activity, and any unrelated layout changes.

## Files touched
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/pulldock/MainActivity.kt`
- `app/src/main/java/com/pulldock/navigation/MainScaffold.kt`
- `feature/player/src/main/java/com/pulldock/feature/player/PlayerScreen.kt`
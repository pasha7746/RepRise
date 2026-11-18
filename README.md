# RepRise Android app

A dark, offline-first Android workout tracker for push-ups and crunches.

## Build an APK using GitHub Actions (no Android Studio required)

1. Create a GitHub account if you do not already have one.
2. Create a new repository named `RepRise`.
3. Upload the contents of this ZIP into the repository (keep the folders).
4. Open the repository's **Actions** tab.
5. Select **Build RepRise APK** and press **Run workflow** (or push to `main`).
6. When the workflow finishes, open its run and download the `RepRise-debug-apk` artifact.
7. Extract the artifact ZIP on your Android phone and install `app-debug.apk`.
   Android may ask you to allow installs from your browser or file manager.

## Current app behaviour

- First launch asks for starting push-up and crunch targets.
- Record each set separately and enter total workout time in minutes.
- Data is stored locally on the phone using app preferences.
- History and simple progress charts are included.
- Each exercise progresses independently.
- No workout recorded on a day means no progression change.

## Progression rules in this starter build

- 120%+ of target: +4
- 110–119%: +3
- 100–109%: +2
- 90–99%: +1
- 75–89%: no change
- 50–74%: -1
- Below 50%: -2
- Meeting the target in two or fewer sets: an additional +1
- Meeting the target with at least the same reps as the previous session, while taking at least 10% less time: an additional +1

The set and time bonuses reward efficient performance. Time comparisons are skipped when there is no previous timed workout to compare against. These rules can be refined after testing.

## Technical notes

- Kotlin + Jetpack Compose
- Minimum Android version: Android 8.0 (API 26)
- Target SDK: 35
- Debug APK is for personal testing. A signed release build is needed for Play Store distribution.

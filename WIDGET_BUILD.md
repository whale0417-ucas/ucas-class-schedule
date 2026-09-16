# Home-screen timetable widget

The Android project includes a native AppWidget (`TimetableWidgetProvider`) and a Capacitor bridge (`WidgetSyncPlugin`).

## Data flow
1. The app loads the real course catalog from `data/courses.json` / bundled `mobile-www/data`.
2. The selected plan is stored in the existing `ucas-planner-v1-${termId}` localStorage record.
3. On native app startup, plan changes, semester changes, and calendar resume, `widget-sync.js` copies the selected course IDs resolved against the real catalog into native SharedPreferences.
4. The widget reads the normalized `name`, `sessions.day`, `sessions.periods`, `sessions.room`, and `sessions.weeks` fields. It calculates the current teaching week from `semesterStart` and displays Monday–Thursday, rows 1–2 / 3–4 / 5–6 / 7–8.
5. Tapping the widget launches the main app.

The widget therefore does not invent or guess course fields and does not depend on the WebView being open after synchronization.

## Build
Requires Node.js, JDK 21, Android SDK 36 and Build Tools 36.0.0. The project’s existing `scripts/build_apk.py` performs the web asset preparation, Capacitor sync, Gradle build, signing and SHA-256 generation.

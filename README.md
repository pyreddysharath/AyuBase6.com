# AyuBase6 Android

Capacitor wrapper around the AyuBase6 web app (pyreddysharath/AyuBase6.com), with reminders scheduled on the phone.

- `src/android-bridge.js` — loaded first in the app: phone-backed Notification API, sends the reminder rules to the phone, live content.
- `scripts/prepare-www.mjs` — builds `www/` from the web app (`WEB_DIR`, default `./web`).
- `android/app/src/main/java/com/ayubase6/app/` — `AyuRemindersPlugin` (bridge), `ReminderRules` (port of the push server's rules.js), `ReminderScheduler` (alarms, channels, notifications), `AlarmReceiver`, `BootReceiver`.
- `.github/workflows/android-debug.yml` — builds the debug APK and publishes it as the `debug-latest` pre-release.
- `DECISIONS.md` — every choice, with the reason.

Local build (needs Android SDK): `npm ci && WEB_DIR=../AyuBase6.com npm run sync && cd android && ./gradlew assembleDebug`.
Parity fixture after a change to the server rules: `node scripts/make-parity-fixture.mjs ../ayubase6-push/src/rules.js`.

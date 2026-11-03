# AyuBase6 Android — decisions

Each line: the choice, then why. Change any of them later; the file to touch is in brackets.

## App
- **Name "AyuBase6", package id `com.ayubase6.app`** — matches the site's domain; the id can never change after the first Play release. [capacitor.config.json, android/app/build.gradle]
- **Icon: the lotus (icons/icon-512.png) on white; status-bar icon: the lotus outline** — same mark as the web app; Android needs a one-colour icon for the status bar. [android/app/src/main/res/mipmap-*, drawable/ic_stat_lotus.xml]
- **Minimum Android 8.0 (API 26)** — covers almost all phones in use and gives notification channels and modern alarms without workarounds. [android/variables.gradle]
- **Capacitor 8.6** — current stable; wraps the existing single-file app with no rewrite. [package.json]

## Web files
- **Bundled, not loaded from the live site** — the app opens offline and is reviewed as a fixed build; a site change needs a new APK. [scripts/prepare-www.mjs]
- **Built from the website repository at build time, not copied into this repo** — one codebase for web and Android; the workflow checks out `AyuBase6.com` (main, or a chosen ref). [.github/workflows/android-debug.yml]
- **Base file: `index.html` (the "with push" build), with push turned off by an empty `config/push.json`** — it is the build that already produces the reminder rules (`ayuPush.buildRules`). [scripts/prepare-www.mjs]
- **Live from ayubase6.com: `content/` JSON and media, `config/announcements.json`, `config/OurOtherSites.json`** — new videos and announcements reach the app without a new release; GitHub Pages, so no server requests. [src/android-bridge.js, prepare-www.mjs]
- **Web ads (AdSense) not loaded in the app** — AdSense is for websites, not app web views; an app would need AdMob instead. [prepare-www.mjs]
- **No service worker in the app** — the phone shows notifications itself; the web cache is not needed for bundled files. Side effect: "Save for offline" videos are not served offline in the app yet. [src/android-bridge.js]

## Reminders
- **Local notifications only, no web push** — no server, no request limit, works offline. [src/android-bridge.js]
- **Own small native scheduler instead of the Local Notifications plugin** — one Android alarm for the next due minute, worked out from the same rules the server uses (a Java port of `rules.js`, checked minute by minute against it in a unit test). This avoids Android's limit of about 500 pending alarms, keeps "every N minutes" and "only these days" exact, and keeps working if the app is not opened for days. [ReminderRules.java, ReminderScheduler.java, ReminderRulesTest.java]
- **Rules are sent from the app whenever they change** (same triggers as the push build: any tap or change, leaving the app, every 5 s if different) — local call, no network. [src/android-bridge.js]
- **Reminders the phone schedules are not also fired by the page's own timers** — avoids double notifications and double sounds; previews, the Help test and the eye timer still show straight away. [src/android-bridge.js]
- **Rescheduled after restart, app update, clock or time-zone change, and when exact alarms are allowed** — `BootReceiver`. [AndroidManifest.xml]
- **Exact alarms (`SCHEDULE_EXACT_ALARM`) with a fallback to inexact** — Android 14+ turns this off by default; the app shows a small card with an Allow button. Not `USE_EXACT_ALARM`, which Play allows only for alarm-clock and calendar apps. [ReminderScheduler.java, src/android-bridge.js]
- **Two notification channels: "Reminders" (sound, vibration, high importance) and "Timers" (quiet countdowns)** — people can change sound per channel in Android settings. [ReminderScheduler.java]
- **A late alarm (phone off) shows only reminders from the last 60 minutes, latest one each** — same as the server, so nobody gets a flood of old alerts. [ReminderScheduler.java]
- **Notification buttons (Done, Not yet, Turn off) not in the first version** — tapping opens the right section; buttons come next. [ReminderScheduler.java]
- **Battery advice stays in the Help text** — exact alarms fire in Doze; `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is restricted on Play. A settings shortcut exists in the plugin (`openBatterySettings`). [AyuRemindersPlugin.java]

## Builds
- **Debug APK from GitHub Actions** — the sandbox cannot reach the Android SDK or Maven. Each push to main publishes the APK as the `debug-latest` pre-release and as a run artifact. [.github/workflows/android-debug.yml]
- **Same debug signing key on every build (kept in the Actions cache)** — a new APK installs over the old one and keeps data. The cache expires after 7 days without builds; then uninstall once. To make it permanent, add a repository secret `DEBUG_KEYSTORE_B64`. No keys or passwords are in the repo. [workflow]
- **Release signing not set up yet** — needs an upload key that Sharath keeps; it goes in repository secrets, never in the repo.

## Google Play (later)
- **Personal developer account, one-time USD 25** — new personal accounts must run a closed test (currently 12 testers for 14 days; check the current rule before starting).
- **Privacy policy URL: a page on ayubase6.com** (e.g. `/privacy`) — Play needs a public URL; the app keeps data on the phone.
- **Health data declaration: "Health apps" form, data stored on the device only, not shared** — the app tracks vitals and routines but sends nothing to a server.
- **Exact-alarm declaration: reminders at times the person sets** — required for `SCHEDULE_EXACT_ALARM`.

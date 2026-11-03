# AyuBase6 Android — decisions

Each line: the choice, then why. Change any of them later; the file to touch is in brackets.

## App
- **Name "AyuBase6", package id `com.ayubase6.app`** — matches the site's domain; the id can never change after the first Play release. [capacitor.config.json, android/app/build.gradle]
- **Icon: the lotus (icons/icon-512.png) on white; status-bar icon: the lotus outline** — same mark as the web app; Android needs a one-colour icon for the status bar. [android/app/src/main/res/mipmap-*, drawable/ic_stat_lotus.xml]
- **Minimum Android 8.0 (API 26)** — covers almost all phones in use and gives notification channels and modern alarms without workarounds. [android/variables.gradle]
- **Capacitor 8.6** — current stable; wraps the existing single-file app with no rewrite. [package.json]

- **Text size fixed at 100% (the phone's font-size setting does not enlarge the app)** — the app looked larger than the website; pinch-zoom still works. [MainActivity.java]

## Web files
- **Bundled, not loaded from the live site** — the app opens offline and is reviewed as a fixed build; a site change needs a new APK. [scripts/prepare-www.mjs]
- **Built from the website repository at build time, not copied into this repo** — one codebase for web and Android; the workflow checks out `AyuBase6.com` (main, or a chosen ref). [.github/workflows/android-debug.yml]
- **Base file: `index-with-Cloudflare.html` (the "with push" build), with push turned off by an empty `config/push.json`** — it is the build that produces the reminder rules (`ayuPush.buildRules`); the live site's `index.html` is the no-push build since 03-Nov-2026. [scripts/prepare-www.mjs]
- **Android wording for the Reminder hours i, Checks Notify Me i and Help → Troubleshooting, and no install buttons** — the website texts describe browser tabs, installing and the push server, which do not apply to the app. [scripts/prepare-www.mjs, src/android-bridge.js]
- **Live from ayubase6.com: `content/` JSON and media, `config/announcements.json`, `config/OurOtherSites.json`** — new videos and announcements reach the app without a new release; GitHub Pages, so no server requests. [src/android-bridge.js, prepare-www.mjs]
- **Web ads (AdSense) not loaded in the app** — AdSense is for websites, not app web views; an app would need AdMob instead. [prepare-www.mjs]
- **No service worker in the app** — the phone shows notifications itself; the web cache is not needed for bundled files. "Save for offline" still works: the page saves videos in its own storage as on the website, and the bridge plays those saved copies (and keeps a video once played, like the website's service worker). The video lists are kept from the last online start, so the app knows its videos offline. Videos are not bundled in the APK, so new videos need no app release. [src/android-bridge.js]

## Reminders
- **Local notifications only, no web push** — no server, no request limit, works offline. [src/android-bridge.js]
- **Own small native scheduler instead of the Local Notifications plugin** — one Android alarm for the next due minute, worked out from the same rules the server uses (a Java port of `rules.js`, checked minute by minute against it in a unit test). This avoids Android's limit of about 500 pending alarms, keeps "every N minutes" and "only these days" exact, and keeps working if the app is not opened for days. [ReminderRules.java, ReminderScheduler.java, ReminderRulesTest.java]
- **Eyes, Walk and Water count "every N minutes" from the moment they were turned on or changed (frequency, hours, days), not from the start of the reminder hours** — Sharath's request; reminders fire only inside the hours. The web app and push server still count from the start of the hours. [src/android-bridge.js, ReminderRules.java]
- **Rules are sent from the app whenever they change** (same triggers as the push build: any tap or change, leaving the app, every 5 s if different) — local call, no network. [src/android-bridge.js]
- **Reminders the phone schedules are not also fired by the page's own timers** — avoids double notifications and double sounds; previews, the Help test and the eye timer still show straight away. [src/android-bridge.js]
- **Rescheduled after restart, app update, clock or time-zone change, and when exact alarms are allowed** — `BootReceiver`. [AndroidManifest.xml]
- **Exact alarms (`SCHEDULE_EXACT_ALARM`) with a fallback to inexact** — Android 14+ turns this off by default; the app shows a small card with an Allow button. Not `USE_EXACT_ALARM`, which Play allows only for alarm-clock and calendar apps. [ReminderScheduler.java, src/android-bridge.js]
- **Settings pages for notifications and "Alarms & reminders" open inside the app's task and close themselves once the permission is allowed** — the app comes back by itself; Back also returns to the app. A "Notifications are off" card with **Open settings** appears when reminders are on but Android has notifications off. [AyuRemindersPlugin.openAndReturn, src/android-bridge.js]
- **Two notification channels: "Reminders" (sound, vibration, high importance) and "Timers" (quiet countdowns)** — people can change sound per channel in Android settings. [ReminderScheduler.java]
- **A late alarm (phone off) shows only reminders from the last 60 minutes, latest one each** — same as the server, so nobody gets a flood of old alerts. [ReminderScheduler.java]
- **Notification buttons (Done, Not yet, Turn off) not in the first version** — tapping opens the right section; buttons come next. [ReminderScheduler.java]
- **Battery advice stays in the Help text** — exact alarms fire in Doze; `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is restricted on Play. A settings shortcut exists in the plugin (`openBatterySettings`). [AyuRemindersPlugin.java]

## Builds
- **Debug APK from GitHub Actions** — the sandbox cannot reach the Android SDK or Maven. Each push to main publishes the APK as the `debug-latest` pre-release and as a run artifact. [.github/workflows/android-debug.yml]
- **Same debug signing key on every build (kept in the Actions cache)** — a new APK installs over the old one and keeps data. The cache expires after 7 days without builds; then uninstall once. To make it permanent, add a repository secret `DEBUG_KEYSTORE_B64`. No keys or passwords are in the repo. [workflow]
- **Release APK signed with Sharath's own key from repository secrets** (`RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) — the same key for every release so updates install over the old app; never in the repo. [android/app/build.gradle, workflow]
- **Publishing is a manual run ("publish" ticked), not every push** — people get an update notice only for versions Sharath chooses to release. [workflow]
- **The public APK is a release in the website repository (`AyuBase6.com`), link `…/releases/latest/download/AyuBase6.apk`, plus `config/android-app.json`** — the Android repo is private; GitHub releases keep the 5 MB file out of the website's history and cost no server requests. Needs secret `WEB_DEPLOY_TOKEN`. [workflow]
- **Website: a one-time banner on Android phone browsers ("Not now" = 30 days), "Get the app" instead of Install there, and Help → Get app on every device; all hidden until the first version is published and inside the app** — no pop-up on every visit. [website get-app-20261103]
- **Update notice in the app, read from `config/android-app.json`; "Later" skips that version** — a downloaded APK never updates itself. [src/android-bridge.js]
- **Backup files saved through the phone (Downloads on Android 10+, share sheet on 8–9)** — the app's web view cannot download the page's own files. [AyuRemindersPlugin.saveFile, src/android-bridge.js]

## Google Play (later)
- **Personal developer account, one-time USD 25** — new personal accounts must run a closed test (currently 12 testers for 14 days; check the current rule before starting).
- **Privacy policy URL: a page on ayubase6.com** (e.g. `/privacy`) — Play needs a public URL; the app keeps data on the phone.
- **Health data declaration: "Health apps" form, data stored on the device only, not shared** — the app tracks vitals and routines but sends nothing to a server.
- **Exact-alarm declaration: reminders at times the person sets** — required for `SCHEDULE_EXACT_ALARM`.

# AyuBase6 — Handoff (read this first)

Owner: Sharath (Hyderabad, India). App: **AyuBase6**, a health PWA (Body, Food, Checks, Reminders, Mind, Vitals). Live at **ayubase6.com** (GitHub Pages).

## Repositories
- App (website): `pyreddysharath/AyuBase6.com` — `index.html` (no-push build, the live site), `index-with-Cloudflare.html` (push build, base of the Android app), `config/announcements.json`, `content/` JSON, `sw.js`, `icons/`.
- Push server: `pyreddysharath/ayubase6-push` (Cloudflare Worker `ayubase6-push`, redeploys itself on commit). **Paused**: `crons = []` in `wrangler.toml` (no every-minute timer). Free-tier request limit is a concern, so avoid anything that adds server calls.
- Android: `pyreddysharath/AyuBase6-Android` (private). See "Android app" below and `DECISIONS.md`. In this repository only, commit and push as you go without waiting for "commit". The website and push-server repositories still follow the "commit only when told" rule.

## Two builds of the same app (single-file HTML)
- **Without Cloudflare** = `index.html` in the repo (the live site since 03-Nov-2026). No server; reminders only while the app is open. Delivered to Sharath as `index-without-Cloudflare.html`.
- **With Cloudflare** = `index-with-Cloudflare.html` in the repo. Web push through the worker (`push.json` holds the API address). Not served as the site; the Android app is built from it (it holds `buildRules()`), with push switched off. Delivered as `index-with-Cloudflare.html`.
- To turn web push back on: swap the two file names again, point the Android `scripts/prepare-www.mjs` at the push build, restore `crons = ["* * * * *"]`.
- Never write the word "Cloudflare" in user-visible app text.
- Changes are applied to both builds with exact-count Python string-replace patches (assert each match count). Extra CSS/JS are appended as `<style id=…>` / `<script id=…>` blocks before the last `</body>`.

## Working rules (Sharath's standing rules)
1. Make only the requested changes.
2. Test in a real browser (Playwright) on desktop and mobile before sharing. Check for page errors.
3. "How about / what do you think" questions: give an opinion first and wait for "go ahead".
4. Deliver only the modified files, named `index-with-Cloudflare.html` and `index-without-Cloudflare.html`. No zip. New data files (e.g. `announcements.json`) are delivered separately.
5. Commit and push to GitHub **only when he says "commit"**. Commit footer lines: `Co-Authored-By: Claude <model> <noreply@anthropic.com>` and the session link.
6. Keep notes short. End by naming the files. Answer his questions at the end of the reply.

## Features built so far (key facts)
- **Footer icons** (order): Backup, Settings, Announcements (solid megaphone), Help, Legal, Other sites. Each takes the open section's colour (`--footer-accent`). Dots: Backup amber (backup due), Settings (notifications or cookies on), Announcements (unread), Help (notification problem).
- **Help** has four underline tabs: Notifications (button "Test my notifications", step list with i popups, "Test again"), Troubleshooting, Feedback, Get app (shown once an Android version is published). Tapping a tab scrolls the text into view, never past the footer icon row. The test notification uses the current section colour.
- **Announcements** (`config/announcements.json`, fetched with `cache:'no-cache'` when the app opens or comes to the front if the last check was 30+ min ago, every 30 min while open, and always when the section opens). Fields: `id`, `date` (dd-MMM-yyyy, e.g. 05-Nov-2026; yyyy-mm-dd also read), `title` (90 chars), `message` (500), `icon` ("i", "!" or `icons/...`), `level` (info|warning), `banner` (default true), `tab` (default true), `repair` (default false; runs service-worker update and push re-register once per id, works with banner and tab false), `expires` (expired messages are ignored completely), `link` {text, help|url}. Blank fields are ignored; a blank id gets an automatic one. Max 10 listed. A message stays in the tab 30 days from its date, and 1 day after first seen. Closing a banner marks it read (clears the dot). localStorage keys: ayuAnnLast, ayuAnnCache, ayuAnnDismissed, ayuAnnRead, ayuAnnSeen, ayuAnnRepaired.
- **My Reminders** are saved in localStorage (`ayuBase6-my-reminders`) and are part of Backup (Reminders group).
- **Day selection** "On these days": everyday by default; Every day / Weekdays / Weekends buttons show selected (`.on`) when they match.
- **Reset buttons**: hover text and accessible name "Reset"; on phones a small visible "Reset" label.
- **Self-repair** for push (`ayuPush.repair()`), quiet self-heal after 5 s and on returning to the front (at most once a minute).
- Tour (How it works) lists all six footer icons.

## Known limits / not built
- On the website, reminders (including repeating ones) ring only while the app is open; web push is paused. The Android app delivers them with the app closed.
- Broadcast push to all users was **not** built (Cloudflare limit). Announcements are checked only when the app is open.
- Chrome's "Tap to copy the URL for this app" notice is the phone's own, not ours.

## Test setup (in the sandbox)
- Serve copies with `python3 -m http.server`, drive with Playwright (Chromium). Headless reports notification permission "denied", so stub `Notification.permission` and `showNotification` to test. Accept or pre-set the cookie consent key `healtheeCookieConsent` and `ayuBase6-tour-seen` so banners do not block clicks.

## State at handoff
- 03-Nov-2026: files swapped (live site = no-push build), Reminders and Checks switches independent, push-server timer paused, Android app offer on the website (hidden until the first publish). Everything requested so far is committed. Next on Sharath's side: release key + token secrets, then the first publish run.
- Section colours (used by `--footer-accent`): Body `#2563eb`, Food `#16a34a`, Checks amber, Reminders teal `#0d9488`, Mind purple, Vitals teal-blue. Popups, banners, footer, test notification and tour follow the open section.
- Footer/section code: `FOOTER_SECTIONS`, `FOOTER_ALIAS`, `FOOTER_SUBS`, `showFooterSection(id)`, `showFooterSub(parent,sub)`; Help subs are check (shown as "Notifications"), trouble, feedback, app (added at run time by `get-app-20261103`); Announcements is its own section.
- Notifications code: `showNudgeNotification`, `nudgeArt` + `NUDGE_THEMES`, `buildRules()` in the push block of `index-with-Cloudflare.html` (what the server and the Android app schedule from: custom reminders, Eyes, Walk, Water, Eyes Exercise, Checks), `ayuPush` (active/api/error/sync/repair).
- Storage keys worth knowing: `ayuBase6-reminder-hours`, `ayubase6_reminder_section_states_v1`, `ayuBase6-my-reminders`, `ayuBase6-routine-*`, `ayu_vitals_tracker_v1`, `ayuBase6-body-choices`, `ayuBase6-food-choices`, `healtheeCookieConsent`, `ayuBase6-tour-seen`, `ayuAnn*`. Backup groups: Body, Food, Checks, Reminders, Vitals.
- Service worker only intercepts `/content/` JSON (network-first). Config files such as `config/announcements.json` pass through.

## Sandbox gotchas
- GitHub: pushes to the app repos work, but the session is bound to its configured repositories. It cannot create repositories, and `gh` API calls outside them are refused. Repositories must be created by Sharath and given to the session.
- Outbound network is an allowlist (package registries, GitHub). Google Fonts, CDNs and Android SDK/Maven hosts may be refused, so Android builds may need GitHub Actions.
- The test web server dies between shell commands; start it in every command. Page errors about fonts/CDNs in tests are expected and harmless.
- Headless Chromium reports notification permission as denied; stub `Notification.permission`, `Notification` and `ServiceWorkerRegistration.prototype.showNotification` when testing.

## Android app (`AyuBase6-Android`)
- Capacitor 8 wrapper, package `com.ayubase6.app`, min Android 8.0. Every choice with its reason is in `DECISIONS.md`.
- `scripts/prepare-www.mjs` builds `www/` from the website's `index-with-Cloudflare.html` (exact-count patches: bridge script, live media, no ads, Android wording for the Reminder hours i, Checks Notify Me i and Troubleshooting) plus an empty `config/push.json`.
- `src/android-bridge.js`: phone-backed Notification API, sends `buildRules()` to the phone when anything changes, live `content/` and announcements from ayubase6.com, no service worker, hides install buttons, "Alarms & reminders" card, Eyes/Walk/Water count from the moment they are turned on (and "Next:" shows that time).
- Native (Java): `AyuRemindersPlugin`, `ReminderRules` (port of the push server's `rules.js`, unit-tested against it), `ReminderScheduler` (one exact alarm for the next due minute, channels, notifications in section colours), `NotificationArt`, `AlarmReceiver`, `BootReceiver`. Text size fixed at 100%.
- GitHub Actions builds a debug APK on every push to main and publishes it as the `debug-latest` pre-release. A website change needs a push to the Android repo (or a manual run) to reach the APK.
- Publishing: Actions → "Android debug APK" → Run workflow with **publish** ticked builds the signed release APK, creates release `app-v<N>` in `AyuBase6.com` (link `https://github.com/pyreddysharath/AyuBase6.com/releases/latest/download/AyuBase6.apk`) and commits `config/android-app.json` there. Secrets in AyuBase6-Android: `RELEASE_KEYSTORE_B64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `WEB_DEPLOY_TOKEN`.
- Website offer (`get-app-20261103` block, both builds): Android phone browsers get a one-time banner (`ayuAppBannerLater`, 30 days) and "Get the app" on the Reminders/Checks install buttons; Help has a fourth tab "Get app" (`sec-app`). Nothing shows until `config/android-app.json` exists, or inside the app (`window.AYU_ANDROID`).
- The app shows "Update available" when `config/android-app.json` has a higher `versionCode` ("Later" = `ayuAndroidUpdateLater`). Backup files are saved through the phone (`saveFile`).
- Offline videos in the app: Body → Save for offline stores them in cache `ayubase6-media-v2` (the web app's own code); the bridge switches media addresses to those saved copies and keeps the last video lists (cache `ayubase6-app-lists`).
- Not built yet: buttons on notifications (Done, Not yet, Turn off), Play Console listing.

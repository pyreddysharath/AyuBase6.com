# AyuBase6 — Handoff (read this first)

Owner: Sharath (Hyderabad, India). App: **AyuBase6**, a health PWA (Body, Food, Checks, Reminders, Mind, Vitals). Live at **ayubase6.com** (GitHub Pages).

## Repositories
- App (website): `pyreddysharath/ayubase6.com` — `index.html` (push build), `index-without-Cloudflare.html`, `config/announcements.json`, `content/` JSON, `sw.js`, `icons/`.
- Push server: `pyreddysharath/ayubase6-push` (Cloudflare Worker `ayubase6-push`, redeploys itself on commit). Free-tier request limit is a concern, so avoid anything that adds server calls.
- Android: `pyreddysharath/<ANDROID-REPO-NAME>` (private; this project). Plan below. In this repository only, commit and push as you go without waiting for "commit". The website repositories still follow the "commit only when told" rule.

## Two builds of the same app (single-file HTML)
- **With Cloudflare** = `index.html` in the repo. Web push through the worker (`push.json` holds the API address). Delivered to Sharath as `index-with-Cloudflare.html`.
- **Without Cloudflare** = `index-without-Cloudflare.html`. No server; reminders only while the app is open. Delivered as `index-without-Cloudflare.html`.
- Never write the word "Cloudflare" in user-visible app text.
- Changes are applied to both builds with exact-count Python string-replace patches (assert each match count). Extra CSS/JS are appended as `<style id=…>` / `<script id=…>` blocks before the last `</body>`.

## Working rules (Sharath's standing rules)
1. Make only the requested changes.
2. Test in a real browser (Playwright) on desktop and mobile before sharing. Check for page errors.
3. "How about / what do you think" questions: give an opinion first and wait for "go ahead".
4. Deliver only the modified files, named `index-with-Cloudflare.html` and `index-without-Cloudflare.html`. No zip. New data files (e.g. `announcements.json`) are delivered separately.
5. Commit and push to GitHub **only when he says "commit"**. Commit footer lines: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` and the session link.
6. Keep notes short. End by naming the files. Answer his questions at the end of the reply.

## Features built so far (key facts)
- **Footer icons** (order): Backup, Settings, Announcements (solid megaphone), Help, Legal, Other sites. Each takes the open section's colour (`--footer-accent`). Dots: Backup amber (backup due), Settings (notifications or cookies on), Announcements (unread), Help (notification problem).
- **Help** has three underline tabs: Notifications (button "Test my notifications", step list with i popups, "Test again"), Troubleshooting, Feedback. Tapping a tab scrolls the text into view, never past the footer icon row. The test notification uses the current section colour.
- **Announcements** (`config/announcements.json`, fetched with `cache:'no-cache'` when the app opens or comes to the front if the last check was 30+ min ago, every 30 min while open, and always when the section opens). Fields: `id`, `date` (dd-MMM-yyyy, e.g. 05-Nov-2026; yyyy-mm-dd also read), `title` (90 chars), `message` (500), `icon` ("i", "!" or `icons/...`), `level` (info|warning), `banner` (default true), `tab` (default true), `repair` (default false; runs service-worker update and push re-register once per id, works with banner and tab false), `expires` (expired messages are ignored completely), `link` {text, help|url}. Blank fields are ignored; a blank id gets an automatic one. Max 10 listed. A message stays in the tab 30 days from its date, and 1 day after first seen. Closing a banner marks it read (clears the dot). localStorage keys: ayuAnnLast, ayuAnnCache, ayuAnnDismissed, ayuAnnRead, ayuAnnSeen, ayuAnnRepaired.
- **My Reminders** are saved in localStorage (`ayuBase6-my-reminders`) and are part of Backup (Reminders group).
- **Day selection** "On these days": everyday by default; Every day / Weekdays / Weekends buttons show selected (`.on`) when they match.
- **Reset buttons**: hover text and accessible name "Reset"; on phones a small visible "Reset" label.
- **Self-repair** for push (`ayuPush.repair()`), quiet self-heal after 5 s and on returning to the front (at most once a minute).
- Tour (How it works) lists all six footer icons.

## Known limits / not built
- Repeating reminders only reach a closed app via push (with-Cloudflare build, installed app, registered phone). Web apps cannot run in the background.
- Broadcast push to all users was **not** built (Cloudflare limit). Announcements are checked only when the app is open.
- Chrome's "Tap to copy the URL for this app" notice is the phone's own, not ours.

## Test setup (in the sandbox)
- Serve copies with `python3 -m http.server`, drive with Playwright (Chromium). Headless reports notification permission "denied", so stub `Notification.permission` and `showNotification` to test. Accept or pre-set the cookie consent key `healtheeCookieConsent` and `ayuBase6-tour-seen` so banners do not block clicks.

## State at handoff
- Last commit on `ayubase6.com` main: **f3addc8** (footer highlight, tap states). Everything requested so far is committed. No open web changes.
- Section colours (used by `--footer-accent`): Body `#2563eb`, Food `#16a34a`, Checks amber, Reminders teal `#0d9488`, Mind purple, Vitals teal-blue. Popups, banners, footer, test notification and tour follow the open section.
- Footer/section code: `FOOTER_SECTIONS`, `FOOTER_ALIAS`, `FOOTER_SUBS`, `showFooterSection(id)`, `showFooterSub(parent,sub)`; Help subs are check (shown as "Notifications"), trouble, feedback; Announcements is its own section.
- Notifications code: `showNudgeNotification`, `nudgeArt` + `NUDGE_THEMES`, `buildRules()` in the push block (this is what the server schedules from: custom reminders, Eyes, Walk, Water, Eyes Exercise, Checks), `ayuPush` (active/api/error/sync/repair).
- Storage keys worth knowing: `ayuBase6-reminder-hours`, `ayubase6_reminder_section_states_v1`, `ayuBase6-my-reminders`, `ayuBase6-routine-*`, `ayu_vitals_tracker_v1`, `ayuBase6-body-choices`, `ayuBase6-food-choices`, `healtheeCookieConsent`, `ayuBase6-tour-seen`, `ayuAnn*`. Backup groups: Body, Food, Checks, Reminders, Vitals.
- Service worker only intercepts `/content/` JSON (network-first). Config files such as `config/announcements.json` pass through.

## Sandbox gotchas
- GitHub: pushes to the app repos work, but the session is bound to its configured repositories. It cannot create repositories, and `gh` API calls outside them are refused. Repositories must be created by Sharath and given to the session.
- Outbound network is an allowlist (package registries, GitHub). Google Fonts, CDNs and Android SDK/Maven hosts may be refused, so Android builds may need GitHub Actions.
- The test web server dies between shell commands; start it in every command. Page errors about fonts/CDNs in tests are expected and harmless.
- Headless Chromium reports notification permission as denied; stub `Notification.permission`, `Notification` and `ServiceWorkerRegistration.prototype.showNotification` when testing.

## Android decisions (decide yourself; Sharath does not need to approve at this stage)
Use the suggestions below, and record every choice with a one-line reason in `DECISIONS.md` in the Android repository so Sharath can change it later.
- App name and package id (suggest `com.ayubase6.app`), app icon (reuse the lotus icon), minimum Android version (suggest 8.0 / API 26).
- Whether the Android app loads the live site (always up to date, needs internet first time) or bundles the files (works offline, needs a new release for each change). Suggest bundling, with the announcements file and `content/` JSON fetched live.
- Whether to keep web push in the Android app (suggest no: use local notifications only, so there is no server dependency).
- Play Console account (one-time $25), privacy policy URL, health-data declaration, closed-test testers.

## Next: Android app (this project)
Goal: remove the web-push limits with on-device (local) notifications.
- Approach: **Capacitor** wrapper around the existing HTML; add the Local Notifications plugin; reschedule on reboot; request exact-alarm permission.
- Reuse the same app files (single codebase for web and Android).
- Build and sign through GitHub Actions if the sandbox cannot reach the Android SDK downloads.
- Google Play: one-time $25 fee; new personal accounts may need a closed test (about 12 testers for 14 days) — verify current rules. Apple costs $99 per year, so iPhone comes later.
- Mapping idea: reuse the reminder rules that `buildRules()` already produces and turn each into scheduled local notifications; reschedule when reminders, hours or days change, and after reboot.
- Android needs a notification channel with sound, the notification permission (Android 13+), exact-alarm permission and battery-optimisation advice (the Help text already covers this for the web app).
- First step: a small proof — the app in the wrapper with one real reminder firing while the app is closed.

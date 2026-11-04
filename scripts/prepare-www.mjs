// Builds www/ (the files bundled into the Android app) from a checkout of the AyuBase6 web app.
// Web app folder: env WEB_DIR, else ./web (GitHub Actions checks the website repository out there).
// The web app itself is not changed; the Android copy gets a few exact-count patches (each must match once).
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const web = path.resolve(root, process.env.WEB_DIR || 'web');
const out = path.join(root, 'www');
const LIVE = 'https://ayubase6.com/';

if (!fs.existsSync(web)) {
  console.error('Web app not found at ' + web + ' (set WEB_DIR).');
  process.exit(1);
}
fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(path.join(out, 'config'), { recursive: true });

// Base: the "with push" build (index-with-Cloudflare.html), because it builds the reminder rules (ayuPush.buildRules)
// that the phone schedules. Push itself is switched off below (empty push.json). Older checkouts had it as index.html.
const baseName = ['index-with-Cloudflare.html', 'index.html'].find(f => {
  const p = path.join(web, f);
  return fs.existsSync(p) && fs.readFileSync(p, 'utf8').includes('function buildRules(');
});
if (!baseName) { console.error('No web app build with buildRules() found in ' + web); process.exit(1); }
let html = fs.readFileSync(path.join(web, baseName), 'utf8');
function patch(find, repl, label) {
  const n = html.split(find).length - 1;
  if (n !== 1) { console.error('Patch "' + label + '" expected 1 match, found ' + n); process.exit(1); }
  html = html.replace(find, () => repl);
}
patch('<head>\n<meta charset="UTF-8">\n',
  '<head>\n<meta charset="UTF-8">\n<script>window.AYU_ANDROID=true;window.AYU_LIVE_BASE=' + JSON.stringify(LIVE) + ';</script>\n<script src="android-bridge.js"></script>\n',
  'bridge');
patch("new URL('content/' + folder + '/media/' + file, document.baseURI)",
  "new URL('content/' + folder + '/media/' + file, window.AYU_LIVE_BASE || document.baseURI)",
  'live media');
// Ads: the website's AdSense (after the person agrees to Advertising) also runs in the app, through Google's
// WebView API for Ads (registered in MainActivity).
// ── Texts that describe the website, said the way the Android app works (reminders arrive with the app closed) ──
function patchBetween(start, end, repl, label) {
  const i = html.indexOf(start), j = i < 0 ? -1 : html.indexOf(end, i);
  if (i < 0 || j < 0 || html.indexOf(start, i + 1) >= 0) { console.error('Patch "' + label + '": markers not found once'); process.exit(1); }
  html = html.slice(0, i) + repl + html.slice(j + end.length);
}
const ALARMS = 'Allow <b>Alarms &amp; reminders</b> for AyuBase6 (Settings → Apps → AyuBase6) so they arrive at the exact minute. If they are late, set Battery to Unrestricted (Settings → Apps → AyuBase6 → Battery).';
// Reminder hours i
patchBetween("<strong>Controls</strong><ul><li>The switch (top right) turns all reminders on or off. The first time, your browser asks for permission.</li>",
  "<li>Each notification window can disable that reminder, disable all, or close.</li></ul>';",
  "<strong>Controls</strong><ul><li>The switch (top right) turns all reminders on or off. The first time, the phone asks for permission to show notifications.</li>'\n"
  + "      +'<li>The reset icon restores the defaults.</li></ul>'\n"
  + "      +'<strong>On this phone</strong><ul><li>Reminders arrive on time even when AyuBase6 is closed, the phone is locked or has been restarted, with or without internet.</li><li>" + ALARMS + "</li></ul>'\n"
  + "      +'<strong>Good to know</strong><ul><li>The “notifications blocked” warning appears when you switch the toggle on, then disappears after a while.</li><li>Tap a notification to open its section.</li></ul>';",
  'reminder hours info');
// Checks Notify Me i
patch("<li>Each notification has <b>Done</b> (ticks it) and <b>Not yet</b>.</li>",
  "<li>Tap a notification to open Checks and tick the activity there.</li>", 'checks notify buttons');
patchBetween("+'<strong>On a computer</strong><ul style=\"margin:4px 0 0 18px;padding:0\">",
  "Everything they say is here.</li></ul>'; }",
  "+'<strong>On this phone</strong><ul style=\"margin:4px 0 0 18px;padding:0\"><li>Notifications arrive on time even when AyuBase6 is closed or the phone is locked, with or without internet.</li><li>" + ALARMS + "</li></ul>'; }",
  'checks notify info');
// Help → Troubleshooting
patchBetween('<div class="trb">', "If it still fails, tell us in Feedback.</p></details>\n    </div>",
  '<div class="trb">\n'
  + '      <details><summary>No notification when the app is closed</summary><p>Open <b>Help → Notifications</b> and fix any step marked ✕. In Settings → Apps → AyuBase6, allow <b>Notifications</b> and <b>Alarms &amp; reminders</b>, and set Battery to Unrestricted.</p></details>\n'
  + '      <details><summary>No sound</summary><p>Turn the phone\'s notification volume up and check that the reminder\'s sound is not muted in its section. In Settings → Apps → AyuBase6 → Notifications → <b>Reminders</b>, make sure sound is on.</p></details>\n'
  + '      <details><summary>Notifications are blocked</summary><p>Long-press the app icon → App info → Notifications, allow them, then tap Test again.</p></details>\n'
  + '      <details><summary>Reminders are late or skipped</summary><p>Allow <b>Alarms &amp; reminders</b> for AyuBase6 and set Battery to Unrestricted. A reminder missed while the phone was switched off for more than an hour is skipped instead of being shown late, and reminders do not fire on days you did not choose.</p></details>\n'
  + '      <details><summary>Nothing helps</summary><p>Make a Backup first. Then uninstall the app, install it again, allow notifications and run the check, and restore your data from the Backup file. If it still fails, tell us in Feedback.</p></details>\n    </div>',
  'troubleshooting');
fs.writeFileSync(path.join(out, 'index.html'), html);

function copyDir(rel) {
  const src = path.join(web, rel);
  if (fs.existsSync(src)) fs.cpSync(src, path.join(out, rel), { recursive: true });
}
copyDir('icons');
copyDir('favicons');
// PDF reader, PDF maker and the OCR loader are bundled, so Download PDF and reading reports work offline.
// The OCR engine and English data stay on the website and are kept on the phone after first use
// (or "Enable offline reading"), which keeps the APK small.
copyDir('lib/pdfjs');
copyDir('lib/jspdf');
fs.mkdirSync(path.join(out, 'lib', 'tesseract'), { recursive: true });
for (const f of ['tesseract.min.js', 'worker.min.js', 'LICENSE.md', 'LICENSE-core']) {
  const src = path.join(web, 'lib', 'tesseract', f);
  if (fs.existsSync(src)) fs.copyFileSync(src, path.join(out, 'lib', 'tesseract', f));
  else if (/\.js$/.test(f)) { console.error('Missing ' + src); process.exit(1); }
}
for (const f of ['manifest.json', 'OurOtherSites.json']) {
  const src = path.join(web, 'config', f);
  if (fs.existsSync(src)) fs.copyFileSync(src, path.join(out, 'config', f));
}
// No web push in the app: an empty address turns it off. Reminders come from the phone itself.
fs.writeFileSync(path.join(out, 'config', 'push.json'), '{ "api": "" }\n');
fs.copyFileSync(path.join(root, 'src', 'android-bridge.js'), path.join(out, 'android-bridge.js'));

const kb = Math.round(fs.statSync(path.join(out, 'index.html')).size / 1024);
console.log('www/ ready from ' + path.join(web, baseName) + ' (index.html ' + kb + ' KB)');

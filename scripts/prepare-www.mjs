// Builds www/ (the files bundled into the Android app) from a checkout of the AyuBase6 web app.
// Web app folder: env WEB_DIR, else ./web (GitHub Actions checks the website repository out there).
// The web app itself is not changed; the Android copy gets a few exact-count patches (each must match once).
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const web = path.resolve(root, process.env.WEB_DIR || 'web');
const out = path.join(root, 'www');
const LIVE = 'https://ayubase6.com/';

if (!fs.existsSync(path.join(web, 'index.html'))) {
  console.error('Web app not found at ' + web + ' (set WEB_DIR).');
  process.exit(1);
}
fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(path.join(out, 'config'), { recursive: true });

// index.html (the "with push" build is the base; push is switched off below via an empty push.json)
let html = fs.readFileSync(path.join(web, 'index.html'), 'utf8');
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
patch('function loadAdSenseScript(){\n  if (adsenseLoaded) return;',
  'function loadAdSenseScript(){\n  if (adsenseLoaded || window.AYU_ANDROID) return;',
  'no web ads in the app');
fs.writeFileSync(path.join(out, 'index.html'), html);

function copyDir(rel) {
  const src = path.join(web, rel);
  if (fs.existsSync(src)) fs.cpSync(src, path.join(out, rel), { recursive: true });
}
copyDir('icons');
copyDir('favicons');
for (const f of ['manifest.json', 'OurOtherSites.json']) {
  const src = path.join(web, 'config', f);
  if (fs.existsSync(src)) fs.copyFileSync(src, path.join(out, 'config', f));
}
// No web push in the app: an empty address turns it off. Reminders come from the phone itself.
fs.writeFileSync(path.join(out, 'config', 'push.json'), '{ "api": "" }\n');
fs.copyFileSync(path.join(root, 'src', 'android-bridge.js'), path.join(out, 'android-bridge.js'));

const kb = Math.round(fs.statSync(path.join(out, 'index.html')).size / 1024);
console.log('www/ ready from ' + web + ' (index.html ' + kb + ' KB)');

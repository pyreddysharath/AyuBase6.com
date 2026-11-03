// Writes android/app/src/test/resources/parity.json: what the push server's rules.js says is due, minute by minute,
// for a sample set of rules. ReminderRulesTest checks the phone's Java port gives exactly the same answers.
// Usage: node scripts/make-parity-fixture.mjs <path to ayubase6-push/src/rules.js>
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const rulesJs = process.argv[2];
if (!rulesJs) { console.error('Give the path to rules.js'); process.exit(1); }
const { dueNotifications, localParts } = await import(pathToFileURL(path.resolve(rulesJs)).href);

const start = Date.UTC(2026, 10, 1, 0, 0) / 60000;   // Sun 1 Nov 2026 00:00 UTC
const rules = {
  v: 1, tz: 'UTC', phone: true,
  reminders: [
    { key: 'break', title: 'Eyes', body: 'Look 20 feet away', every: 20, from: 540, to: 1080 },
    { key: 'walk', title: 'Walk', body: 'Time for a walk', every: 45, from: 1320, to: 120, days: [1, 2, 3, 4, 5] },
    { key: 'drink', title: 'Water', body: 'Drink a glass of water', every: 60, from: null, to: null }
  ],
  exercise: { title: 'Eye Exercise', body: 'Follow the dot', times: [600, 900], weekdays: [0, 2, 4] },
  checks: { mode: 'beforeend', doneDay: '2026-11-02', done: ['sun'], days: [1, 2, 3], items: [
    { key: 'sun', title: 'Sit in the Sun', body: 'Morning sun', at: 420, end: 540, window: '7:00–9:00 AM', muted: false },
    { key: 'walkex', title: 'Walk or Exercise', body: 'Walk now', at: 1080, end: 1110, window: '6:00–6:30 PM', muted: false },
    { key: 'palms', title: 'Palms', body: 'muted one', at: 500, end: 600, window: 'x', muted: true }
  ] },
  custom: [
    { id: 'r1x', name: 'Medicine', mode: 'times', times: [480, 1260], days: [0, 6] },
    { id: 'r-2', name: 'Stretch', mode: 'regular', every: 95, anchor: (start + 17) * 60000 + 12345 }
  ]
};
const minutes = 7 * 1440;
const expected = {};
for (let t = start; t < start + minutes; t++) {
  const out = dueNotifications(rules, localParts('UTC', t * 60000), t);
  if (out.length) expected[t] = out.map(n => ({ key: n.key, title: n.title, body: n.body, section: n.section }));
}
const file = path.join(path.dirname(new URL(import.meta.url).pathname), '..', 'android/app/src/test/resources/parity.json');
fs.writeFileSync(file, JSON.stringify({ rules, start, minutes, expected }, null, 1));
console.log('parity.json: ' + Object.keys(expected).length + ' minutes with reminders');

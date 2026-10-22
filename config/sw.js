// Tiny service worker for the Nudges notifications.
// It only does one thing: when a notification (or one of its buttons) is
// clicked, tell the open page so it can start / cancel the 20-20-20 timer.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));

self.addEventListener('notificationclick', event => {
  const n = event.notification;
  const key = (n.data && n.data.key) || '';
  const action = event.action || '';
  n.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(list => {
      list.forEach(c => c.postMessage({ type: 'nudge-click', key, action }));
      // Plain click on the notification body with the app closed: reopen it.
      if (!list.length && !action) return self.clients.openWindow('../');
    })
  );
});


const CACHE_NAME = 'ayubase6-pwa-v4';

const APP_SHELL = [
  './',
  './index.html',
  './config/manifest.json',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/apple-touch-icon.png'
];

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE_NAME);

    // Keep the main app pages required for offline use.
    await cache.add('./');
    await cache.add('./index.html');

    // Optional assets should not prevent installation.
    await Promise.all(
      APP_SHELL
        .filter(path => path !== './' && path !== './index.html')
        .map(async path => {
          try {
            await cache.add(path);
          } catch (error) {
            console.warn(
              '[AyuBase6 SW] Could not precache:',
              path,
              error
            );
          }
        })
    );

    await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keys = await caches.keys();

    await Promise.all(
      keys
        .filter(
          key =>
            key.startsWith('ayubase6-pwa-') &&
            key !== CACHE_NAME
        )
        .map(key => caches.delete(key))
    );

    await self.clients.claim();
  })());
});

// Display notifications delivered by a push service.
self.addEventListener('push', event => {
  event.waitUntil((async () => {
    let payload = {};

    try {
      payload = event.data ? event.data.json() : {};
    } catch (error) {
      payload = {
        body: event.data ? event.data.text() : ''
      };
    }

    await self.registration.showNotification(
      payload.title || 'AyuBase6 reminder',
      {
        body: payload.body || 'You have a reminder.',
        icon: payload.icon || './icons/icon-192.png',
        badge: payload.badge || './icons/icon-192.png',
        tag: payload.tag || undefined,
        renotify: Boolean(payload.renotify),
        data: {
          url: payload.url || './',
          ...(payload.data &&
          typeof payload.data === 'object'
            ? payload.data
            : {})
        }
      }
    );
  })());
});

// Tapping a notification: focus the already-open AyuBase6 tab (no reload) and
// tell the page which section to show; open a new window only if none is open.
self.addEventListener('notificationclick', event => {
  event.notification.close();

  event.waitUntil((async () => {
    const data = event.notification.data || {};
    const section = data.section === 'routines' ? 'routines' : (data.section === 'alerts' ? 'alerts' : '');

    const targetUrl = new URL(data.url || './', self.location.href);
    if (targetUrl.origin !== self.location.origin) {
      targetUrl.href = new URL('./', self.location.href).href;
    }
    if (section && !targetUrl.searchParams.has('section')) {
      targetUrl.searchParams.set('section', section);
    }

    const windows = await self.clients.matchAll({
      type: 'window',
      includeUncontrolled: true
    });

    const open = windows.find(c => c.url.startsWith(self.location.origin));
    if (open) {
      open.postMessage({
        type: 'nudge-click',
        key: data.key || '',
        action: event.action || '',
        section
      });
      return 'focus' in open ? open.focus() : undefined;
    }

    if (self.clients.openWindow) {
      return self.clients.openWindow(targetUrl.href);
    }
  })());
});

self.addEventListener('fetch', event => {
  const request = event.request;

  if (request.method !== 'GET') return;

  const url = new URL(request.url);

  if (url.origin !== self.location.origin) return;

  event.respondWith((async () => {
    try {
      return await fetch(request);
    } catch (error) {
      const cache = await caches.open(CACHE_NAME);
      const cachedResponse = await cache.match(request);

      if (cachedResponse) return cachedResponse;

      if (request.mode === 'navigate') {
        const appShell = await cache.match('./index.html');

        if (appShell) return appShell;
      }

      return Response.error();
    }
  })());
});

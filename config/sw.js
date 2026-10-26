const CACHE_NAME = 'ayubase6-pwa-v2';
const APP_SHELL = ['../', '../index.html', './manifest.json', '../icon/icon-192.png', '../icon/icon-512.png', '../icon/apple-touch-icon.png'];

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE_NAME);
    await cache.addAll(APP_SHELL.map(path => new Request(path, { cache: 'reload' })));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter(key => key.startsWith('ayubase6-pwa-') && key !== CACHE_NAME).map(key => caches.delete(key)));
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', event => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (request.mode === 'navigate') {
    event.respondWith((async () => {
      try {
        const response = await fetch(request);
        if (response && response.ok) {
          const cache = await caches.open(CACHE_NAME);
          cache.put(request, response.clone());
        }
        return response;
      } catch (error) {
        return (await caches.match(request)) || (await caches.match('../index.html')) || (await caches.match('../'));
      }
    })());
    return;
  }
  event.respondWith((async () => {
    const cached = await caches.match(request);
    if (cached) return cached;
    try {
      const response = await fetch(request);
      if (response && response.ok && response.type === 'basic') {
        const cache = await caches.open(CACHE_NAME);
        cache.put(request, response.clone());
      }
      return response;
    } catch (error) {
      return cached || Response.error();
    }
  })());
});

self.addEventListener('notificationclick', event => {
  const action = event.action || 'start';
  const data = event.notification && event.notification.data ? event.notification.data : {};
  event.notification.close();
  if (!data.key) return;
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const client of windows) {
      if ('focus' in client) {
        await client.focus();
        client.postMessage({ type: 'nudge-click', key: data.key, action });
        return;
      }
    }
    const url = new URL('./', self.registration.scope);
    url.hash = 'nudge=' + encodeURIComponent(data.key) + '&action=' + encodeURIComponent(action);
    await self.clients.openWindow(url.toString());
  })());
});

self.addEventListener('notificationclose', event => {
  // Intentionally no-op; reminders are managed by the page.
});

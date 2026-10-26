
const CACHE_NAME = 'ayubase6-pwa-v3';

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
    await cache.addAll(APP_SHELL);
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(
      keys
        .filter(key => key.startsWith('ayubase6-pwa-') && key !== CACHE_NAME)
        .map(key => caches.delete(key))
    );
    await self.clients.claim();
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
      return (await cache.match(request)) ||
             (request.mode === 'navigate'
               ? await cache.match('./index.html')
               : undefined) ||
             Response.error();
    }
  })());
});

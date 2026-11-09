
const CACHE_NAME = 'ayubase6-pwa-v9';   // v9: PDF tools and OCR loader served from lib/ on this site
// Videos and preview images from content/ are kept here the first time they are used.
// Separate from CACHE_NAME so an app update does not throw the videos away.
const MEDIA_CACHE = 'ayubase6-media-v2';
// The PDF reader, the PDF maker and the OCR loader (lib/ on this site): kept so they work offline.
// The OCR engine and English data are kept by the page itself (Enable offline reading), not here.
const LIB_CACHE = 'ayubase6-lib-v1';
const LIB_FILES = [
  './lib/pdfjs/pdf.min.js',
  './lib/pdfjs/pdf.worker.min.js',
  './lib/jspdf/jspdf.umd.min.js',
  './lib/tesseract/tesseract.min.js',
  './lib/tesseract/worker.min.js'
];

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

    // PDF tools and the OCR loader (about 2 MB), so Download PDF and reading reports work offline.
    try {
      const lib = await caches.open(LIB_CACHE);
      await Promise.all(LIB_FILES.map(async path => {
        try { await lib.add(path); } catch (error) { console.warn('[AyuBase6 SW] Could not precache:', path, error); }
      }));
    } catch (error) {}

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
            (key.startsWith('ayubase6-pwa-') && key !== CACHE_NAME) ||
            (key.startsWith('ayubase6-media-') && key !== MEDIA_CACHE)
        )
        .map(key => caches.delete(key))
    );

    await self.clients.claim();
  })());
});

// Tapping a notification: focus the already-open AyuBase6 tab (no reload) and
// tell the page which section to show; open a new window only if none is open.
self.addEventListener('notificationclick', event => {
  event.notification.close();

  event.waitUntil((async () => {
    const data = event.notification.data || {};
    if (data.confirm) return;   // tapping the confirmation just dismisses it
    const section = data.section === 'routines' ? 'routines' : (data.section === 'alerts' ? 'alerts' : '');

    const targetUrl = new URL(data.url || './', self.location.href);
    if (targetUrl.origin !== self.location.origin) {
      targetUrl.href = new URL('./', self.location.href).href;
    }
    if (section && !targetUrl.searchParams.has('section')) {
      targetUrl.searchParams.set('section', section);
    }

    const action = event.action || '';
    if (action) {
      // A button on the notification (Done, Not yet, Turn off...): carry it out without opening the app.
      const wins = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
      const page = wins.find(c => c.url.startsWith(self.location.origin));
      if (page) {
        page.postMessage({ type: 'nudge-click', key: data.key || '', action, section: '' });
      } else if (action !== 'not-yet') {
        // The app is closed: remember the tap and apply it the next time the app opens.
        const n = new Date();
        const day = n.getFullYear() + '-' + String(n.getMonth() + 1).padStart(2, '0') + '-' + String(n.getDate()).padStart(2, '0');
        const cache = await caches.open('ayubase6-pending');
        const id = Date.now() + '-' + Math.random().toString(36).slice(2, 8);
        await cache.put(new URL('./__pending/' + id, self.location.href).href,
          new Response(JSON.stringify({ key: data.key || '', action, day, at: Date.now() }), { headers: { 'content-type': 'application/json' } }));
      }
      // Confirm in the same notification slot, then let it clear itself.
      const say = {
        'disable-this': ['Reminder turned off', 'You can turn it back on in Reminders.'],
        'disable-all': ['All reminders turned off', 'You can turn them back on in Reminders.'],
        'done': ['Marked as done \u2713', 'Nice work.'],
        'not-yet': ['Noted \u2014 not yet', 'We will not mark it as done.']
      }[action];
      if (say) {
        const tag = event.notification.tag || ('nudge-' + (data.key || 'x'));
        try {
          await self.registration.showNotification(say[0], {
            body: say[1], tag, icon: './icons/icon-192.png', badge: './icons/icon-192.png',
            silent: true, renotify: false, requireInteraction: false, data: { confirm: true }
          });
          await new Promise(r => setTimeout(r, 3500));
          const list = await self.registration.getNotifications({ tag });
          list.forEach(x => { if (x.data && x.data.confirm) x.close(); });
        } catch (e) {}
      }
      return;
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
      // A button tapped while the app was closed: pass it on so the page can carry it out once it opens.
      if (event.action && data.key) {
        targetUrl.hash = 'nudge=' + encodeURIComponent(data.key) + '&action=' + encodeURIComponent(event.action);
      }
      return self.clients.openWindow(targetUrl.href);
    }
  })());
});

self.addEventListener('fetch', event => {
  const request = event.request;

  if (request.method !== 'GET') return;

  const url = new URL(request.url);

  if (url.origin !== self.location.origin) return;

  if (/\/content\/.+\.(mp4|jpg|jpeg|png|webp|json)$/i.test(url.pathname)) {
    event.respondWith(handleMedia(request, url));
    return;
  }

  // lib/ PDF tools and OCR loader: the saved copy straight away, refreshed in the background.
  const libPath = LIB_FILES.find(p => url.pathname === new URL(p, self.location.href).pathname);
  if (libPath) {
    event.respondWith((async () => {
      const lib = await caches.open(LIB_CACHE);
      const hit = await lib.match(libPath);
      const fresh = fetch(request).then(async r => { if (r && r.ok) await lib.put(libPath, r.clone()); return r; }).catch(() => null);
      if (hit) { event.waitUntil(fresh); return hit; }
      return (await fresh) || Response.error();
    })());
    return;
  }

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

// ── content/ files (videos, preview images, JSON lists) ──
// JSON lists: network first, so edits show up, with the saved copy as the offline fallback.
// Videos and images: saved on first use, then served from the saved copy.
// Phones ask videos for byte ranges (to play and skip), so a saved video is answered with the
// range asked for; a plain "give me the file" answer would not play on iPhones.
async function handleMedia(request, url) {
  const cache = await caches.open(MEDIA_CACHE);
  const key = url.origin + url.pathname;   // one saved copy per file, whatever range is asked
  const isJson = /\.json$/i.test(url.pathname);

  if (isJson) {
    try {
      // no-cache: ask the server whether the list changed instead of trusting the browser's own copy
      const fresh = await fetch(request, { cache: 'no-cache' });
      if (fresh && fresh.ok) await cache.put(key, fresh.clone());
      return fresh;
    } catch (error) {
      const saved = await cache.match(key);
      return saved || Response.error();
    }
  }

  // The app sets x-ayu-refresh when the saved copy is out of date: skip it and fetch the file again.
  const force = request.headers.get('x-ayu-refresh') === '1';
  let saved = force ? null : await cache.match(key);
  if (!saved) {
    try {
      const full = await fetch(url.href, force ? { cache: 'reload' } : undefined);   // whole file, no Range header
      if (!full || !full.ok) return full || Response.error();
      await cache.put(key, full.clone());
      saved = await cache.match(key);
    } catch (error) {
      return Response.error();
    }
  }
  return rangeResponse(request, saved);
}

async function rangeResponse(request, response) {
  const range = request.headers.get('range');
  if (!range) return response;

  const match = /^bytes=(\d*)-(\d*)$/.exec(range.trim());
  if (!match) return response;

  const blob = await response.blob();
  const size = blob.size;
  let start = match[1] === '' ? NaN : parseInt(match[1], 10);
  let end = match[2] === '' ? NaN : parseInt(match[2], 10);

  if (isNaN(start) && isNaN(end)) return response;
  if (isNaN(start)) { start = Math.max(0, size - end); end = size - 1; }       // "last N bytes"
  else if (isNaN(end) || end >= size) end = size - 1;

  if (start > end || start >= size) {
    return new Response(null, { status: 416, headers: { 'Content-Range': 'bytes */' + size } });
  }

  return new Response(blob.slice(start, end + 1), {
    status: 206,
    statusText: 'Partial Content',
    headers: {
      'Content-Type': response.headers.get('Content-Type') || 'video/mp4',
      'Content-Range': 'bytes ' + start + '-' + end + '/' + size,
      'Content-Length': String(end - start + 1),
      'Accept-Ranges': 'bytes'
    }
  });
}

/* AyuBase6 Android bridge.
   Loaded first in the Android app only (scripts/prepare-www.mjs adds it). Outside the app it does nothing.
   - Notifications: the web Notification API is provided by the phone (AyuReminders native plugin).
   - Reminders: the rules the app already builds (ayuPush.buildRules) are handed to the phone, which
     schedules them with Android alarms, so they arrive when the app is closed and after a restart.
   - No web push and no service worker in the app; content lists and announcements are read live from the site. */
(function(){
  var C = window.Capacitor;
  var isNative = !!(C && (typeof C.isNativePlatform === 'function' ? C.isNativePlatform() : C.platform === 'android'));
  var state = window.ayuNative = { native:false, active:false, exact:true, next:null, error:'' };
  if(!isNative) return;
  var P = (typeof C.registerPlugin === 'function') ? C.registerPlugin('AyuReminders') : (C.Plugins && C.Plugins.AyuReminders);
  if(!P) return;
  state.native = true;
  function noop(){}
  function ls(k, v){ try{ if(arguments.length > 1) localStorage.setItem(k, v); else return localStorage.getItem(k); }catch(e){} }

  // ── Web content: lists and announcements come from the live site, falling back to the bundled copy ──
  var LIVE = window.AYU_LIVE_BASE || 'https://ayubase6.com/';
  var LIVE_RE = /^(content\/.+\.json|config\/announcements\.json|config\/OurOtherSites\.json|config\/android-app\.json)(\?.*)?$/;
  var origFetch = window.fetch;
  function relPath(u){
    u = String(u || '');
    var base = location.origin + '/';
    if(u.indexOf(base) === 0) u = u.slice(base.length);
    return u.replace(/^\.\//, '');
  }
  // The last copy of each list read from the site is kept, so the app knows its videos (and saved copies) offline.
  var JSON_CACHE = 'ayubase6-app-lists';
  function liveJson(rel, init){
    var url = LIVE + rel.replace(/\?.*$/, '');
    return origFetch.call(window, LIVE + rel, init).then(function(r){
      if(r.ok && 'caches' in window){ var copy = r.clone(); caches.open(JSON_CACHE).then(function(c){ return c.put(url, copy); }).catch(noop); }
      return r;
    }).catch(function(err){
      if(/^config\/android-app\.json/.test(rel)) throw err;   // the update check must say when it could not reach the site
      var fromCache = ('caches' in window) ? caches.open(JSON_CACHE).then(function(c){ return c.match(url); }).catch(function(){ return null; }) : Promise.resolve(null);
      return fromCache.then(function(hit){ return hit || origFetch.call(window, rel, init); });
    });
  }
  window.fetch = function(input, init){
    try{
      if(typeof input === 'string'){
        var rel = relPath(input);
        if(LIVE_RE.test(rel)) return liveJson(rel, init);
        // "Save again" asks for a fresh copy with a header meant for the website's service worker; to the live
        // site from the app that header would block the download, so a plain fresh request is made instead.
        if(input.indexOf(LIVE) === 0 && init && init.headers && init.headers['x-ayu-refresh']){
          var copy = {}; for(var k in init) if(k !== 'headers') copy[k] = init[k];
          copy.cache = 'reload';
          return origFetch.call(window, input, copy);
        }
      }
    }catch(e){}
    return origFetch.apply(window, arguments);
  };

  // ── Saved videos (Body → Save for offline) play from the phone ──
  // The web app saves them in its storage (cache "ayubase6-media-v2"); on the website its service worker plays them
  // from there. The app has no service worker, so media addresses are switched to the saved copies directly.
  var MEDIA_CACHE = 'ayubase6-media-v2', MEDIA_RE = /\/content\/.+\/media\//, saved = {};
  function mediaKey(u){ try{ var x = new URL(u, document.baseURI); return x.origin + x.pathname; }catch(e){ return ''; } }
  function isMedia(u){ return typeof u === 'string' && u.indexOf(LIVE) === 0 && MEDIA_RE.test(u); }
  function savedUrl(u){ var s = isMedia(u) && saved[mediaKey(u)]; return s || null; }
  function swapEl(el){
    ['src', 'poster'].forEach(function(a){
      var v = el.getAttribute && el.getAttribute(a); if(!v) return;
      var l = savedUrl(v); if(l && v !== l){ el.setAttribute('data-ayu-' + a, v); origSetAttr.call(el, a, l); }
    });
  }
  function swapAll(root){
    try{ (root || document).querySelectorAll('video[src],video[poster],source[src],img[src]').forEach(swapEl); }catch(e){}
  }
  var loading = null;
  function loadSaved(){
    if(!('caches' in window)) return Promise.resolve();
    if(loading) return loading;
    loading = caches.open(MEDIA_CACHE).then(function(c){
      return c.keys().then(function(reqs){
        var next = {};
        return Promise.all(reqs.map(function(rq){
          return c.match(rq).then(function(r){ return r && r.ok !== false ? r.blob() : null; }).then(function(b){
            var k = mediaKey(rq.url); if(!b || !k) return;
            next[k] = URL.createObjectURL(b);   // old addresses are kept, so a video playing now keeps playing
          });
        })).then(function(){ saved = next; });
      });
    }).catch(noop).then(function(){ loading = null; swapAll(); });
    return loading;
  }
  state.reloadSaved = loadSaved;
  // The web app compares a saved video's "saved at" time with the site's file time to ask "save again" after a change.
  // From the app the site's own date is hidden (another address), so the time of saving is written on the saved copy.
  try{
    var origPut = Cache.prototype.put;
    Cache.prototype.put = function(req, resp){
      try{
        var u = typeof req === 'string' ? req : (req && req.url);
        if(isMedia(u) && resp && !resp.headers.get('date')){
          var h = new Headers();
          ['content-type', 'content-length', 'last-modified'].forEach(function(k){ var v = resp.headers.get(k); if(v) h.set(k, v); });
          h.set('date', new Date().toUTCString());
          resp = new Response(resp.body, { status:resp.status, statusText:resp.statusText, headers:h });
        }
      }catch(e){}
      return origPut.call(this, req, resp);
    };
  }catch(e){}
  // Like the website: a video that has played once is kept, so it plays without internet next time.
  function keep(u){
    if(!isMedia(u) || savedUrl(u) || !('caches' in window)) return;
    var key = mediaKey(u);
    origFetch.call(window, u).then(function(r){
      if(!r.ok) return;
      return caches.open(MEDIA_CACHE).then(function(c){ return c.put(key, r.clone()); }).then(function(){ return r.blob(); })
        .then(function(b){ saved[key] = URL.createObjectURL(b); });
    }).catch(noop);
  }
  document.addEventListener('playing', function(e){
    var v = e.target; if(!v || !v.tagName) return;
    keep(v.currentSrc || v.src);
    if(v.poster) keep(v.getAttribute('data-ayu-poster') || v.poster);   // its preview image too
  }, true);
  var origSetAttr = Element.prototype.setAttribute;
  Element.prototype.setAttribute = function(name, value){
    if((name === 'src' || name === 'poster') && /^(VIDEO|SOURCE|IMG)$/.test(this.tagName)){
      var l = savedUrl(String(value)); if(l){ origSetAttr.call(this, 'data-ayu-' + name, value); value = l; }
    }
    return origSetAttr.call(this, name, value);
  };
  [[HTMLMediaElement.prototype, 'src'], [HTMLVideoElement.prototype, 'poster'], [HTMLImageElement.prototype, 'src'], [HTMLSourceElement.prototype, 'src']].forEach(function(p){
    var d = Object.getOwnPropertyDescriptor(p[0], p[1]); if(!d || !d.set) return;
    Object.defineProperty(p[0], p[1], { configurable:true, enumerable:d.enumerable, get:d.get,
      set:function(v){ var l = savedUrl(String(v)); d.set.call(this, l || v); } });
  });
  try{
    new MutationObserver(function(list){
      list.forEach(function(m){ m.addedNodes && m.addedNodes.forEach(function(n){ if(n.nodeType === 1){ if(/^(VIDEO|SOURCE|IMG)$/.test(n.tagName)) swapEl(n); swapAll(n); } }); });
    }).observe(document.documentElement, { childList:true, subtree:true });
  }catch(e){}
  loadSaved();   // early, so videos drawn at start already use the saved copies

  // ── The app is "installed": no install prompts, and no service worker (the phone handles notifications) ──
  var origMM = window.matchMedia;
  window.matchMedia = function(q){
    if(/display-mode\s*:\s*standalone/.test(String(q))) return { matches:true, media:String(q), onchange:null, addListener:noop, removeListener:noop, addEventListener:noop, removeEventListener:noop, dispatchEvent:function(){ return false; } };
    return origMM.call(window, q);
  };
  try{ delete Navigator.prototype.serviceWorker; }catch(e){}
  try{ if('serviceWorker' in navigator) Object.defineProperty(navigator, 'serviceWorker', { value:undefined, configurable:true }); }catch(e){}

  // ── Notification API backed by the phone ──
  var PERM_KEY = 'ayuAndroidNotifPerm';
  var perm = ls(PERM_KEY) || 'default';
  function mapPerm(s){ return s === 'granted' ? 'granted' : (s === 'denied' ? 'denied' : 'default'); }
  function setPerm(p){ if(p !== perm){ perm = p; ls(PERM_KEY, p); setTimeout(sync, 50); try{ if(typeof refreshNotifStatusUI === 'function') refreshNotifStatusUI(); }catch(e){} } }
  function accent(){
    // Same way as the web app's sectionTheme(): the open section's colour, read as a computed colour.
    try{
      var el = document.createElement('i'); el.style.color = 'var(--footer-accent,#0d9488)'; document.body.appendChild(el);
      var m = (getComputedStyle(el).color.match(/[\d.]+/g) || []).slice(0, 3).map(Number); el.remove();
      if(m.length === 3) return '#' + m.map(function(v){ v = Math.max(0, Math.min(255, Math.round(v))); return (v < 16 ? '0' : '') + v.toString(16); }).join('');
    }catch(e){}
    return '';
  }
  // Reminders the phone already schedules: the app's own timer for these stays quiet, so nothing arrives twice.
  var SCHEDULED_TAG = /^(nudge-custom-|ayubase6-custom-)/;
  function AyuNotification(title, opts){
    opts = opts || {};
    this.title = String(title || ''); this.body = String(opts.body || ''); this.tag = String(opts.tag || ''); this.data = opts.data || null; this.onclick = null;
    if(state.active && !opts.silent && SCHEDULED_TAG.test(this.tag)){ this.tag = ''; return; }
    var key = this.tag || ('note-' + Date.now());
    this.tag = key;
    // Colours follow the section the notification belongs to (Reminders teal, Checks amber);
    // the Help test, which belongs to no section, takes the colour of the section that is open.
    var section = (opts.data && opts.data.section) || '', tapKey = (opts.data && opts.data.key) || '';
    var own = (section === 'alerts' || section === 'routines') && tapKey !== 'test';
    P.show({ key:key, title:this.title, body:this.body, silent:!!opts.silent, color:own ? '' : accent(),
             section:section, tapKey:tapKey }).catch(noop);
  }
  AyuNotification.prototype.close = function(){ if(this.tag) P.cancel({ key:this.tag }).catch(noop); };
  AyuNotification.prototype.addEventListener = noop;
  AyuNotification.prototype.removeEventListener = noop;
  Object.defineProperty(AyuNotification, 'permission', { get:function(){ return perm; } });
  AyuNotification.maxActions = 0;
  AyuNotification.requestPermission = function(cb){
    return P.requestPermissions().then(function(r){ setPerm(mapPerm(r && r.notifications)); return perm; }, function(){ return perm; })
      .then(function(p){ if(typeof cb === 'function'){ try{ cb(p); }catch(e){} } refreshStatus(); setTimeout(showBlockedCard, 800); return p; });
  };
  try{ window.Notification = AyuNotification; }catch(e){}
  if(window.Notification !== AyuNotification){ try{ Object.defineProperty(window, 'Notification', { value:AyuNotification, configurable:true, writable:true }); }catch(e){} }

  // ── Hand the reminder rules to the phone ──
  var lastSent = null, busy = false, again = false;
  // Eyes, Walk and Water repeat every N minutes counted from the moment the reminder was turned on or its
  // frequency, hours or days were changed (not from the start of the reminder hours). That moment is kept per reminder.
  var ANCHOR_KEY = 'ayuAndroidRemAnchor';
  function addAnchors(rules){
    var old = {}, now = {};
    try{ old = JSON.parse(ls(ANCHOR_KEY) || '{}') || {}; }catch(e){}
    if(rules && Array.isArray(rules.reminders)) rules.reminders.forEach(function(r){
      var sig = JSON.stringify([r.every, r.from, r.to, r.days || null]);
      var a = old[r.key] && old[r.key].sig === sig ? old[r.key].at : Date.now();
      now[r.key] = { sig:sig, at:a }; r.anchor = a;
    });
    ls(ANCHOR_KEY, JSON.stringify(now));   // a reminder turned off forgets its moment, so turning it on starts afresh
    return rules;
  }
  // "Next:" under Eyes, Walk and Water shows the time the phone will actually ring (counted from that moment).
  function nextFromAnchor(key, fromMs){
    var rules = currentRules(), r = null;
    (rules && rules.reminders || []).forEach(function(x){ if(x.key === key) r = x; });
    if(!r || !(r.every > 0) || !r.anchor) return null;
    var a = r.from, b = r.to;
    if(a == null || b == null || a === b){ a = 0; b = 1439; }
    var anchorMin = Math.floor(r.anchor / 60000), every = Math.floor(r.every);
    var t = Math.floor(fromMs / 60000) + 1;
    t += ((every - ((t - anchorMin) % every)) % every + every) % every;   // first minute after now on the count
    if(t <= anchorMin) t = anchorMin + every;
    for(var n = 0; n < 4000; n++, t += every){
      var d = new Date(t * 60000), m = d.getHours() * 60 + d.getMinutes();
      if(Array.isArray(r.days) && r.days.length && r.days.indexOf(d.getDay()) < 0) continue;
      var inside = a < b ? (m >= a && m <= b) : (m >= a || m <= b);
      if(inside) return t * 60000;
    }
    return null;
  }
  function useAnchorForNext(){
    var slots = window.ayuReminderSlots;
    if(!slots || typeof slots.next !== 'function' || slots.next.__ayuAnchor) return;
    var orig = slots.next;
    slots.next = function(key, fromMs){
      if(state.active){ var t = nextFromAnchor(key, fromMs || Date.now()); if(t) return t; }
      return orig.apply(this, arguments);
    };
    slots.next.__ayuAnchor = true;
  }
  // Alarms (Checks → Alarm at the start time, Reminders → Alarm at its own time) travel with the rules, so the phone rings them.
  // They work on their own: with every other reminder off, the rules carry only the alarms.
  function alarmList(){ try{ return (typeof window.ayuAlarmList === 'function') ? (window.ayuAlarmList() || []) : []; }catch(e){ return []; } }
  function addAlarm(rules){
    var list = alarmList().map(function(a){
      return { id:String(a.id), on:true, at:+a.at, days:a.days, title:a.title, body:a.body, section:a.section === 'alerts' ? 'alerts' : 'routines',
               tone:a.tone, ring:+a.ring||0, snoozeMin:+a.snoozeMin||5, snoozeMax:+a.snoozeMax||0, keys:a.keys === 'stop' ? 'stop' : 'snooze', vibrate:a.vibrate !== false };
    });
    if(!list.length) return rules;
    if(!rules) rules = { v:1, tz:(function(){ try{ return Intl.DateTimeFormat().resolvedOptions().timeZone; }catch(e){ return ''; } })(), phone:true, reminders:[], exercise:null, checks:null, custom:[] };
    rules.alarms = list;
    return rules;
  }
  state.previewTone = function(tone){ return P.previewTone({ tone:String(tone || 'rise') }); };
  state.stopPreview = function(){ return P.stopPreview().catch(noop); };
  state.saveTone = function(b64){ return P.saveTone({ base64:b64 }); };
  document.addEventListener('ayu-alarm-changed', function(){
    // an alarm needs notifications allowed (its screen and buttons come through one)
    if(alarmList().length && perm !== 'granted'){ try{ window.Notification.requestPermission(); }catch(e){} }
    setTimeout(function(){ sync(true); }, 100);
  });
  function currentRules(){
    try{ return addVibrate(addAlarm(addAnchors((window.ayuPush && typeof ayuPush.buildRules === 'function') ? ayuPush.buildRules() : null))); }catch(e){ return null; }
  }
  // Vibrate switches (Checks, Health Breaks and each section, each Personal Reminder) go to the phone only, not to the server rules.
  function addVibrate(rules){
    try{ if(rules && typeof window.ayuVibrateApply === 'function') window.ayuVibrateApply(rules); }catch(e){}
    return rules;
  }
  function sync(force){
    if(busy){ again = true; return; }
    var rules = currentRules();
    var body = JSON.stringify(rules);
    if(body === lastSent && force !== true) return;
    busy = true;
    P.setRules({ rules:rules }).then(function(r){
      lastSent = body; state.active = !!rules; state.error = '';
      state.next = (r && r.next) || null; state.exact = !(r && r.exact === false);
      showExactCard();
    }).catch(function(e){ lastSent = null; state.error = String(e && e.message || e); })
      .then(function(){ busy = false; if(again){ again = false; setTimeout(sync, 0); } });
  }
  state.sync = function(){ sync(true); };
  // Settings → Notifications button: Android lets only its own settings page turn the permission on or off
  state.openNotifSettings = function(){ return P.openNotificationSettings().catch(noop); };
  // Clock: the page's timer / stopwatch / World Clock state goes to the phone (live notifications with buttons, the timer's alarm)
  if(typeof P.clockSet === 'function' && typeof P.clockGet === 'function'){
    state.clockSet = function(o){ return P.clockSet(o || {}); };
    state.clockGet = function(){ return P.clockGet(); };
    try{ P.addListener('clockChanged', function(){ try{ window.dispatchEvent(new Event('ayu-clock-native')); }catch(e){} }); }catch(e){}
  }
  // Speak: the phone's own voices and a preview (reminders are read out by the phone, also with the app closed)
  if(typeof P.ttsVoices === 'function'){
    state.tts = {
      voices: function(){ return P.ttsVoices().then(function(r){ return (r && r.voices) || []; }); },
      say: function(text, voice, lang){ return P.ttsSay({ text:String(text || ''), voice:String(voice || ''), lang:String(lang || '') }); }
    };
  }
  // Backup to the person's Google account (Android key/value backup of one data copy)
  if(typeof P.backupSave === 'function' && typeof P.backupLoad === 'function'){   // only on an app build that has them
    state.backupSave = function(data){ return P.backupSave({ data:String(data) }); };
    state.backupLoad = function(){ return P.backupLoad(); };
    state.backupClear = function(){ return typeof P.backupClear === 'function' ? P.backupClear() : Promise.resolve(); };
  }
  function refreshStatus(){
    return P.checkPermissions().then(function(r){
      setPerm(mapPerm(r && r.notifications));
      var ex = !(r && r.exactAlarm === false);
      if(ex !== state.exact){ state.exact = ex; sync(true); }
      showExactCard(); showBlockedCard();
      return r;
    }).catch(noop);
  }

  // ── Notifications turned off for the app (in Android settings): one tap opens the page, and the app comes back by itself once allowed ──
  var BLOCK_KEY = 'ayuAndroidBlockedLater';
  function wantsNotifications(){
    try{ return !!((typeof isMasterNotifyOn === 'function' && isMasterNotifyOn()) || (typeof routineNotificationsEnabled === 'function' && routineNotificationsEnabled()) || alarmList().length); }catch(e){ return false; }
  }
  function showBlockedCard(){
    var card = document.getElementById('ayu-blocked-card');
    var later = Number(ls(BLOCK_KEY)) || 0;
    var want = perm === 'denied' && wantsNotifications() && Date.now() - later > 3 * 86400000 && document.body;
    if(!want){ if(card) card.remove(); return; }
    if(card) return;
    var ex = document.getElementById('ayu-exact-card'); if(ex) ex.remove();
    card = document.createElement('div');
    card.id = 'ayu-blocked-card'; card.className = 'ayu-card'; card.setAttribute('role', 'dialog'); card.setAttribute('aria-label', 'Notifications are off');
    card.innerHTML = '<div class="ayu-ec-text"><b>Notifications are off</b><span>Turn on notifications for AyuBase6 to get your reminders. The app comes back here once they are on.</span></div>'
      + '<button type="button" class="ayu-ec-later ayu-ec-x" aria-label="Close" title="Close">×</button><div class="ayu-ec-btns"><button type="button" class="ayu-ec-allow">Open settings</button></div>';
    card.querySelector('.ayu-ec-allow').onclick = function(){ P.openNotificationSettings().catch(noop); };
    card.querySelector('.ayu-ec-later').onclick = function(){ ls(BLOCK_KEY, String(Date.now())); card.remove(); };
    document.body.appendChild(card);
  }
  // Allowed in Android settings: the phone brings the app back and tells us; refresh everything at once.
  try{ P.addListener('permissionsChanged', function(){ refreshStatus().then(function(){ sync(true); }); }); }catch(e){}

  // ── "Alarms & reminders" permission: without it Android may deliver reminders some minutes late ──
  var CARD_KEY = 'ayuAndroidExactLater';
  function showExactCard(){
    var card = document.getElementById('ayu-exact-card');
    var later = Number(ls(CARD_KEY)) || 0;
    var want = state.active && !state.exact && Date.now() - later > 3 * 86400000 && document.body && !document.getElementById('ayu-blocked-card');
    if(!want){ if(card) card.remove(); return; }
    if(card) return;
    card = document.createElement('div');
    card.id = 'ayu-exact-card'; card.setAttribute('role', 'dialog'); card.setAttribute('aria-label', 'Allow exact reminders');
    card.innerHTML = '<div class="ayu-ec-text"><b>Reminders on time</b><span>Allow “Alarms &amp; reminders” for AyuBase6 so reminders arrive at the exact minute.</span></div>'
      + '<button type="button" class="ayu-ec-later ayu-ec-x" aria-label="Close" title="Close">×</button><div class="ayu-ec-btns"><button type="button" class="ayu-ec-allow">Allow</button></div>';
    card.querySelector('.ayu-ec-allow').onclick = function(){ P.openExactAlarmSettings().catch(noop); };
    card.querySelector('.ayu-ec-later').onclick = function(){ ls(CARD_KEY, String(Date.now())); card.remove(); };
    document.body.appendChild(card);
  }
  function addStyle(){
    var st = document.createElement('style'); st.id = 'ayu-android-bridge-css';
    st.textContent = '#ayu-exact-card,.ayu-card{position:fixed;left:12px;right:12px;bottom:calc(12px + env(safe-area-inset-bottom,0px));z-index:2147483000;max-width:520px;margin:0 auto;'
      + 'background:#fff;color:#2d2b23;border-radius:14px;box-shadow:0 8px 28px rgba(0,0,0,.22);padding:12px 14px;display:flex;flex-direction:column;gap:10px;font:inherit;font-size:.86rem;line-height:1.45;'
      + 'border-left:5px solid var(--footer-accent,#0d9488)}'
      + '#ayu-exact-card .ayu-ec-text,.ayu-card .ayu-ec-text{display:flex;flex-direction:column;gap:2px}'
      + '#ayu-exact-card .ayu-ec-btns,.ayu-card .ayu-ec-btns{display:flex;justify-content:flex-end;gap:8px}'
      + '#ayu-exact-card button,.ayu-card button{border:0;border-radius:10px;padding:8px 16px;font:inherit;font-weight:600;cursor:pointer}'
      + '#ayu-exact-card .ayu-ec-allow,.ayu-card .ayu-ec-allow{background:var(--footer-accent,#0d9488);color:#fff}'
      + '#ayu-exact-card .ayu-ec-later,.ayu-card .ayu-ec-later{background:transparent;color:var(--footer-accent,#0d9488)}'
      // × at the top right closes a card (the same as "Later" before)
      + '#ayu-exact-card .ayu-ec-x,.ayu-card .ayu-ec-x{position:absolute;top:4px;right:6px;padding:2px 8px;border-radius:8px;font-size:22px;line-height:1;font-weight:400;color:#5b5847}'
      + '#ayu-exact-card .ayu-ec-text,.ayu-card .ayu-ec-text{padding-right:26px}'
      + '#ayu-toast{position:fixed;left:50%;transform:translateX(-50%);bottom:calc(20px + env(safe-area-inset-bottom,0px));z-index:2147483001;background:#2d2b23;color:#fff;border-radius:12px;padding:10px 16px;font:inherit;font-size:.86rem;max-width:86vw;box-shadow:0 6px 18px rgba(0,0,0,.25)}'
      // the app is installed already: no install buttons or install tips
      + '#install-app-btn,#routines-install-app-btn,#install-help,#routines-install-help{display:none!important}';
    document.head.appendChild(st);
  }

  // ── A new app version: the website's config/android-app.json names it. Update downloads it here and opens Android's
  //    "Update this app?" screen. "Later" hides the banner for 3 days. Backup & Restore → App Updates shows the same. ──
  var UPD_KEY = 'ayuAndroidUpdateLater', LATER_MS = 3 * 86400000;
  var upd = state.update = { mine:null, latest:null, checkedAt:0, error:'', phase:'', pct:0 };
  function updEvent(){ try{ window.dispatchEvent(new Event('ayu-update-info')); }catch(e){} }
  function laterHidden(code){
    var v = ls(UPD_KEY); if(!v) return false;
    var o = null; try{ o = JSON.parse(v); }catch(e){}
    if(!o || typeof o !== 'object'){ o = { v:String(v), at:Date.now() }; ls(UPD_KEY, JSON.stringify(o)); }   // older "Later": count from now
    if(o.never) return true;   // Cancel: never again (App Updates in the footer still shows it)
    return String(o.v) === String(code) && Date.now() - (+o.at || 0) < LATER_MS;
  }
  state.updateCheck = function(){
    upd.error = ''; upd.phase = upd.phase === 'downloading' ? upd.phase : 'checking'; updEvent();
    return Promise.all([
      P.getStatus().catch(function(){ return {}; }),
      fetch('config/android-app.json?t=' + Date.now(), { cache:'no-store' })   // the query skips the website's 10-minute cache
     .then(function(r){ if(!r.ok) throw new Error('http'); return r.json(); })
    ]).then(function(v){
      upd.mine = { code:Number(v[0] && v[0].versionCode) || 0, name:String((v[0] && v[0].versionName) || '') };
      var cfg = v[1];
      upd.latest = (cfg && Number(cfg.versionCode) > upd.mine.code && /^https:\/\//.test(String(cfg.url || ''))) ? cfg : null;
      upd.checkedAt = Date.now(); if(upd.phase === 'checking') upd.phase = '';
      updEvent(); return upd;
    }, function(){
      if(!upd.mine) P.getStatus().then(function(st){ upd.mine = { code:Number(st.versionCode) || 0, name:String(st.versionName || '') }; updEvent(); }).catch(noop);
      upd.error = navigator.onLine === false ? 'offline' : 'failed'; if(upd.phase === 'checking') upd.phase = ''; updEvent(); return upd;
    });
  };
  // Update: download (progress), then Android's install screen. If installing updates is not allowed yet, Android's
  // switch opens; coming back to the app continues by itself.
  var waitAllow = false;
  function openInstaller(){
    return P.updateInstall().then(function(r){
      if(r && r.needAllow){ waitAllow = true; upd.phase = 'allow'; updEvent(); return; }
      upd.phase = 'installing'; updEvent();
    }, function(){ upd.phase = ''; upd.error = 'install'; updEvent(); });
  }
  state.updateInstall = function(){ return openInstaller(); };
  state.updateStart = function(){
    var cfg = upd.latest; if(!cfg) return Promise.resolve();
    if(typeof P.updateDownload !== 'function'){ P.openUrl({ url:cfg.url }).catch(noop); return Promise.resolve(); }   // an app without it: the browser
    if(upd.phase === 'downloading') return Promise.resolve();
    upd.phase = 'downloading'; upd.pct = 0; upd.error = ''; updEvent();
    return P.updateDownload({ url:String(cfg.url) }).then(function(){ upd.pct = 100; return openInstaller(); },
      function(){ upd.phase = ''; upd.error = 'download'; updEvent(); });
  };
  try{ P.addListener('updateProgress', function(d){ upd.pct = Math.max(0, Math.min(100, +(d && d.pct) || 0)); updEvent(); }); }catch(e){}
  document.addEventListener('visibilitychange', function(){
    if(document.hidden) return;
    if(waitAllow && typeof P.updateCanInstall === 'function'){
      P.updateCanInstall().then(function(r){ if(r && r.allowed && r.ready){ waitAllow = false; openInstaller(); } }).catch(noop);
    }
    if(upd.phase === 'installing'){ upd.phase = ''; updEvent(); }   // back from Android's screen without updating
  });

  function checkUpdate(){
    state.updateCheck().then(function(){
      var cfg = upd.latest; if(!cfg) return;
      if(laterHidden(cfg.versionCode)) return;
      if(document.getElementById('ayu-update-card') || document.getElementById('ayu-exact-card')) return;
      var card = document.createElement('div');
      card.id = 'ayu-update-card'; card.className = 'ayu-card'; card.setAttribute('role', 'dialog'); card.setAttribute('aria-label', 'Update available');
      card.innerHTML = '<div class="ayu-ec-text"><b>Update available</b><span>Version ' + String(cfg.versionName || '').replace(/[<>&"]/g, '') + ' of AyuBase6 is ready. Tap Update; your data stays.</span></div>'
        + '<button type="button" class="ayu-ec-later ayu-ec-x" aria-label="Close" title="Close">×</button><div class="ayu-ec-btns"><button type="button" class="ayu-ec-allow">Update</button></div>';
      card.querySelector('.ayu-ec-allow').onclick = function(){
        card.remove(); state.updateStart();
        setTimeout(function(){   // after this tap (the footer closes on taps outside it)
          try{ if(typeof window.showFooterSection === 'function'){ window.showFooterSection('backup'); if(typeof window.showFooterSub === 'function') window.showFooterSub('backup', 'app'); var sa = document.getElementById('sec-app'); if(sa) sa.scrollIntoView({ block:'center', behavior:'smooth' }); } }catch(e){}
        }, 60);
      };
      card.querySelector('.ayu-ec-later').onclick = function(){
        ls(UPD_KEY, JSON.stringify({ never:true, at:Date.now() })); card.remove();
        toast('You can update any time from Backup & Restore → App Updates, in the footer.', 7000);
      };
      document.body.appendChild(card);
    });
  }

  // ── Backup files: the web view cannot download the page's own files, so the phone saves them ──
  function toast(text, ms){
    var t = document.getElementById('ayu-toast'); if(t) t.remove();
    t = document.createElement('div'); t.id = 'ayu-toast'; t.setAttribute('role', 'status'); t.textContent = text;
    document.body.appendChild(t); setTimeout(function(){ t.remove(); }, ms || 4500);
  }
  // Any file the page makes for you (the Backup file, the Body / Food / lab PDFs) is saved through the phone.
  function saveDownload(a){
    var href = a.href || '';
    if(!a.download || !/^(blob:|data:)/.test(href)) return false;
    fetch(href).then(function(r){ return r.blob(); }).then(function(b){
      return new Promise(function(res, rej){ var fr = new FileReader(); fr.onload = function(){ res([b, String(fr.result).split(',')[1] || '']); }; fr.onerror = rej; fr.readAsDataURL(b); });
    }).then(function(v){
      var mime = v[0].type || (/\.pdf$/i.test(a.download) ? 'application/pdf' : (/\.json$/i.test(a.download) ? 'application/json' : 'application/octet-stream'));
      return P.saveFile({ name:a.download, base64:v[1], mime:mime });
    }).then(function(r){
      if(r && r.where === 'downloads') toast('Saved to Downloads: ' + r.name);
      else if(r && r.where === 'folder') toast('Saved to Download/AyuBase6: ' + r.name);
    }).catch(function(){ toast('The file could not be saved.'); });
    return true;
  }
  var origClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function(){ if(saveDownload(this)) return; return origClick.apply(this, arguments); };
  var origDispatch = EventTarget.prototype.dispatchEvent;
  EventTarget.prototype.dispatchEvent = function(ev){
    if(ev && ev.type === 'click' && this instanceof HTMLAnchorElement && saveDownload(this)) return true;
    return origDispatch.apply(this, arguments);
  };
  // a real tap on such a link
  document.addEventListener('click', function(e){
    var a = e.target && e.target.closest && e.target.closest('a[download]');
    if(a && saveDownload(a)) e.preventDefault();
  }, true);

  // ── A tapped notification opens its section, like the web app's notification tap ──
  var ready = false, pendingTap = null;
  function openTap(d){
    if(!ready){ pendingTap = d; return; }
    var key = String(d && d.key || ''), section = String(d && d.section || '');
    if(section === 'alerts' || section === 'routines'){
      try{ if(typeof switchTab === 'function') switchTab(section); }catch(e){}
      try{ if(typeof nudgeShowSection === 'function') nudgeShowSection(key); }catch(e){}
    }
    if(section === 'announcements'){   // a new announcement, sent while the app was not in use
      try{ if(typeof window.showFooterSection === 'function') setTimeout(function(){ window.showFooterSection('announcements'); }, 300); }catch(e){}
      return;
    }
    try{ if(key && typeof handleNudgeClick === 'function') handleNudgeClick(key, ''); }catch(e){}
  }
  try{ P.addListener('reminderTap', openTap); }catch(e){}

  // ── Buttons on reminders (Done, Turn off this, Turn off all) were used, perhaps with the app closed:
  //    the phone has already acted; here the app does the same (ticks the activity, turns the switch off) ──
  function todayStr(){ var d = new Date(); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); }
  function applyActions(){
    if(typeof P.takeActions !== 'function') return Promise.resolve(0);
    return P.takeActions().then(function(r){
      var list = (r && r.actions) || [];
      list.forEach(function(a){
        if(!a || !a.key || !a.action) return;
        if(a.action === 'done' && a.day && a.day !== todayStr()) return;   // a tick from an earlier day must not tick today
        try{ if(typeof handleNudgeClick === 'function') handleNudgeClick(a.key, a.action); }catch(e){}
      });
      return list.length;
    }).catch(function(){ return 0; });
  }
  try{ P.addListener('reminderAction', function(){ applyActions().then(function(n){ if(n) setTimeout(function(){ sync(true); }, 300); }); }); }catch(e){}

  function quiet(name, test){
    var orig = window[name]; if(typeof orig !== 'function') return;
    window[name] = function(){ if(state.active && test.apply(null, arguments)) return true; return orig.apply(this, arguments); };
  }
  function start(){
    addStyle();
    // Timers in the page for reminders the phone schedules are kept quiet (the phone delivers them).
    quiet('fireAlert', function(){ return true; });
    quiet('fireEyeExAlert', function(){ return true; });
    quiet('notifyRoutine', function(key){
      key = String(key || '');
      return /^end-/.test(key) || (typeof ROUTINE_NOTIFY_ITEMS !== 'undefined' && Object.prototype.hasOwnProperty.call(ROUTINE_NOTIFY_ITEMS, key));
    });
    // Help's notification check asks for the background service: on the phone that is the reminder scheduler.
    var phoneReg = { active:{}, update:function(){ return refreshStatus(); }, getNotifications:function(){ return Promise.resolve([]); },
                     showNotification:function(t, o){ try{ new AyuNotification(t, o); }catch(e){} return Promise.resolve(); } };
    if(typeof window.getNudgeReg === 'function') window.getNudgeReg = function(){ return Promise.resolve(phoneReg); };
    useAnchorForNext(); setTimeout(useAnchorForNext, 1000);
    loadSaved();
    if(typeof window.ayuSaveOffline === 'function'){
      var origSave = window.ayuSaveOffline;
      window.ayuSaveOffline = function(){ return Promise.resolve(origSave.apply(this, arguments)).then(function(r){ return loadSaved().then(function(){ return r; }); }); };
    }
    // The app is installed already: no install buttons (set inline, so the page's own styles cannot bring them back).
    function hideInstall(){ ['install-app-btn','routines-install-app-btn','install-help','routines-install-help'].forEach(function(id){ var el = document.getElementById(id); if(el) el.style.setProperty('display', 'none', 'important'); }); }
    hideInstall(); setTimeout(hideInstall, 1000); setTimeout(hideInstall, 4000);
    // Actions from notification buttons first, so the rules sent to the phone already include them.
    // (after the page's own start-up has drawn its switches)
    setTimeout(function(){ applyActions().then(refreshStatus).then(function(){ setTimeout(function(){ sync(true); }, 300); }); }, 400);
    var soon = null; function syncSoon(){ clearTimeout(soon); soon = setTimeout(function(){ sync(); showBlockedCard(); }, 250); }
    document.addEventListener('change', syncSoon, true);
    document.addEventListener('click', syncSoon, true);
    document.addEventListener('visibilitychange', function(){ if(document.hidden) sync(); else applyActions().then(function(n){ refreshStatus(); if(n) setTimeout(function(){ sync(true); }, 300); }); });
    window.addEventListener('pagehide', function(){ sync(); });
    setInterval(sync, 5000);   // sends only when something changed
    setTimeout(function(){ ready = true; if(pendingTap){ var d = pendingTap; pendingTap = null; openTap(d); } }, 600);
    setTimeout(checkUpdate, 4000);
    document.addEventListener('visibilitychange', function(){ if(!document.hidden) setTimeout(checkUpdate, 1500); });
  }
  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, { once:true }); else start();
})();

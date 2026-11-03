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
  var LIVE_RE = /^(content\/.+\.json|config\/announcements\.json|config\/OurOtherSites\.json)(\?.*)?$/;
  var origFetch = window.fetch;
  function relPath(u){
    u = String(u || '');
    var base = location.origin + '/';
    if(u.indexOf(base) === 0) u = u.slice(base.length);
    return u.replace(/^\.\//, '');
  }
  window.fetch = function(input, init){
    try{
      if(typeof input === 'string'){
        var rel = relPath(input);
        if(LIVE_RE.test(rel)) return origFetch.call(window, LIVE + rel, init).catch(function(){ return origFetch.call(window, rel, init); });
      }
    }catch(e){}
    return origFetch.apply(window, arguments);
  };

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
  function setPerm(p){ if(p !== perm){ perm = p; ls(PERM_KEY, p); setTimeout(sync, 50); } }
  function accent(){
    try{ var v = getComputedStyle(document.documentElement).getPropertyValue('--footer-accent').trim(); if(/^#[0-9a-f]{6}$/i.test(v)) return v; }catch(e){}
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
    P.show({ key:key, title:this.title, body:this.body, silent:!!opts.silent, color:accent(),
             section:(opts.data && opts.data.section) || '', tapKey:(opts.data && opts.data.key) || '' }).catch(noop);
  }
  AyuNotification.prototype.close = function(){ if(this.tag) P.cancel({ key:this.tag }).catch(noop); };
  AyuNotification.prototype.addEventListener = noop;
  AyuNotification.prototype.removeEventListener = noop;
  Object.defineProperty(AyuNotification, 'permission', { get:function(){ return perm; } });
  AyuNotification.maxActions = 0;
  AyuNotification.requestPermission = function(cb){
    return P.requestPermissions().then(function(r){ setPerm(mapPerm(r && r.notifications)); return perm; }, function(){ return perm; })
      .then(function(p){ if(typeof cb === 'function'){ try{ cb(p); }catch(e){} } refreshStatus(); return p; });
  };
  try{ window.Notification = AyuNotification; }catch(e){}
  if(window.Notification !== AyuNotification){ try{ Object.defineProperty(window, 'Notification', { value:AyuNotification, configurable:true, writable:true }); }catch(e){} }

  // ── Hand the reminder rules to the phone ──
  var lastSent = null, busy = false, again = false;
  function currentRules(){
    try{ return (window.ayuPush && typeof ayuPush.buildRules === 'function') ? ayuPush.buildRules() : null; }catch(e){ return null; }
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
  function refreshStatus(){
    return P.checkPermissions().then(function(r){
      setPerm(mapPerm(r && r.notifications));
      var ex = !(r && r.exactAlarm === false);
      if(ex !== state.exact){ state.exact = ex; sync(true); }
      showExactCard();
      return r;
    }).catch(noop);
  }

  // ── "Alarms & reminders" permission: without it Android may deliver reminders some minutes late ──
  var CARD_KEY = 'ayuAndroidExactLater';
  function showExactCard(){
    var card = document.getElementById('ayu-exact-card');
    var later = Number(ls(CARD_KEY)) || 0;
    var want = state.active && !state.exact && Date.now() - later > 3 * 86400000 && document.body;
    if(!want){ if(card) card.remove(); return; }
    if(card) return;
    card = document.createElement('div');
    card.id = 'ayu-exact-card'; card.setAttribute('role', 'dialog'); card.setAttribute('aria-label', 'Allow exact reminders');
    card.innerHTML = '<div class="ayu-ec-text"><b>Reminders on time</b><span>Allow “Alarms &amp; reminders” for AyuBase6 so reminders arrive at the exact minute.</span></div>'
      + '<div class="ayu-ec-btns"><button type="button" class="ayu-ec-later">Later</button><button type="button" class="ayu-ec-allow">Allow</button></div>';
    card.querySelector('.ayu-ec-allow').onclick = function(){ P.openExactAlarmSettings().catch(noop); };
    card.querySelector('.ayu-ec-later').onclick = function(){ ls(CARD_KEY, String(Date.now())); card.remove(); };
    document.body.appendChild(card);
  }
  function addStyle(){
    var st = document.createElement('style'); st.id = 'ayu-android-bridge-css';
    st.textContent = '#ayu-exact-card{position:fixed;left:12px;right:12px;bottom:calc(12px + env(safe-area-inset-bottom,0px));z-index:2147483000;max-width:520px;margin:0 auto;'
      + 'background:#fff;color:#2d2b23;border-radius:14px;box-shadow:0 8px 28px rgba(0,0,0,.22);padding:12px 14px;display:flex;flex-direction:column;gap:10px;font:inherit;font-size:.86rem;line-height:1.45;'
      + 'border-left:5px solid var(--footer-accent,#0d9488)}'
      + '#ayu-exact-card .ayu-ec-text{display:flex;flex-direction:column;gap:2px}'
      + '#ayu-exact-card .ayu-ec-btns{display:flex;justify-content:flex-end;gap:8px}'
      + '#ayu-exact-card button{border:0;border-radius:10px;padding:8px 16px;font:inherit;font-weight:600;cursor:pointer}'
      + '#ayu-exact-card .ayu-ec-allow{background:var(--footer-accent,#0d9488);color:#fff}'
      + '#ayu-exact-card .ayu-ec-later{background:transparent;color:var(--footer-accent,#0d9488)}';
    document.head.appendChild(st);
  }

  // ── A tapped notification opens its section, like the web app's notification tap ──
  var ready = false, pendingTap = null;
  function openTap(d){
    if(!ready){ pendingTap = d; return; }
    var key = String(d && d.key || ''), section = String(d && d.section || '');
    if(section === 'alerts' || section === 'routines'){
      try{ if(typeof switchTab === 'function') switchTab(section); }catch(e){}
      try{ if(typeof nudgeShowSection === 'function') nudgeShowSection(key); }catch(e){}
    }
    try{ if(key && typeof handleNudgeClick === 'function') handleNudgeClick(key, ''); }catch(e){}
  }
  try{ P.addListener('reminderTap', openTap); }catch(e){}

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
    refreshStatus().then(function(){ setTimeout(function(){ sync(true); }, 300); });
    var soon = null; function syncSoon(){ clearTimeout(soon); soon = setTimeout(sync, 250); }
    document.addEventListener('change', syncSoon, true);
    document.addEventListener('click', syncSoon, true);
    document.addEventListener('visibilitychange', function(){ if(document.hidden) sync(); else refreshStatus(); });
    window.addEventListener('pagehide', function(){ sync(); });
    setInterval(sync, 5000);   // sends only when something changed
    setTimeout(function(){ ready = true; if(pendingTap){ var d = pendingTap; pendingTap = null; openTap(d); } }, 600);
  }
  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start, { once:true }); else start();
})();

"""Renders AyuBase6's alarm tones to WAV files for the Android app (res/raw/tone_<id>.wav).
Same notes and wave shapes as the website's ALERT_SOUNDS (plus the two alarm tones), so a tone
sounds the same in the app and on the website. Each file is one pattern plus a pause; the app loops it."""
import numpy as np, wave, os, sys
OUT = os.path.join(os.path.dirname(__file__), '..', 'android', 'app', 'src', 'main', 'res', 'raw')
SR = 22050
TONES = {
  'rise':      ('sine',     [(523,.2),(659,.2),(784,.2),(1046,.4)]),
  'alarm':     ('square',   [(880,.12),(0,.08),(880,.12),(0,.08),(880,.12),(0,.08),(880,.12)]),
  'chime':     ('sine',     [(880,.15),(1174,.25)]),
  'bell':      ('sine',     [(988,.5)]),
  'ping':      ('triangle', [(1500,.12)]),
  'marimba':   ('sine',     [(523,.12),(659,.12),(784,.2)]),
  'xylophone': ('sine',     [(1046,.1),(1318,.1),(1568,.18)]),
  'drop':      ('sine',     [(1200,.08),(600,.15)]),
  'bubble':    ('sine',     [(700,.06),(900,.06),(1100,.1)]),
  'drum':      ('square',   [(110,.15),(90,.2)]),
}
def osc(kind, f, t):
    ph = (f * t) % 1.0
    if kind == 'sine': return np.sin(2*np.pi*ph)
    if kind == 'square': return np.where(ph < .5, 1.0, -1.0)
    if kind == 'triangle': return 4*np.abs(ph-.5)-1
    return 2*ph-1  # sawtooth
def render(kind, notes, gap=0.7):
    parts = []
    for f, d in notes:
        n = int(SR*(d+0.02)); t = np.arange(n)/SR
        if f > 0:
            env = np.where(t < 0.01, 0.0001*(0.25/0.0001)**(t/0.01), 0.25*(0.0001/0.25)**np.clip((t-0.01)/max(d-0.01,1e-3),0,1))
            parts.append(osc(kind, f, t)*env*(0.55 if kind in ('square','sawtooth') else 1.0))
        else:
            parts.append(np.zeros(n))
        parts.append(np.zeros(int(SR*0.01)))
    parts.append(np.zeros(int(SR*gap)))
    y = np.concatenate(parts); y = y/ max(1e-6, np.abs(y).max()) * 0.85
    return (y*32767).astype(np.int16)
for k, (kind, notes) in TONES.items():
    with wave.open(os.path.join(OUT, 'tone_%s.wav' % k), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(render(kind, notes).tobytes())
print('tones:', ', '.join(sorted(TONES)))

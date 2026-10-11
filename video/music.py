"""Trilha original do vídeo "Escreva fora da margem" (RosaNotes): funk 150 elegante.

150 BPM (1 tempo = 0,4 s; semicolcheia = 0,1 s), Fá menor, resolve em Láb maior na logo.
Seções do roteiro + efeitos sonoros lidos de events.json (gerado pelo render das cenas).
Uso: python3 music.py  ->  trilha.wav (40 s)
"""
import json, os, re
import numpy as np
from synth import *

DUR = 40.0
B = 0.4            # tempo
S = B / 4          # semicolcheia
BAR = 4 * B        # compasso
m = Mix(DUR)
here = os.path.dirname(os.path.abspath(__file__))
ev = json.load(open(os.path.join(here, "events.json"))) if os.path.exists(os.path.join(here, "events.json")) else {}

# ---- Instrumentos extras -------------------------------------------------------------

def crash(dur=1.6):
    n = int(dur * SR); t = t_arr(n)
    metal = sum(np.sin(2 * np.pi * f * t + i) for i, f in enumerate((3120, 4410, 5290, 6830, 8110))) * 0.08
    s = (highpass(noise(n), 5200) * 0.7 + metal) * np.exp(-t * 2.6)
    return s * env(n, 0.001)

def cowbell():
    n = int(0.22 * SR); t = t_arr(n)
    s = np.sign(np.sin(2 * np.pi * 540 * t)) * 0.5 + np.sign(np.sin(2 * np.pi * 800 * t)) * 0.5
    return bandpass(s, 400, 4000) * np.exp(-t * 18) * 0.5

def perc(f=200):
    n = int(0.12 * SR); t = t_arr(n)
    return np.sin(2 * np.pi * f * (1 + 0.5 * np.exp(-t * 40)) * t) * np.exp(-t * 30)

def woodblock(f=950):
    n = int(0.08 * SR); t = t_arr(n)
    return (np.sin(2 * np.pi * f * t) + 0.4 * np.sin(2 * np.pi * f * 2.7 * t)) * np.exp(-t * 60)

def heartbeat():
    n = int(0.35 * SR); t = t_arr(n)
    thump = np.sin(2 * np.pi * np.cumsum(55 + 30 * np.exp(-t * 25)) / SR) * np.exp(-t * 12)
    return thump + highpass(noise(n), 1500) * np.exp(-t * 200) * 0.2

def k808(m_note, dur, glide_from=None):
    n = int(dur * SR); t = t_arr(n)
    f0 = hz(m_note - 24)
    f = np.full(n, f0)
    if glide_from is not None:
        g = int(0.03 * SR); f[:g] = np.linspace(hz(glide_from - 24), f0, g)
    f = f * (1 + 1.6 * np.exp(-t * 45))
    s = np.sin(2 * np.pi * np.cumsum(f) / SR)
    s = np.tanh(2.5 * s) * 0.8 + 0.25 * np.tanh(4 * s)        # harmônicos p/ alto-falante de celular
    return s * env(n, 0.002, r=0.05) * np.exp(-t * 2.5)

def reverse_cymbal(dur):
    return crash(dur)[::-1] * 0.9

def tape_stop(dur=0.25, f0=180):
    n = int(dur * SR); t = t_arr(n)
    f = f0 * (1 - t / dur) ** 2 + 20
    return np.tanh(2 * np.sin(2 * np.pi * np.cumsum(f) / SR)) * (1 - t / dur) * 0.6

def rewind(dur=0.4):
    n = int(dur * SR); t = t_arr(n)
    f = 600 + 2200 * np.abs(np.sin(2 * np.pi * 7 * t))
    return (np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.4 + bandpass(noise(n), 2000, 6000) * 0.25) * env(n, 0.01, r=0.05)

def ding(midi=84):
    return bell(midi, 1.2)

def flip(pitch=1.0):
    n = int(0.12 * SR); t = t_arr(n)
    return bandpass(noise(n), 1500 * pitch, 5000 * pitch) * np.exp(-t * 30) + np.sin(2 * np.pi * 300 * pitch * t) * np.exp(-t * 40) * 0.4

def poof():
    n = int(0.4 * SR); t = t_arr(n)
    return lowpass(noise(n), 1800) * np.exp(-t * 9) * 0.8 + np.sin(2 * np.pi * np.cumsum(300 + 900 * t / 0.4) / SR) * np.exp(-t * 10) * 0.2

def boom():
    n = int(0.8 * SR); t = t_arr(n)
    s = np.sin(2 * np.pi * np.cumsum(55 * (1 - 0.3 * t / 0.8)) / SR) * np.exp(-t * 4)
    return np.tanh(2.2 * s) + highpass(noise(n), 2500) * np.exp(-t * 60) * 0.5

def lead_saw(m_note, dur=0.25):
    """Camada serrilhada curta: dá presença (2–6 kHz) ao gancho no alto-falante do celular."""
    from synth import _saw
    n = int(dur * SR); t = t_arr(n)
    s = _saw(hz(m_note), t, 14) * np.exp(-t * 11)
    return highpass(lowpass(s, 6500), 900) * env(n, 0.002, r=0.03) * 0.6

# ---- Harmonia ---------------------------------------------------------------------------
Fm = [53, 56, 60]; Db = [49, 53, 56]; Eb = [51, 55, 58]
Db_maj_open = [49, 56, 60, 65, 68]; Ab_add9 = [44, 56, 60, 63, 70, 72]

def chord_at(t):
    """Fm | Réb–Mib (2 compassos)."""
    bar = int(t // BAR) % 2
    if bar == 0: return Fm
    return Db if (t % BAR) < 2 * B else Eb

HOOK = [(0, 72), (3, 72), (6, 68), (8, 70), (10, 72), (12, 75), (14, 72),
        (16, 68), (19, 68), (22, 65), (24, 67), (26, 68), (28, 72), (30, 70)]
KICK = [0, 3, 6, 8, 11, 14]
CLAP = [4, 12]
PERC = [2, 5, 9, 12, 14]
ARP = {0: [65, 68, 72, 75], 1: [61, 65, 68, 72], 2: [63, 67, 70, 73]}

def groove(t0, t1, lead=True, cow=False, arp=False, stabs=False, lead_oct=False, gain=1.0, target=None):
    """Bateria + 808 + gancho de t0 a t1 (alinhado à grade)."""
    tgt = target or m
    step0 = int(round(t0 / S)); step1 = int(round(t1 / S))
    prev_root = None
    for st in range(step0, step1):
        t = st * S; s16 = st % 16; s32 = st % 32
        ch = chord_at(t)
        if s16 in KICK:
            if target is None: m.kick_at(t, 0.9 * gain)
            else: tgt.add(kick(), t, "drums", 0.9 * gain)
            nxt = [k for k in KICK if k > s16]
            d = ((nxt[0] if nxt else 16) - s16) * S
            tgt.add(k808(ch[0], d * 0.95, prev_root), t, "bass", 0.55 * gain)
            prev_root = ch[0]
        if s16 in CLAP: tgt.add(clap(), t, "drums", 0.42 * gain)
        if st % 2 == 0: tgt.add(hat(), t, "drums", (0.16 if s16 % 4 == 2 else 0.1) * gain, pan=0.25)
        if s16 in PERC: tgt.add(perc(180 if s16 % 2 else 240), t, "drums", 0.16 * gain, pan=-0.3)
        if lead:
            for hs, note in HOOK:
                if hs == s32:
                    tgt.add(pluck(note, 0.3), t, "lead", 0.26 * gain, pan=-0.1)
                    tgt.add(lead_saw(note), t, "lead", 0.2 * gain, pan=0.1)
                    if lead_oct: tgt.add(pluck(note + 12, 0.25, 0.7), t, "lead", 0.12 * gain, pan=0.2)
                    if cow: tgt.add(cowbell(), t, "fxdry", 0.05 * gain, pan=0.35)
        if arp:
            seq = ARP[0 if ch is Fm else 1 if ch is Db else 2]
            tgt.add(pluck(seq[st % 4] + 12, 0.18, 0.6), t, "lead", 0.07 * gain, pan=0.45 if st % 2 else -0.45)
        if stabs and s16 in (0, 6):
            tgt.add(chord_stab([n + 12 for n in ch]), t, "music", 0.5 * gain)
    # pad do acorde por compasso
    tb = (int(t0 // BAR)) * BAR
    while tb < t1 - 1e-6:
        for half in (0, 2 * B):
            ts = tb + half
            if t0 - 1e-6 <= ts < t1:
                tgt.add(supersaw([n + 12 for n in chord_at(ts)], 2 * B + 0.1, cutoff=2600, a=0.02, r=0.1), ts, "music", 0.32 * gain)
        tb += BAR

# ---- GANCHO 0–1,6: groove abafado + stomps + boom + tape-stop ----------------------------
class Sub:
    def __init__(self, dur):
        self.n = int(dur * SR); self.bus = {b: np.zeros((2, self.n)) for b in Mix.BUSES}
    add = Mix.add
hook = Sub(1.6)
groove(0, 1.4, lead=True, target=hook, gain=0.9)
muff = sum(hook.bus.values())
muff = np.stack([lowpass(muff[0], 800), lowpass(muff[1], 800)])
m.add(muff, 0, "fx", 0.8)
for i, a in enumerate((0, .2, .4, .6)):
    m.kick_at(a, 1.0)
    m.add(clap(), a, "drums", 0.5 + 0.1 * i)
    m.add(flip(1.4), a, "fxdry", 0.25)
    m.add(chord_stab([65, 68, 72]), a, "music", 0.45 + 0.1 * i)
m.add(boom(), 1.2, "fxdry", 0.9)
m.add(impact(1.0), 1.2, "fx", 0.35)
m.add(tape_stop(0.2), 1.4, "fxdry", 0.7)

# ---- BUILD 1,6–3,2 ------------------------------------------------------------------------
m.add(rewind(0.4), 1.6, "fxdry", 0.45)
m.add(riser(1.0, 7000), 2.0, "fx", 0.45)
t = 2.0; step = B / 2
while t < 3.0 - 1e-6:
    m.add(clap(), t, "drums", 0.15 + 0.25 * (t - 2.0))
    step = B / 2 if t < 2.4 else B / 4 if t < 2.8 else B / 8
    t += step
for i, n_ in enumerate((77, 80, 84)):
    m.add(bell(n_, 1.0), 2.4 + i * 0.05, "lead", 0.18)
m.add(reverse_cymbal(0.2), 3.0, "fx", 0.5)

# ---- DROP 1 / 1b ----------------------------------------------------------------------------
groove(3.2, 9.6)
groove(9.6, 12.8, cow=True)
groove(12.8, 20.8, cow=True, arp=True)
for a in (3.2, 6.4, 9.6, 12.8, 15.2, 17.6):
    m.add(crash(), a, "fx", 0.35)
m.add(impact(), 3.2, "fx", 0.45)
m.add(impact(), 8.8, "fx", 0.4)
m.add(crash(), 8.8, "fx", 0.3)
m.add(downlifter(0.4), 20.4, "fx", 0.4)

# ---- BREAK 20,8–24,0 -------------------------------------------------------------------------
pad = supersaw([53 + 12, 56 + 12, 60 + 12, 48 + 12], 2.8, cutoff=900, a=0.3, r=0.6)
m.add(pad, 20.8, "music", 0.55)
hum = np.sin(2 * np.pi * 120 * t_arr(int(2.8 * SR))) * 0.04
m.add(hum, 20.8, "fxdry", 1.0)
for a in (20.8, 22.4):
    m.add(heartbeat(), a, "drums", 0.9); m.add(heartbeat(), a + 0.22, "drums", 0.6)
for k in range(8):
    m.add(woodblock(950 if k % 2 == 0 else 760), 20.8 + k * B, "fxdry", 0.35)
m.add(riser(1.2, 5000), 22.4, "fx", 0.35)
for a in (23.65, 23.85):
    m.add(heartbeat(), a, "drums", 1.0)

# ---- DROP 2 24,0–30,4 / LISTA 30,4–32,8 -------------------------------------------------------
groove(24.0, 30.4, cow=True, arp=True, stabs=True, lead_oct=True)
m.add(impact(), 24.0, "fx", 0.55)
m.add(crash(), 24.0, "fx", 0.4)
m.add(crash(), 27.2, "fx", 0.3)
m.add(crash(1.0), 28.8, "fx", 0.25)
groove(30.4, 32.8, lead=False, arp=True, stabs=True, gain=0.85)

# ---- HERÓI 32,8–34,4 ------------------------------------------------------------------------
for a in (32.8, 33.6):
    m.kick_at(a, 0.9); m.add(k808(49, 0.7), a, "bass", 0.5)
m.add(supersaw([n + 12 for n in Db_maj_open], 1.6, cutoff=3200, a=0.05, r=0.2), 32.8, "music", 0.45)
for k in range(6):   # arpejo de Réb maior por cima do meio-tempo
    m.add(pluck([61, 65, 68, 72, 73, 77][k] + 12, 0.35, 0.8), 32.8 + k * 0.2, "lead", 0.13, pan=(-0.3 if k % 2 else 0.3))
    m.add(lead_saw([61, 65, 68, 72, 73, 77][k] + 12, 0.2), 32.8 + k * 0.2, "lead", 0.04)
m.add(reverse_cymbal(0.4), 34.0, "fx", 0.55)

# ---- LOGO 34,4–36,8 ---------------------------------------------------------------------------
m.add(impact(1.2), 34.4, "fx", 0.6)
m.add(crash(2.0), 34.4, "fx", 0.4)
m.add(supersaw([n + 12 for n in Ab_add9], 2.6, cutoff=4200, a=0.01, r=0.8), 34.4, "music", 0.55)
m.add(sub(44, 1.2), 34.4, "bass", 0.6)
for i, n_ in enumerate((80, 84, 87, 92)):
    m.add(bell(n_, 1.6), 34.45 + i * 0.07, "lead", 0.16)
m.kick_at(34.4, 1.0)
groove(35.2, 36.8, lead=False, gain=0.7)

# ---- CTA 36,8–40,0: groove leve com filtro abrindo 1,2 → 8 kHz (emenda no gancho do loop) --------
def sweep(sig, f0, f1, k=10):
    """Passa-baixa variando no tempo (exponencial de f0 a f1) por mistura de versões filtradas."""
    cuts = f0 * (f1 / f0) ** (np.arange(k) / (k - 1))
    vers = [np.stack([lowpass(sig[0], c), lowpass(sig[1], c)]) for c in cuts]
    pos = np.linspace(0, k - 1, sig.shape[1]) ** 1.0
    i0 = np.minimum(np.floor(pos).astype(int), k - 2); fr = pos - i0
    out = np.zeros_like(sig)
    for j in range(k - 1):
        sel = i0 == j
        out[:, sel] = vers[j][:, sel] * (1 - fr[sel]) + vers[j + 1][:, sel] * fr[sel]
    return out
cta = Sub(3.2)
groove(0, 2.8, lead=True, target=cta, gain=0.8)
cta_mix = sweep(sum(cta.bus.values()), 1200, 8000)
m.add(cta_mix, 36.8, "fxdry", 0.9)
m.add(supersaw([n + 12 for n in Ab_add9[1:]], 3.0, cutoff=1500, a=0.5, r=1.0), 36.8, "music", 0.35)
m.add(reverse_cymbal(0.8), 39.2, "fx", 0.45)

# ---- Efeitos das cenas (events.json) ----------------------------------------------------------
for a, b, kind in ev.get("pen", []):
    d = max(0.08, b - a)
    if kind == "marker": m.add(marker(d), a, "fxdry", 0.10)
    elif kind == "eraser": m.add(bandpass(noise(int(d * SR)), 500, 2500) * env(int(d * SR), 0.02, r=0.05), a, "fxdry", 0.12)
    else: m.add(lowpass(pen_scratch(d), 5500), a, "fxdry", 0.07)
for a in ev.get("taps", []):
    m.add(tick(2400), a, "fxdry", 0.25)

RULES = [  # (padrão no texto da nota, função -> (sinal, barramento, ganho))
    (r"whoosh|whip|swish|deslize", lambda: (whoosh(0.45, 3000), "fx", 0.16)),
    (r"pop|plic|blip", lambda: (pop(1000), "fxdry", 0.16)),
    (r"tic|tique|catraca|reco", lambda: (tick(2600), "fxdry", 0.14)),
    (r"ding", lambda: (ding(84), "lead", 0.14)),
    (r"clack|thock|thump|golpe|click-lock", lambda: (perc(140), "drums", 0.35)),
    (r"card flip|flip", lambda: (flip(1.0), "fxdry", 0.2)),
    (r"poof", lambda: (poof(), "fxdry", 0.35)),
    (r"zip", lambda: (glitch(0.2), "fxdry", 0.3)),
    (r"boing", lambda: (tape_stop(0.2, 90)[::-1], "fxdry", 0.4)),
    (r"estalo|partículas", lambda: (highpass(noise(int(0.25 * SR)), 2500) * np.exp(-t_arr(int(0.25 * SR)) * 14), "fx", 0.18)),
    (r"squeak", lambda: (marker(0.25), "fxdry", 0.12)),
    (r"sininhos|brilho", lambda: (bell(88, 1.0), "lead", 0.08)),
]
SKIP = r"DROP|BREAK|SILÊNCIO|crash|IMPACTO|riser|downlifter|batimento|groove|kick volta|corte seco|tique-taque|rebobinar|prato reverso|swell|meio-tempo"
for tt, desc in ev.get("notes", []):
    if re.search(SKIP, desc): continue
    for pat, fn in RULES:
        if re.search(pat, desc, re.I):
            sig, bus, g = fn()
            m.add(sig, tt, bus, g)
            break

# ---- Equilíbrio p/ alto-falante de celular: menos sub, mais médios (melodia/acordes) -----------
for bname, g in (("bass", 0.5), ("lead", 2.2), ("music", 1.7)):
    m.bus[bname] *= g
d = m.bus["drums"]
lo = np.stack([lowpass(d[0], 120), lowpass(d[1], 120)])
hi = np.stack([highpass(d[0], 9000), highpass(d[1], 9000)])
m.bus["drums"] = d - lo * 0.4 - hi * 0.35
m.render(os.path.join(here, "trilha.wav"), fade_out=0.03)
print("trilha.wav pronta")

"""Instrumentos e mixagem sintetizados (numpy) para a trilha do vídeo do RosaNotes.

Tudo original: nenhum sample externo. Uso:
    from synth import Mix, kick, clap, ...
    m = Mix(duration); m.add(kick(), t, bus="drums"); ... ; m.render("trilha.wav")
"""
import numpy as np, wave

SR = 44100
rng = np.random.default_rng(11)


def t_arr(n):
    return np.arange(n) / SR


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def _spec_filter(x, gain_fn):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X *= gain_fn(np.maximum(f, 1e-6))
    return np.fft.irfft(X, len(x))


def lowpass(x, fc, order=4):
    return _spec_filter(x, lambda f: 1 / np.sqrt(1 + (f / fc) ** (2 * order)))


def highpass(x, fc, order=4):
    return _spec_filter(x, lambda f: 1 / np.sqrt(1 + (fc / f) ** (2 * order)))


def bandpass(x, lo, hi, order=4):
    return highpass(lowpass(x, hi, order), lo, order)


def env(n, a=0.005, d=None, r=None):
    t = t_arr(n)
    e = np.minimum(1, t / max(a, 1e-4))
    if d:
        e = e * np.exp(-t * d)
    if r:
        k = min(n, int(r * SR))
        e[-k:] *= np.linspace(1, 0, k)
    return e


def noise(n):
    return rng.standard_normal(n)


# ---- Bateria ---------------------------------------------------------------------------

def kick(punch=1.0):
    n = int(0.45 * SR); t = t_arr(n)
    f = 46 + 140 * np.exp(-t * 38)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 6.5)
    click = highpass(noise(n), 2500) * np.exp(-t * 260) * 0.35 * punch
    return np.tanh(1.8 * (body + click)) * 0.95


def clap():
    n = int(0.32 * SR); t = t_arr(n); s = np.zeros(n)
    for d in (0, 0.009, 0.019, 0.028):
        i = int(d * SR); m = n - i
        s[i:] += bandpass(noise(m), 900, 5200) * np.exp(-t_arr(m) * (60 if d < 0.028 else 16))
    snap = np.sin(2 * np.pi * 210 * t) * np.exp(-t * 40) * 0.5
    return (s * 0.55 + snap) * 0.9


def hat(open_=False):
    n = int((0.22 if open_ else 0.05) * SR)
    return highpass(noise(n), 7500) * env(n, 0.001, d=12 if open_ else 55) * 0.8


def ride():
    n = int(0.5 * SR); t = t_arr(n)
    s = sum(np.sin(2 * np.pi * f * t) for f in (3150, 4600, 5900, 7700)) * 0.15
    return (s + highpass(noise(n), 6000) * 0.4) * np.exp(-t * 7)


def tom(m=50):
    n = int(0.3 * SR); t = t_arr(n)
    f = hz(m) * (1 + 0.6 * np.exp(-t * 20))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 10)


# ---- Instrumentos ----------------------------------------------------------------------

def _saw(f, t, harm=18):
    s = np.zeros_like(t)
    for h in range(1, harm + 1):
        if f * h > SR / 2.2:
            break
        s += np.sin(2 * np.pi * f * h * t + h * 0.7) / h
    return s


def supersaw(notes, dur, cutoff=3800, a=0.01, r=0.15, voices=(-0.17, -0.07, 0.0, 0.07, 0.17)):
    n = int(dur * SR); t = t_arr(n)
    L = np.zeros(n); R = np.zeros(n)
    for m in notes:
        for i, det in enumerate(voices):
            s = _saw(hz(m + det), t)
            if i % 2: L += s
            else: R += s
            if det == 0.0: L += s * 0.5; R += s * 0.5
    e = env(n, a, r=r)
    L = lowpass(L, cutoff) * e; R = lowpass(R, cutoff) * e
    k = 1 / (len(notes) * len(voices) * 1.2)
    return np.stack([L, R]) * k


def pluck(m, dur=0.35, bright=1.0):
    n = int(dur * SR); t = t_arr(n); f = hz(m)
    s = (np.sin(2 * np.pi * f * t) + 0.6 * bright * np.sin(4 * np.pi * f * t) * np.exp(-t * 8)
         + 0.3 * bright * np.sin(6 * np.pi * f * t) * np.exp(-t * 14) + 0.15 * bright * np.sin(8 * np.pi * f * t) * np.exp(-t * 20))
    return s * env(n, 0.002, d=7)


def bell(m, dur=1.6):
    n = int(dur * SR); t = t_arr(n); f = hz(m); s = np.zeros(n)
    for ratio, amp, d in [(1, 1, 2.2), (2.0, 0.5, 3), (3.01, 0.25, 5), (4.2, 0.12, 7)]:
        s += amp * np.sin(2 * np.pi * f * ratio * t) * np.exp(-t * d)
    return s * env(n, 0.002)


def sub(m, dur):
    n = int(dur * SR); t = t_arr(n); f = hz(m - 24)
    return np.sin(2 * np.pi * f * t) * env(n, 0.004, r=0.04)


def bass(m, dur, drive=2.2):
    n = int(dur * SR); t = t_arr(n); f = hz(m - 12)
    s = np.tanh(drive * (_saw(f, t, 10) * 0.6 + np.sin(2 * np.pi * f * t)))
    return lowpass(s, 900) * env(n, 0.003, d=3, r=0.03) * 0.8


def chord_stab(notes, dur=0.28):
    st = supersaw(notes, dur, cutoff=5200, a=0.002, r=0.08)
    n = st.shape[1]
    return st * np.exp(-t_arr(n) * 7)


# ---- Efeitos ---------------------------------------------------------------------------

def riser(dur, top=6000):
    n = int(dur * SR); t = t_arr(n); k = (t / dur) ** 2
    nz = noise(n)
    s = lowpass(nz, 500) * (1 - k) + lowpass(nz, top) * k
    tone = np.sin(2 * np.pi * np.cumsum(180 + 900 * k) / SR) * 0.3
    return (s * 0.5 + tone) * k


def downlifter(dur=1.2):
    return riser(dur)[::-1] * 0.8


def impact(size=1.0):
    n = int(2.2 * SR); t = t_arr(n)
    boom = np.sin(2 * np.pi * np.cumsum(34 + 70 * np.exp(-t * 9)) / SR) * np.exp(-t * 2.2)
    air = lowpass(noise(n), 2400) * np.exp(-t * 4.5) * 0.45
    crack = highpass(noise(n), 3000) * np.exp(-t * 40) * 0.4
    return np.tanh(1.5 * (boom + air * size + crack))


def whoosh(dur=0.6, bright=2600):
    n = int(dur * SR); t = t_arr(n); x = t / dur
    nz = noise(n)
    s = lowpass(nz, 400) * (1 - x) + lowpass(nz, bright) * x
    return s * np.sin(np.pi * np.minimum(1, x)) ** 2


def pop(pitch=900):
    n = int(0.14 * SR); t = t_arr(n)
    f = pitch * (1 + 1.4 * np.exp(-t * 60))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 32)


def tick(pitch=2200):
    n = int(0.05 * SR); t = t_arr(n)
    return np.sin(2 * np.pi * pitch * t) * np.exp(-t * 120)


def pen_scratch(dur):
    n = int(dur * SR); t = t_arr(n)
    g = bandpass(noise(n), 1800, 5200)
    strokes = 0.5 + 0.5 * np.abs(np.sin(2 * np.pi * 4.2 * t + 1.7 * np.sin(2 * np.pi * 1.1 * t)))
    return g * strokes * env(n, 0.02, r=0.06)


def marker(dur):
    n = int(dur * SR)
    return bandpass(noise(n), 700, 3000) * env(n, 0.04, r=0.1)


def swipe(dur=0.35):
    return whoosh(dur, 5000) * 0.8


def glitch(dur=0.25):
    n = int(dur * SR); t = t_arr(n)
    sq = np.sign(np.sin(2 * np.pi * 180 * t * (1 + 3 * (t * 7 % 1))))
    gate = (np.sin(2 * np.pi * 37 * t) > 0)
    return lowpass(sq * gate, 4000) * env(n, 0.001, r=0.05) * 0.5


# ---- Mixagem ---------------------------------------------------------------------------

def _reverb_ir(seconds=1.8, decay=3.2):
    n = int(seconds * SR); t = t_arr(n)
    L = lowpass(noise(n), 6000) * np.exp(-t * decay)
    R = lowpass(noise(n), 6000) * np.exp(-t * decay)
    return np.stack([L, R]) / np.sqrt(n) * 6


def _convolve(x, h):
    n = x.shape[-1] + h.shape[-1] - 1
    N = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, N) * np.fft.rfft(h, N), N)[..., :x.shape[-1]]


class Mix:
    BUSES = ("drums", "bass", "music", "lead", "fx", "fxdry")

    def __init__(self, duration):
        self.n = int(duration * SR)
        self.bus = {b: np.zeros((2, self.n)) for b in self.BUSES}
        self.kicks = []

    def add(self, sig, at, bus="music", gain=1.0, pan=0.0):
        i = int(round(at * SR))
        if i >= self.n or i < -len(np.atleast_2d(sig)[0]):
            return
        sig = np.asarray(sig, dtype=float)
        if sig.ndim == 1:
            l = sig * np.cos((pan + 1) * np.pi / 4) * np.sqrt(2)
            r = sig * np.sin((pan + 1) * np.pi / 4) * np.sqrt(2)
            sig = np.stack([l, r])
        a = max(0, i); b = min(self.n, i + sig.shape[1])
        self.bus[bus][:, a:b] += sig[:, a - i:b - i] * gain

    def kick_at(self, at, gain=1.0):
        self.kicks.append(at)
        self.add(kick(), at, "drums", gain)

    def _sidechain(self, depth=0.7, release=0.16):
        g = np.ones(self.n)
        rel = int(release * SR)
        curve = 1 - depth * np.exp(-np.linspace(0, 5, rel))
        for k in self.kicks:
            i = int(k * SR)
            if 0 <= i < self.n:
                j = min(self.n, i + rel)
                g[i:j] = np.minimum(g[i:j], curve[:j - i])
        return g

    def render(self, path, fade_out=1.5):
        sc = self._sidechain()
        music = (self.bus["music"] + self.bus["lead"] * 0.9) * sc
        bassb = self.bus["bass"] * (0.35 + 0.65 * sc)
        rev_in = self.bus["music"] * 0.25 + self.bus["lead"] * 0.35 + self.bus["fx"] * 0.3
        rev = _convolve(rev_in, _reverb_ir()) * sc
        mix = self.bus["drums"] + bassb + music + self.bus["fx"] + self.bus["fxdry"] + rev * 0.5
        # cola o grave em mono
        low = lowpass(mix.mean(axis=0), 140)
        mix = mix - np.stack([lowpass(mix[0], 140), lowpass(mix[1], 140)]) + low
        peak = np.max(np.abs(mix)) + 1e-9
        mix = np.tanh(mix / peak * 1.8) / np.tanh(1.8)
        fo = int(fade_out * SR)
        mix[:, -fo:] *= np.linspace(1, 0, fo) ** 1.4
        mix[:, :int(0.01 * SR)] *= np.linspace(0, 1, int(0.01 * SR))
        mix *= 0.9
        pcm = (mix.T * 32767).astype(np.int16)
        with wave.open(path, "wb") as w:
            w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())

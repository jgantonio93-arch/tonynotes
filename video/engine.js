// Motor de motion do vídeo do RosaNotes: tudo é função do tempo t (sem estado entre
// quadros), então qualquer quadro pode ser renderizado em qualquer ordem/processo.
(function () {
  const E = {};
  E.clamp = v => Math.max(0, Math.min(1, v));
  E.seg = (t, a, b) => E.clamp((t - a) / (b - a));
  E.lerp = (a, b, k) => a + (b - a) * k;
  E.out3 = x => 1 - Math.pow(1 - x, 3);
  E.out5 = x => 1 - Math.pow(1 - x, 5);
  E.in3 = x => x * x * x;
  E.inOut = x => x < .5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
  E.back = (x, c = 1.7) => 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2);
  E.elastic = x => x === 0 || x === 1 ? x : Math.pow(2, -10 * x) * Math.sin((x * 10 - .75) * (2 * Math.PI) / 3) + 1;
  E.hash = (i) => { const s = Math.sin(i * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); };
  const $ = id => document.getElementById(id);
  E.$ = $;

  // ---- Batida ------------------------------------------------------------------------
  E.setBeat = (bpm, offset = 0) => { E.BPM = bpm; E.BEAT = 60 / bpm; E.BEAT0 = offset; };
  E.beatAt = n => E.BEAT0 + n * E.BEAT;
  /** 1 na batida, decai rápido (pulsos de zoom/brilho). */
  E.pulse = (t, from = -1e9, to = 1e9, every = 1) => {
    if (t < from || t > to) return 0;
    const p = ((t - E.BEAT0) % (E.BEAT * every) + E.BEAT * every) % (E.BEAT * every);
    return Math.exp(-p * 10);
  };
  /** Envelope de impacto: soma dos impactos recentes. */
  E.hitEnv = (t, hits, decay = 9) => hits.reduce((m, h) => t >= h ? Math.max(m, Math.exp(-(t - h) * decay)) : m, 0);

  // ---- Gravações do app (quadros reais) ----------------------------------------------
  /**
   * clip = { dir, frames, touches, map: [[v0, v1, fA, fB], ...] } — o tempo do vídeo v0..v1
   * toca os quadros fA..fB (velocidade livre por trecho).
   */
  E.clipFrame = (clip, t) => {
    const m = clip.map;
    if (t <= m[0][0]) return m[0][2];
    for (const [v0, v1, a, b] of m) if (t < v1) return Math.min(clip.frames - 1, Math.floor(E.lerp(a, b, (t - v0) / (v1 - v0))));
    return Math.min(clip.frames - 1, m[m.length - 1][3]);
  };
  /** Tempo de vídeo em que o quadro f da gravação aparece. */
  E.clipTime = (clip, f) => {
    for (const [v0, v1, a, b] of clip.map) if (f < b) return E.lerp(v0, v1, Math.max(0, f - a) / (b - a));
    return clip.map[clip.map.length - 1][1];
  };
  /** Mapa a partir de trechos [quadroA, quadroB, velocidade], começando em v0. */
  E.buildMap = (v0, parts) => {
    const out = []; let v = v0;
    for (const [a, b, s] of parts) { const d = (b - a) / 30 / s; out.push([v, v + d, a, b]); v += d; }
    return out;
  };
  E.clipEnd = clip => clip.map[clip.map.length - 1][1];
  /** Foco suavizado (média dos toques em volta do quadro) — câmera sem tremer e sem estado. */
  E.focus = (clip, f, win = 18) => {
    let sx = 0, sy = 0, n = 0;
    for (let i = Math.max(0, f - win); i <= Math.min(clip.frames - 1, f + win); i++) {
      const p = clip.touches[i] && clip.touches[i].p[0];
      if (p) { const w = 1 - Math.abs(i - f) / (win + 1); sx += p[0] * w; sy += p[1] * w; n += w; }
    }
    return n ? [sx / n, sy / n] : null;
  };

  const imgCache = {};
  /** Garante que <img id> mostra o quadro f do clipe (espera decodificar). */
  E.showFrame = async (imgId, clip, f) => {
    const el = $(imgId);
    const src = `${clip.dir}/f${String(f).padStart(4, "0")}.jpg`;
    if (imgCache[imgId] === src) return;
    el.src = src; imgCache[imgId] = src;
    try { await el.decode(); } catch (e) {}
  };

  // ---- Texto -------------------------------------------------------------------------
  /** Carimbo: entra grande e borrado e assenta. */
  E.stamp = (el, t, at, opts = {}) => {
    const d = opts.dur || .18, from = opts.from || 2.4, rot = opts.rot || 0;
    const k = E.out5(E.seg(t, at, at + d));
    el.style.opacity = t >= at ? 1 : 0;
    el.style.transform = `${opts.base || ""} scale(${E.lerp(from, 1, k)}) rotate(${(1 - k) * rot}deg)`;
    el.style.filter = `blur(${(1 - k) * (opts.blur ?? 16)}px)`;
    return k;
  };
  /** Revela por máscara de baixo para cima (linha "sobe" de dentro da caixa). */
  E.rise = (el, t, at, dur = .35, dist = 120) => {
    const k = E.out5(E.seg(t, at, at + dur));
    el.style.transform = `translateY(${(1 - k) * dist}%)`;
    el.style.opacity = t >= at ? 1 : 0;
    return k;
  };
  /** Escrita revelada da esquerda para a direita. */
  E.wipe = (el, t, a, b) => {
    const k = E.out3(E.seg(t, a, b));
    el.style.clipPath = `inset(-30% ${100 - k * 100}% -30% -5%)`;
    return k;
  };

  // ---- Partículas --------------------------------------------------------------------
  /** Explosão de pétalas a partir de (cx, cy) no instante t0. Retorna HTML. */
  E.petals = (t, t0, cx, cy, n = 30, spread = 1, colors = ["#FF8C99", "#FFC9D1", "#E8404F", "#ffffff"]) => {
    const k = t - t0;
    if (k < 0 || k > 2.8) return "";
    let h = "";
    for (let i = 0; i < n; i++) {
      const a = i * 2.39996 + E.hash(i) * .6, sp = (700 + E.hash(i + 9) * 900) * spread;
      const r = (1 - Math.exp(-k * 3.2)) / 1.6;
      const x = cx + Math.cos(a) * sp * r + Math.sin(k * 3 + i) * 20;
      const y = cy + Math.sin(a) * sp * r + 380 * k * k;
      const s = .6 + E.hash(i + 3) * 1.4;
      h += `<div class="petal" style="left:${x}px;top:${y}px;background:${colors[i % colors.length]};opacity:${E.clamp(1.4 - k / 2)};transform:rotate(${a * 60 + k * (300 + i * 7)}deg) scale(${s})"></div>`;
    }
    return h;
  };

  window.E = E;
})();

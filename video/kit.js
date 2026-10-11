// Componentes compartilhados das cenas do vídeo "Escreva fora da margem".
// Tudo é função do tempo global t (sem estado entre quadros).
(function () {
  const { clamp, seg, lerp, out3, out5, inOut, back, hash } = E;
  const K = {};
  K.W = 1080; K.H = 1920; K.FPS = 30;
  E.setBeat(150, 0);            // 1 tempo = 0,4 s = 12 quadros
  K.BEAT = E.BEAT;
  K.b = n => n * K.BEAT;        // tempo da n-ésima batida (s)

  // ---- Cores e tipografia --------------------------------------------------------------
  K.C = {
    paper: "#F6F4EF", ink: "#1B1B1F", red: "#C2253B", redNight: "#F28A97", rose1: "#E8404F", rose2: "#A3162B",
    petal: "#FFC9D1", marker: "#FFE45C", markerNight: "#7A6A12", blue: "#1F5FD1", night: "#0F0C0D", wine: "#3A0A14",
  };

  // ---- Gravações ---------------------------------------------------------------------
  // window.REC = { r1: { dir, info:{frames,width,height,scale}, touches:[], views:[], markers:{}, anchors:{} }, ... }
  K.rec = id => window.REC[id];
  K.m = (id, name) => { const v = window.REC[id].markers[name]; if (v === undefined) throw new Error(`marker ${id}.${name} não existe`); return v; };

  /**
   * Clipe: trechos [inícioVídeo, fimVídeo, quadroA, quadroB] (quadros podem ser nomes de
   * marker ou números; "nome+5" soma). Velocidade livre por trecho; quadroB < quadroA toca
   * ao contrário (rebobinar).
   */
  K.clip = (id, parts) => {
    const r = K.rec(id);
    const f = v => {
      if (typeof v === "number") return v;
      const m = /^([^+-]+)([+-]\d+)?$/.exec(v);
      return K.m(id, m[1]) + (m[2] ? Number(m[2]) : 0);
    };
    return { id, rec: r, frames: r.info.frames, touches: r.touches, views: r.views, map: parts.map(([v0, v1, a, b]) => [v0, v1, f(a), f(b)]) };
  };
  K.frameAt = (clip, t) => {
    const m = clip.map;
    if (t <= m[0][0]) return clamp01i(m[0][2], clip);
    for (const [v0, v1, a, b] of m) if (t < v1) return clamp01i(Math.floor(lerp(a, b, (t - v0) / (v1 - v0))), clip);
    return clamp01i(m[m.length - 1][3], clip);
  };
  function clamp01i(f, clip) { return Math.max(0, Math.min(clip.frames - 1, f)); }
  /** Tempo de vídeo em que o quadro f aparece (primeiro trecho que o contém). */
  K.timeOf = (clip, f) => {
    for (const [v0, v1, a, b] of clip.map) {
      const lo = Math.min(a, b), hi = Math.max(a, b);
      if (f >= lo && f <= hi) return lerp(v0, v1, (f - a) / (b - a || 1));
    }
    return null;
  };
  /** Foco (px da gravação) suavizado pelos toques em volta do quadro f; olha um pouco à frente.
   *  lo/hi limitam os quadros considerados (para não "ver" gestos de outra cena). */
  K.focus = (clip, f, win = 16, ahead = 6, lo = 0, hi = 1e9) => {
    let sx = 0, sy = 0, n = 0;
    for (let i = f - win + ahead; i <= f + win + ahead; i++) {
      const tc = clip.touches[Math.max(lo, Math.min(hi, clip.frames - 1, i))];
      const p = tc && tc.p[0];
      if (p) { const w = 1 - Math.abs(i - f - ahead) / (win + 1); sx += p[0] * w; sy += p[1] * w; n += w; }
    }
    return n ? [sx / n, sy / n] : null;
  };

  // ---- Visor do app: a gravação dentro de um retângulo, com câmera -------------------
  /**
   * Cria um visor (div com overflow hidden) com a imagem da gravação e uma camada de
   * cursores. el: container (posicionado pela cena). Devolve objeto com set().
   */
  K.viewer = (el, opts = {}) => {
    el.classList.add("viewer");
    el.innerHTML = `<div class="vw-in"><img class="vw-img"><div class="vw-ann"></div><div class="vw-over"></div></div>`;
    const inner = el.querySelector(".vw-in"), img = el.querySelector(".vw-img"), over = el.querySelector(".vw-over");
    const ann = el.querySelector(".vw-ann");   // anotações da cena, no espaço da gravação (px da imagem)
    let lastSrc = "";
    return {
      el, img, inner, over, ann,
      /**
       * Mostra o quadro f do clipe com a câmera: zoom (1 = a largura da gravação preenche o
       * visor), foco [x,y] em px da gravação que vai para (ax, ay) do visor (0..1).
       */
      async set(clip, f, cam = {}) {
        const r = clip.rec, iw = r.info.width, ih = r.info.height;
        const vw = el.clientWidth || cam.w, vh = el.clientHeight || cam.h;
        const fill = vw / iw, s = fill * (cam.zoom || 1);
        const [fx, fy] = cam.focus || [iw / 2, ih / 2];
        const ax = (cam.ax ?? .5) * vw, ay = (cam.ay ?? .5) * vh;
        let tx = ax - fx * s, ty = ay - fy * s;
        if (cam.clamp !== false) { tx = Math.min(0, Math.max(vw - iw * s, tx)); ty = Math.min(0, Math.max(vh - ih * s, ty)); }
        inner.style.width = iw + "px"; inner.style.height = ih + "px";
        inner.style.transform = `translate(${tx}px, ${ty}px) scale(${s}) rotate(${cam.rot || 0}deg)`;
        inner.style.transformOrigin = "0 0";
        const src = `${r.dir}/f${String(f).padStart(4, "0")}.jpg`;
        if (src !== lastSrc) { img.src = src; lastSrc = src; try { await img.decode(); } catch (e) {} }
        // cursores
        const tc = clip.touches[f] || { k: "none", p: [] };
        over.innerHTML = cam.cursors === false ? "" : K.cursorHTML(tc, 1 / s, cam);
        return { s, tx, ty };
      },
    };
  };

  /** HTML dos cursores (S Pen / dedo) no espaço da gravação; inv = 1/escala do visor. */
  K.cursorHTML = (tc, inv, cam = {}) => {
    if (!tc || tc.up && tc.k !== "tap") return "";
    let h = "";
    if (tc.k === "pen" && tc.p[0]) {
      const [x, y] = tc.p[0];
      h += `<div class="spen" style="left:${x}px;top:${y}px;transform:scale(${inv * (cam.penScale || 1)})"><i class="sh"></i><i class="body"></i><i class="btn"></i><i class="tip"></i><i class="glow"></i></div>`;
    } else if ((tc.k === "finger" || tc.k === "pinch" || tc.k === "tap") && !tc.up) {
      for (const [x, y] of tc.p) h += `<div class="finger" style="left:${x}px;top:${y}px;transform:translate(-50%,-50%) scale(${inv})"></div>`;
    }
    return h;
  };

  // ---- Celular 3D (aro preto, cantos arredondados, reflexo) ----------------------------
  /** Cria um celular dentro de el; devolve { phone, screen } (screen é o container do visor). */
  K.phone = (el, w = 640) => {
    const h = w * 2673 / 1233;
    el.innerHTML = `<div class="phone3d" style="width:${w + 28}px;height:${h + 28}px"><div class="ph-screen" style="width:${w}px;height:${h}px"></div><div class="ph-glass"></div></div>`;
    return { phone: el.querySelector(".phone3d"), screen: el.querySelector(".ph-screen"), w, h };
  };

  // ---- Texto cinético -----------------------------------------------------------------
  /** SLAM: 1,4 -> 1 com desfoque 14 -> 0 e fantasma rosa nos 2 primeiros quadros. */
  K.slam = (el, t, at, o = {}) => {
    const d = o.dur || 4 / 30;
    const k = back(seg(t, at, at + d), 1.4);
    el.style.opacity = t >= at - (o.pre || 0) ? 1 : 0;
    el.style.transform = `${o.base || ""} scale(${lerp(o.from || 1.4, 1, k)}) rotate(${(1 - k) * (o.rot || 0)}deg)`;
    const blur = (1 - clamp(k)) * 14;
    const ghost = t >= at && t < at + 2 / 30 ? `drop-shadow(8px 0 0 rgba(232,64,79,.75))` : "";
    el.style.filter = `blur(${blur}px) ${ghost}`;
    return k;
  };
  /** RISE: a linha sobe de dentro da máscara (pai com overflow hidden). */
  K.rise = (el, t, at, d = 6 / 30) => {
    const k = out5(seg(t, at, at + d));
    el.style.transform = `translateY(${(1 - k) * 110}%)`;
    el.style.opacity = t >= at ? 1 : 0;
    return k;
  };
  /** STAMP: 1,15 -> 1 com giro -2° -> 0. */
  K.stamp = (el, t, at, o = {}) => {
    const k = out5(seg(t, at, at + 5 / 30));
    el.style.opacity = t >= at ? 1 : 0;
    el.style.transform = `${o.base || ""} scale(${lerp(1.15, 1, k)}) rotate(${lerp(o.rot ?? -2, 0, k)}deg)`;
    return k;
  };
  /** WHIP: sai deslizando rápido com borrão no eixo do movimento. dir = [dx, dy] unitário. */
  K.whipOut = (el, t, at, dir = [-1, 0], d = 5 / 30, dist = 1200) => {
    const k = E.in3(seg(t, at, at + d));
    if (t < at) return 0;
    el.style.translate = `${dir[0] * dist * k}px ${dir[1] * dist * k}px`;
    el.style.filter = `url(#blur${dir[0] ? "X" : "Y"}${k > .5 ? 40 : k > .15 ? 20 : 0})`;
    el.style.opacity = k >= 1 ? 0 : 1;
    return k;
  };
  /** Faixa de marca-texto atrás de um elemento .hl (usa a variável --hl de 0 a 1). */
  K.hl = (el, t, at, d = 5 / 30) => { const k = out3(seg(t, at, at + d)); el.style.setProperty("--hl", k); return k; };
  /** Revela texto/traço da esquerda para a direita. */
  K.wipe = (el, t, a, b) => { const k = out3(seg(t, a, b)); el.style.clipPath = `inset(-40% ${100 - k * 100}% -40% -5%)`; return k; };
  /** Desenha um <path> SVG (pathLength=1) de a até b. */
  K.draw = (path, t, a, b, ease = out3) => { path.style.strokeDasharray = 1; path.style.strokeDashoffset = 1 - ease(seg(t, a, b)); };
  /** Mostra el só no intervalo [a, b). */
  K.vis = (el, t, a, b) => { const on = t >= a && t < b; el.style.display = on ? "" : "none"; return on; };

  // ---- Efeitos globais (registrados pelas cenas, aplicados no fim de cada quadro) ------
  K.fx = { hits: [], petals: [], flashes: [], shakes: [] };
  /** Impacto: tremor + aberração cromática + bump de escala. */
  K.hit = (at, power = 1) => K.fx.hits.push([at, power]);
  K.flash = (at, color = "#fff", power = 1, frames = 1) => K.fx.flashes.push([at, color, power, frames]);
  K.burst = (at, x, y, n = 40, spread = 1) => K.fx.petals.push([at, x, y, n, spread]);
  K.applyFx = (t) => {
    let h = 0, hp = 0;
    for (const [at, p] of K.fx.hits) if (t >= at && t < at + .5) { const e = Math.exp(-(t - at) * 11) * p; if (e > h) { h = e; hp = p; } }
    const shake = h * 14, sx = Math.sin(t * 93.7) * shake, sy = Math.cos(t * 77.3) * shake;
    const bump = 1 + h * .06;
    const ab = (h > .25 && hp >= .8) ? 6 * h : 0;
    const st = E.$("stage");
    st.style.transform = `translate(${sx}px, ${sy}px) scale(${bump})`;
    st.style.filter = ab ? `drop-shadow(${ab}px 0 0 rgba(255,40,80,.65)) drop-shadow(${-ab}px 0 0 rgba(40,220,255,.55))` : "none";
    let fl = 0, flc = "#fff";
    for (const [at, c, p, fr] of K.fx.flashes) if (t >= at && t < at + fr / 30) { fl = p; flc = c; }
    E.$("flash").style.opacity = fl; E.$("flash").style.background = flc;
    let ph = "";
    for (const [at, x, y, n, sp] of K.fx.petals) ph += E.petals(t, at, x, y, n, sp, ["#FFC9D1", "#E8404F", "#FF8C99", "#ffffff"]);
    E.$("petals").innerHTML = ph;
  };

  // ---- Grade de pontos presa ao mundo do app -----------------------------------------
  /**
   * Ajusta o fundo de pontos de el para coincidir com a grade do app visto num visor.
   * view = [ox, oy, s] da gravação (px da imagem); place = {s, tx, ty} devolvido por viewer.set
   * (mais o deslocamento do visor dentro do vídeo: vx, vy).
   */
  K.syncGrid = (el, view, place, vx = 0, vy = 0, baseSpacing = 40) => {
    if (!view) return;
    const [ox, oy, s] = view;
    let sp = baseSpacing * s;            // espaçamento em px da gravação (o app dobra/divide com o zoom)
    const k = place.s;
    while (sp * k < 18) sp *= 2;
    while (sp * k > 80) sp /= 2;
    const size = sp * k;
    const x0 = vx + place.tx + ox * k, y0 = vy + place.ty + oy * k;
    el.style.backgroundSize = `${size}px ${size}px`;
    el.style.backgroundPosition = `${x0 - size / 2}px ${y0 - size / 2}px`;
  };

  window.K = K;
})();

// Registro de cenas.
window.SCENES = window.SCENES || [];
K.scene = def => window.SCENES.push(def);

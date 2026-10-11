// Cenas S01–S05 (gravação R1): gancho da margem, um toque em "Infinita", drop escreve/arrasta,
// pra baixo também, a aula inteira numa tela só.
(function () {
  const { clamp, seg, lerp, out3, out5, inOut, back } = E;

  // ===================================================================================
  // S01 — gancho: "NÃO ESCREVA FORA DA MARGEM." + letra espremida na borda + "socorro"
  // ===================================================================================
  const S01 = { start: 0, end: 1.6 };
  // 0,0–0,6 termina "gia"; 0,6–1,0 escadinha "da célula"; 1,0–1,6 congela no fim
  const c01 = K.clip("r1", [[0, .6, "gancho", "aperta"], [.6, 1.0, "aperta", "socorro"], [1.0, 1.6, "socorro", "socorro"]]);
  // caixa da escadinha (px da gravação), a partir dos toques reais
  const squeeze = (() => {
    let x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9;
    for (let f = K.m("r1", "aperta"); f < K.m("r1", "socorro"); f++) for (const [x, y] of c01.touches[f].p) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
    return { x0, y0, x1, y1, cx: (x0 + x1) / 2, cy: (y0 + y1) / 2 };
  })();
  const EDGE = window.REC.r1.markers.margem[0];   // borda direita da folha (px da gravação)

  K.scene({
    id: "s01", ...S01,
    setup(root) {
      root.innerHTML = `
        <div class="viewer" id="s01v" style="left:0;top:0;width:1080px;height:1920px"></div>
        <div class="abs" id="s01txt" style="left:84px;top:250px">
          <div class="sticker" style="padding:18px 34px 26px;transform:rotate(-1.5deg)">
            <div class="disp" id="s01a" style="font-size:250px;color:var(--ink);position:relative">NÃO
              <svg id="s01x" viewBox="0 0 520 220" style="position:absolute;left:-10px;top:10px;width:560px;height:230px;overflow:visible">
                <path pathLength="1" d="M10,70 L500,40 L30,130 L510,110 L20,190 L490,180" fill="none" stroke="#C2253B" stroke-width="30" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </div>
            <div id="s01rest">
              <div class="disp" id="s01b" style="font-size:150px">ESCREVA</div>
              <div class="disp" id="s01c" style="font-size:150px">FORA DA</div>
              <div class="disp" id="s01d" style="font-size:150px">MARGEM.</div>
            </div>
          </div>
        </div>`;
      this.v = K.viewer(root.querySelector("#s01v"));
      // anotação no espaço da gravação: círculo vermelho + "socorro"
      const pad = 46, rx = (squeeze.x1 - squeeze.x0) / 2 + pad, ry = (squeeze.y1 - squeeze.y0) / 2 + pad;
      this.v.ann.innerHTML = `
        <svg style="position:absolute;left:0;top:0;width:1px;height:1px;overflow:visible">
          <ellipse id="s01circ" pathLength="1" cx="${squeeze.cx}" cy="${squeeze.cy}" rx="${rx}" ry="${ry}" fill="none" stroke="#C2253B" stroke-width="10" stroke-linecap="round" transform="rotate(-8 ${squeeze.cx} ${squeeze.cy})"/>
        </svg>
        <div class="hand" id="s01soc" style="position:absolute;left:${squeeze.x0 - 300}px;top:${squeeze.y1 - 10}px;font-size:64px;color:#C2253B;transform:rotate(-10deg);white-space:nowrap">socorro</div>`;
      window.EVENTS.pen.push([0, .6, "pen"], [.6, 1.0, "pen"]);
    },
    async render(t, root) {
      const f = K.frameAt(c01, t);
      // câmera: macro 2,2 -> 2,5 na borda da folha; no impacto, punch-in de 18% na escadinha
      const push = lerp(2.2, 2.5, inOut(seg(t, 0, 1.2)));
      const punch = t >= 1.2 ? 1.3 : 1;
      const focus = K.focus(c01, f, 10, 4, K.m("r1", "gancho"), K.m("r1", "socorro") - 1) || [squeeze.cx, squeeze.cy];
      const toSq = inOut(seg(t, .7, 1.2));
      const fx = lerp(Math.min(focus[0], EDGE - 220), squeeze.cx - 60, toSq);
      const fy = lerp(focus[1], squeeze.cy, toSq);
      const tape = seg(t, 1.4, 1.6);
      await this.v.set(c01, f, { zoom: push * punch, focus: [fx, fy], ax: .56, ay: .66, penScale: 1.15 });
      this.v.el.style.transform = `translateY(${tape * 40}px)`;
      this.v.el.style.filter = `${tape ? `url(#blurY${tape > .5 ? 40 : 20})` : ""} saturate(${1 - tape * .6})`;

      // textos: um slam por colcheia (0,0 / 0,2 / 0,4 / 0,6)
      K.slam(root.querySelector("#s01a"), t, -2 / 30, { from: 1.4 });
      K.slam(root.querySelector("#s01b"), t, .2);
      K.slam(root.querySelector("#s01c"), t, .4);
      K.slam(root.querySelector("#s01d"), t, .6, { from: 1.5 });
      // rabisco vermelho no NÃO (1,0–1,2)
      K.draw(root.querySelector("#s01x path"), t, 1.0, 1.2, x => x);
      // 1,2: o NÃO despenca; o resto sobe com mola
      const fall = E.in3(seg(t, 1.2, 1.5));
      root.querySelector("#s01a").style.translate = `0 ${fall * 900}px`;
      root.querySelector("#s01a").style.rotate = `${fall * 18}deg`;
      const up = back(seg(t, 1.2, 1.45));
      root.querySelector("#s01rest").style.transform = `translateY(${-up * 260}px)`;
      // círculo e "socorro" (no espaço da gravação)
      K.draw(this.v.ann.querySelector("#s01circ"), t, 1.2, 1.2 + 4 / 30);
      const soc = this.v.ann.querySelector("#s01soc");
      soc.style.opacity = t >= 1.26 ? 1 : 0;
      soc.style.translate = `${Math.sin(t * 60) * 2}px ${Math.cos(t * 50) * 2}px`;
      K.wipe(soc, t, 1.26, 1.4);
    },
  });
  // impactos e tremores da cena
  [0, .2, .4].forEach(a => K.hit(a, .45));
  K.hit(.6, .6);
  K.hit(1.2, 1);
  K.flash(1.2, "#fff", .35, 1);
})();

// ===================================================================================
// BLOCO A — S02 (rebobina + "Infinita"), S03 (drop escreve/arrasta), S04 (pra baixo),
// S05 (a aula inteira). Gravação R1. Tudo função pura de t.
// ===================================================================================
(function () {
  const { clamp, seg, lerp, out3, out5, inOut, back, hash } = E;
  const R1 = window.REC.r1, IW = R1.info.width, IH = R1.info.height;
  const VW = K.W, VH = K.H, BT = K.BEAT, C = K.C;
  const in2 = x => x * x;
  const expo = x => x >= 1 ? 1 : 1 - Math.pow(2, -10 * x);
  const FILL = VW / IW;                         // escala da gravação com zoom 1 num visor de 1080
  const DEV = R1.info.scale;                    // 1,25: escala do canvas = zoom do app × 1,25

  // ---------- utilidades ------------------------------------------------------------------
  /** Beat bounce: +2,5% em cada kick, decaindo em 6 quadros. */
  const bounce = t => { const p = ((t % BT) + BT) % BT; const k = Math.max(0, 1 - p / .2); return 1 + .025 * k * k; };
  /** Quadro contínuo (fracionário) do clipe no tempo t. */
  function frameF(clip, t) {
    const m = clip.map;
    if (t <= m[0][0]) return m[0][2];
    for (const [v0, v1, a, b] of m) if (t < v1) return lerp(a, b, (t - v0) / (v1 - v0));
    return m[m.length - 1][3];
  }
  /** Câmera do canvas (view.json) interpolada num quadro fracionário. */
  function viewAt(ff) {
    const n = R1.views.length - 1, a = Math.max(0, Math.min(n, Math.floor(ff))), b = Math.min(n, a + 1), k = ff - a;
    const A = R1.views[a], B = R1.views[b];
    return [lerp(A[0], B[0], k), lerp(A[1], B[1], k), lerp(A[2], B[2], k)];
  }
  /** Foco só pela ponta da caneta (ignora dedo/toques), média ponderada em volta de f. */
  function penFocus(clip, f, win, ahead, lo, hi) {
    let sx = 0, sy = 0, n = 0;
    for (let i = f - win + ahead; i <= f + win + ahead; i++) {
      const tc = clip.touches[Math.max(lo, Math.min(hi, i))];
      if (tc && tc.k === "pen" && tc.p[0]) { const w = 1 - Math.abs(i - f - ahead) / (win + 1); sx += tc.p[0][0] * w; sy += tc.p[0][1] * w; n += w; }
    }
    return n ? [sx / n, sy / n] : null;
  }
  /** Mesmo cálculo de K.viewer.set (sem tocar no DOM): devolve {s, tx, ty}. */
  function place(vw, vh, cam) {
    const s = vw / IW * (cam.zoom || 1);
    const [fx, fy] = cam.focus || [IW / 2, IH / 2];
    let tx = (cam.ax ?? .5) * vw - fx * s, ty = (cam.ay ?? .5) * vh - fy * s;
    tx = Math.min(0, Math.max(vw - IW * s, tx)); ty = Math.min(0, Math.max(vh - IH * s, ty));
    return { s, tx, ty };
  }
  /** Velocidade do conteúdo do app (px da gravação por quadro de vídeo), a partir do view.json. */
  function contentSpeed(clip, t) {
    const ff = frameF(clip, t), f0 = viewAt(ff), f1 = viewAt(ff + 1);
    let rate = 1;
    for (const [v0, v1, a, b] of clip.map) if (t >= v0 && t < v1) { rate = Math.abs(b - a) / ((v1 - v0) * 30); break; }
    return [(f1[0] - f0[0]) * rate, (f1[1] - f0[1]) * rate];
  }
  /** Desfoque direcional via filtro SVG próprio (stdDeviation ajustado por quadro). */
  function whipBlur(el, id, bx, by) {
    bx = Math.min(40, Math.abs(bx)); by = Math.min(40, Math.abs(by));
    if (bx < 1 && by < 1) { el.style.filter = "none"; return; }
    document.getElementById(id + "B").setAttribute("stdDeviation", `${bx.toFixed(1)} ${by.toFixed(1)}`);
    el.style.filter = `url(#${id})`;
  }
  /** Entrada em chicote: chega deslizando (com desfoque no eixo) e assenta em `at` com slam. */
  function whipIn(el, t, at, dir = [1, 0], dist = 1100) {
    K.slam(el, t, at, { pre: 5 / 30, from: 1.25 });
    if (t < at) {
      const k = out3(seg(t, at - 5 / 30, at));
      el.style.translate = `${dir[0] * dist * (1 - k)}px ${dir[1] * dist * (1 - k)}px`;
      el.style.filter = `url(#blur${dir[0] ? "X" : "Y"}${k < .5 ? 40 : 20})`;
    } else el.style.translate = "";
  }
  /** Caneta "grudada": durante a escrita a S Pen fica visível também nos quadros de ponta erguida
   *  (entre traços), sem piscar; some quando a escrita acaba. */
  function stickyPen(viewer, clip, f, pl, cam) {
    const tc = clip.touches[f], nx = clip.touches[f + 1];
    if (tc && tc.k === "pen" && tc.p[0] && nx && nx.k === "pen") viewer.over.innerHTML = K.cursorHTML({ k: "pen", up: false, p: tc.p }, 1 / pl.s, cam);
  }
  /** Zera estilos que os helpers do kit não resetam (render é pura, a ordem dos quadros é livre). */
  const reset = (...els) => els.forEach(el => { el.style.translate = ""; el.style.filter = ""; el.style.clipPath = ""; });
  const q = (root, s) => root.querySelector(s);
  /** Anel rosa do toque dentro da lupa. */
  function loupeRing(lw, t, at) {
    const r = lw.querySelector(".a-lring"), k = seg(t, at, at + .3);
    const d = lerp(40, 260, out3(k));
    Object.assign(r.style, { width: d + "px", height: d + "px", margin: `${-d / 2}px 0 0 ${-d / 2}px`, opacity: k > 0 && k < 1 ? 1 - k : 0 });
  }

  // ---------- CSS e filtros (uma vez) ---------------------------------------------------
  function injectOnce() {
    if (document.getElementById("a-style")) return;
    const st = document.createElement("style");
    st.id = "a-style";
    st.textContent = `
      .a-it { font-style: italic; font-synthesis: style; }
      .a-red { color: #C2253B; } .a-blue { color: #1F5FD1; } .a-ink { color: #1B1B1F; }
      .a-osd { font-family: Onest; font-weight: 800; font-size: 62px; letter-spacing: .1em; color: #fff; display: flex; align-items: center; gap: 20px;
        text-shadow: 4px 0 0 #1B1B1F, -4px 0 0 #1B1B1F, 0 4px 0 #1B1B1F, 0 -4px 0 #1B1B1F, 3px 3px 0 #1B1B1F, -3px -3px 0 #1B1B1F, 3px -3px 0 #1B1B1F, -3px 3px 0 #1B1B1F; }
      .a-scan { position: absolute; inset: 0; pointer-events: none; background: repeating-linear-gradient(0deg, rgba(0,0,0,.2) 0, rgba(0,0,0,.2) 2px, transparent 2px, transparent 4px); }
      .a-band { position: absolute; left: 0; width: 1080px; height: 150px; background: linear-gradient(transparent, rgba(255,255,255,.55) 45%, rgba(255,255,255,.2) 60%, transparent); mix-blend-mode: screen; }
      .a-pill { position: absolute; background: #1B1B1F; color: #F6F4EF; border-radius: 24px; box-shadow: 6px 6px 0 rgba(27,27,31,.25); overflow: hidden; }
      .a-loupe { position: absolute; border-radius: 50%; overflow: hidden; box-shadow: 0 0 0 9px #F6F4EF, 0 0 0 14px #1B1B1F, 12px 16px 0 14px rgba(27,27,31,.25); }
      .a-dash { position: absolute; width: 11px; height: 40px; margin-left: -5.5px; background: #C2253B; border-radius: 5px; box-shadow: 0 0 14px rgba(232,64,79,.8); }
      .a-spark { position: absolute; width: 16px; height: 16px; margin: -8px 0 0 -8px; border-radius: 50%; }
      .a-white { color: #fff; text-shadow: 0 5px 0 rgba(0,0,0,.28), 0 0 34px rgba(0,0,0,.4); }
      .a-letter { display: inline-block; white-space: pre; }
      .a-lring { width: 0; height: 0; border-radius: 50%; border: 10px solid #E8404F; }
      .a-sweep { position: absolute; width: 1800px; height: 700px; background: rgba(255,228,92,.8); mix-blend-mode: multiply; clip-path: polygon(0 0, 1660px 0, 1800px 100%, 0 100%); transform-origin: 0 50%; }
    `;
    document.head.appendChild(st);
    const sv = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    sv.setAttribute("width", "0"); sv.setAttribute("height", "0"); sv.style.position = "absolute";
    sv.innerHTML = `<defs>
      <filter id="aRGB" x="0" y="0" width="100%" height="100%" color-interpolation-filters="sRGB">
        <feOffset in="SourceGraphic" dx="9" dy="0" result="o1"/>
        <feColorMatrix in="o1" type="matrix" values="1 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0" result="r"/>
        <feOffset in="SourceGraphic" dx="-9" dy="0" result="o2"/>
        <feColorMatrix in="o2" type="matrix" values="0 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 1 0" result="gb"/>
        <feComposite in="r" in2="gb" operator="arithmetic" k1="0" k2="1" k3="1" k4="0"/>
      </filter>
      <filter id="aWhip3" x="0" y="0" width="100%" height="100%"><feGaussianBlur id="aWhip3B" stdDeviation="0 0" edgeMode="duplicate"/></filter>
      <filter id="aWhip4" x="0" y="0" width="100%" height="100%"><feGaussianBlur id="aWhip4B" stdDeviation="0 0" edgeMode="duplicate"/></filter>
      <filter id="aGlow" filterUnits="userSpaceOnUse" x="-900" y="-600" width="1800" height="1200"><feGaussianBlur stdDeviation="16"/></filter>
    </defs>`;
    document.body.appendChild(sv);
  }

  // ---------- clipes ----------------------------------------------------------------------
  // S02: 1,6–2,0 escadinha ao contrário; 2,0 bandeja Página; 2,4 toque em Infinita; 2,8 bandeja fecha
  const c02 = K.clip("r1", [[1.6, 2.0, "socorro", "aperta"], [2.0, 2.4, 56, 62], [2.4, 2.4 + 8 / 30, 63, 66], [2.4 + 8 / 30, 2.8, 66, 72], [2.8, 3.2, 73, 76]]);
  // S03: a caneta cruza a antiga borda exatamente em 3,4 (quadro 85)
  const c03 = K.clip("r1", [[3.2, 3.4, "drop", 85], [3.4, 4.0, 85, "arrasta1"], [4.0, 4.4, "arrasta1", "atp"], [4.4, 4.8, "atp", "arrasta2"],
    [4.8, 5.2, "arrasta2", "vermelho"], [5.2, 6.0, "vermelho", 195], [6.0, 6.4, 195, "desce"]]);
  const c04 = K.clip("r1", [[6.4, 6.8, "desce", "krebs"], [6.8, 7.6, "krebs", 238], [7.6, 8.0, 238, 238]]);
  const c05 = K.clip("r1", [[8.0, 8.8, "zoom", 268], [8.8, 9.6, 268, 287]]);

  // caixa da escadinha (mesma conta da S01) para desfazer o círculo/"socorro" no rebobinar
  const squeeze = (() => {
    let x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9;
    for (let f = K.m("r1", "aperta"); f < K.m("r1", "socorro"); f++) for (const [x, y] of R1.touches[f].p) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
    return { x0, y0, x1, y1, cx: (x0 + x1) / 2, cy: (y0 + y1) / 2 };
  })();
  const LINE_Y = 1512;                              // pauta de "energia" (px da gravação)
  const BORDER_WX = (1233 - R1.views[76][0]) / R1.views[76][2];   // x de MUNDO da antiga borda da folha

  // ---------- câmeras (compartilhadas entre cenas para emendar sem salto) -------------
  function cam02(t) {
    if (t < 2.0) {
      const k = inOut(seg(t, 1.6, 2.0));
      return { zoom: lerp(3.25, 2.25, k), focus: [lerp(squeeze.cx - 60, 1010, k), lerp(squeeze.cy, 1525, k)], ax: .56, ay: lerp(.66, .56, k), penScale: 1.15 };
    }
    return { zoom: lerp(1.2, 1.27, seg(t, 2.0, 3.0)) * bounce(t), focus: [422, 1050], ax: .33, ay: .46 };
  }
  function cam03(t) {
    const f = K.frameAt(c03, t);
    const pf = penFocus(c03, f, 14, 6, 77, 198) || [1300, LINE_Y];
    const push = inOut(seg(t, 6.0, 6.4));
    const fx = lerp(pf[0], 500, push), fy = lerp(lerp(LINE_Y, pf[1], .35), 1500, push);
    return { zoom: 1.45 * (1 + .08 * push) * bounce(t), focus: [fx, fy], ax: .5, ay: .53, penScale: 1.1 };
  }
  const KREBS_C = [777, 1765];
  function cam04(t, noBounce) {
    const f = K.frameAt(c04, t);
    const kd = inOut(seg(t, 6.4, 6.8));
    const pf = penFocus(c04, f, 12, 5, 216, 238) || [760, 1770];
    const kf = [lerp(KREBS_C[0], pf[0], .55), lerp(KREBS_C[1], pf[1], .5)];
    let fx = lerp(500, kf[0], kd), fy = lerp(1500, kf[1], kd);
    const ko = inOut(seg(t, 7.6, 8.0));
    fx = lerp(fx, KREBS_C[0], ko); fy = lerp(fy, KREBS_C[1], ko);
    const zoom = lerp(lerp(1.566, 1.52, kd), 1.45, ko) * (noBounce ? 1 : bounce(t));
    return { zoom, focus: [fx, fy], ax: .5, ay: lerp(.53, .7, kd), penScale: 1.1 };
  }

  // ---------- ∞ (lemniscata) --------------------------------------------------------------
  const A_INF = 500;
  const lem = u => { const s = Math.sin(u), c = Math.cos(u), d = 1 + s * s; return [A_INF * c / d, A_INF * s * c / d]; };
  const INF_D = (() => { let d = ""; for (let i = 0; i <= 200; i++) { const [x, y] = lem(Math.PI / 2 + i / 200 * 2 * Math.PI); d += (i ? "L" : "M") + x.toFixed(1) + "," + y.toFixed(1); } return d; })();
  const LOOP = (() => { const p = []; for (let i = 0; i <= 72; i++) p.push(lem(Math.PI / 2 + i / 72 * Math.PI)); return p; })();
  const LOOP_C = (() => { let a = 0, cx = 0, cy = 0; for (let i = 0; i < LOOP.length - 1; i++) { const [x0, y0] = LOOP[i], [x1, y1] = LOOP[i + 1]; const c = x0 * y1 - x1 * y0; a += c; cx += (x0 + x1) * c; cy += (y0 + y1) * c; } a /= 2; return [cx / (6 * a), cy / (6 * a)]; })();
  const infSVG = (id, main) => `<div class="abs" id="${id}" style="left:0;top:0;width:0;height:0;transform-origin:0 0">
      <svg style="position:absolute;left:-900px;top:-600px;width:1800px;height:1200px;overflow:visible" viewBox="-900 -600 1800 1200">
        ${main ? `<path class="a-infg" pathLength="1" d="${INF_D}" fill="none" stroke="#FF8C99" stroke-width="120" stroke-linecap="round" stroke-linejoin="round" opacity=".55" filter="url(#aGlow)"/>` : ""}
        <path class="a-infp" pathLength="1" d="${INF_D}" fill="none" stroke="#C2253B" stroke-width="60" stroke-linecap="round" stroke-linejoin="round"/>
      </svg></div>`;
  const rot = (p, r) => { const c = Math.cos(r), s = Math.sin(r); return [p[0] * c - p[1] * s, p[0] * s + p[1] * c]; };

  // ---------- ícones --------------------------------------------------------------------
  const RW_ICON = `<svg width="92" height="56" viewBox="0 0 92 56" style="overflow:visible"><g fill="#fff" stroke="#1B1B1F" stroke-width="5" stroke-linejoin="round"><path d="M44,4 L4,28 L44,52 Z"/><path d="M88,4 L48,28 L88,52 Z"/></g></svg>`;
  const INF_ICON = (w, sw, col) => `<svg width="${w}" height="${w * .42}" viewBox="-560 -230 1120 460" style="overflow:visible;display:block"><path d="${INF_D}" fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round"/></svg>`;

  // ===================================================================================
  // S02 — ◀◀ REBOBINA / Um toque… / e a borda some.  (1,6–3,2)
  // ===================================================================================
  const TRAY_TOP = 679;                                 // topo da bandeja no quadro 56 (px da gravação)
  const BTN_BIG = [90, 933, 665, 239], BTN_SMALL = [90, 1945, 665, 240];   // botão "Infinita" antes/depois
  K.scene({
    id: "s02", start: 1.6, end: 3.2,
    setup(root) {
      injectOnce();
      root.innerHTML = `
        <div class="abs" id="a2world" style="left:0;top:0;width:1080px;height:1920px">
          <div id="a2v" style="left:0;top:0;width:1080px;height:1920px"></div>
          <div class="abs" id="a2scrim" style="left:0;top:0;width:1080px;height:1920px;background:#000"></div>
          <div id="a2tr" style="left:0;top:0;width:1080px;height:1920px;background:transparent"></div>
          <div class="abs" id="a2t1" style="left:84px;top:300px;transform-origin:0 50%"><div class="sticker dark" id="a2t1s" style="transform:rotate(-2deg);padding:4px 30px 20px"><div class="disp" id="a2t1i" style="font-size:112px">Um toque…</div></div></div>
          <div class="abs" id="a2t2" style="left:104px;top:476px;transform-origin:0 50%"><div class="sticker" style="transform:rotate(2deg);padding:4px 30px 20px"><div class="disp a-it a-red" style="font-size:104px">e a borda some.</div></div></div>
        </div>
        <div id="a2vhs">
          <div class="a-scan"></div>
          <div class="a-band" id="a2band"></div>
          <div class="abs a-osd" id="a2osd" style="left:84px;top:262px">${RW_ICON}<span>REBOBINA</span></div>
        </div>
        <div id="a2p" style="left:0;top:0;width:1080px;height:1920px"></div>
        ${infSVG("a2g2", false)}${infSVG("a2g1", false)}${infSVG("a2inf", true)}`;
      this.v = K.viewer(q(root, "#a2v"));
      this.tr = K.viewer(q(root, "#a2tr"));
      this.tr.el.style.background = "transparent";
      this.tr.img.style.clipPath = `inset(${TRAY_TOP}px 0 0 0 round 64px 64px 0 0)`;
      this.pv = K.viewer(q(root, "#a2p"));
      // anotações no espaço da gravação: círculo + "socorro" (desfeitos no rebobinar), anel do toque, botão piscando
      const pad = 46, rx = (squeeze.x1 - squeeze.x0) / 2 + pad, ry = (squeeze.y1 - squeeze.y0) / 2 + pad;
      this.v.ann.innerHTML = `
        <svg style="position:absolute;left:0;top:0;width:1px;height:1px;overflow:visible">
          <ellipse id="a2circ" pathLength="1" cx="${squeeze.cx}" cy="${squeeze.cy}" rx="${rx}" ry="${ry}" fill="none" stroke="#C2253B" stroke-width="10" stroke-linecap="round" transform="rotate(-8 ${squeeze.cx} ${squeeze.cy})"/>
          <circle id="a2ring" cx="422" cy="1050" r="60" fill="none" stroke="#E8404F" stroke-width="12"/>
        </svg>
        <div class="hand" id="a2soc" style="position:absolute;left:${squeeze.x0 - 300}px;top:${squeeze.y1 - 10}px;font-size:64px;color:#C2253B;transform:rotate(-10deg);white-space:nowrap">socorro</div>
        <div id="a2btn" style="position:absolute;border-radius:62px;background:#E8404F;mix-blend-mode:multiply"></div>`;
      window.EVENTS.taps.push(2.4);
      window.EVENTS.notes.push([1.6, "rebobinar de fita VHS (1,6–2,0), com chiado e wow"], [2.0, "whoosh curto da bandeja subindo"],
        [2.0, "riser + rufada acelerando até 3,0"], [2.4, "clique + brilho Fá5-Láb5-Dó6 enquanto o ∞ se desenha (2,4–2,67)"],
        [3.0, "silêncio, só prato reverso até 3,2 (mergulho no ∞)"]);
    },
    async render(t, root) {
      const rw = t < 2.0, rise = !rw && t < 2.0 + 8 / 30;
      const f = K.frameAt(c02, t);
      const cam = cam02(t);
      // na subida da bandeja a base é o quadro 49 (escadinha já desfeita, sem menu); sem cursor
      const pl = await this.v.set(c02, rise ? K.m("r1", "pagina") : f, rise ? { ...cam, cursors: false } : cam);
      // --- VHS no rebobinar
      const fi = Math.round(t * 30);
      K.vis(q(root, "#a2vhs"), t, 1.6, 2.0);
      this.v.el.style.filter = rw ? "url(#aRGB) contrast(1.08) saturate(1.25)" : "none";
      this.v.el.style.transform = rw ? `translate(${(hash(fi) - .5) * 18}px, ${hash(fi + 50) > .75 ? (hash(fi + 9) - .5) * 26 : 0}px) scale(1.04)` : "";
      if (rw) stickyPen(this.v, c02, f, pl, cam);
      q(root, "#a2band").style.top = `${lerp(-150, 1920, seg(t, 1.6, 1.97))}px`;
      q(root, "#a2osd").style.opacity = (Math.floor(t * 7.5) % 2 === 0 || t < 1.7) ? 1 : .55;
      K.slam(q(root, "#a2osd"), t, 1.6, { from: 1.25 });
      const circ = q(root, "#a2circ"), soc = q(root, "#a2soc");
      circ.style.display = soc.style.display = rw ? "" : "none";
      if (rw) {
        circ.style.strokeDasharray = 1; circ.style.strokeDashoffset = in2(seg(t, 1.6, 1.78));
        const kw = 1 - seg(t, 1.6, 1.72);
        soc.style.clipPath = `inset(-40% ${100 - kw * 100}% -40% -5%)`;
      }
      // --- bandeja subindo (ease-out-expo em 8 quadros) por cima do app escurecido
      K.vis(this.tr.el, t, 2.0, 2.0 + 8 / 30);
      const ke = expo(seg(t, 2.0, 2.0 + 8 / 30));
      if (rise) {
        await this.tr.set(c02, 56, { ...cam, cursors: false });
        this.tr.el.style.transform = `translateY(${(1 - ke) * 1750}px)`;
      }
      const scrim = q(root, "#a2scrim");
      scrim.style.opacity = rise ? .32 * ke : 0;
      // --- toque em "Infinita": anel + botão piscando rosa
      const ring = q(root, "#a2ring"), kr = seg(t, 2.4, 2.4 + 9 / 30);
      ring.style.display = kr > 0 && kr < 1 ? "" : "none";
      ring.setAttribute("r", lerp(50, 330, out3(kr))); ring.style.opacity = 1 - kr;
      const btn = q(root, "#a2btn"), bf = t >= 2.4 && t < 2.6;
      btn.style.display = bf ? "" : "none";
      if (bf) {
        const b = t < 2.4 + 8 / 30 ? BTN_BIG : BTN_SMALL;
        Object.assign(btn.style, { left: b[0] + "px", top: b[1] + "px", width: b[2] + "px", height: b[3] + "px" });
        btn.style.opacity = .55 * (Math.floor((t - 2.4) * 30) % 2 === 0 ? 1 : .35);
      }
      // --- textos
      const t1 = q(root, "#a2t1"), t1i = q(root, "#a2t1i"), t2 = q(root, "#a2t2");
      K.slam(t1, t, 2.0);
      K.vis(t1, t, 2.0, 3.2);
      const lit = t >= 2.8;                             // a bandeja fecha: a "luz acende" e o texto vira tinta
      q(root, "#a2t1s").className = lit ? "sticker" : "sticker dark";
      t1i.style.color = lit ? C.ink : "#F6F4EF";
      t1.style.scale = lit ? `${1 + .12 * Math.exp(-(t - 2.8) * 18)}` : "";
      K.slam(t2, t, 2.8, { from: 1.5, rot: -4 });
      K.vis(t2, t, 2.8, 3.2);
      // --- ∞ nasce do ícone do botão, cresce e vira portal
      const inf = q(root, "#a2inf"), g1 = q(root, "#a2g1"), g2 = q(root, "#a2g2");
      const on = t >= 2.4;
      [inf, g1, g2].forEach(el => el.style.display = on ? "" : "none");
      const pv = this.pv.el;
      pv.style.display = t >= 3.0 ? "" : "none";
      const world = q(root, "#a2world");
      world.style.transform = ""; world.style.filter = ""; world.style.transformOrigin = "0 0";
      if (on) {
        const icon = [pl.tx + 422 * pl.s, pl.ty + 1050 * pl.s];
        const P0 = [540, 1250];
        let P, k, r;
        const g = seg(t, 2.4, 2.8);
        if (t < 2.8) {
          const ge = out3(g);
          k = .072 * Math.pow(.9 / .072, ge) * (1 + .06 * Math.sin(g * Math.PI));
          P = [lerp(icon[0], P0[0], ge), lerp(icon[1], P0[1], ge)]; r = lerp(0, -8, ge);
        } else {
          k = .9 + .08 * seg(t, 2.8, 3.0); P = P0; r = -8 - 2 * seg(t, 2.8, 3.0);
        }
        const rr = r * Math.PI / 180;
        const paths = inf.querySelectorAll("path");
        paths.forEach(p => K.draw(p, t, 2.4, 2.4 + 8 / 30, out3));
        // mergulho no laço esquerdo (3,0–3,2): escala ×8 exponencial, o centro do laço vai para o centro da tela
        const u = seg(t, 3.0, 3.0 + 5 / 30);
        let Pd = P, kd = k;
        const SL1 = (() => { const c = rot(LOOP_C, rr); return [P[0] + k * c[0], P[1] + k * c[1]]; })();
        if (t >= 3.0) {
          kd = k * Math.pow(8.5, Math.pow(u, 1.5));
          const SL = [lerp(SL1[0], 540, out3(u)), lerp(SL1[1], 960, out3(u))];
          const c = rot(LOOP_C, rr);
          Pd = [SL[0] - kd * c[0], SL[1] - kd * c[1]];
          // fantasmas = blur radial falso
          [[g1, .86, .32], [g2, .72, .16]].forEach(([el, m, o]) => {
            const kg = kd * m; el.style.transform = `translate(${SL[0] - kg * c[0]}px, ${SL[1] - kg * c[1]}px) rotate(${r}deg) scale(${kg})`;
            el.style.opacity = o * Math.min(1, u * 3);
            el.querySelectorAll("path").forEach(p => { p.style.strokeDasharray = ""; p.style.strokeDashoffset = ""; });
          });
          // portal: dentro do laço já aparece o DROP (quadro 77) com a câmera da S03
          const pts = LOOP.map(p => { const q2 = rot(p, rr); return `${(Pd[0] + kd * q2[0]).toFixed(1)}px ${(Pd[1] + kd * q2[1]).toFixed(1)}px`; });
          pv.style.clipPath = `polygon(${pts.join(",")})`;
          const c3 = cam03(3.2);
          await this.pv.set(c03, K.m("r1", "drop"), { ...c3, zoom: c3.zoom * lerp(.78, 1, out3(u)), cursors: true });
          // o resto do mundo é empurrado pela câmera
          const ws = 1 + 2.4 * in2(u);
          world.style.transformOrigin = `${SL1[0]}px ${SL1[1]}px`;
          world.style.transform = `scale(${ws})`;
          world.style.filter = `blur(${(u * 9).toFixed(1)}px)`;
        } else { g1.style.opacity = g2.style.opacity = 0; }
        inf.style.transform = `translate(${Pd[0]}px, ${Pd[1]}px) rotate(${r}deg) scale(${kd})`;
        inf.style.opacity = 1;
      }
    },
  });
  K.hit(2.0, .45); K.hit(2.4, .35); K.hit(2.8, .5);

  // ===================================================================================
  // S03 — DROP: Escreva / fora da margem. / Arrasta. / E continua. + odômetro 21 cm → ∞
  // ===================================================================================
  const DASH_Y = []; for (let y = 360; y < IH; y += 66) DASH_Y.push(y);
  const SPARKS = 18;
  K.scene({
    id: "s03", start: 3.2, end: 6.4,
    setup(root) {
      injectOnce();
      root.innerHTML = `
        <div id="a3v" style="left:0;top:0;width:1080px;height:1920px"></div>
        <div class="abs" id="a3t1" style="left:84px;top:258px;transform-origin:0 50%"><div class="sticker" style="transform:rotate(-2deg);padding:4px 30px 18px"><div class="disp" style="font-size:132px">Escreva</div></div></div>
        <div class="abs" id="a3t2" style="left:112px;top:438px;transform-origin:0 30%"><div class="sticker" style="transform:rotate(1.6deg);padding:4px 34px 26px">
          <div class="disp a-it a-red" style="font-size:150px">fora da</div><div class="disp a-it a-red" style="font-size:150px"><span class="hl" id="a3hl1">margem.</span></div></div></div>
        <div class="abs" id="a3t3" style="left:84px;top:300px;transform-origin:0 50%"><div class="sticker" style="transform:rotate(-2.2deg);padding:4px 34px 22px"><div class="disp" style="font-size:156px"><span class="hl" id="a3hl2">Arrasta.</span></div></div></div>
        <div class="abs" id="a3t4" style="left:84px;top:300px;transform-origin:0 50%"><div class="sticker" style="transform:rotate(1.8deg);padding:4px 34px 22px"><div class="disp" style="font-size:138px">E <span class="a-it a-red hl" id="a3hl3">continua.</span></div></div></div>
        <div class="a-pill" id="a3cnt" style="left:84px;top:1356px;width:280px;height:108px;transform-origin:0 50%">
          <div class="abs" id="a3col" style="left:0;top:0;width:280px">
            ${["21 cm", "1 m", "10 m"].map(s => `<div class="lab" style="height:108px;display:flex;align-items:center;justify-content:center;font-size:64px;letter-spacing:.04em;text-transform:none">${s}</div>`).join("")}
            <div style="height:108px;display:flex;align-items:center;justify-content:center">${INF_ICON(150, 80, "#F6F4EF")}</div>
          </div>
        </div>
        <div class="a-loupe" id="a3lpw" style="left:690px;top:600px;width:290px;height:290px"><div id="a3lp" style="left:0;top:0;width:290px;height:290px"></div><div class="abs a-lring" style="left:145px;top:145px"></div></div>`;
      this.v = K.viewer(q(root, "#a3v"));
      this.lp = K.viewer(q(root, "#a3lp"));
      // fantasma da antiga borda (tracejado vermelho) + faíscas, no espaço da gravação
      this.v.ann.innerHTML = `<div id="a3gb">${DASH_Y.map((y, i) => `<div class="a-dash" data-i="${i}" style="top:${y}px"></div>`).join("")}</div>
        <div id="a3sp">${Array.from({ length: SPARKS }, (_, i) => `<div class="a-spark" style="background:${["#FFC9D1", "#E8404F", "#FF8C99", "#fff"][i % 4]}"></div>`).join("")}</div>`;
      const T = f => K.timeOf(c03, f);
      window.EVENTS.pen.push([T(77), T(102) + 1 / 30, "pen"], [T(116), T(146) + 1 / 30, "pen"], [T(164), T(194) + 1 / 30, "pen"]);
      window.EVENTS.taps.push(T(160));
      window.EVENTS.notes.push([3.2, "DROP: crash + kick + 808 + palma"], [3.4, "estalo de partículas: a antiga borda estoura quando a caneta passa"],
        [4.0, "whoosh horizontal (passa-banda 300→3000 Hz, pan L→R) no arrasto"], [4.8, "whoosh horizontal no arrasto"],
        [4.4, "tic-tic de catraca do odômetro (1 m)"], [5.2, "tic-tic de catraca do odômetro (10 m)"], [6.0, "tic-tic + 'ding' metálico (∞)"]);
    },
    async render(t, root) {
      const f = K.frameAt(c03, t);
      const cam = cam03(t);
      const pl = await this.v.set(c03, f, cam);
      stickyPen(this.v, c03, f, pl, cam);
      // chicote horizontal real: desfoque proporcional à velocidade do arrasto na tela
      const [dx] = contentSpeed(c03, t);
      whipBlur(this.v.el, "aWhip3", dx * pl.s * .4, 0);
      // --- fantasma da borda: aparece no drop, estoura quando a caneta passa (3,4)
      const view = viewAt(frameF(c03, t));
      const bx = BORDER_WX * view[2] + view[0];
      const dashes = this.v.ann.querySelectorAll(".a-dash");
      const gbOn = t < 3.9;
      q(root, "#a3gb").style.display = gbOn ? "" : "none";
      if (gbOn) dashes.forEach((d, i) => {
        const y = DASH_Y[i], dl = Math.abs(y - LINE_Y) / 1800 * .16;
        const k = seg(t, 3.4 + dl, 3.4 + dl + .42);
        const vx = (hash(i * 3.1) - .3) * 760, vy = (hash(i * 7.7) - .5) * 420 + Math.sign(y - LINE_Y) * 160;
        const ke = out3(k);
        const pulse = t < 3.4 ? 1 + .25 * Math.exp(-(t - 3.2) * 14) : 1;
        d.style.left = `${bx + vx * ke}px`;
        d.style.top = `${y + vy * ke + 260 * k * k}px`;
        d.style.transform = `rotate(${(hash(i) - .5) * 720 * ke}deg) scale(${(1 - .55 * k) * pulse})`;
        d.style.opacity = t < 3.2 + 1 / 30 ? 1 : 1 - Math.pow(k, 1.4);
      });
      const sp = this.v.ann.querySelectorAll(".a-spark"), ks = seg(t, 3.4, 3.8);
      q(root, "#a3sp").style.display = ks > 0 && ks < 1 ? "" : "none";
      sp.forEach((s, i) => {
        const a = i * 2.39996, r = (140 + hash(i + 4) * 260) * out3(ks);
        s.style.left = `${bx + Math.cos(a) * r}px`; s.style.top = `${LINE_Y + Math.sin(a) * r + 200 * ks * ks}px`;
        s.style.opacity = 1 - ks; s.style.transform = `scale(${1.6 - ks})`;
      });
      // --- textos em adesivos; saem em chicote junto com os arrastos reais
      const t1 = q(root, "#a3t1"), t2 = q(root, "#a3t2"), t3 = q(root, "#a3t3"), t4 = q(root, "#a3t4");
      reset(t1, t2, t3, t4);
      K.slam(t1, t, 3.2); K.whipOut(t1, t, 4.08, [-1, 0]);
      K.slam(t2, t, 3.6, { from: 1.5 }); K.whipOut(t2, t, 4.12, [-1, 0]);
      t2.style.translate = t < 4.12 ? `${Math.sin(t * 90) * 8 * Math.exp(-Math.max(0, t - 3.6) * 9) * (t >= 3.6 ? 1 : 0)}px 0` : t2.style.translate;
      K.hl(q(root, "#a3hl1"), t, 3.8);
      K.vis(t1, t, 3.2, 4.4); K.vis(t2, t, 3.6, 4.4);
      whipIn(t3, t, 4.4); K.hl(q(root, "#a3hl2"), t, 4.6); K.whipOut(t3, t, 4.92, [-1, 0]);
      K.vis(t3, t, 4.4 - 5 / 30, 5.2);
      whipIn(t4, t, 5.2); K.hl(q(root, "#a3hl3"), t, 5.6);
      K.vis(t4, t, 5.2 - 5 / 30, 6.4);
      // push-in final no "prova!" também empurra o adesivo um pouco
      t4.style.scale = `${1 + .04 * inOut(seg(t, 6.0, 6.4))}`;
      // --- odômetro 21 cm → 1 m → 10 m → ∞ (rolagem de 4 quadros + pop de 115%)
      const cnt = q(root, "#a3cnt"), col = q(root, "#a3col");
      const sw = [4.4, 5.2, 6.0];
      let idx = 0, roll = 0, pop = 0;
      sw.forEach((ts, i) => { if (t >= ts) { idx = i; roll = out3(seg(t, ts, ts + 4 / 30)); pop = 1 - out3(seg(t, ts, ts + 7 / 30)); } });
      const pos = t >= 4.4 ? idx + roll : 0;
      col.style.transform = `translateY(${-pos * 108}px)`;
      col.style.filter = roll > 0 && roll < 1 ? "url(#blurY20)" : "";
      K.slam(cnt, t, 3.2, { from: 1.6 });
      cnt.style.scale = `${1 + .15 * pop}`;
      // --- lupa: o toque REAL no vermelho da barra
      const lw = q(root, "#a3lpw");
      const lon = K.vis(lw, t, 5.12, 5.62);
      if (lon) {
        const kin = back(seg(t, 5.12, 5.26), 1.6), kout = E.in3(seg(t, 5.5, 5.62));
        lw.style.transform = `scale(${Math.max(0, kin * (1 - kout))}) rotate(${(1 - kin) * -12}deg)`;
        await this.lp.set(c03, f, { zoom: 1.3 * IW / 290, focus: [1310, 352], cursors: false });
        loupeRing(lw, t, 5.2);
      }
    },
  });
  K.hit(3.2, 1); K.flash(3.2, "#fff", .9, 1);
  K.hit(3.6, .55); K.hit(4.4, .5); K.hit(5.2, .5); K.hit(6.0, .4);

  // ===================================================================================
  // S04 — Pra baixo / também.  (6,4–8,0)
  // ===================================================================================
  const PB = "Pra baixo".split("");
  /** Queda com 2 quiques: 1 = no alto, 0 = assentado. */
  const fall2 = x => {
    if (x <= 0) return 1;
    if (x < .5) return 1 - in2(x / .5);
    if (x < .8) { const u = (x - .5) / .3; return .16 * 4 * u * (1 - u); }
    if (x < 1) { const u = (x - .8) / .2; return .05 * 4 * u * (1 - u); }
    return 0;
  };
  K.scene({
    id: "s04", start: 6.4, end: 8.0,
    setup(root) {
      injectOnce();
      root.innerHTML = `
        <div id="a4v" style="left:0;top:0;width:1080px;height:1920px"></div>
        <div class="abs" id="a4old" style="left:84px;top:300px;transform-origin:0 50%"><div class="sticker" style="transform:rotate(1.8deg);padding:4px 34px 22px"><div class="disp" style="font-size:138px">E <span class="a-it a-red hl" style="--hl:1">continua.</span></div></div></div>
        <div class="abs disp" id="a4t1" style="left:84px;top:262px;font-size:138px;white-space:nowrap">${PB.map(c => `<span class="a-letter">${c}</span>`).join("")}</div>
        <div class="abs" id="a4t2" style="left:92px;top:418px;transform-origin:0 50%"><div class="disp a-it a-blue" style="font-size:138px">também.</div></div>
        <svg class="abs" id="a4arr" style="left:640px;top:396px;width:150px;height:220px;overflow:visible" viewBox="0 0 150 220">
          <path pathLength="1" d="M78,8 C70,60 84,110 74,186" fill="none" stroke="#1F5FD1" stroke-width="11" stroke-linecap="round"/>
          <path pathLength="1" d="M34,140 C52,160 64,176 74,192 C88,170 104,152 124,138" fill="none" stroke="#1F5FD1" stroke-width="11" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <div class="a-loupe" id="a4lpw" style="left:700px;top:640px;width:290px;height:290px"><div id="a4lp" style="left:0;top:0;width:290px;height:290px"></div><div class="abs a-lring" style="left:145px;top:145px"></div></div>`;
      this.v = K.viewer(q(root, "#a4v"));
      this.lp = K.viewer(q(root, "#a4lp"));
      const T = f => K.timeOf(c04, f);
      window.EVENTS.pen.push([T(216), T(238) + 1 / 30, "pen"]);
      window.EVENTS.taps.push(T(212));
      window.EVENTS.notes.push([6.4, "crash + whoosh vertical descendente (arrasto real para cima)"],
        [6.4, "'plic' de cada letra de 'Pra baixo' caindo (6,4–6,8), escala descendente"], [6.8, "caneta mais leve em 'Krebs'"]);
    },
    async render(t, root) {
      const f = K.frameAt(c04, t);
      const cam = cam04(t);
      const pl = await this.v.set(c04, f, cam);
      stickyPen(this.v, c04, f, pl, cam);
      const [, dy] = contentSpeed(c04, t);
      whipBlur(this.v.el, "aWhip4", 0, dy * pl.s * .4);
      // "E continua." sai em chicote vertical junto com o arrasto
      const old = q(root, "#a4old"); reset(old);
      old.style.opacity = 1; old.style.transform = `scale(1.04)`;
      K.whipOut(old, t, 6.4, [0, -1], 5 / 30, 1300);
      // "Pra baixo": letra por letra caindo do topo, 2 quiques
      q(root, "#a4t1").querySelectorAll(".a-letter").forEach((s, i) => {
        const a = 6.4 + i * .045, x = seg(t, a, a + .42);
        s.style.transform = `translateY(${-fall2(x) * 760}px) rotate(${fall2(x) * (hash(i) - .5) * 30}deg)`;
        s.style.opacity = t >= a ? 1 : 0;
      });
      // "também." + seta azul que quica em cada kick
      const t2 = q(root, "#a4t2");
      K.slam(t2, t, 7.2, { from: 1.5 });
      const arr = q(root, "#a4arr");
      arr.style.display = t >= 7.3 ? "" : "none";
      const ap = arr.querySelectorAll("path");
      K.draw(ap[0], t, 7.3, 7.3 + 4 / 30, x => x); K.draw(ap[1], t, 7.3 + 3 / 30, 7.3 + 6 / 30, x => x);
      const pb = ((t % BT) + BT) % BT;
      arr.style.transform = `translateY(${t >= 7.6 ? Math.sin(Math.min(1, pb / .28) * Math.PI) * 34 : 0}px)`;
      // lupa: toque REAL no azul
      const lw = q(root, "#a4lpw");
      if (K.vis(lw, t, 6.74, 7.16)) {
        const kin = back(seg(t, 6.74, 6.88), 1.6), kout = E.in3(seg(t, 7.04, 7.16));
        lw.style.transform = `scale(${Math.max(0, kin * (1 - kout))}) rotate(${(1 - kin) * -12}deg)`;
        await this.lp.set(c04, f, { zoom: 1.3 * IW / 290, focus: [1437, 352], cursors: false });
        loupeRing(lw, t, 6.8);
      }
    },
  });
  K.hit(6.4, .8); K.hit(7.2, .5);

  // ===================================================================================
  // S05 — 140% → 22%: A aula / INTEIRA / numa tela só.  (8,0–9,6)
  // ===================================================================================
  const PH_W = 1080, PH_H = PH_W * 2673 / 1233, PH_C = [(PH_W + 28) / 2, (PH_H + 28) / 2];
  const V287 = R1.views[287];
  const INT = "INTEIRA".split(""), INT_BACK = [false, true, false, true, true, false, false];
  /** Estado do celular no tempo t: escala, centro, rotações. */
  function phoneState(t) {
    const c4 = cam04(8.0, true), b = bounce(t);
    const p4 = place(VW, VH, c4);
    const S0 = c4.zoom;
    // centro do celular que reproduz exatamente o enquadramento da S04 (quadro limpo de emenda)
    const P0 = [p4.tx + S0 * (PH_C[0] - 14), p4.ty + S0 * (PH_C[1] - 14)];
    const S1 = .6, P1 = [548, 1300];
    const u = inOut(seg(t, 8.0, 8.8));
    let S = S0 * Math.pow(S1 / S0, u);
    let P = [lerp(P0[0], P1[0], u), lerp(P0[1], P1[1], u)];
    const ur = inOut(seg(t, 8.05, 8.8));
    let rx = 10 * ur, ry = -16 * ur, rz = 0;
    if (t >= 8.8) {
      const d = t - 8.8;
      rz = 4 * Math.exp(-d * 6) * Math.cos(d * 22);
      S *= 1 + .025 * seg(t, 8.8, 9.6);
      P = [P[0], P[1] - 14 * seg(t, 8.8, 9.6)];
      ry += 2.5 * Math.sin(d * 3);
    }
    S *= b;
    return { S, P, rx, ry, rz };
  }
  const phoneTf = st => `translate(${st.P[0]}px, ${st.P[1]}px) perspective(2000px) rotateX(${st.rx}deg) rotateY(${st.ry}deg) rotateZ(${st.rz}deg) scale(${st.S}) translate(${-PH_C[0]}px, ${-PH_C[1]}px)`;
  K.scene({
    id: "s05", start: 8.0, end: 9.6,
    setup(root) {
      injectOnce();
      const word = cls => `<div class="disp a-it a-red" style="font-size:212px;white-space:nowrap;letter-spacing:-.02em">${INT.map((c, i) => `<span class="a-letter" style="visibility:${(cls === "back") === INT_BACK[i] ? "visible" : "hidden"}">${c}</span>`).join("")}</div>`;
      root.innerHTML = `
        <div class="abs" style="inset:0;background:#F6F4EF"></div>
        <div class="abs dots" id="a5d1" style="inset:0"></div>
        <div class="abs dots" id="a5d2" style="inset:0"></div>
        <img class="abs" id="a5leak" src="${R1.dir}/f0287.jpg" style="left:0;top:0;width:${IW}px;height:${IH}px;transform-origin:0 0;clip-path:inset(470px 0 140px 0);mix-blend-mode:multiply;opacity:.35">
        <div class="abs" id="a5pet" style="inset:0"></div>
        <div class="abs" id="a5ib" style="left:92px;top:486px;transform-origin:50% 60%">${word("back")}</div>
        <div class="abs" id="a5ph" style="left:0;top:0;width:${PH_W + 28}px;height:${PH_H + 28}px;transform-origin:0 0"></div>
        <div class="abs" id="a5if" style="left:92px;top:486px;transform-origin:50% 60%">${word("front")}</div>
        <div class="a-pill" id="a5odo" style="left:84px;top:250px;height:112px;padding:0 28px;display:flex;align-items:center;transform-origin:0 50%">
          <div class="lab" id="a5num" style="font-size:86px;letter-spacing:.02em;display:flex;align-items:center;height:112px"></div>
        </div>
        <div class="abs" id="a5t1" style="left:84px;top:370px;transform-origin:0 50%"><div class="disp" style="font-size:112px">A aula</div></div>
        <div class="abs" id="a5t3" style="left:150px;top:690px;transform-origin:0 50%"><div class="sticker" style="transform:rotate(-2deg);padding:6px 30px 18px"><div class="disp" style="font-size:92px">numa tela só.</div></div></div>
        <div class="a-sweep" id="a5sw" style="left:0;top:610px"></div>`;
      const ph = K.phone(q(root, "#a5ph"), PH_W);
      this.ph = ph;
      ph.phone.style.transformStyle = "flat";
      this.v = K.viewer(ph.screen);
      // odômetro: colunas de dígitos
      const colH = 112;
      const digits = (n, blank) => `<div style="position:relative;height:${colH}px;overflow:hidden;width:.62em"><div class="a5c" data-n="${n}" style="position:absolute;left:0;top:0;width:100%">${(blank ? [" ", "1"] : "0123456789".split("").concat(["0"])).map(c => `<div style="height:${colH}px;line-height:${colH}px;text-align:center">${c}</div>`).join("")}</div></div>`;
      q(root, "#a5num").innerHTML = digits(2, true) + digits(1) + digits(0) + `<div style="margin-left:.06em">%</div>`;
      window.EVENTS.notes.push([8.0, "zoom-out: pitch descendo + reverb abrindo (8,0–8,8)"], [8.0, "tique granular a cada número do odômetro 140%→22% (8,0–8,8)"],
        [8.8, "IMPACTO + crash + sininhos das pétalas (INTEIRA)"], [9.2, "thump (numa tela só.)"], [9.4, "squeak de marca-texto começando (varredura amarela 9,4–9,6)"]);
    },
    async render(t, root) {
      const f = K.frameAt(c05, t), ff = frameF(c05, t);
      const st = phoneState(t);
      const tf = phoneTf(st);
      const phEl = q(root, "#a5ph");
      phEl.style.transform = tf;
      this.ph.phone.style.setProperty("--gl", `${lerp(100, -20, seg(t, 8.15, 9.1))}%`);
      const pl = await this.v.set(c05, f, { zoom: 1 });
      // grade de pontos presa ao mundo do app (view.json), com duas camadas cruzando para não "pular"
      const view = viewAt(ff);
      const k = pl.s * st.S;                                     // px da gravação -> px do vídeo
      const O = [st.P[0] + st.S * (14 + pl.tx + view[0] * pl.s - PH_C[0]), st.P[1] + st.S * (14 + pl.ty + view[1] * pl.s - PH_C[1])];
      let D = 40 * view[2] * k;
      while (D < 23) D *= 2; while (D >= 46) D /= 2;
      const d1 = q(root, "#a5d1"), d2 = q(root, "#a5d2");
      [[d1, D, (D - 23) / 23], [d2, 2 * D, 1]].forEach(([el, sz, op]) => {
        el.style.backgroundSize = `${sz}px ${sz}px`;
        el.style.backgroundPosition = `${O[0] - sz / 2}px ${O[1] - sz / 2}px`;
        el.style.opacity = op;
      });
      // cópia da nota vazando para fora do aparelho (quadro 287 = o mundo inteiro), presa ao zoom real
      const A = pl.s * view[2] / V287[2];
      const B = [14 + pl.tx + pl.s * (view[0] - V287[0] * view[2] / V287[2]), 14 + pl.ty + pl.s * (view[1] - V287[1] * view[2] / V287[2])];
      const leak = q(root, "#a5leak");
      leak.style.transform = `${tf} translate(${B[0]}px, ${B[1]}px) scale(${A})`;
      leak.style.opacity = .35 * seg(t, 8.05, 8.3);
      // pétalas atrás do celular no impacto
      q(root, "#a5pet").innerHTML = E.petals(t, 8.8, 540, 980, 46, 1.15);
      // odômetro 140% -> 22%, sincronizado com o view.json (termina exatamente no 22% do app)
      const vEnd = V287[2] / DEV * 100, vReal = view[2] / DEV * 100;
      const v = t < 8.0 + 1 / 30 ? 140 : 22 + (vReal - vEnd) * (140 - 22) / (140 - vEnd);
      const cols = q(root, "#a5num").querySelectorAll(".a5c");
      const ones = ((v % 10) + 10) % 10, tensRaw = Math.floor(v / 10);
      const roll = ones > 9 ? ones - 9 : 0;
      const tens = (tensRaw % 10) + roll, hund = Math.floor(v / 100) + (Math.floor(v / 10) % 10 === 9 ? roll : 0);
      const speed = Math.abs(contentSpeedZoom(ff));
      cols.forEach(c => {
        const n = +c.dataset.n;
        const p = n === 0 ? ones : n === 1 ? tens : Math.min(1, hund);
        c.style.transform = `translateY(${-p * 112}px)`;
        if (n === 2) c.parentElement.style.width = `${.62 * clamp(hund)}em`;
        c.style.filter = n === 0 && speed > .5 ? `url(#blurY${speed > 2 ? 40 : 20})` : "";
      });
      const odo = q(root, "#a5odo");
      K.slam(odo, t, 8.0, { from: 1.3 });
      odo.style.scale = `${1 + .1 * Math.exp(-Math.max(0, t - 8.8) * 12) * (t >= 8.8 ? 1 : 0)}`;
      // textos
      K.slam(q(root, "#a5t1"), t, 8.4);
      const ib = q(root, "#a5ib"), iF = q(root, "#a5if");
      [ib, iF].forEach(el => { K.slam(el, t, 8.8, { from: 1.6, rot: -6 }); el.style.translate = `0 ${-6 * seg(t, 8.8, 9.6)}px`; });
      const t3 = q(root, "#a5t3");
      K.slam(t3, t, 9.2, { from: 1.4, rot: 3 });
      // varredura amarela começa pela esquerda (9,4–9,6) — emenda com a S06
      const sw = q(root, "#a5sw");
      const ks = seg(t, 9.4, 9.6);
      sw.style.display = t >= 9.4 ? "" : "none";
      sw.style.transform = `rotate(-8deg) translateX(${lerp(-1800 - 150, -1800 + 420, Math.pow(ks, 1.6))}px)`;
    },
  });
  /** Velocidade do zoom do app (variação de escala por quadro, em % do app). */
  function contentSpeedZoom(ff) { return (viewAt(ff + 1)[2] - viewAt(ff)[2]) / DEV * 100 / 2.5; }
  K.hit(8.0, .35); K.hit(8.4, .4); K.hit(8.8, 1); K.hit(9.2, .45);
})();

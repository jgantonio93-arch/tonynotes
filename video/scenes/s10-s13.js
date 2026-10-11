// ===================================================================================
// BLOCO C — S10 (madrugada: celular deitado, relógio, mergulho no sol), S11 (drop 2:
// lua → modo noturno + 6 modelos de folha), S12 (pastas que saltam + #prova) e S13
// (PDF do slide). Gravações R3 (tela inicial) e R4 (editor noturno). MUNDO NOITE.
// Tudo é função pura de t: nada guarda estado entre quadros.
// ===================================================================================
(function () {
  const { clamp, seg, lerp, out3, out5, in3, inOut, back, hash } = E;
  const R3 = window.REC.r3, R4 = window.REC.r4;
  const I3W = R3.info.width, I3H = R3.info.height, I4W = R4.info.width;
  const A3 = R3.anchors;
  const PINK = "#F28A97", PAPER = "#F6F4EF";
  const q = (root, s) => root.querySelector(s);
  const show = (el, v) => { el.style.display = v ? "" : "none"; return v; };
  const on = (t, a, b) => t >= a && t < b;
  const expo = x => x >= 1 ? 1 : 1 - Math.pow(2, -10 * x);
  /** Zera estilos que os helpers do kit não resetam (os quadros saem fora de ordem). */
  const reset = (...els) => els.forEach(el => { el.style.translate = ""; el.style.filter = ""; el.style.clipPath = ""; el.style.opacity = ""; el.style.scale = ""; el.style.rotate = ""; });

  // ---- Batida -------------------------------------------------------------------------
  // drop (funk 3+3+2): kicks em .0 .3 .6 .8 1.1 1.4 de cada compasso de 1,6 s
  const KICKS = [0, .3, .6, .8, 1.1, 1.4];
  const kickEnv = t => { const p = ((t % 1.6) + 1.6) % 1.6; let d = 9; for (const o of KICKS) if (p >= o - 1e-6) d = Math.min(d, p - o); return Math.exp(-d * 16); };
  // break (20,8–24,0): só batimento na cabeça do compasso + 2 batimentos no silêncio
  const HEART = [20.8, 22.4, 23.65, 23.85];
  const heartEnv = t => E.hitEnv(t, HEART, 14);
  const beatEnv = t => t < 24 ? heartEnv(t) : kickEnv(t);
  const bounce = t => 1 + .025 * beatEnv(t);
  /** pulso em cada tempo (0,4 s) */
  const tick = t => Math.exp(-(((t % .4) + .4) % .4) * 10);

  // ---- Celular 3D: projeção idêntica à do CSS ----------------------------------------
  const PW = 600, PK = PW / I3W, PH = PW * 2673 / 1233;           // px do aparelho por px da gravação r3
  const PCX = (PW + 28) / 2, PCY = (PH + 28) / 2, DP = 2000;
  const phoneTf = p => `translate(${p.x}px,${p.y}px) perspective(${DP}px) rotateX(${p.rx || 0}deg) rotateY(${p.ry || 0}deg) rotateZ(${p.rz || 0}deg) scale(${p.S}) translate(${-PCX}px,${-PCY}px)`;
  /** ponto (lx, ly) do aparelho (canto do aro = 0,0) -> [x, y, w] no vídeo */
  function proj(p, lx, ly) {
    let x = (lx - PCX) * p.S, y = (ly - PCY) * p.S, z = 0, a, c, s;
    const r = Math.PI / 180;
    a = (p.rz || 0) * r; c = Math.cos(a); s = Math.sin(a); [x, y] = [x * c - y * s, x * s + y * c];
    a = (p.ry || 0) * r; c = Math.cos(a); s = Math.sin(a); [x, z] = [x * c + z * s, -x * s + z * c];
    a = (p.rx || 0) * r; c = Math.cos(a); s = Math.sin(a); [y, z] = [y * c - z * s, y * s + z * c];
    const w = DP / (DP - z);
    return [p.x + x * w, p.y + y * w, w];
  }
  /** px da gravação r3 -> px do aparelho */
  const scr = (ix, iy) => [14 + ix * PK, 14 + iy * PK];
  /** Ajusta p.x/p.y para que o ponto L do aparelho caia em B (vídeo). */
  const anchorPose = (p, L, B) => { const o = proj({ ...p, x: 0, y: 0 }, L[0], L[1]); return { ...p, x: B[0] - o[0], y: B[1] - o[1] }; };
  const mixPose = (a, b, k) => ({ x: lerp(a.x, b.x, k), y: lerp(a.y, b.y, k), S: a.S * Math.pow(b.S / a.S, k), rx: lerp(a.rx || 0, b.rx || 0, k), ry: lerp(a.ry || 0, b.ry || 0, k), rz: lerp(a.rz || 0, b.rz || 0, k) });

  // ---- Ícones vetoriais (sol e lua do botão de tema) -----------------------------------
  const SUN_SVG = (col, sw = 1.9) => `<svg viewBox="-12 -12 24 24" style="width:100%;height:100%;overflow:visible;display:block"><g fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round">
      <circle r="4.3"/>${Array.from({ length: 8 }, (_, i) => { const a = i * Math.PI / 4, c = Math.cos(a), s = Math.sin(a); return `<line x1="${(7.2 * c).toFixed(2)}" y1="${(7.2 * s).toFixed(2)}" x2="${(9.9 * c).toFixed(2)}" y2="${(9.9 * s).toFixed(2)}"/>`; }).join("")}</g></svg>`;
  const MOON_D = "M12,3c-4.97,0-9,4.03-9,9s4.03,9,9,9,9-4.03,9-9c0-.46-.04-.92-.1-1.36-.98,1.37-2.58,2.26-4.4,2.26-2.98,0-5.4-2.42-5.4-5.4,0-1.81.89-3.42,2.26-4.4-.44-.06-.9-.1-1.36-.1z";
  const MOON_SVG = col => `<svg viewBox="0 0 24 24" style="width:100%;height:100%;overflow:visible;display:block"><path d="${MOON_D}" fill="none" stroke="${col}" stroke-width="1.9" stroke-linejoin="round"/></svg>`;
  const MOON_C = [995, 173];                  // centro da lua real (px da gravação r3)
  const MOON_BOX = 62.7;                      // caixa 24×24 do ícone em px da gravação (lua ≈ 47 px)
  const S11 = .95;                            // escala do aparelho na revelação do noturno
  const MOON1 = MOON_BOX * PK * S11;          // lua na escala 1 (px de vídeo)
  const MOON_BIG = MOON1 * 30;                // lua "que ocupa a tela"
  const MOON_C0 = [540, 900];

  // ---- CSS + filtros ------------------------------------------------------------------
  const NOISE = "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='240' height='240'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 1.4 0'/></filter><rect width='240' height='240' filter='url(%23n)'/></svg>";
  function injectOnce() {
    if (document.getElementById("c-style")) return;
    const st = document.createElement("style");
    st.id = "c-style";
    st.textContent = `
      .c-full { position: absolute; left: 0; top: 0; width: 1080px; height: 1920px; }
      .c-night { background: linear-gradient(180deg, #0F0C0D 0%, #160A0D 48%, #3A0A14 100%); }
      .c-redl { background: radial-gradient(95% 42% at 50% 106%, rgba(194,37,59,.35), rgba(194,37,59,.14) 55%, rgba(194,37,59,0) 82%); transform-origin: 50% 100%; }
      .c-grain { opacity: .05; mix-blend-mode: screen; background-image: url("${NOISE}"); }
      .c-white { color: #F6F4EF; text-shadow: 0 6px 0 rgba(0,0,0,.4), 0 0 40px rgba(0,0,0,.45); }
      .c-pink { color: #F28A97; }
      .c-it { font-style: italic; font-synthesis: style; }
      .c-nw { white-space: nowrap; }
      .c-mask { overflow: hidden; display: block; padding: 0 .1em .08em; margin: 0 -.1em; }
      .c-mask > div { will-change: transform; }
      .c-clock { position: absolute; display: flex; align-items: center; font-family: Onest; font-weight: 800; font-size: 200px; font-variant-numeric: tabular-nums; color: rgba(242,138,151,.78); transform-origin: 50% 50%; }
      .c-cell { position: relative; width: .64em; height: 1.1em; margin: 0 .035em; }
      .c-h { position: absolute; left: 0; width: 100%; height: 50%; overflow: hidden; background: linear-gradient(180deg, #24161A, #1A1013); backface-visibility: hidden; box-shadow: 0 0 0 2px rgba(242,138,151,.08) inset; }
      .c-h b { position: absolute; left: 0; width: 100%; height: 1.1em; line-height: 1.1em; text-align: center; font-weight: 800; }
      .c-t { top: 0; border-radius: .09em .09em 0 0; transform-origin: 50% 100%; }
      .c-b { top: 50%; border-radius: 0 0 .09em .09em; transform-origin: 50% 0; }
      .c-t b { top: 0; } .c-b b { top: -.55em; }
      .c-b { background: linear-gradient(180deg, #1C1114, #150D10); }
      .c-cell::after { content: ""; position: absolute; left: -2px; right: -2px; top: calc(50% - 3px); height: 6px; background: #0F0C0D; z-index: 6; }
      .c-colon { width: .28em; text-align: center; line-height: 1; margin-top: -.08em; }
      .c-bloom { position: absolute; border-radius: 64px; background: #fff; filter: blur(60px); }
      .c-ring { position: absolute; border-radius: 50%; border: 10px solid #fff; box-shadow: 0 0 0 3px rgba(0,0,0,.25), 0 0 30px rgba(255,255,255,.6); }
      .c-pill { display: inline-block; background: rgba(20,14,16,.94); border-radius: 999px; padding: 20px 48px 22px; font-family: Onest; font-weight: 800; font-size: 60px; line-height: 1; letter-spacing: .1em; text-transform: uppercase; color: #F28A97; box-shadow: 6px 6px 0 rgba(0,0,0,.5), 0 0 0 3px rgba(242,138,151,.35) inset; white-space: nowrap; transform-origin: 50% 50%; }
      .c-letter { display: inline-block; transform-origin: 50% 80%; white-space: pre; }
      .c-spr { position: absolute; left: 0; top: 0; overflow: hidden; transform-origin: 50% 50%; border-radius: 46px; }
      .c-spr img { position: absolute; display: block; }
      .c-spr .c-lab { position: absolute; font-family: Onest; font-weight: 700; color: #ECE7DE; white-space: nowrap; }
      .c-stk { background: #F6F4EF; color: #1B1B1F; box-shadow: 8px 8px 0 rgba(0,0,0,.45); padding: 4px 30px 16px; display: inline-block; white-space: nowrap; }
      .c-dark { background: rgba(20,14,16,.92); box-shadow: 6px 6px 0 rgba(0,0,0,.45); padding: 6px 30px 20px; display: inline-block; white-space: nowrap; }
      .c-hlo { --hlc: rgba(122,106,18,.95); }
    `;
    document.head.appendChild(st);
    const sv = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    sv.setAttribute("width", "0"); sv.setAttribute("height", "0"); sv.style.position = "absolute";
    sv.innerHTML = `<defs>
      <filter id="cBrush" x="-20%" y="-20%" width="140%" height="140%">
        <feTurbulence type="fractalNoise" baseFrequency=".022 .06" numOctaves="3" seed="7" result="n"/>
        <feDisplacementMap in="SourceGraphic" in2="n" scale="30" xChannelSelector="R" yChannelSelector="G"/>
      </filter>
      <filter id="cWhip12" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur id="cWhip12B" stdDeviation="0 0"/></filter>
    </defs>`;
    document.body.appendChild(sv);
  }

  // ---- Mundo noite (fundo vivo) -------------------------------------------------------
  const NIGHT = id => `<div class="c-full c-night"></div><div class="c-full dots night" id="${id}d"></div><div class="c-full c-redl" id="${id}l"></div>`;
  function nightFx(root, id, t, ox = 0, oy = 0) {
    const env = beatEnv(t);
    const l = q(root, `#${id}l`);
    l.style.opacity = .88 + .12 * env;
    l.style.transform = `scaleY(${1 + .06 * env})`;
    q(root, `#${id}d`).style.backgroundPosition = `${ox}px ${oy}px`;
  }
  const grainFx = (el, t) => { const fi = Math.round(t * 30); el.style.backgroundPosition = `${Math.floor(hash(fi) * 240)}px ${Math.floor(hash(fi + 7) * 240)}px`; };

  /** Celular com visor da gravação; devolve { box, ph, v }. */
  function makePhone(box, bloom = false) {
    const ph = K.phone(box, PW);
    if (bloom) box.insertAdjacentHTML("afterbegin", `<div class="c-bloom" style="left:14px;top:14px;width:${PW}px;height:${PH}px"></div>`);
    const v = K.viewer(ph.screen);
    v.el.style.background = "#151413";
    return { box, ph, v };
  }

  // =====================================================================================
  // S10 — madrugada (20,8–24,0)
  // =====================================================================================
  // (a partir do quadro 38 o app já abre a bandeja "Tema"; o vídeo segura o quadro 37, dedo no sol)
  const c10 = K.clip("r3", [[20.8, 23.2, 0, 35], [23.2, 23.27, 36, 37], [23.27, 24.0, 37, 37]]);
  const CLOCK = [[20.8, "01:12"], [21.2, "01:58"], [21.6, "02:41"], [22.0, "03:12"], [22.4, "03:58"], [22.8, "04:31"], [23.2, "04:52"]];
  const SUN = scr(996, 172);                 // botão de tema (px do aparelho)
  const SUN_T = [905, 800];                  // onde o botão para depois do mergulho
  const SUN_BOX = 62.7 * PK;                 // caixa do ícone do sol (px do aparelho)
  function lie10(t) {
    const g = seg(t, 20.8, 22.8);
    return {
      x: 540 + 12 * Math.sin(t * 1.4), y: 1300 - 10 * Math.sin(t * 1.1),
      S: .96 * (1 + .05 * g) * (1 + .012 * heartEnv(t)), rx: 35 - 3 * g, ry: 2 * Math.sin(t * .8), rz: -6 + 1.6 * Math.sin(t * .9),
    };
  }
  function pose10(t) {
    const lie = lie10(t);
    const k = inOut(seg(t, 22.8, 23.2));
    if (k <= 0) return lie;
    const lie0 = lie10(22.8);
    const B0 = proj(lie0, SUN[0], SUN[1]);
    const S1 = 2.2 * (1 + .06 * seg(t, 23.2, 23.6));
    const flat = { S: S1, rx: 0, ry: 0, rz: 0, x: 0, y: 0 };
    const p = mixPose({ ...lie0 }, flat, k);
    p.x = 0; p.y = 0;
    const B = [lerp(B0[0], SUN_T[0], k), lerp(B0[1], SUN_T[1], k)];
    return anchorPose(p, SUN, B);
  }
  function clockAt(t) {
    let cur = CLOCK[0][1], prev = CLOCK[0][1], at = -9;
    for (const [tb, s] of CLOCK) if (t >= tb) { prev = cur; cur = s; at = tb; }
    return { cur, prev, at };
  }
  const CELL = `<div class="c-cell"><div class="c-h c-t"><b></b></div><div class="c-h c-b"><b></b></div><div class="c-h c-t"><b></b></div><div class="c-h c-b"><b></b></div></div>`;

  K.scene({
    id: "s10", start: 20.8, end: 24.0,
    setup(root) {
      injectOnce();
      root.innerHTML = `
        ${NIGHT("c10")}
        <div class="c-full" id="c10refl" style="background:radial-gradient(40% 26% at 50% 50%, rgba(120,160,255,.28), rgba(120,160,255,.08) 55%, rgba(120,160,255,0) 80%);mix-blend-mode:screen"></div>
        <div class="abs" id="c10ph" style="left:0;top:0;width:${PW + 28}px;height:${PH + 28}px;transform-origin:0 0"></div>
        <div class="c-full" id="c10sunbg" style="background:#EFEDE8"></div>
        <div class="abs" id="c10sun" style="left:0;top:0;width:100px;height:100px;transform-origin:50% 50%">${SUN_SVG("#5E5953")}</div>
        <div class="c-full" id="c10over" style="background:radial-gradient(circle at 50% 45%, #fff 0%, rgba(255,255,255,.85) 40%, rgba(255,250,235,.4) 75%)"></div>
        <div class="c-clock" id="c10clk" style="left:0;top:248px;width:1080px;justify-content:center">${CELL}${CELL}<div class="c-colon">:</div>${CELL}${CELL}</div>
        <div class="abs" style="left:84px;top:500px"><div class="c-mask"><div class="disp c-white c-nw" id="c10t1" style="font-size:112px">Virando a noite</div></div></div>
        <div class="abs" id="c10t2" style="left:96px;top:622px;transform-origin:0 60%"><div class="disp c-pink c-it c-nw" style="font-size:132px;text-shadow:0 6px 0 rgba(0,0,0,.45)">estudando?</div></div>
        <div class="abs hand c-pink c-nw" id="c10soc" style="left:90px;top:520px;font-size:100px;line-height:1;transform:rotate(-5deg);transform-origin:0 50%;text-shadow:0 4px 0 rgba(0,0,0,.5)">socorro, meus olhos</div>
        <svg class="abs" id="c10arr" style="left:0;top:0;width:1080px;height:1920px;overflow:visible" viewBox="0 0 1080 1920">
          <path pathLength="1" d="M 772 600 C 850 650, 836 766, 745 830" fill="none" stroke="${PINK}" stroke-width="10" stroke-linecap="round"/>
          <path pathLength="1" d="M 789 826 L 745 830 L 757 788" fill="none" stroke="${PINK}" stroke-width="10" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <div class="abs" id="c10tq" style="left:84px;top:1130px;transform-origin:0 50%"><div class="c-dark" style="transform:rotate(-2deg)"><span class="disp" style="font-size:124px;color:${PAPER}">Um toque e…</span></div></div>
        <div class="c-full" id="c10blk" style="background:#000">
          <div class="abs" id="c10moon" style="left:${MOON_C0[0] - MOON_BIG / 2}px;top:${MOON_C0[1] - MOON_BIG / 2}px;width:${MOON_BIG}px;height:${MOON_BIG}px;opacity:.2">${MOON_SVG(PAPER)}</div>
          <div class="abs" id="c10resp" style="left:0;top:${MOON_C0[1] + 20}px;width:1080px;text-align:center"><span class="disp c-it" style="display:inline-block;font-size:64px;color:rgba(246,244,239,.8)">Respira.</span></div>
        </div>
        <div class="c-full c-grain" id="c10gr"></div>`;
      const P = makePhone(q(root, "#c10ph"), true);
      Object.assign(this, P);
      this.v.ann.innerHTML = `<div class="c-ring" id="c10ring" style="left:0;top:0;width:200px;height:200px;border-color:#E8404F"></div>`;
      window.EVENTS.taps.push(23.2);
      window.EVENTS.notes.push([20.8, "BREAK: a bateria sai; pad de Fm + zumbido de luz forte (120 Hz) baixo"],
        ...CLOCK.map(([tb]) => [tb, "tique-taque de woodblock + flip mecânico dos dígitos (3 quadros)"]),
        [20.8, "batimento (kick) na cabeça do compasso"], [22.4, "batimento + riser até 23,6"], [23.2, "clique do toque no sol"],
        [23.4, "brilho/whoosh do sol ocupando a tela (23,4–23,6)"], [23.6, "SILÊNCIO; batimentos cardíacos em 23,65 e 23,85"]);
    },
    async render(t, root) {
      const f = K.frameAt(c10, t);
      const P = pose10(t);
      nightFx(root, "c10", t, (P.x - 540) * .25, (P.y - 1300) * .25);
      grainFx(q(root, "#c10gr"), t);
      // celular deitado (bloom + reflexo azulado), mergulho até o sol
      this.box.style.transform = phoneTf(P);
      this.ph.phone.style.setProperty("--gl", `${lerp(110, -10, seg(t, 20.8, 23.4))}%`);
      await this.v.set(c10, f, { zoom: 1, cursors: t < 23.36 });
      const pulse = tick(t) * (t >= 22.4 ? 1 : .4);
      this.v.el.style.filter = `brightness(${1.08 + .3 * pulse}) contrast(.96)`;
      const bloom = q(root, ".c-bloom");
      bloom.style.opacity = (.55 + .3 * pulse + .15 * heartEnv(t)) * (1 - .6 * seg(t, 22.8, 23.2));
      const refl = q(root, "#c10refl"), cpos = proj(P, PCX, PCY);
      refl.style.transform = `translate(${cpos[0] - 540}px, ${cpos[1] - 960 + 120}px) scale(${1.2 + .3 * pulse})`;
      refl.style.opacity = 1 - seg(t, 22.8, 23.1);
      // anel do toque no sol
      const ring = q(root, "#c10ring"), kr = seg(t, 23.2, 23.2 + .3);
      if (show(ring, kr > 0 && kr < 1)) {
        const d = lerp(80, 300, out3(kr));
        Object.assign(ring.style, { left: `${996 - d / 2}px`, top: `${172 - d / 2}px`, width: `${d}px`, height: `${d}px`, opacity: 1 - in3(kr) });
      }
      // sol ocupa a tela e gira 45° (23,4–23,6)
      const sp = proj(P, SUN[0], SUN[1]);
      const sunbg = q(root, "#c10sunbg"), sun = q(root, "#c10sun"), over = q(root, "#c10over");
      const ks = seg(t, 23.4, 23.6);
      if (show(sunbg, on(t, 23.4, 23.6))) sunbg.style.clipPath = `circle(${lerp(30, 2300, out3(seg(t, 23.4, 23.53)))}px at ${sp[0]}px ${sp[1]}px)`;
      if (show(sun, on(t, 23.4, 23.6))) {
        const base = SUN_BOX * P.S;
        const sc = (base / 100) * Math.pow(42, Math.pow(ks, .8));
        const kc = out3(ks), cx = lerp(sp[0], 560, kc), cy = lerp(sp[1], 940, kc);
        sun.style.transform = `translate(${cx - 50}px, ${cy - 50}px) scale(${sc}) rotate(${45 * inOut(ks)}deg)`;
      }
      if (show(over, on(t, 23.45, 23.6))) over.style.opacity = .85 * in3(seg(t, 23.45, 23.6));
      // relógio gigante com flip mecânico a cada tempo
      const clk = q(root, "#c10clk");
      if (show(clk, t < 23.6)) {
        K.slam(clk, t, 20.8, { from: 1.25 });
        clk.style.scale = `${1 + .03 * tick(t) + .03 * heartEnv(t)}`;
        const { cur, prev, at } = clockAt(t);
        const k = seg(t, at, at + 3 / 30);
        clk.querySelectorAll(".c-cell").forEach((cell, i) => {
          const ci = [0, 1, 3, 4][i], N = cur[ci], O = prev[ci];
          const [st, sb, fa, fb] = cell.children;
          st.firstChild.textContent = N;
          const flipping = N !== O && k < 1;
          sb.firstChild.textContent = flipping ? O : N;
          show(fa, flipping && k < .5); show(fb, flipping && k >= .5);
          if (flipping) {
            fa.firstChild.textContent = O; fb.firstChild.textContent = N;
            fa.style.transform = `perspective(500px) rotateX(${-180 * k}deg)`;
            fb.style.transform = `perspective(500px) rotateX(${90 - 180 * (k - .5)}deg)`;
            fa.style.filter = "brightness(1.25)"; fb.style.filter = "brightness(.7)";
          }
        });
      }
      // textos
      const t1 = q(root, "#c10t1"), t2 = q(root, "#c10t2"), soc = q(root, "#c10soc"), arr = q(root, "#c10arr"), tq = q(root, "#c10tq");
      reset(t1, t2, soc, tq);
      if (show(t1, on(t, 21.2, 22.6))) { K.rise(t1, t, 21.2); K.whipOut(t1, t, 22.4, [-1, 0], 5 / 30, 1300); }
      if (show(t2, on(t, 22.0, 22.6))) { K.slam(t2, t, 22.0, { from: 1.5, rot: -4 }); K.whipOut(t2, t, 22.43, [-1, 0], 5 / 30, 1300); }
      if (show(soc, on(t, 22.4, 23.0))) {
        soc.style.opacity = 1; K.wipe(soc, t, 22.4, 22.4 + 6 / 30);
        soc.style.translate = `${Math.sin(t * 61) * 3}px ${Math.cos(t * 47) * 3}px`;
        K.whipOut(soc, t, 22.8, [-1, 0], 5 / 30, 1300);
      }
      if (show(arr, on(t, 22.55, 22.8))) { const ps = arr.querySelectorAll("path"); K.draw(ps[0], t, 22.55, 22.55 + 4 / 30, x => x); K.draw(ps[1], t, 22.55 + 4 / 30, 22.55 + 6 / 30, x => x); }
      if (show(tq, on(t, 22.8, 23.6))) { K.slam(tq, t, 22.8, { from: 1.5, rot: -3 }); tq.style.translate = `0 ${-30 * seg(t, 22.8, 23.6)}px`; }
      // 23,6–24,0: preto absoluto, lua apagada, "Respira."
      const blk = q(root, "#c10blk");
      if (show(blk, t >= 23.6)) {
        const hb = E.hitEnv(t, [23.65, 23.85], 9);
        q(root, "#c10resp").style.transform = `scale(${1 + .04 * hb})`;
        q(root, "#c10moon").style.transform = `scale(${1 + .015 * hb}) rotate(${-4 + 4 * seg(t, 23.6, 24.0)}deg)`;
      }
    },
  });
  K.hit(20.8, .55); K.hit(22.4, .3); K.hit(23.2, .3); K.hit(23.6, .2);

  // =====================================================================================
  // S11 — DROP 2: lua → modo noturno (24,0–24,8) + 6 modelos de folha (24,8–27,2)
  // =====================================================================================
  const c11a = K.clip("r3", [[24.0, 24.8, 48, 63]]);
  const MODELS = [["Pautado", 0], ["Quadriculado", 6], ["Universitário", 12], ["Cornell", 18], ["Partitura", 24], ["Semanal", 30]];
  const T11B = 24.8;
  const c11b = K.clip("r4", MODELS.map(([, fr], i) => [T11B + i * .4, T11B + (i + 1) * .4, fr + 2, fr + 2]));
  const MOON_L = scr(MOON_C[0], MOON_C[1]);
  function pose11(t) {
    const k = out3(seg(t, 24.0, 24.8));
    return { x: 556, y: 1300 - 14 * k, S: S11 * (1 + .03 * k), rx: 6, ry: lerp(20, -8, k), rz: lerp(-2.5, 0, k) };
  }
  const MODO = "Modo".split(""), NOT = "noturno.".split("");
  K.scene({
    id: "s11", start: 24.0, end: 27.2,
    setup(root) {
      injectOnce();
      const L = (arr, off) => arr.map((c, i) => `<span class="c-letter" data-i="${off + i}">${c}</span>`).join("");
      root.innerHTML = `
        <div class="c-full" style="background:#000"></div>
        <div class="c-full" id="c11a">
          <div class="c-full" id="c11rev">
            ${NIGHT("c11")}
            <div class="abs" id="c11ph" style="left:0;top:0;width:${PW + 28}px;height:${PH + 28}px;transform-origin:0 0"></div>
          </div>
          <div class="abs" id="c11moon" style="left:0;top:0;width:100px;height:100px;transform-origin:50% 50%">${MOON_SVG("#CFC8BE")}</div>
          <div class="c-full" id="c11pet"></div>
          <div class="abs disp c-white c-nw" id="c11w1" style="left:84px;top:246px;font-size:150px">${L(MODO, 0)}</div>
          <div class="abs disp c-pink c-it c-nw" id="c11w2" style="left:84px;top:396px;font-size:150px;text-shadow:0 6px 0 rgba(0,0,0,.45)">${L(NOT, MODO.length)}</div>
          <div class="abs" style="left:92px;top:576px"><div class="c-mask"><div class="c-pink c-nw" id="c11sub" style="font-family:Onest;font-weight:600;font-size:54px;letter-spacing:.01em">pra virar a noite.</div></div></div>
        </div>
        <div class="c-full" id="c11b">
          <div class="c-full" style="background:#151413"></div>
          <div id="c11v" style="left:0;top:0;width:1080px;height:1920px"></div>
          <div class="c-full" style="background:linear-gradient(180deg, rgba(15,12,13,.75) 0%, rgba(15,12,13,0) 16%, rgba(15,12,13,0) 84%, rgba(15,12,13,.55) 100%)"></div>
          <div class="abs" id="c11hd" style="left:84px;top:262px;transform-origin:0 50%"><div class="disp c-white c-nw" style="font-size:108px">Prefere folhas?</div></div>
          <div class="abs" style="left:0;top:1376px;width:1080px;text-align:center;perspective:900px">
            <div class="c-pill" id="c11pill"><span id="c11lab"></span></div>
          </div>
        </div>
        <div class="c-full c-grain" id="c11gr"></div>`;
      const P = makePhone(q(root, "#c11ph"));
      this.box = P.box; this.ph = P.ph; this.v = P.v;
      this.vb = K.viewer(q(root, "#c11v"));
      this.vb.el.style.background = "#151413";
      window.EVENTS.notes.push([24.0, "DROP 2: IMPACTO + crash + stab de luz acendendo + whoosh da lua encolhendo"],
        [24.0, "tiques das letras de 'Modo noturno.' (24,0–24,4)"], [24.1, "pétalas/sininhos na revelação circular"],
        ...MODELS.map((m, i) => [T11B + i * .4, `card flip de papel (${m[0]}), meio tom acima do anterior`]));
    },
    async render(t, root) {
      grainFx(q(root, "#c11gr"), t);
      const A = show(q(root, "#c11a"), t < T11B), B = show(q(root, "#c11b"), t >= T11B);
      if (A) {
        const P = pose11(t);
        nightFx(root, "c11", t, (P.ry) * 3, 0);
        this.box.style.transform = phoneTf(P);
        this.ph.phone.style.setProperty("--gl", `${lerp(120, -20, seg(t, 24.0, 24.8))}%`);
        await this.v.set(c11a, K.frameAt(c11a, t), { zoom: 1 });
        // a lua que ocupa a tela encolhe (30 -> 1 em 7 quadros) até o botão real
        const tgt = proj(P, MOON_L[0], MOON_L[1]);
        const km = out3(seg(t, 24.0, 24.0 + 7 / 30)), kp = expo(seg(t, 24.0, 24.0 + 7 / 30));
        const mc = [lerp(MOON_C0[0], tgt[0], kp), lerp(MOON_C0[1], tgt[1], kp)];
        const ms = lerp(30, 1, km) * MOON1 * lerp(1, tgt[2], km);
        const moon = q(root, "#c11moon");
        if (show(moon, t < 24.3)) {
          moon.style.transform = `translate(${mc[0] - 50}px, ${mc[1] - 50}px) scale(${ms / 100})`;
          moon.style.opacity = t < 24.24 ? 1 : 1 - seg(t, 24.24, 24.3);
          moon.style.filter = `drop-shadow(0 0 ${lerp(60, 6, km)}px rgba(246,244,239,.7))`;
        }
        // revelação circular a partir do botão
        const rv = q(root, "#c11rev"), kr = out3(seg(t, 24.1, 24.42));
        rv.style.clipPath = kr >= 1 ? "" : `circle(${(kr * 2300).toFixed(1)}px at ${tgt[0]}px ${tgt[1]}px)`;
        rv.style.visibility = kr > 0 ? "" : "hidden";
        q(root, "#c11pet").innerHTML = E.petals(t, 24.12, tgt[0], tgt[1], 44, 1.05);
        // "Modo noturno." letra a letra (1 quadro entre letras) e "pra virar a noite."
        root.querySelectorAll("#c11a .c-letter").forEach(s => K.slam(s, t, 24.0 + (+s.dataset.i) * 1 / 30, { from: 1.9, rot: (+s.dataset.i % 2 ? 6 : -6) }));
        K.rise(q(root, "#c11sub"), t, 24.4);
        ["#c11w1", "#c11w2"].forEach(id => q(root, id).style.transform = `scale(${1 + .02 * kickEnv(t)})`);
      }
      if (B) {
        const i = Math.min(5, Math.floor((t - T11B) / .4 + 1e-6)), tb = T11B + i * .4;
        const f = K.frameAt(c11b, t);
        const kz = seg(t, tb, tb + .4);
        const odd = i % 2 === 1;
        const zoom = 1.047 * (1 + .02 * kz + .03 * seg(t, T11B, 27.2)) * bounce(t);
        await this.vb.set(c11b, f, { zoom, focus: [694, 1090], ax: .5, ay: .459, cursors: false });
        // corte seco a cada tempo: rotação ±2° e escala 1,00/1,04 alternadas
        const cut = 1 - out3(seg(t, tb, tb + 3 / 30));
        this.vb.el.style.transform = `rotate(${(odd ? 2 : -2) + (odd ? -1 : 1) * 1.2 * cut}deg) scale(${1.09 * (odd ? 1.04 : 1) * (1 + .02 * cut)})`;
        // cabeçalho fixo + pílula com o modelo (flip de cartão em 3 quadros)
        const hd = q(root, "#c11hd");
        K.slam(hd, t, T11B, { from: 1.4 });
        hd.style.scale = `${1 + .015 * kickEnv(t)}`;
        q(root, "#c11lab").textContent = MODELS[i][0];
        const pill = q(root, "#c11pill"), kf = seg(t, tb, tb + 3 / 30);
        pill.style.transform = `rotateX(${lerp(-90, 0, out3(kf))}deg) scale(${1 + .12 * (1 - out3(seg(t, tb, tb + 7 / 30)))})`;
        pill.style.opacity = t >= tb ? 1 : 0;
      }
    },
  });
  K.hit(24.0, 1); K.flash(24.0, "#FFC9D1", .4, 1); K.flash(24.13, PINK, .25, 1);
  MODELS.forEach((m, i) => { const tb = T11B + i * .4; K.flash(tb, PINK, .15, 1); K.hit(tb, i === 0 ? .5 : .28); });

  // =====================================================================================
  // S12 — pastas saltam + etiqueta #prova (27,2–28,8; mantém até 29,1 por baixo do traço)
  // =====================================================================================
  const c12 = K.clip("r3", [[27.2, 27.86, 64, 84], [27.86, 28.0, 84, 88], [28.0, 28.2, 88, 91], [28.2, 28.3, 91, 94], [28.3, 29.1, 94, 108]]);
  const OFF_END = A3.Biologia[1] - A3.BiologiaRola[1];        // rolagem total (px da gravação)
  const FY0 = R3.touches[64].p[0][1], FY1 = R3.touches[84].p[0][1];
  const rollOff = f => f <= 64 ? 0 : f >= 84 ? OFF_END : (FY0 - R3.touches[f].p[0][1]) / (FY0 - FY1) * OFF_END;
  const CHIP = scr(150, 773), CHIP_T = [372, 900];
  const SEARCH = scr(640, 560);
  const RES = scr(555, 760), RES_T = [540, 1062];          // depois do filtro: busca + notas achadas
  function base12(t) {
    const g = seg(t, 27.2, 28.0), kin = seg(t, 27.2, 27.2 + 7 / 30);
    return {
      x: 640 + 14 * Math.sin(t * 2.2), y: 1270 + 10 * Math.sin(t * 1.7),
      S: .84 * (1 + .04 * g) * lerp(.86, 1, back(kin, 2)), rx: 8, ry: 20 - 6 * g + 16 * (1 - out3(kin)), rz: -1.5,
    };
  }
  function pose12(t) {
    const b = base12(t);
    const k = out5(seg(t, 28.0, 28.2));
    if (k <= 0) return b;
    const S1 = 2.31 * (1 + .03 * seg(t, 28.2, 28.3));
    const p = mixPose(b, { S: S1, rx: 0, ry: 0, rz: 0, x: 0, y: 0 }, k);
    const B0 = proj(b, CHIP[0], CHIP[1]);
    const pZ = anchorPose(p, CHIP, [lerp(B0[0], CHIP_T[0], k), lerp(B0[1], CHIP_T[1], k)]);
    const k2 = inOut(seg(t, 28.3, 28.55));
    if (k2 <= 0) return pZ;
    // recua para mostrar a busca filtrada e as notas achadas
    const BZ = proj(pZ, RES[0], RES[1]);
    const pE = mixPose(pZ, { ...pZ, S: (.9 / PK) * (1 + .04 * seg(t, 28.3, 29.1)) }, k2);
    return anchorPose(pE, RES, [lerp(BZ[0], RES_T[0], k2), lerp(BZ[1], RES_T[1], k2)]);
  }
  // sprites das pastas (recortados de r3/f0084 pelos anchors). Química e Redação ficam abaixo
  // da área gravada: viram o cartão da Física com a cor trocada e o nome reescrito.
  const FIS = A3.FísicaRola, BIO = A3.BiologiaRola;
  const CARDS = [
    { name: "Biologia", at: 27.2, a: A3.Biologia, crop: BIO, end: [258, 1452], rot: -14, sc: .5 },
    { name: "Química", at: 27.4, a: A3.Química, crop: FIS, hue: "hue-rotate(196deg) saturate(1.25) brightness(1.55)", end: [468, 1520], rot: -5, sc: .5 },
    { name: "Física", at: 27.6, a: A3.Física, crop: FIS, end: [672, 1528], rot: 5, sc: .5 },
    { name: "Redação", at: 27.76, a: A3.Redação, crop: FIS, hue: "hue-rotate(62deg) saturate(1.15) brightness(1.2)", end: [880, 1462], rot: 14, sc: .5 },
  ];
  const sprHTML = (c, i) => {
    const [cx, cy, w, h] = c.crop, H = c.a[3];
    const img = `<img src="${R3.dir}/f0084.jpg" style="left:${-cx}px;top:${-cy}px;width:${I3W}px;height:${I3H}px">`;
    const patch = c.hue ? `<div style="position:absolute;left:36px;top:192px;width:300px;height:66px;background:rgb(29,41,63)"></div>` : "";
    return `<div class="c-spr" id="c12s${i}" style="width:${w}px;height:${H}px;background:${c.name === "Biologia" ? "rgb(65,31,32)" : "rgb(29,41,63)"}">
        <div style="position:absolute;inset:0;${c.hue ? `filter:${c.hue}` : ""}"><div style="position:absolute;inset:0;background:rgb(29,41,63);${c.name === "Biologia" ? "display:none" : ""}"></div>${img}${patch}</div>
        ${c.hue ? `<div class="c-lab" style="left:45px;top:198px;font-size:46px">${c.name}</div>` : ""}
      </div>`;
  };
  const FLY = .24;                                            // voo de cada pasta (s)
  function sprite(c, t) {
    if (t < c.at) return null;
    const k = seg(t, c.at, c.at + FLY);
    const P0 = base12(c.at), f0 = K.frameAt(c12, c.at);
    const [ax, ay, aw, ah] = c.a;
    const iy = Math.min(I3H - ah * .28, ay + ah / 2 - rollOff(f0));
    const L = scr(ax + aw / 2, iy);
    const s0 = proj(P0, L[0], L[1]);
    const e = out3(k), z = Math.sin(Math.PI * Math.min(1, k * 1.05));
    const x = lerp(s0[0], c.end[0], e), y = lerp(s0[1], c.end[1], e) - 380 * z;
    const scl = lerp(PK * P0.S * s0[2], c.sc, out3(k)) * (1 + .62 * z) * (1 + .07 * Math.sin(Math.PI * seg(t, c.at + FLY, c.at + FLY + .14)));
    const rot = lerp(-18, c.rot, out3(k)) + 24 * z * (c.rot < 0 ? -1 : 1);
    return { x, y, scl, rot, z, k };
  }
  K.scene({
    id: "s12", start: 27.2, end: 29.1,
    setup(root) {
      injectOnce();
      root.innerHTML = `
        ${NIGHT("c12")}
        <div class="abs" id="c12ph" style="left:0;top:0;width:${PW + 28}px;height:${PH + 28}px;transform-origin:0 0"></div>
        <div class="c-full" id="c12cards">${CARDS.map(sprHTML).join("")}</div>
        <div class="abs" id="c12t1" style="left:84px;top:250px;transform-origin:0 60%"><div class="disp c-white c-nw" style="font-size:136px">Uma pasta</div></div>
        <div class="abs" id="c12t2" style="left:84px;top:400px;transform-origin:0 50%">
          <div class="disp c-white c-nw" style="font-size:116px">pra cada</div>
          <div class="disp c-pink c-it c-nw" style="font-size:132px;text-shadow:0 6px 0 rgba(0,0,0,.45)">matéria.</div></div>
        <div class="abs" id="c12stk" style="left:0;top:0;transform-origin:50% 50%"><div class="c-stk"><span class="disp" style="font-size:120px"><span style="color:#C2253B">#</span>prova</span></div></div>
        <div class="abs" id="c12t3" style="left:84px;top:252px;transform-origin:0 50%"><div class="c-dark" style="transform:rotate(-1.5deg);padding:2px 34px 24px"><span class="disp c-pink c-it" style="font-size:118px">achou na hora.</span></div></div>
        <svg class="abs" id="c12tick" style="left:850px;top:236px;width:150px;height:150px;overflow:visible" viewBox="0 0 150 150">
          <path pathLength="1" d="M 20 78 C 34 92, 44 106, 54 122 C 76 76, 104 38, 138 10" fill="none" stroke="#FFC9D1" stroke-width="14" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <div class="c-full c-grain" id="c12gr"></div>`;
      const P = makePhone(q(root, "#c12ph"));
      this.box = P.box; this.ph = P.ph; this.v = P.v;
      this.v.ann.innerHTML = `<div class="c-ring" id="c12ring" style="left:0;top:0;width:200px;height:200px;border-color:#F28A97"></div>`;
      window.EVENTS.taps.push(28.2);
      window.EVENTS.notes.push([27.2, "crash"], ...CARDS.map((c, i) => [c.at, `whoosh 3D da pasta ${c.name} (pan ${i % 2 ? "direita" : "esquerda"}) + thud macio ao pousar em ${(c.at + FLY).toFixed(2)}`]),
        [28.0, "whip-zoom até as etiquetas"], [28.2, "pop do toque no chip #prova"], [28.27, "pop de adesivo (#prova)"], [28.4, "ding em Dó6 (achou na hora.)"]);
    },
    async render(t, root) {
      grainFx(q(root, "#c12gr"), t);
      const P = pose12(t);
      nightFx(root, "c12", t, (P.x - 640) * .3, (P.y - 1270) * .3);
      this.box.style.transform = phoneTf(P);
      this.ph.phone.style.setProperty("--gl", `${lerp(120, -10, seg(t, 27.2, 28.2))}%`);
      const f = K.frameAt(c12, t);
      await this.v.set(c12, f, { zoom: 1 });
      // borrão do whip-zoom
      const kw = seg(t, 28.0, 28.2), bw = Math.sin(Math.PI * kw) * 26;
      if (bw > 1) { document.getElementById("cWhip12B").setAttribute("stdDeviation", `${(bw * .35).toFixed(1)} ${bw.toFixed(1)}`); this.box.style.filter = "url(#cWhip12)"; }
      else this.box.style.filter = "";
      // anel do toque no chip
      const ring = q(root, "#c12ring"), kr = seg(t, 28.2, 28.5);
      if (show(ring, kr > 0 && kr < 1)) {
        const d = lerp(90, 330, out3(kr));
        Object.assign(ring.style, { left: `${150 - d / 2}px`, top: `${773 - d / 2}px`, width: `${d}px`, height: `${d}px`, opacity: 1 - in3(kr) });
      }
      // pastas: saltam da tela, pousam em leque (uma por colcheia) e saem em chicote no 28,0
      CARDS.forEach((c, i) => {
        const el = q(root, "#c12s" + i), s = sprite(c, t);
        if (!show(el, s && t < 28.4)) return;
        const ko = in3(seg(t, 28.0 + i * .04, 28.0 + i * .04 + 5 / 30));
        const w = c.crop[2], h = c.a[3];
        el.style.transform = `translate(${s.x - w / 2}px, ${s.y - h / 2 + ko * 1500}px) rotate(${s.rot + ko * (c.rot < 0 ? -20 : 20)}deg) scale(${s.scl})`;
        el.style.boxShadow = `0 ${30 + 70 * s.z}px ${50 + 60 * s.z}px rgba(0,0,0,${.55 - .2 * s.z})`;
        el.style.filter = ko > .1 ? `url(#blurY${ko > .5 ? 40 : 20})` : "";
        el.style.zIndex = 10 + i;
      });
      // textos
      const t1 = q(root, "#c12t1"), t2 = q(root, "#c12t2"), stk = q(root, "#c12stk"), t3 = q(root, "#c12t3"), tk = q(root, "#c12tick");
      reset(t1, t2, t3);
      if (show(t1, t < 28.2)) { K.slam(t1, t, 27.2, { from: 1.5, rot: -3 }); K.whipOut(t1, t, 28.0, [-1, 0], 5 / 30, 1300); }
      if (show(t2, on(t, 27.6, 28.2))) { K.slam(t2, t, 27.6, { from: 1.45, rot: 3 }); K.whipOut(t2, t, 28.03, [-1, 0], 5 / 30, 1300); }
      if (show(stk, t >= 28.27)) {
        const sp = proj(P, SEARCH[0], SEARCH[1]);
        const ks = seg(t, 28.27, 28.27 + 7 / 30);
        const sc = ks < .45 ? lerp(0, 1.15, out3(ks / .45)) : lerp(1.15, 1, inOut((ks - .45) / .55));
        stk.style.transform = `translate(${sp[0] - stk.offsetWidth / 2}px, ${sp[1] - stk.offsetHeight / 2}px) rotate(-6deg) scale(${sc})`;
      }
      if (show(t3, t >= 28.4)) { K.slam(t3, t, 28.4, { from: 1.4, rot: -3 }); t3.style.translate = `0 ${-12 * seg(t, 28.4, 29.1)}px`; }
      if (show(tk, t >= 28.5)) K.draw(tk.querySelector("path"), t, 28.5, 28.5 + 5 / 30, x => x);
    },
  });
  K.hit(27.2, .7); CARDS.forEach(c => K.hit(c.at + FLY, .22)); K.hit(28.0, .35); K.hit(28.27, .3); K.hit(28.4, .3);

  // =====================================================================================
  // S13 — PDF do slide (28,8–30,4): traço de pincel revela R4 'pdf'; a S Pen escreve por cima
  // =====================================================================================
  // toques limpos: os toques de cor (botão vermelho e o artefato em [42,42]) não viram cursor
  const T4 = (() => {
    const out = []; let last = null;
    R4.touches.forEach((tc, i) => {
      if (tc.k === "pen" && tc.p[0]) { last = tc.p[0]; out.push({ k: "pen", up: false, p: [tc.p[0]] }); }
      else if (i >= 43 && i <= 102 && last) out.push({ k: "pen", up: false, p: [last] });
      else out.push({ k: "none", up: false, p: [] });
    });
    return out;
  })();
  const c13 = Object.assign(K.clip("r4", [[28.8, 29.0, 40, 43], [29.0, 29.55, 43, 75], [29.55, 29.85, 75, 93], [29.85, 30.0, 93, 103], [30.0, 30.4, 103, 110]]), { touches: T4 });
  function penFocus(clip, f, win, ahead, lo, hi) {
    let sx = 0, sy = 0, n = 0;
    for (let i = f - win + ahead; i <= f + win + ahead; i++) {
      const tc = clip.touches[Math.max(lo, Math.min(hi, i))];
      if (tc && tc.k === "pen" && tc.p[0]) { const w = 1 - Math.abs(i - f - ahead) / (win + 1); sx += tc.p[0][0] * w; sy += tc.p[0][1] * w; n += w; }
    }
    return n ? [sx / n, sy / n] : null;
  }
  function cam13(t) {
    const f = K.frameAt(c13, t);
    const kz = inOut(seg(t, 29.0, 29.2));
    const pf = penFocus(c13, f, 9, 4, 43, 102) || [400, 1500];
    const fx = lerp(600, lerp(pf[0], 560, .45), kz), fy = lerp(1250, lerp(1280, pf[1], .12), kz);
    let zoom = lerp(1.3 * (1 + .04 * seg(t, 28.8, 29.0)), 1.4, kz) * (1 + .03 * seg(t, 29.2, 30.0));
    zoom *= 1 - .08 * out3(seg(t, 30.0, 30.2));
    return { zoom: zoom * bounce(t), focus: [fx, fy], ax: .5, ay: .5, penScale: 1.1 };
  }
  // traço de pincel: diagonal de baixo-esquerda para cima-direita
  const BR0 = [-170, 1640], BR1 = [1250, 300];
  const BRL = Math.hypot(BR1[0] - BR0[0], BR1[1] - BR0[1]);
  const BU = [(BR1[0] - BR0[0]) / BRL, (BR1[1] - BR0[1]) / BRL], BN = [-BU[1], BU[0]];
  const BR_D = `M ${BR0[0]} ${BR0[1]} C 300 1250, 760 820, ${BR1[0]} ${BR1[1]}`;
  K.scene({
    id: "s13", start: 28.8, end: 30.4,
    setup(root) {
      injectOnce();
      root.innerHTML = `
        <div id="c13bg">${NIGHT("c13")}</div>
        <div class="c-full" id="c13w">
          <div class="c-full" style="background:#151413"></div>
          <div id="c13v" style="left:0;top:0;width:1080px;height:1920px"></div>
          <div class="c-full" style="background:linear-gradient(180deg, rgba(15,12,13,.6) 0%, rgba(15,12,13,0) 14%)"></div>
          <div class="abs" id="c13t1" style="left:84px;top:256px;transform-origin:0 50%"><div class="c-dark" style="transform:rotate(-2deg);padding:4px 34px 24px"><span class="disp" style="font-size:118px;color:${PAPER}">PDF do slide?</span></div></div>
          <div class="abs" id="c13t2" style="left:84px;top:256px;transform-origin:0 50%"><div class="c-dark" style="transform:rotate(1.5deg);padding:4px 34px 26px">
            <div class="disp c-pink c-it" style="font-size:106px">Escreve <span class="hl c-hlo" id="c13hl">por cima.</span></div></div></div>
          <div class="c-full c-grain" id="c13gr"></div>
        </div>
        <svg class="abs" id="c13br" style="left:0;top:0;width:1080px;height:1920px;overflow:visible" viewBox="0 0 1080 1920">
          <g filter="url(#cBrush)">
            <path class="c13p" pathLength="1" d="${BR_D}" fill="none" stroke="#C2253B" stroke-width="240" stroke-linecap="round"/>
            <path class="c13p" pathLength="1" d="${BR_D}" fill="none" stroke="#A3162B" stroke-width="40" stroke-linecap="round" transform="translate(-40 -52)" opacity=".75"/>
            <path class="c13p" pathLength="1" d="${BR_D}" fill="none" stroke="#E8404F" stroke-width="26" stroke-linecap="round" transform="translate(46 60)" opacity=".8"/>
            <path class="c13p" pathLength="1" d="${BR_D}" fill="none" stroke="#7E0F20" stroke-width="12" stroke-linecap="round" transform="translate(10 -96)" opacity=".6"/>
          </g>
        </svg>`;
      this.v = K.viewer(q(root, "#c13v"));
      this.v.el.style.background = "#151413";
      const T = f => +K.timeOf(c13, f).toFixed(3);
      window.EVENTS.pen.push([T(43), T(75), "pen"], [T(75), T(93), "pen"], [T(93), T(102) + 1 / 30, "pen"]);
      window.EVENTS.notes.push([28.8, "crash curto + traço de pincel largo (6 quadros)"], [29.6, "thump (Escreve por cima.)"],
        [30.0, "chiado curto de marca-texto (faixa oliva)"], [30.0, "whoosh para cima (30,0–30,4)"]);
    },
    async render(t, root) {
      grainFx(q(root, "#c13gr"), t);
      if (show(q(root, "#c13bg"), t >= 29.1)) nightFx(root, "c13", t, 0, -900 * in3(seg(t, 30.2, 30.4)));
      const f = K.frameAt(c13, t);
      await this.v.set(c13, f, cam13(t));
      // revelação: faixa ao longo do traço que se alarga até cobrir tudo
      const w = q(root, "#c13w");
      const kd = out3(seg(t, 28.8, 29.0));
      const hw = lerp(118, 2600, out3(seg(t, 28.9, 29.08)));
      if (t < 29.1) {
        const head = [BR0[0] + BU[0] * (BRL + 260) * kd, BR0[1] + BU[1] * (BRL + 260) * kd];
        const tail = [BR0[0] - BU[0] * 300, BR0[1] - BU[1] * 300];
        const pts = [[tail[0] - BN[0] * hw, tail[1] - BN[1] * hw], [head[0] - BN[0] * hw, head[1] - BN[1] * hw], [head[0] + BN[0] * hw, head[1] + BN[1] * hw], [tail[0] + BN[0] * hw, tail[1] + BN[1] * hw]];
        w.style.clipPath = `polygon(${pts.map(p => `${p[0].toFixed(1)}px ${p[1].toFixed(1)}px`).join(",")})`;
      } else w.style.clipPath = "";
      // traço vermelho: desenha (28,8–29,0) e a cauda some (28,95–29,15)
      const br = q(root, "#c13br");
      if (show(br, t < 29.17)) {
        const b = kd, a = in3(seg(t, 28.95, 29.15));
        br.querySelectorAll(".c13p").forEach(p => { p.style.strokeDasharray = `0 ${a.toFixed(4)} ${Math.max(0, b - a).toFixed(4)} 2`; });
      }
      // recuo + whip para cima (30,0–30,4)
      const ku = in3(seg(t, 30.2, 30.4));
      w.style.transform = ku > 0 ? `translateY(${-1700 * ku}px)` : "";
      w.style.filter = ku > .05 ? `url(#blurY${ku > .4 ? 40 : 20})` : "";
      // textos
      const t1 = q(root, "#c13t1"), t2 = q(root, "#c13t2");
      reset(t1, t2);
      if (show(t1, on(t, 29.0, 29.8))) { K.slam(t1, t, 29.0, { from: 1.5, rot: -3 }); K.whipOut(t1, t, 29.6, [0, -1], 5 / 30, 1100); }
      if (show(t2, t >= 29.6)) { K.slam(t2, t, 29.6, { from: 1.45, rot: 3 }); K.hl(q(root, "#c13hl"), t, 30.0); }
    },
  });
  K.hit(28.8, .6); K.flash(28.8, "#C2253B", .2, 1); K.hit(29.6, .45); K.hit(30.0, .3);
})();

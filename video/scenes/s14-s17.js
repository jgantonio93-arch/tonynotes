// BLOCO D — S14 (E ainda: lista noturna), S15 (tinta na ponta, R5 espiral), S16 (match cut
// espiral -> miolo da rosa, logo), S17 (CTA "Comenta ROSA" + loop de volta ao quadro 0).
// Tudo é função pura de t: nenhum estado guardado entre quadros.
(function () {
  const { clamp, seg, lerp, out3, out5, inOut, back, hash } = E;
  const in2 = x => x * x, in3 = x => x * x * x;
  const expo = x => x >= 1 ? 1 : 1 - Math.pow(2, -10 * x);
  const q = (root, s) => root.querySelector(s);
  const qa = (root, s) => root.querySelectorAll(s);
  const PI = Math.PI;
  const INK = "#1B1B1F", RED = "#C2253B", PINK = "#F28A97", PAPER = "#F6F4EF";
  const T30 = t => Math.round(t * 30);                 // índice do quadro (evita erro de ponto flutuante)

  // ---- Batida -----------------------------------------------------------------------------
  const KICKS = [0, .3, .6, .8, 1.1, 1.4];             // funk 3+3+2 (semicolcheias 0,3,6,8,11,14)
  const sinceKick = (t, pat = KICKS) => { const p = ((t % 1.6) + 1.6) % 1.6; let d = 9; for (const o of pat) if (p >= o - 1e-6) d = Math.min(d, p - o); return Math.max(0, d); };
  const kickEnv = (t, pat) => { const d = sinceKick(t, pat); const k = Math.max(0, 1 - d / .2); return k * k; };
  const bounce = (t, amt = .025, pat) => 1 + amt * kickEnv(t, pat);
  const beatEnv = t => { const p = ((t % .4) + .4) % .4; return Math.exp(-p * 12); };
  /** zera estilos que os helpers do kit não resetam (quadros fora de ordem) */
  const reset = (...els) => els.forEach(el => { el.style.translate = ""; el.style.filter = ""; el.style.clipPath = ""; el.style.scale = ""; el.style.rotate = ""; });

  // ---- Logo (paths reais de logo.svg) -------------------------------------------------------
  const P_LEAF = "M61,69 C60,61 67,56 75,57 C75,65 68,70 61,69 Z";
  const P_STEM = "M37,34 V80";
  const P_LEG = "M50,56 C57,63 62,72 71,80";
  const P_ROSE = "M32.5,33 C32.5,27 37,25 41,26.5 C44,22 51,21 54.5,25.5 C59,21 67,22.5 69,29 C73,32 74,37 73,42 C71.5,52 63.5,59 52,59 H37 C34.5,59 32.5,57 32.5,54.5 Z";
  const P_SPIRAL = "M54.5,37 C52.5,36 50.5,38 51.8,40.2 C53.3,42.6 58,41.6 58,37.6 C58,33.2 52.5,31.6 49.6,34.6 C46.4,38 47.6,44.6 54,45.6 C60.5,46.5 64.8,42 64.6,36";
  const P_ARC = "M42.5,40 C42.5,49.5 48.5,53.8 55,53.8 C62,53.8 67.5,49.5 68.5,42.5";
  const logoInner = (p, opts = {}) => `
    <g class="${p}sq"><rect class="${p}rect" width="108" height="108" rx="30" fill="url(#dGrad)"/></g>
    <g class="${p}leafg"><path d="${P_LEAF}" fill="#FFC9D1"/></g>
    <path class="${p}stem" d="${P_STEM}" stroke="#fff" stroke-width="9" stroke-linecap="round" fill="none"${opts.draw ? ' pathLength="1"' : ""}/>
    <path class="${p}leg" d="${P_LEG}" stroke="#fff" stroke-width="9" stroke-linecap="round" fill="none"${opts.draw ? ' pathLength="1"' : ""}/>
    <path d="${P_ROSE}" fill="#fff"/>
    <g class="${p}spir" stroke="#B81D33" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" fill="none"><path d="${P_SPIRAL}"/><path d="${P_ARC}"/></g>
    ${opts.spec ? `<g clip-path="url(#dClip)"><rect class="${p}spec" x="-40" y="-40" width="26" height="200" fill="url(#dSpec)" transform="skewX(-20)"/></g>` : ""}`;

  // ---- CSS + defs (uma vez) -----------------------------------------------------------------
  function injectOnce() {
    if (document.getElementById("d-style")) return;
    const st = document.createElement("style");
    st.id = "d-style";
    st.textContent = `
      .d-it { font-style: italic; font-synthesis: style; }
      .d-mask { display: inline-block; overflow: hidden; vertical-align: top; padding: 0 .02em .06em; margin: 0 -.02em -.06em; }
      .d-let { display: inline-block; white-space: pre; }
      .d14row { position: absolute; display: flex; align-items: center; height: 96px; transform-origin: 0 50%; white-space: nowrap; }
      .d14ic { width: 72px; height: 72px; flex: none; position: relative; }
      .d14ic svg { position: absolute; left: 0; top: 0; overflow: visible; }
      .d14tx { position: relative; margin-left: 30px; font-family: Onest; font-weight: 700; font-size: 64px; color: #F6F4EF; letter-spacing: -.005em; line-height: 1; }
      .d14band { position: absolute; left: -14px; right: -16px; top: 30%; bottom: -4%; background: #7A6A12; border-radius: 6px 16px 8px 18px; transform-origin: 0 50%; z-index: 0; }
      .d14word { position: relative; z-index: 1; display: inline-block; }
      .d14card { position: absolute; left: 0; top: 0; width: 560px; height: 1214px; margin: -607px 0 0 -280px; border-radius: 46px; overflow: hidden; opacity: .18; filter: blur(6px); backface-visibility: hidden; }
      .d14card img { width: 100%; height: 100%; display: block; }
      .d-pill { position: absolute; background: #C2253B; color: #fff; border-radius: 999px; overflow: hidden; font-family: Onest; font-weight: 700; font-size: 40px; letter-spacing: .01em; padding: 20px 38px 22px; white-space: nowrap; box-shadow: 6px 6px 0 rgba(27,27,31,.22); }
      .d-glint { position: absolute; top: -20px; bottom: -20px; width: 90px; background: linear-gradient(90deg, rgba(255,255,255,0), rgba(255,255,255,.75), rgba(255,255,255,0)); transform: skewX(-20deg); }
      .d-hl { position: relative; z-index: 0; --hl: 0; }
      .d-hl::before { content: ""; position: absolute; z-index: -1; left: -.08em; top: .5em; bottom: .02em; width: calc((100% + .16em) * var(--hl)); background: rgba(255,228,92,.9); border-radius: .06em .2em .1em .25em; transform: skewX(-8deg) rotate(-1deg); }
    `;
    document.head.appendChild(st);
    const sv = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    sv.setAttribute("width", "0"); sv.setAttribute("height", "0"); sv.style.position = "absolute";
    sv.innerHTML = `<defs>
      <linearGradient id="dGrad" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#E8404F"/><stop offset="1" stop-color="#A3162B"/></linearGradient>
      <linearGradient id="dSpec" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset=".5" stop-color="#fff" stop-opacity=".55"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>
      <clipPath id="dClip"><rect width="108" height="108" rx="30"/></clipPath>
      <filter id="dShadow" x="-60%" y="-60%" width="220%" height="240%"><feDropShadow id="dShadowF" dx="0" dy="9" stdDeviation="9" flood-color="#A3162B" flood-opacity=".28"/></filter>
      <filter id="dWhip" x="-5%" y="0" width="110%" height="100%"><feGaussianBlur id="dWhipB" stdDeviation="0 0"/></filter>
      <filter id="dWhip15" x="0" y="-10%" width="100%" height="120%"><feGaussianBlur id="dWhip15B" stdDeviation="0 0"/></filter>
      <radialGradient id="dTipGlow"><stop offset="0" stop-color="#fff" stop-opacity=".35"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
    </defs>`;
    document.body.appendChild(sv);
  }

  /** grade de pontos presa a um mundo com passo Dworld (px de vídeo), origem (ox, oy); duas camadas cruzam */
  function worldDots(d1, d2, Dworld, ox, oy, Lb = 23) {
    let D = Dworld;
    while (D < Lb) D *= 2;
    while (D >= 2 * Lb) D /= 2;
    [[d1, D, (D - Lb) / Lb], [d2, 2 * D, 1]].forEach(([el, sz, op]) => {
      el.style.backgroundSize = `${sz}px ${sz}px`;
      el.style.backgroundPosition = `${ox - sz / 2}px ${oy - sz / 2}px`;
      el.style.opacity = op;
    });
  }

  // =========================================================================================
  // S14 — E ainda: (30,4–32,8) — MUNDO NOITE, lista cumulativa com marca-texto oliva
  // =========================================================================================
  const S14 = { start: 30.4, end: 32.8 };
  const ITEMS = ["Áudio junto da escrita", "Busca até na letra à mão", "Letra vira texto", "Ponteiro laser", "Backup no seu Drive"];
  const IT_T = [30.8, 31.2, 31.6, 32.0, 32.4];
  const IT_Y = i => 548 + 148 * i;                    // centro vertical de cada linha
  const CARDS = (() => {
    const imgs = ["img/d14-a.jpg", "img/d14-c.jpg", "img/d14-b.jpg"];
    const out = [];
    let n = 0;
    for (let r = -1; r <= 3; r++) for (let c = -1; c <= 2; c++) {
      out.push({ x: 120 + c * 600 + r * 250, y: 260 + r * 720 - c * 200, img: imgs[(n + r + 3) % 3] }); n++;
    }
    return out;
  })();
  const ICONS = [
    // 1. forma de onda
    `<svg width="72" height="72" viewBox="0 0 72 72">${Array.from({ length: 7 }, (_, i) => `<line class="d14w" x1="${9 + i * 9}" x2="${9 + i * 9}" y1="30" y2="42" stroke="${i === 3 ? PINK : PAPER}" stroke-width="5" stroke-linecap="round"/>`).join("")}</svg>`,
    // 2. lupa sobre "rosa" cursivo
    `<svg width="72" height="72" viewBox="0 0 72 72"><defs><clipPath id="d14lc"><circle id="d14lcc" cx="30" cy="36" r="12"/></clipPath></defs>
      <text x="5" y="45" font-family="Caveat" font-weight="700" font-size="31" fill="${PAPER}" opacity=".6">rosa</text>
      <g clip-path="url(#d14lc)"><rect x="0" y="0" width="72" height="72" fill="#2A0C14"/><text id="d14mag" x="5" y="45" font-family="Caveat" font-weight="700" font-size="31" fill="${PINK}">rosa</text></g>
      <g id="d14lens"><circle cx="0" cy="0" r="13" fill="none" stroke="${PAPER}" stroke-width="5"/><line x1="9.5" y1="9.5" x2="19" y2="19" stroke="${PAPER}" stroke-width="6" stroke-linecap="round"/></g></svg>`,
    // 3. "aula" cursivo -> digitado
    `<svg width="72" height="72" viewBox="0 0 72 72">
      <g id="d14cur"><text x="36" y="47" text-anchor="middle" font-family="Caveat" font-weight="700" font-size="36" fill="${PAPER}">aula</text></g>
      <g id="d14typ"><text x="33" y="46" text-anchor="middle" font-family="Onest" font-weight="700" font-size="23" fill="${PAPER}">aula</text><rect id="d14caret" x="58" y="27" width="3.5" height="24" rx="1" fill="${PINK}"/></g>
      <g id="d14gl"><text x="36" y="47" text-anchor="middle" font-family="Caveat" font-weight="700" font-size="36" fill="${PINK}" opacity=".8">aula</text><text x="33" y="46" text-anchor="middle" font-family="Onest" font-weight="700" font-size="23" fill="#7FE3FF" opacity=".8">aula</text></g></svg>`,
    // 4. ponteiro laser sobre um slide
    `<svg width="72" height="72" viewBox="0 0 72 72"><rect x="5" y="13" width="62" height="44" rx="7" fill="none" stroke="${PAPER}" stroke-width="4.5"/>
      <line x1="16" y1="27" x2="40" y2="27" stroke="${PAPER}" stroke-width="3.5" stroke-linecap="round" opacity=".45"/><line x1="16" y1="37" x2="52" y2="37" stroke="${PAPER}" stroke-width="3.5" stroke-linecap="round" opacity=".45"/>
      <g id="d14trail">${Array.from({ length: 9 }, () => `<circle r="4" fill="${PINK}"/>`).join("")}</g>
      <circle id="d14dotg" r="10" fill="${PINK}" opacity=".45"/><circle id="d14dot" r="4.5" fill="#fff"/></svg>`,
    // 5. nuvem com seta subindo
    `<svg width="72" height="72" viewBox="0 0 72 72"><defs><clipPath id="d14cc"><rect x="0" y="16" width="72" height="40"/></clipPath></defs>
      <path d="M21,54 C11,54 7,46 10,40 C12,35 17,33 21,34 C22,24 31,18 40,20 C47,22 51,27 52,33 C60,32 66,38 65,45 C64,51 59,54 53,54 Z" fill="none" stroke="${PAPER}" stroke-width="5" stroke-linejoin="round"/>
      <g clip-path="url(#d14cc)"><g id="d14arr"><line x1="37" y1="50" x2="37" y2="33" stroke="${PINK}" stroke-width="5" stroke-linecap="round"/><path d="M30,39 L37,32 L44,39" fill="none" stroke="${PINK}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/></g></g></svg>`,
  ];

  K.scene({
    id: "s14", ...S14,
    setup(root) {
      injectOnce();
      root.innerHTML = `
        <div class="abs" style="inset:0;background:linear-gradient(180deg,#0F0C0D 0%,#1C0A0F 45%,#3A0A14 100%)"></div>
        <div class="abs dots night" id="d14dots" style="left:-120px;top:-120px;width:1320px;height:2160px"></div>
        <div class="abs" id="d14cards" style="inset:0;perspective:2000px;perspective-origin:50% 40%">
          ${CARDS.map(c => `<div class="d14card"><img src="${c.img}"></div>`).join("")}
        </div>
        <div class="abs" id="d14glow" style="left:-200px;top:900px;width:1480px;height:1300px;background:radial-gradient(ellipse 50% 55% at 50% 100%, rgba(194,37,59,.35), rgba(194,37,59,.12) 55%, rgba(194,37,59,0) 100%);transform-origin:50% 100%"></div>
        <div class="abs" id="d14list" style="left:0;top:0;width:1080px;height:1920px;transform-origin:96px 360px">
          <div class="abs" id="d14h" style="left:92px;top:250px;transform-origin:0 60%"><div class="disp d-it" style="font-size:156px;color:${PINK};white-space:nowrap">E ainda:</div></div>
          ${ITEMS.map((s, i) => `<div class="d14row" id="d14r${i}" style="left:96px;top:${IT_Y(i) - 48}px">
              <div class="d14ic" id="d14i${i}">${ICONS[i]}</div>
              <div class="d14tx"><span class="d14band" id="d14b${i}"></span><span class="d14word" id="d14x${i}">${s}</span></div>
            </div>`).join("")}
        </div>`;
      window.EVENTS.notes.push([30.4, "whoosh de entrada (E ainda:) vindo de baixo"],
        [30.8, "stamp + chiado curto de marca-texto + bip de onda"], [31.2, "stamp + chiado de marca-texto + ping da lupa"],
        [31.6, "stamp + chiado de marca-texto + glitch digital de 80 ms"], [32.0, "stamp + chiado de marca-texto + 'piu' de laser"],
        [32.4, "stamp + chiado de marca-texto + whoosh para cima (nuvem)"]);
      IT_T.forEach(a => window.EVENTS.pen.push([a, +(a + 5 / 30).toFixed(3), "marker"]));
    },
    render(t, root) {
      const lt = t - S14.start;
      // fundo: cartões das capturas reais deslizando na diagonal; pontos andando junto
      const vx = -230, vy = -330;
      qa(root, ".d14card").forEach((el, i) => {
        const c = CARDS[i];
        const x = c.x + vx * lt, y = c.y + vy * lt;
        el.style.transform = `translate(${x}px, ${y}px) rotateZ(-20deg) rotateX(50deg)`;
      });
      const dots = q(root, "#d14dots");
      dots.style.transform = `translate(${((vx * lt * .55) % 46)}px, ${((vy * lt * .55) % 46)}px)`;
      // luz vermelha de baixo pulsando +6% em cada kick
      const ke = kickEnv(t);
      const glow = q(root, "#d14glow");
      glow.style.opacity = .9 + .1 * ke;
      glow.style.transform = `scale(${1 + .06 * ke})`;
      // lista inteira: sobe 3% e faz zoom 1,00 -> 1,04 nos 2,4 s (+ bounce leve no kick)
      const L = q(root, "#d14list"), u = seg(t, 30.4, 32.8);
      L.style.transform = `translateY(${-58 * inOut(u)}px) scale(${lerp(1, 1.04, u) * bounce(t, .012)})`;
      // "E ainda:" chega em chicote de baixo (continua o whip para cima da S13) e assenta com slam
      const h = q(root, "#d14h");
      reset(h);
      K.slam(h, t, 30.4 + 4 / 30, { pre: 4 / 30, from: 1.18 });
      if (t < 30.4 + 4 / 30) {
        const k = out3(seg(t, 30.4, 30.4 + 4 / 30));
        h.style.translate = `0 ${(1 - k) * 760}px`;
        h.style.filter = `url(#blurY${k < .5 ? 40 : 20})`;
      }
      h.style.opacity = 1;
      // itens: STAMP + faixa oliva varrendo na altura das letras em 5 quadros; anterior cai a 55%
      ITEMS.forEach((s, i) => {
        const row = q(root, `#d14r${i}`), at = IT_T[i];
        reset(row);
        K.stamp(row, t, at);
        const kb = out3(seg(t, at, at + 5 / 30));
        q(root, `#d14b${i}`).style.transform = `skewX(-8deg) scaleX(${kb})`;
        q(root, `#d14x${i}`).style.clipPath = `inset(-30% ${(1 - kb) * 100}% -30% -4%)`;
        const nxt = IT_T[i + 1];
        const dim = nxt ? out3(seg(t, nxt, nxt + 3 / 30)) : 0;
        row.style.opacity = t >= at ? 1 - .45 * dim : 0;
        // ícone: pop com mola
        const ic = q(root, `#d14i${i}`), kp = back(seg(t, at, at + .26), 2.2);
        ic.style.transform = `scale(${Math.max(0, kp)}) rotate(${(1 - clamp(kp)) * -25}deg)`;
      });
      // --- animações dos ícones (tempo local de cada um)
      // 1. onda pulsando no tempo
      const w = qa(root, ".d14w"), be = beatEnv(t);
      w.forEach((ln, i) => {
        const hgt = 8 + 34 * (.35 + .65 * Math.abs(Math.sin(t * 9 + i * 1.3))) * (.55 + .45 * be) * (1 - Math.abs(i - 3) * .12);
        ln.setAttribute("y1", 36 - hgt / 2); ln.setAttribute("y2", 36 + hgt / 2);
      });
      // 2. lupa varrendo "rosa"
      {
        const k = t - IT_T[1];
        const lx = 30 + 22 * Math.sin(Math.max(0, k) * 7.85 - PI / 2) * .9 + 4, ly = 36 + 3 * Math.sin(k * 12);
        q(root, "#d14lens").setAttribute("transform", `translate(${lx},${ly})`);
        q(root, "#d14lcc").setAttribute("cx", lx); q(root, "#d14lcc").setAttribute("cy", ly);
        q(root, "#d14mag").setAttribute("transform", `translate(${lx},${ly}) scale(1.45) translate(${-lx},${-ly})`);
      }
      // 3. "aula" cursivo -> digitado (ciclo de 0,8 s com glitch de 2 quadros)
      {
        const k = Math.max(0, t - IT_T[2]), p = k % .8;
        const typed = p >= .26 && p < .72, gl = (p >= .2 && p < .27) || (p >= .7 && p < .76);
        q(root, "#d14cur").style.opacity = typed || gl ? 0 : 1;
        q(root, "#d14typ").style.opacity = typed && !gl ? 1 : 0;
        const g = q(root, "#d14gl");
        g.style.display = gl ? "" : "none";
        if (gl) { const f = T30(t); g.setAttribute("transform", `translate(${(hash(f) - .5) * 10},${(hash(f + 3) - .5) * 4})`); }
        q(root, "#d14caret").style.opacity = Math.floor(k * 7.5) % 2 ? .25 : 1;
      }
      // 4. laser com rastro que some (posições passadas calculadas do próprio t)
      {
        const pos = tt => [36 + 22 * Math.sin(tt * 6.2), 35 + 13 * Math.sin(tt * 12.4 + .6)];
        const [x, y] = pos(t);
        ["#d14dot", "#d14dotg"].forEach(s => { q(root, s).setAttribute("cx", x); q(root, s).setAttribute("cy", y); });
        q(root, "#d14dotg").setAttribute("r", 9 + 3 * be);
        qa(root, "#d14trail circle").forEach((c, j) => {
          const [a, b] = pos(t - (j + 1) * .028);
          c.setAttribute("cx", a); c.setAttribute("cy", b);
          c.setAttribute("r", 4 * (1 - j / 10)); c.setAttribute("opacity", .7 * (1 - j / 9));
        });
      }
      // 5. seta subindo pela nuvem a cada tempo
      {
        const p = (((t - IT_T[4]) % .4) + .4) % .4 / .4;
        const arr = q(root, "#d14arr");
        arr.setAttribute("transform", `translate(0,${lerp(16, -14, out3(p))})`);
        arr.style.opacity = p < .75 ? 1 : 1 - (p - .75) / .25;
      }
    },
  });
  K.hit(30.4, .5);
  IT_T.forEach(a => K.hit(a, .28));

  // =========================================================================================
  // S15 — Tinta colada / na ponta. (32,8–34,4) — macro de R5 desenhando a espiral
  // =========================================================================================
  const S15 = { start: 32.8, end: 34.4 };
  const R5 = window.REC.r5, LM = R5.markers.logoMap;             // [cx, cy, unidade, 55.5, 42]
  const L2I = (lx, ly) => [LM[0] + (lx - LM[3]) * LM[2], LM[1] + (ly - LM[4]) * LM[2]];
  const LOGO_IN_IMG = `translate(${LM[0] - LM[3] * LM[2]},${LM[1] - LM[4] * LM[2]}) scale(${LM[2]})`;
  const c15 = K.clip("r5", [[32.8, 33.6, "espiral", "arco"], [33.6, 34.0, "arco", "fim"], [34.0, 34.4, "fim", "fim"]]);
  const R5FILL = 1080 / R5.info.width;
  const A_COMP = L2I(55.5, 42.8), A_SPIR = L2I(55.5, 40.5);
  const S15_BASE = 1.8, S_CUT = 4.0;                              // px de vídeo por px da gravação; 4,0 = 70 px/unidade da logo
  const INK_W = 36 / LM[2];                                       // largura real da tinta gravada (36 px) em unidades da logo
  function cam15(t) {
    const f = K.frameAt(c15, t);
    const z = lerp(1, 1.12, inOut(seg(t, 32.8, 34.0)));
    const pf = K.focus(c15, f, 8, 3, K.m("r5", "espiral"), K.m("r5", "fim") - 1) || A_COMP;
    let A = [lerp(A_COMP[0], pf[0], .2), lerp(A_COMP[1], pf[1], .2)];
    const d = in3(seg(t, 34.0, 34.4));
    const S = S15_BASE * z * Math.pow(S_CUT / (S15_BASE * 1.12), d);
    A = [lerp(A[0], A_SPIR[0], d), lerp(A[1], A_SPIR[1], d)];
    const P = [540, lerp(1150, 960, d)];
    const R = lerp(-4, 2, seg(t, 32.8, 34.4));
    return { f, S, A, P, R, d };
  }
  const toScreen = (cam, x, y) => {
    const dx = (x - cam.A[0]) * cam.S, dy = (y - cam.A[1]) * cam.S, r = cam.R * PI / 180;
    return [cam.P[0] + dx * Math.cos(r) - dy * Math.sin(r), cam.P[1] + dx * Math.sin(r) + dy * Math.cos(r)];
  };
  const NSPEED = 46;

  K.scene({
    id: "s15", ...S15,
    setup(root) {
      injectOnce();
      root.innerHTML = `
        <div class="abs" style="inset:0;background:#1A1A1A"></div>
        <div id="d15v" style="left:0;top:0;width:1080px;height:1920px"></div>
        <div class="abs" style="inset:0;background:radial-gradient(ellipse 75% 60% at 50% 58%, rgba(0,0,0,0) 55%, rgba(0,0,0,.42) 100%)"></div>
        <div class="abs" id="d15glow" style="left:-100px;top:1250px;width:1280px;height:800px;background:radial-gradient(ellipse 50% 60% at 50% 100%, rgba(194,37,59,.28), rgba(194,37,59,0) 100%);mix-blend-mode:screen"></div>
        <svg class="abs" id="d15sp" width="1080" height="1920" style="left:0;top:0">${Array.from({ length: NSPEED }, () => `<line stroke="#fff" stroke-width="2.5" stroke-linecap="round"/>`).join("")}</svg>
        <svg class="abs" id="d15gh" width="1080" height="1920" style="left:0;top:0;overflow:visible">
          ${[0, 1, 2].map(i => `<g class="d15g"><g transform="${LOGO_IN_IMG}" stroke="#FF8AA2" stroke-width="${INK_W}" stroke-linecap="round" fill="none"><path d="${P_SPIRAL}"/><path d="${P_ARC}"/></g></g>`).join("")}
        </svg>
        <div class="abs" id="d15tip" style="left:0;top:0;width:48px;height:48px;margin:-24px 0 0 -24px;border-radius:50%;background:radial-gradient(circle, rgba(255,255,255,.35), rgba(255,255,255,0) 70%)"></div>
        <div class="abs" id="d15a" style="left:84px;top:250px;transform-origin:0 60%"><div class="disp" style="font-size:156px;color:${PAPER};white-space:nowrap">Tinta colada</div></div>
        <div class="abs" id="d15b" style="left:90px;top:404px;transform-origin:0 60%"><div class="disp d-it" style="font-size:156px;color:${PINK};white-space:nowrap">na ponta.</div></div>
        <div class="abs mask" id="d15cm" style="left:92px;top:582px;padding:4px 0 8px"><div id="d15c" style="font-family:Onest;font-weight:600;font-size:44px;color:rgba(246,244,239,.78);white-space:nowrap;font-variant-numeric:tabular-nums">latência mínima · até <span id="d15n" style="color:${PINK};font-weight:800">120</span> Hz</div></div>`;
      this.v = K.viewer(q(root, "#d15v"));
      this.v.el.style.background = "#1A1A1A";
      // tinta vetorial exatamente sobre a gravação (logoMap) — entra no crossfade de 34,3
      this.v.ann.innerHTML = `<svg style="position:absolute;left:0;top:0;width:1px;height:1px;overflow:visible"><g id="d15vec" transform="${LOGO_IN_IMG}" stroke="#FF8AA2" stroke-width="${INK_W}" stroke-linecap="round" stroke-linejoin="round" fill="none"><path d="${P_SPIRAL}"/><path d="${P_ARC}"/></g></svg>`;
      const T = f => K.timeOf(c15, f);
      window.EVENTS.pen.push([32.8, +(T(32) + 1 / 30).toFixed(3), "pen"], [+T(33).toFixed(3), +(T(51) + 1 / 30).toFixed(3), "pen"]);
      window.EVENTS.notes.push([32.8, "corte seco: meio-tempo + acorde de Réb maior aberto; caneta em primeiro plano (aguda na espiral)"],
        [33.2, "golpe curto (na ponta.)"], [33.6, "caneta mais grave no arco; blips do contador 60→120 (6 quadros)"],
        [34.0, "swell reverso com sucção até 34,4 (mergulho no centro da espiral)"]);
    },
    async render(t, root) {
      const cam = cam15(t), f = cam.f;
      const pl = await this.v.set(c15, f, { zoom: cam.S / R5FILL, focus: cam.A, ax: cam.P[0] / 1080, ay: cam.P[1] / 1920, clamp: false, penScale: 1.15 });
      // caneta "grudada" entre quadros de ponta erguida dentro do mesmo traço
      const tc = c15.touches[f], nx = c15.touches[f + 1];
      if (tc && tc.k === "pen" && tc.p[0] && nx && nx.k === "pen") this.v.over.innerHTML = K.cursorHTML({ k: "pen", up: false, p: tc.p }, 1 / pl.s, { penScale: 1.15 });
      const ve = this.v.el;
      ve.style.transformOrigin = `${cam.P[0]}px ${cam.P[1]}px`;
      ve.style.transform = `rotate(${cam.R}deg)`;
      // brilho quente na ponta (só com tinta)
      const tip = q(root, "#d15tip"), inkOn = tc && tc.k === "pen" && tc.p[0] && !(tc.up && !(nx && nx.k === "pen"));
      tip.style.display = inkOn ? "" : "none";
      if (inkOn) { const [sx, sy] = toScreen(cam, tc.p[0][0], tc.p[0][1]); tip.style.left = sx + "px"; tip.style.top = sy + "px"; }
      // linhas de velocidade radiais (15%), mais longas e fortes no mergulho
      const n = Math.floor(t * 15), d = cam.d;
      qa(root, "#d15sp line").forEach((ln, i) => {
        const a = (i + hash(i * 13 + n) * .7) / NSPEED * 2 * PI;
        const r0 = lerp(600 + hash(i + n * 7) * 260, 380 + hash(i + n * 7) * 200, d), len = (90 + hash(i * 3 + n * 5) * 220) * (1 + 2.6 * d);
        const cx = cam.P[0], cy = cam.P[1];
        ln.setAttribute("x1", cx + Math.cos(a) * r0); ln.setAttribute("y1", cy + Math.sin(a) * r0);
        ln.setAttribute("x2", cx + Math.cos(a) * (r0 + len)); ln.setAttribute("y2", cy + Math.sin(a) * (r0 + len));
        ln.setAttribute("opacity", (hash(i + n) > .35 ? 1 : 0) * (.15 + .2 * d));
      });
      // mergulho: fantasmas ampliados = blur radial falso
      const gh = qa(root, ".d15g");
      gh.forEach((g, i) => {
        const gs = 1 + (i + 1) * .07 * d;
        g.setAttribute("transform", `translate(${cam.P[0]},${cam.P[1]}) rotate(${cam.R}) scale(${cam.S * gs}) translate(${-cam.A[0]},${-cam.A[1]})`);
        g.style.opacity = d > .02 ? (.34 - i * .1) * Math.min(1, d * 2.5) : 0;
      });
      // 34,3: a gravação vira o vetor (mesma posição, mesma cor/largura) em 3 quadros
      q(root, "#d15vec").style.opacity = seg(t, 34.27, 34.37);
      // textos
      const a = q(root, "#d15a"), b = q(root, "#d15b"), cm = q(root, "#d15cm");
      reset(a, b, cm);
      K.slam(a, t, 32.8, { from: 1.45 });
      K.slam(b, t, 33.2, { from: 1.5, rot: -4 });
      const c = q(root, "#d15c");
      K.rise(c, t, 33.6);
      const kn = seg(t, 33.6, 33.6 + 6 / 30);
      q(root, "#d15n").textContent = String(Math.round(lerp(60, 120, kn)));
      q(root, "#d15n").style.filter = kn > 0 && kn < 1 ? "url(#blurY20)" : "";
      // 34,0: textos saem em whip para cima (um quadro de defasagem entre linhas)
      K.whipOut(a, t, 34.0, [0, -1], 5 / 30, 1300);
      K.whipOut(b, t, 34.0 + 1 / 30, [0, -1], 5 / 30, 1300);
      K.whipOut(cm, t, 34.0 + 2 / 30, [0, -1], 5 / 30, 1300);
      // leve bounce de meio-tempo na gravação (kick no 1 e no 3)
      const hb = kickEnv(t, [0, .8]);
      q(root, "#d15glow").style.opacity = .85 + .15 * hb;
    },
  });
  K.hit(32.8, .6); K.hit(33.2, .35); K.hit(33.6, .22);

  // =========================================================================================
  // S16 — LOGO (34,4–36,8): match cut da espiral para o miolo da rosa; recuo 14x -> 1x
  // =========================================================================================
  const S16 = { start: 34.4, end: 36.8 };
  const LS0 = S_CUT * LM[2];                    // px de vídeo por unidade da logo no corte (= fim da S15)
  const LS1 = 340 / 108;                        // ícone pousado com 340 px
  const ICON_C = [540, 640];
  /** câmera da logo: ponto A (unidades) vai para P (tela), escala S, giro R */
  function cam16(t) {
    const u = seg(t, 34.4, 35.4), k = expo(u);
    const S = LS0 * Math.pow(LS1 / LS0, k);
    const A = [lerp(55.5, 54, k), lerp(40.5, 54, k)];
    const P = [540, lerp(960, ICON_C[1], k)];
    const R = lerp(2, 0, out3(u));
    // pouso (35,4): "thock" com mola
    const dl = t - 35.4;
    const thock = dl >= 0 ? 1 - .05 * Math.exp(-dl * 9) * Math.sin(dl * 26 + .0) : 1;
    // kick de meio-tempo depois de 36,0 (1 e 3 do compasso)
    const kp = t >= 36.0 ? 1 + .022 * kickEnv(t, [0, .8]) : 1;
    return { S: S * thock * kp, A, P, R, k, u };
  }
  const logoTf = c => `translate(${c.P[0]},${c.P[1]}) rotate(${c.R}) scale(${c.S}) translate(${-c.A[0]},${-c.A[1]})`;
  const NAME = "RosaNotes".split("");
  const NAME_TOP = 864, SLOG_TOP = 1052;
  const NRAYS = 18;

  K.scene({
    id: "s16", ...S16,
    setup(root) {
      injectOnce();
      root.innerHTML = `
        <div class="abs" style="inset:0;background:${PAPER}"></div>
        <div class="abs dots" id="d16d1" style="inset:0"></div><div class="abs dots" id="d16d2" style="inset:0"></div>
        <svg class="abs" id="d16rays" width="1080" height="1920" style="left:0;top:0"><g id="d16rg">${Array.from({ length: NRAYS }, (_, i) => {
          const a0 = i / NRAYS * 2 * PI, a1 = a0 + PI / NRAYS * .8, R = 1900;
          return `<path d="M0,0 L${(Math.cos(a0) * R).toFixed(0)},${(Math.sin(a0) * R).toFixed(0)} L${(Math.cos(a1) * R).toFixed(0)},${(Math.sin(a1) * R).toFixed(0)} Z" fill="${i % 2 ? "rgba(232,64,79,.10)" : "rgba(255,201,209,.42)"}"/>`;
        }).join("")}</g></svg>
        <svg class="abs" id="d16svg" width="1080" height="1920" viewBox="0 0 1080 1920" style="left:0;top:0">
          <g id="d16logo">${logoInner("d16", { draw: true, spec: true })}</g>
          <circle id="d16shock" cx="540" cy="960" r="10" fill="none" stroke="#FFC9D1" stroke-width="40"/>
        </svg>
        <div class="abs" id="d16name" style="left:0;top:${NAME_TOP}px;width:1080px;text-align:center"><div class="disp" style="font-size:160px;color:${INK};white-space:nowrap;display:inline-block">${NAME.map(c => `<span class="d-mask"><span class="d-let">${c}</span></span>`).join("")}</div></div>
        <div class="abs" id="d16slog" style="left:0;top:${SLOG_TOP}px;width:1080px;text-align:center"><div class="mask" style="display:inline-block;padding:0 14px 10px"><div id="d16sl" class="disp d-it" style="font-size:66px;font-weight:600;color:${INK};white-space:nowrap;letter-spacing:-.01em">Escreva <span class="d-hl" id="d16hl" style="color:${RED}">fora da margem.</span></div></div></div>`;
      window.EVENTS.notes.push([34.4, "IMPACTO máximo + primeira resolução em maior (Láb add9), sub 40 Hz, crash, sino, cintilar de pétalas"],
        [34.6, "swish dos traços (haste e perna do R, 34,6–35,0)"], [35.0, "pop agudo da folha (seno 1200→600 Hz)"], [35.4, "thock (ícone pousa)"],
        [36.0, "kick volta em meio-tempo; slogan sobe"]);
      NAME.forEach((c, i) => window.EVENTS.notes.push([+(35.6 + i * .1).toFixed(2), `tic da letra '${c}'`]));
      window.EVENTS.pen.push([36.07, 36.23, "marker"]);
    },
    render(t, root) {
      const c = cam16(t);
      const svg = q(root, "#d16svg");
      const g = q(svg, "#d16logo");
      g.setAttribute("transform", logoTf(c));
      // quadrado bate por trás com mola (1 -> 1,08 -> 1) quando aparece
      const ds = t - 34.47;
      const sq = ds > 0 ? 1 + .2 * Math.exp(-ds * 9) * Math.sin(ds * 15) : 1;
      q(svg, ".d16sq").setAttribute("transform", `translate(54,54) scale(${sq}) translate(-54,-54)`);
      // sombra só quando o ícone já é pequeno (evita filtro gigante)
      const rect = q(svg, ".d16rect");
      if (c.S < 9) {
        rect.setAttribute("filter", "url(#dShadow)");
        const fe = document.getElementById("dShadowF");
        fe.setAttribute("dy", (30 / c.S).toFixed(2)); fe.setAttribute("stdDeviation", (30 / c.S).toFixed(2));
      } else rect.removeAttribute("filter");
      // espiral: da largura da tinta gravada para a da logo
      q(svg, ".d16spir").setAttribute("stroke-width", lerp(INK_W, 2.4, c.k));
      // haste e perna se desenham (34,6–35,0); folha brota em 35,0
      K.draw(q(svg, ".d16stem"), t, 34.6, 34.86, out3);
      K.draw(q(svg, ".d16leg"), t, 34.72, 35.0, out3);
      const kl = seg(t, 35.0, 35.3);
      const ls = kl <= 0 ? 0 : kl < .55 ? lerp(0, 1.15, out3(kl / .55)) : lerp(1.15, 1, inOut((kl - .55) / .45));
      q(svg, ".d16leafg").setAttribute("transform", `translate(61,69) rotate(${lerp(-20, 0, out3(kl))}) scale(${ls}) translate(-61,-69)`);
      // reflexo especular cruzando o ícone (35,4–35,8)
      const ksp = inOut(seg(t, 35.42, 35.85));
      q(svg, ".d16spec").setAttribute("x", lerp(-60, 170, ksp));
      q(svg, ".d16spec").style.display = ksp > 0 && ksp < 1 ? "" : "none";
      // onda de choque
      const shock = q(svg, "#d16shock"), kk = seg(t, 34.4, 34.95);
      shock.style.display = kk < 1 ? "" : "none";
      shock.setAttribute("r", 40 + 1500 * out3(kk));
      shock.setAttribute("stroke-width", 70 * (1 - kk));
      shock.style.opacity = 1 - kk * .6;
      // raios de luz girando atrás, somem em 0,8 s
      const lc = [c.P[0] + (54 - c.A[0]) * c.S, c.P[1] + (54 - c.A[1]) * c.S];
      const rays = q(root, "#d16rays");
      const ko = seg(t, 34.4, 35.2);
      rays.style.display = ko < 1 ? "" : "none";
      rays.style.opacity = 1 - in2(ko);
      q(rays, "#d16rg").setAttribute("transform", `translate(${lc[0]},${lc[1]}) rotate(${(t - 34.4) * 55}) scale(${1 + .15 * ko})`);
      // papel + grade de pontos presa à logo voltando
      worldDots(q(root, "#d16d1"), q(root, "#d16d2"), 46 * c.S / LS1, lc[0], lc[1]);
      // "RosaNotes" sobe letra a letra nas semicolcheias (35,6…)
      qa(root, "#d16name .d-let").forEach((el, i) => K.rise(el, t, 35.6 + i * .1, 6 / 30));
      // slogan (36,0) + marca-texto amarelo atrás de "fora da margem."
      K.rise(q(root, "#d16sl"), t, 36.0, 6 / 30);
      K.hl(q(root, "#d16hl"), t, 36.07, 5 / 30);
    },
  });
  K.hit(34.4, 1); K.flash(34.4, "#FFC9D1", .3, 1); K.burst(34.4, 540, 960, 40, 1.1);
  K.hit(35.4, .3); K.hit(36.0, .25);

  // =========================================================================================
  // S17 — CTA "Comenta ROSA" + loop (36,8–40,0)
  // =========================================================================================
  const S17 = { start: 36.8, end: 40.0 };
  // --- posição do conjunto ícone/nome/slogan (sobe e diminui com mola em 36,8)
  function lockup(t) {
    const u = seg(t, 36.8, 37.15), k = back(u, 1.25);
    const iconS = lerp(LS1, 180 / 108, k), iconY = lerp(ICON_C[1], 318, k);
    const nameSc = lerp(1, .58, k), nameDy = lerp(0, 452 - NAME_TOP, k);
    const slogSc = lerp(1, .62, k), slogDy = lerp(0, 534 - SLOG_TOP, k);
    return { iconS, iconY, nameSc, nameDy, slogSc, slogDy };
  }
  // --- S01 em t<=0 (enquadramento do quadro 0, para o loop sem emenda)
  const c01 = K.clip("r1", [[0, .6, "gancho", "aperta"], [.6, 1.0, "aperta", "socorro"], [1.0, 1.6, "socorro", "socorro"]]);
  const SQ01 = (() => {
    let x0 = 1e9, y0 = 1e9, x1 = -1e9, y1 = -1e9;
    for (let f = K.m("r1", "aperta"); f < K.m("r1", "socorro"); f++) for (const [x, y] of c01.touches[f].p) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
    return { cx: (x0 + x1) / 2, cy: (y0 + y1) / 2 };
  })();
  const EDGE01 = window.REC.r1.markers.margem[0];
  function cam01() {
    const f = K.frameAt(c01, 0);
    const focus = K.focus(c01, f, 10, 4, K.m("r1", "gancho"), K.m("r1", "socorro") - 1) || [SQ01.cx, SQ01.cy];
    return { f, cam: { zoom: 2.2, focus: [Math.min(focus[0], EDGE01 - 220), focus[1]], ax: .56, ay: .66, penScale: 1.15 } };
  }
  const GAP = 400, VX = 1080 + GAP;             // a gravação fica à direita do CTA, depois de um trecho de papel
  /** posição do "trilho" no chicote (0 -> -VX), 39,2–39,9, pousando com ease-out */
  const whipK = t => {
    const u = seg(t, 39.2, 39.9);
    return u < .38 ? .5 * in3(u / .38) : .5 + .5 * out5((u - .38) / .62);
  };
  const UL_D = "M930,1052 C820,1046 700,1062 590,1056 C480,1050 380,1066 282,1058 C220,1053 168,1036 176,1004 C184,976 226,986 214,1026 C204,1064 150,1092 104,1104 C10,1128 -150,1150 -420,1170";

  K.scene({
    id: "s17", ...S17,
    setup(root) {
      injectOnce();
      root.innerHTML = `
        <div class="abs" style="inset:0;background:${PAPER}"></div>
        <div class="abs" id="d17strip" style="left:0;top:0;width:${VX + 1080}px;height:1920px">
          <div class="abs" id="d17world" style="left:0;top:0;width:${VX + 200}px;height:1920px;transform-origin:540px 960px">
            <div class="abs" style="left:0;top:0;width:${VX + 200}px;height:1920px;background:${PAPER}"></div>
            <div class="abs dots" id="d17dots" style="left:0;top:0;width:${VX + 200}px;height:1920px"></div>
            <div class="abs" id="d17margin" style="left:108px;top:0;width:5px;height:1920px;background:#E8404F;transform-origin:50% 0"></div>
            <svg class="abs" id="d17logo" width="1080" height="1920" style="left:0;top:0"><g id="d17lg">${logoInner("d17")}</g></svg>
            <div class="abs" id="d17name" style="left:0;top:${NAME_TOP}px;width:1080px;text-align:center;transform-origin:540px 70px"><div class="disp" style="font-size:160px;color:${INK};white-space:nowrap;display:inline-block">RosaNotes</div></div>
            <div class="abs" id="d17slog" style="left:0;top:${SLOG_TOP}px;width:1080px;text-align:center;transform-origin:540px 40px"><div style="display:inline-block;padding:0 14px 10px"><div class="disp d-it" style="font-size:66px;font-weight:600;color:${INK};white-space:nowrap;letter-spacing:-.01em">Escreva <span class="d-hl" style="color:${RED};--hl:1">fora da margem.</span></div></div></div>
            <div class="abs" id="d17cta" style="left:0;top:0;width:1080px;height:1920px;transform-origin:540px 900px">
              <div class="abs" id="d17c1" style="left:0;top:640px;width:1080px;text-align:center;transform-origin:540px 70px"><div class="disp" style="font-size:156px;color:${INK};white-space:nowrap;display:inline-block">Comenta</div></div>
              <div class="abs" id="d17c2" style="left:0;top:790px;width:1080px;text-align:center;transform-origin:540px 120px"><div class="disp d-it" style="font-size:250px;color:${RED};white-space:nowrap;display:inline-block;letter-spacing:-.02em"><span class="d-hl" id="d17hl">ROSA</span></div></div>
              <svg class="abs" id="d17bub" width="150" height="140" viewBox="0 0 150 140" style="left:800px;top:560px;overflow:visible;transform-origin:30px 120px">
                <path d="M30,8 H122 C136,8 144,16 144,30 V82 C144,96 136,104 122,104 H64 L30,132 L36,104 H30 C16,104 8,96 8,82 V30 C8,16 16,8 30,8 Z" fill="#E8404F" stroke="${INK}" stroke-width="6" stroke-linejoin="round"/>
                <circle class="d17bd" cx="48" cy="56" r="10" fill="#fff"/><circle class="d17bd" cx="76" cy="56" r="10" fill="#fff"/><circle class="d17bd" cx="104" cy="56" r="10" fill="#fff"/>
              </svg>
              <div class="abs mask" id="d17lm" style="left:0;top:1112px;width:1080px;text-align:center;padding-bottom:8px"><div id="d17l" style="font-family:Onest;font-weight:600;font-size:50px;color:rgba(27,27,31,.8);white-space:nowrap">que eu te mando o link</div></div>
              <div class="abs" id="d17pw" style="left:0;top:1222px;width:1080px;height:100px"><div class="d-pill" id="d17pill" style="left:50%;top:0;transform-origin:50% 50%">Para Galaxy com S Pen<div class="d-glint" id="d17gl"></div></div></div>
              <svg class="abs" width="1080" height="1920" style="left:0;top:0;overflow:visible"><path id="d17ul" d="${UL_D}" fill="none" stroke="${RED}" stroke-width="15" stroke-linecap="round" stroke-linejoin="round"/></svg>
              <div class="abs" id="d17pen" style="left:0;top:0;width:0;height:0"></div>
            </div>
          </div>
          <div id="d17v" style="left:${VX}px;top:0;width:1080px;height:1920px"></div>
        </div>
        <div class="abs" id="d17s01" style="left:84px;top:250px">
          <div class="sticker" style="padding:18px 34px 26px;transform:rotate(-1.5deg)">
            <div class="disp" id="d17nao" style="font-size:250px;color:var(--ink);position:relative">NÃO</div>
            <div style="opacity:0">
              <div class="disp" style="font-size:150px">ESCREVA</div>
              <div class="disp" style="font-size:150px">FORA DA</div>
              <div class="disp" style="font-size:150px">MARGEM.</div>
            </div>
          </div>
        </div>`;
      this.v = K.viewer(q(root, "#d17v"));
      window.EVENTS.notes.push([36.8, "groove leve volta (kick, palma, cowbell) com passa-baixa abrindo 1,2→8 kHz; zíper da margem"],
        [36.93, "slam de ROSA + chiado curto de marca-texto"], [37.2, "pop do balão (pulsa em cada tempo)"], [37.6, "pop da pílula"],
        [38.0, "caneta do sublinhado com pan da direita para a esquerda até sair (38,0–38,8)"],
        [39.2, "prato reverso + chiado de caneta crescendo até 40,0 (whip para a gravação)"], [40.0, "corte seco no downbeat = quadro 0 (loop)"]);
      window.EVENTS.pen.push([38.0, 38.8, "pen"], [37.0, 37.13, "marker"]);
    },
    async render(t, root) {
      const fi = T30(t);
      // ---------- trilho: chicote para a direita ao longo da grade e pouso no quadro 0 da R1
      const wk = whipK(t), wk1 = whipK(t - 1 / 30);
      const X = -VX * wk, vel = (wk - wk1) * VX;           // px de vídeo por quadro
      const strip = q(root, "#d17strip");
      strip.style.transform = `translateX(${X}px)`;
      const bx = Math.min(40, Math.abs(vel) * .45);
      if (bx > .8) { document.getElementById("dWhipB").setAttribute("stdDeviation", `${bx.toFixed(1)} 0`); strip.style.filter = "url(#dWhip)"; }
      else strip.style.filter = "none";
      // ---------- gravação R1 (só quando entra na tela)
      const vOn = t >= 39.45;
      this.v.el.style.display = vOn ? "" : "none";
      if (vOn) { const o = cam01(); await this.v.set(c01, o.f, o.cam); }
      // ---------- adesivo NÃO da S01 (o slam começa 2 quadros antes do 0, como na S01)
      const s01 = q(root, "#d17s01"), tt = (fi - 1200) / 30;
      s01.style.display = tt >= -2 / 30 ? "" : "none";
      if (tt >= -2 / 30) K.slam(q(root, "#d17nao"), tt, -2 / 30, { from: 1.4 });
      // ---------- câmera do CTA: dolly 1 -> 1,03 (38,8–39,2) e segue até o chicote
      const world = q(root, "#d17world");
      world.style.transform = `scale(${1 + .03 * inOut(seg(t, 38.8, 39.2)) + .02 * in2(seg(t, 39.2, 39.6))})`;
      const dd = q(root, "#d17dots");
      dd.style.backgroundSize = "46px 46px"; dd.style.backgroundPosition = `${540 - 23}px ${ICON_C[1] - 23}px`;
      // ---------- margem vermelha se desenha de cima para baixo (zíper)
      q(root, "#d17margin").style.transform = `scaleY(${out3(seg(t, 36.8, 37.12))})`;
      // ---------- ícone + nome + slogan sobem e diminuem com mola
      const L = lockup(t);
      const lg = q(root, "#d17lg");
      const ib = t >= 37.2 ? 1 + .03 * beatEnv(t) : 1;
      lg.setAttribute("transform", `translate(540,${L.iconY}) scale(${L.iconS * ib}) translate(-54,-54)`);
      q(root, ".d17rect").setAttribute("filter", "url(#dShadow)");
      const fe = document.getElementById("dShadowF"), shp = lerp(30, 18, clamp(seg(t, 36.8, 37.15)));
      fe.setAttribute("dy", (shp / L.iconS).toFixed(2)); fe.setAttribute("stdDeviation", (shp / L.iconS).toFixed(2));
      q(root, "#d17name").style.transform = `translateY(${L.nameDy}px) scale(${L.nameSc})`;
      q(root, "#d17slog").style.transform = `translateY(${L.slogDy}px) scale(${L.slogSc})`;
      // ---------- Comenta / ROSA (+ faixa amarela em 4 quadros), bounce no kick
      const c1 = q(root, "#d17c1"), c2 = q(root, "#d17c2");
      reset(c1, c2);
      K.slam(c1, t, 36.8, { from: 1.45 });
      K.slam(c2, t, 36.8 + 4 / 30, { from: 1.6, rot: -5 });
      K.hl(q(root, "#d17hl"), t, 37.0, 4 / 30);
      const cta = q(root, "#d17cta");
      cta.style.transform = `scale(${bounce(t, .02)})`;
      // ---------- balão de comentário: pulsa em cada tempo, "digitando…"
      const bub = q(root, "#d17bub");
      const kb = back(seg(t, 37.2, 37.45), 2);
      bub.style.opacity = t >= 37.2 ? 1 : 0;
      bub.style.transform = `scale(${Math.max(0, kb) * (1 + .12 * (t >= 37.6 ? beatEnv(t) : 0))}) rotate(${lerp(-25, 6, clamp(kb)) + 3 * Math.sin(t * 5)}deg)`;
      qa(root, ".d17bd").forEach((dd, i) => dd.setAttribute("cy", 56 - 9 * Math.max(0, Math.sin((t * 2.5 - i * .18) * 2 * PI))));
      // ---------- "que eu te mando o link" (37,2) e pílula (37,6) com brilho
      K.rise(q(root, "#d17l"), t, 37.2);
      const pill = q(root, "#d17pill");
      const pw = pill.offsetWidth || 520;
      K.stamp(pill, t, 37.6, { base: `translateX(${-pw / 2}px)`, rot: -3 });
      const gk = (t - 37.7) % 1.2, gl = q(root, "#d17gl");
      gl.style.left = `${lerp(-120, pw + 60, out3(clamp(gk / .45)))}px`;
      gl.style.display = t >= 37.7 && gk < .45 ? "" : "none";
      // ---------- S Pen: entra pela direita, sublinha ROSA, cruza a margem e sai pela esquerda
      const ul = q(root, "#d17ul"), Lt = ul.getTotalLength();
      const kd = seg(t, 38.0, 38.8), pd = kd < .1 ? .1 * in2(kd / .1) * 1 : .1 + .9 * ((kd - .1) / .9) ** 1.12;
      const pk = t < 38.0 ? 0 : pd;
      ul.style.strokeDasharray = `${Lt} ${Lt + 10}`;
      ul.style.strokeDashoffset = Lt * (1 - pk);
      ul.style.visibility = t >= 38.0 ? "" : "hidden";
      const pen = q(root, "#d17pen");
      let pp = null;
      if (t >= 37.82 && t < 38.0) {                         // aproximação pairando, vinda de fora da tela
        const ka = out3(seg(t, 37.82, 38.0)), p0 = ul.getPointAtLength(0);
        pp = [lerp(1320, p0.x, ka), lerp(1180, p0.y, ka)];
      } else if (t >= 38.0 && kd < 1) { const p = ul.getPointAtLength(Lt * pk); pp = [p.x, p.y]; }
      pen.innerHTML = pp ? K.cursorHTML({ k: "pen", up: false, p: [pp] }, 1.12) : "";
    },
  });
  K.hit(36.8, .6); K.hit(36.8 + 4 / 30, .4); K.hit(37.2, .25); K.hit(37.6, .3);
})();

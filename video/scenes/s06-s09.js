// Cenas S06–S09 (gravação R2): marca-texto que se ajusta à letra, barra em pé + 3 cores,
// seis pincéis em ladrilhos, laço (circula/move/gira/aumenta) e borracha.
// Tudo é função pura de t: nenhuma variável guarda estado entre quadros.
(function () {
  const { clamp, seg, lerp, out3, out5, in3, inOut, back, hash } = E;
  const R = window.REC.r2, IW = R.info.width, IH = R.info.height, AN = R.anchors;
  const FILL = 1080 / IW;                       // escala do visor em tela cheia com zoom 1
  const RED = "#C2253B", INK = "#1B1B1F", APPRED = "#D7263D", BLUE = "#1F5FD1", YEL = "#FFE45C";
  const M = n => K.m("r2", n);

  // ---- Toques limpos: o toque sintético em [48,48] (artefato do quickColors) repete o anterior
  const T2 = [];
  {
    let last = null;
    for (const tc of R.touches) {
      const p = tc.p[0];
      if (tc.k === "tap" && p && p[0] === 48 && p[1] === 48 && last) T2.push({ k: "tap", up: tc.up, p: [last] });
      else { T2.push(tc); if (tc.k === "tap" && p) last = p; }
    }
  }
  const clip = parts => Object.assign(K.clip("r2", parts), { touches: T2 });

  // ---- Câmera: centro em px da gravação, preso para o quadro nunca sair da imagem ---------
  const cc = (zoom, cx, cy) => {
    const s = FILL * zoom, hw = 540 / s, hh = 960 / s;
    return [zoom, hw * 2 >= IW ? IW / 2 : Math.max(hw, Math.min(IW - hw, cx)), hh * 2 >= IH ? IH / 2 : Math.max(hh, Math.min(IH - hh, cy))];
  };
  const mix = (p, q, k) => [lerp(p[0], q[0], k), lerp(p[1], q[1], k), lerp(p[2], q[2], k)];
  const camOf = (P, extra = {}) => { const c = cc(P[0], P[1], P[2]); return Object.assign({ zoom: c[0], focus: [c[1], c[2]], ax: .5, ay: .5, clamp: false }, extra); };
  /** velocidade da câmera (px de vídeo por quadro) — para o borrão de movimento */
  const camVel = (fn, t) => {
    const a = cc(...fn(t)), b = cc(...fn(t - 1 / 30)), s = FILL * a[0];
    return [(a[1] - b[1]) * s, (a[2] - b[2]) * s, Math.abs(Math.log(a[0] / b[0])) * 1000];
  };
  const blurOf = (vx, vy) => {
    const f = [];
    const ax = Math.abs(vx), ay = Math.abs(vy);
    if (ax > 70) f.push(`url(#blurX${ax > 160 ? 40 : 20})`);
    if (ay > 70) f.push(`url(#blurY${ay > 160 ? 40 : 20})`);
    return f.join(" ");
  };
  const proj = (pl, x, y) => [pl.tx + x * pl.s, pl.ty + y * pl.s];

  // ---- Batida: kick do funk (3+3+2) — bounce de +2,5% decaindo em ~6 quadros -------------
  const KICKS = [0, .3, .6, .8, 1.1, 1.4];
  const kick = t => { const p = ((t % 1.6) + 1.6) % 1.6; let d = 9; for (const o of KICKS) if (p >= o - 1e-6) d = Math.min(d, p - o); return Math.exp(-Math.max(0, d) * 16); };
  const bounce = t => 1 + .025 * kick(t);

  // ---- Anel de toque (no espaço da gravação, tamanho fixo em px de vídeo) ---------------
  const ring = (el, t, at, x, y, inv, color = "#fff") => {
    const k = seg(t, at, at + .32);
    const on = t >= at && k < 1;
    el.style.display = on ? "" : "none";
    if (!on) return;
    el.style.left = x + "px"; el.style.top = y + "px";
    el.style.transform = `translate(-50%,-50%) scale(${inv * lerp(.45, 1.9, out3(k))})`;
    el.style.opacity = 1 - in3(k);
    el.style.borderColor = color;
  };
  /** 1 na janela [a,b), 0 fora */
  const on = (t, a, b) => t >= a && t < b;
  const show = (el, v) => { el.style.display = v ? "" : "none"; return v; };

  // ---- CSS do bloco ------------------------------------------------------------------
  const CSS = `
  .b-full { position: absolute; left: 0; top: 0; width: 1080px; height: 1920px; }
  .b-st { position: absolute; background: rgba(246,244,239,.96); box-shadow: 6px 6px 0 rgba(27,27,31,.25); padding: 6px 26px 16px; white-space: nowrap; }
  .b-pill { position: absolute; font-family: Onest; font-weight: 800; text-transform: uppercase; letter-spacing: .1em; font-size: 48px; line-height: 1; color: #F6F4EF; background: #1B1B1F; border-radius: 999px; padding: 14px 30px 15px; white-space: nowrap; box-shadow: 5px 5px 0 rgba(194,37,59,.9); }
  .b-ring { position: absolute; width: 130px; height: 130px; border-radius: 50%; border: 9px solid #fff; box-shadow: 0 0 0 3px rgba(27,27,31,.28), inset 0 0 0 3px rgba(27,27,31,.2); display: none; }
  .b-verb { font-family: Fraunces; font-weight: 900; font-size: 146px; letter-spacing: -.03em; line-height: 1; color: #fff; white-space: nowrap;
    background: #1B1B1F; padding: 8px 30px 24px 26px; box-shadow: 8px 8px 0 rgba(194,37,59,.95); text-shadow: 8px 8px 0 #C2253B; }
  .b-tile { position: absolute; border-radius: 28px; overflow: hidden; background: #fff; box-shadow: 0 18px 40px -16px rgba(27,27,31,.45), 0 0 0 2px rgba(27,27,31,.08); }
  .b-lab { position: absolute; left: 16px; top: 14px; z-index: 3; font-family: Onest; font-weight: 800; text-transform: uppercase; letter-spacing: .1em; font-size: 30px; line-height: 1; padding: 9px 16px 10px; border-radius: 12px; background: rgba(246,244,239,.95); box-shadow: 3px 3px 0 rgba(27,27,31,.18); white-space: nowrap; }
  .b-mask { overflow: hidden; display: inline-block; vertical-align: top; padding: 0 .08em .06em; margin: 0 -.08em; }
  .b-mask > span { display: inline-block; }
  .b-dust { position: absolute; border-radius: 40% 60% 55% 45%; background: #1B1B1F; }
  `;
  const injectCSS = () => { if (document.getElementById("b-css")) return; const st = document.createElement("style"); st.id = "b-css"; st.textContent = CSS; document.head.appendChild(st); };

  // ===================================================================================
  // S06 — Marca-texto: botão próprio, faixa fina na letra pequena, alta na letra grande
  // ===================================================================================
  const S06 = { start: 9.6, end: 12.8 };
  const c06 = clip([[9.6, 9.8, 0, 2], [9.8, 10.3, 2, 11], [10.3, 10.4, 11, 12], [10.4, 11.0, 12, 30], [11.0, 11.2, 30, 35], [11.2, 11.9, 36, 57], [11.9, 12.8, 57, 66]]);
  const THIN = { x0: 73, x1: 764, y0: 1128, y1: 1187 }, TALL = { x0: 71, x1: 1061, y0: 814, y1: 952 };
  const MK = [AN.marca[0] + AN.marca[2] / 2, AN.marca[1] + AN.marca[3] / 2];
  const penX06 = (f, lo, hi, def) => { const p = K.focus(c06, f, 8, 3, lo, hi); return p ? p[0] : def; };
  /** plano de câmera de S06 em [zoom, cx, cy] (antes do bounce) */
  function plan06(t) {
    const f = K.frameAt(c06, t);
    const pA = [1.12 + .14 * out3(seg(t, 9.6, 9.8)), 520, 930];
    const pB = [lerp(1.85, 1.97, seg(t, 9.95, 10.4)), 300, 282];
    const pD = [lerp(1.5, 1.56, seg(t, 10.55, 11.2)), penX06(Math.max(f, 12), 12, 29, 112) + 110, 1233];
    const pF = [lerp(1.5, 1.55, seg(t, 11.36, 12)), penX06(Math.max(f, 36), 36, 56, 121) + 110, 1083];
    const pH = [1.05, 600, 1250];
    if (t < 9.8) return pA;
    if (t < 10.4) return mix(cc(...pA), cc(...pB), out5(seg(t, 9.8, 9.95)));
    if (t < 11.2) return mix(cc(...pB), cc(...pD), out5(seg(t, 10.4, 10.56)));
    if (t < 12.0) return mix(cc(...pD), cc(...pF), out5(seg(t, 11.2, 11.37)));
    return mix(cc(...pF), cc(...pH), out5(seg(t, 12.0, 12.24)));
  }
  const zoomFx06 = t => bounce(t) * (t >= 12.4 ? 1 + .06 * back(seg(t, 12.4, 12.5), 2) : 1);
  /** colchete-régua em px de vídeo */
  const bracketD = (x, y0, y1) => {
    let d = `M${x},${y0} L${x},${y1} M${x - 30},${y0} L${x + 10},${y0} M${x - 30},${y1} L${x + 10},${y1}`;
    const h = y1 - y0, n = Math.max(2, Math.round(h / 16));
    for (let i = 1; i < n; i++) { const y = y0 + h * i / n; d += ` M${x - (i % 4 === 0 ? 18 : 10)},${y} L${x},${y}`; }
    return d;
  };

  K.scene({
    id: "s06", ...S06,
    setup(root) {
      injectCSS();
      root.innerHTML = `
        <div class="b-full dots" style="background-color:#F6F4EF"></div>
        <div class="b-full" id="b6all">
          <div class="viewer" id="b6v" style="left:0;top:0;width:1080px;height:1920px"></div>
          <div class="viewer" id="b6pop" style="border-radius:50%;box-shadow:0 14px 30px -8px rgba(27,27,31,.5),0 0 0 4px #fff;display:none"></div>
          <div class="b-full" id="b6band" style="mix-blend-mode:multiply;background:rgba(255,228,92,.82);pointer-events:none"></div>
          <svg class="b-full" id="b6br" style="overflow:visible" viewBox="0 0 1080 1920">
            <path id="b6brA" d="" fill="none" stroke="${RED}" stroke-width="5" stroke-linecap="round"/>
            <path id="b6brB" d="" fill="none" stroke="${RED}" stroke-width="5" stroke-linecap="round"/>
          </svg>
          <div class="b-pill" id="b6pA"><span id="b6pAt">letra pequena</span></div>
          <div class="b-pill" id="b6pB">letra pequena</div>
          <div class="abs" id="b6tw" style="left:84px;top:1010px"><div class="b-st" id="b6t" style="position:relative;transform-origin:30% 60%;rotate:-2deg"><span class="disp" style="font-size:130px">Marca-texto</span></div></div>
          <div class="abs" id="b6end" style="left:96px;top:1150px">
            <div class="b-st" id="b6endst" style="position:relative;rotate:-1.5deg;padding:14px 30px 24px">
              <div class="disp" style="font-size:112px"><span class="b-mask"><span id="b6w1">Ele</span></span> <span class="b-mask"><span id="b6w2">se</span></span> <span class="b-mask"><span id="b6w3">ajusta</span></span></div>
              <div class="disp" style="font-size:140px;color:${RED};margin-top:6px"><span class="b-mask" style="padding-top:.06em"><span id="b6w4"><span class="hl" id="b6hl" style="--hlc:rgba(255,228,92,.9)"><i>à letra.</i></span></span></span></div>
            </div>
          </div>
        </div>`;
      this.v = K.viewer(root.querySelector("#b6v"));
      this.pop = K.viewer(root.querySelector("#b6pop"));
      // anotação "botão próprio" no espaço da gravação (traço Caveat vermelho)
      const [mx, my] = MK;
      this.v.ann.innerHTML = `
        <svg style="position:absolute;left:0;top:0;width:1px;height:1px;overflow:visible">
          <ellipse id="b6circ" pathLength="1" cx="${mx}" cy="${my}" rx="88" ry="76" transform="rotate(-12 ${mx} ${my})" fill="none" stroke="${RED}" stroke-width="7" stroke-linecap="round"/>
          <path id="b6arr" pathLength="1" d="M ${mx + 70} ${my + 196} C ${mx + 60} ${my + 160}, ${mx + 40} ${my + 128}, ${mx + 22} ${my + 92}" fill="none" stroke="${RED}" stroke-width="6" stroke-linecap="round"/>
          <path id="b6arh" pathLength="1" d="M ${mx + 4} ${my + 120} L ${mx + 20} ${my + 89} L ${mx + 48} ${my + 108}" fill="none" stroke="${RED}" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <div class="hand" id="b6bp" style="position:absolute;left:${mx - 30}px;top:${my + 196}px;font-size:44px;line-height:1;color:${RED};transform:rotate(-6deg);transform-origin:0 50%;white-space:nowrap">botão próprio</div>
        <div class="b-ring" id="b6ring"></div>`;
      // eventos para a trilha
      const tm = f => +K.timeOf(c06, f).toFixed(3);
      window.EVENTS.taps.push(tm(3));
      window.EVENTS.pen.push([tm(12), tm(29), "marker"], [tm(36), tm(56), "marker"]);
      window.EVENTS.notes.push([9.6, "squeak gigante de marca-texto + crash (varredura amarela)"], [9.9, "clique do botão do marca-texto"],
        [10.0, "rabisco de marcador (anotação 'botão próprio')"], [11.0, "tic-tic de régua (colchete aparece)"], [11.9, "tic-tic de régua (colchete cresce com mola)"], [12.0, "pop"], [12.4, "clap + punch-in"]);
    },
    async render(t, root) {
      const f = K.frameAt(c06, t);
      const P = plan06(t);
      const camP = camOf([P[0] * zoomFx06(t), P[1], P[2]]);
      const pl = await this.v.set(c06, f, camP);
      const inv = 1 / pl.s;
      // borrão de movimento nos chicotes da câmera
      const [vx, vy] = camVel(plan06, t);
      this.v.inner.style.filter = blurOf(vx, vy);

      // 9,6–9,8: varredura gigante de marca-texto revela R2
      const band = root.querySelector("#b6band");
      if (show(band, t < 9.8)) {
        const L = lerp(330, 2150, 1 - Math.pow(1 - seg(t, 9.6, 9.8 - 1 / 30), 1.6));
        band.style.clipPath = `polygon(${L - 820 + 135}px 0, ${L + 135}px 0, ${L - 135}px 1920px, ${L - 820 - 135}px 1920px)`;
        this.v.el.style.clipPath = `polygon(0 0, ${L + 135}px 0, ${L - 135}px 1920px, 0 1920px)`;
      } else this.v.el.style.clipPath = "";

      // toque no botão: anel + ícone que pula
      const tTap = K.timeOf(c06, 3);
      ring(this.v.ann.querySelector("#b6ring"), t, tTap, MK[0], MK[1], inv, "#fff");
      const popEl = this.pop.el;
      if (show(popEl, on(t, tTap, 10.4))) {
        const k = seg(t, tTap, tTap + .3);
        const ps = 1 + .42 * Math.sin(Math.PI * out3(k)) * (1 - k * .3);
        const size = 128 * pl.s;
        const [cx, cy] = proj(pl, MK[0], MK[1]);
        popEl.style.width = size + "px"; popEl.style.height = size + "px";
        popEl.style.left = cx - size / 2 + "px"; popEl.style.top = cy - size / 2 + "px";
        popEl.style.transform = `scale(${k >= 1 ? 1 : ps}) rotate(${Math.sin(k * 9) * 8 * (1 - k)}deg)`;
        popEl.style.opacity = k >= 1 ? 0 : 1;
        await this.pop.set(c06, f, { zoom: pl.s / (size / IW), focus: MK, clamp: false, cursors: false });
      }
      // anotação: círculo 10,0 / seta + "botão próprio" 10,05–10,3
      K.draw(this.v.ann.querySelector("#b6circ"), t, 10.0, 10.0 + 5 / 30);
      K.draw(this.v.ann.querySelector("#b6arr"), t, 10.07, 10.07 + 4 / 30);
      K.draw(this.v.ann.querySelector("#b6arh"), t, 10.2, 10.2 + 2 / 30);
      const bp = this.v.ann.querySelector("#b6bp");
      bp.style.opacity = t >= 10.1 ? 1 : 0;
      K.wipe(bp, t, 10.1, 10.32);

      // título "Marca-texto": slam no 9,6; chicoteia para cima com a câmera no 10,4
      const tw = root.querySelector("#b6tw");
      K.slam(root.querySelector("#b6t"), t, 9.6, { from: 1.45 });
      if (t < 10.4) { tw.style.translate = ""; tw.style.filter = ""; tw.style.opacity = 1; }
      K.whipOut(tw, t, 10.4, [0, -1], 5 / 30, 1500);

      // colchete-régua: aparece em 11,0 na faixa fina; cresce com mola até a faixa alta em 11,9
      const brA = root.querySelector("#b6brA"), brB = root.querySelector("#b6brB");
      const pA = root.querySelector("#b6pA"), pB = root.querySelector("#b6pB");
      const tA = t >= 11.0;
      show(brA, tA); show(pA, tA);
      if (tA) {
        const u = back(seg(t, 11.9, 12.08), 2.1);
        const rx = lerp(THIN.x1 + 52, TALL.x1 + 40, u);
        const ry0 = lerp(THIN.y0, TALL.y0, u), ry1 = lerp(THIN.y1, TALL.y1, u);
        const [x, y0] = proj(pl, rx, ry0), [, y1] = proj(pl, rx, ry1);
        const g = out5(seg(t, 11.0, 11.0 + 4 / 30));
        brA.setAttribute("d", bracketD(x, y0, lerp(y0, y1, g)));
        // selo
        const big = t >= 11.95;
        root.querySelector("#b6pAt").textContent = big ? "letra grande" : "letra pequena";
        const w = pA.offsetWidth;
        const px = Math.max(90, Math.min(950 - w, x - w / 2 - 10));
        pA.style.left = px + "px"; pA.style.top = y1 + 30 + "px";
        K.stamp(pA, t, big ? 11.95 : 11.03, { rot: big ? 3 : -3 });
      }
      // 12,0+: o colchete da letra pequena volta, para comparar as duas
      const tB = t >= 12.08;
      show(brB, tB); show(pB, tB);
      if (tB) {
        const [x, y0] = proj(pl, THIN.x1 + 52, THIN.y0), [, y1] = proj(pl, THIN.x1 + 52, THIN.y1);
        brB.setAttribute("d", bracketD(x, y0, lerp(y0, y1, out5(seg(t, 12.08, 12.2)))));
        const w = pB.offsetWidth;
        pB.style.left = Math.max(90, Math.min(950 - w, x - w / 2 - 10)) + "px"; pB.style.top = y1 + 30 + "px";
        K.stamp(pB, t, 12.12, { rot: -3 });
      }

      // "Ele se ajusta à letra." — RISE palavra a palavra, faixa amarela no clap 12,4
      const endEl = root.querySelector("#b6end");
      if (show(endEl, t >= 12.0)) {
        K.stamp(root.querySelector("#b6endst"), t, 12.0, { rot: -3 });
        ["#b6w1", "#b6w2", "#b6w3", "#b6w4"].forEach((id, i) => K.rise(root.querySelector(id), t, 12.0 + i * 2 / 30 + (i === 3 ? 2 / 30 : 0)));
        K.hl(root.querySelector("#b6hl"), t, 12.4, 4 / 30);
      }

      // saída: chicote diagonal para S07
      const all = root.querySelector("#b6all");
      const wk = in3(seg(t, 12.7, 12.8));
      all.style.translate = `${-wk * 900}px ${-wk * 1300}px`;
      all.style.filter = wk > .05 ? `url(#blurX${wk > .4 ? 40 : 20}) url(#blurY${wk > .4 ? 40 : 20})` : "";
    },
  });
  K.hit(9.6, .6);
  K.flash(9.6, "#FFF6C2", .35, 1);
  K.hit(10.4, .3);
  K.hit(11.0, .25);
  K.hit(11.9, .35);
  K.hit(12.4, .4);

  // ===================================================================================
  // S07 — Barra no caminho? Arrasta pra borda. Ela fica em pé. 3 cores a um toque.
  // ===================================================================================
  const S07 = { start: 12.8, end: 15.2 };
  const c07 = clip([[12.8, 13.6, 67, 88], [13.6, 14.0, 88, 93], [14.0, 14.4, 94, 120], [14.4, 14.8, 120, 140], [14.8, 15.2, 140, 157]]);
  const BAR = AN.barraEmPe;                         // [x, y, w, h] da barra em pé
  const DOTS = [[108, 1782, INK, "#B9B9C2"], [108, 1902, APPRED, "#FFB3BD"], [108, 2022, BLUE, "#AFC6F5"]];
  function plan07(t) {
    const f = K.frameAt(c07, t);
    if (t < 13.6) {
      const p = K.focus(c07, Math.max(67, f), 7, 3, 67, 87) || [63, 293];
      return [1.3, 300, p[1] + 260];
    }
    const pImp = [1.12, 550, 1400];
    if (t < 14.0) {
      const p = K.focus(c07, 87, 7, 3, 67, 87);
      return mix(cc(1.3, 300, p[1] + 260), cc(...pImp), out5(seg(t, 13.6, 13.73)));
    }
    return [lerp(1.4, 1.46, seg(t, 14.0, 15.2)), 425, 1700];
  }
  const tintOf = t => t >= 14.8 ? BLUE : t >= 14.4 ? APPRED : INK;

  K.scene({
    id: "s07", ...S07,
    setup(root) {
      injectCSS();
      root.innerHTML = `
        <div class="b-full dots" style="background-color:#F6F4EF"></div>
        <div class="b-full" id="b7all">
          <div class="viewer" id="b7v" style="left:0;top:0;width:1080px;height:1920px"></div>
          <div class="b-full" id="b7tint" style="mix-blend-mode:multiply;pointer-events:none"></div>
          <div class="abs" id="b7out" style="border:9px solid #FF8C99;border-radius:30px;box-shadow:0 0 0 4px ${RED},0 0 40px 6px rgba(232,64,79,.7);display:none"></div>
          <div class="b-full" id="b7glow" style="pointer-events:none"></div>
          <div class="abs" id="b7a" style="left:290px;top:870px"><div class="b-st" style="position:relative;rotate:-2deg"><span class="disp" style="font-size:110px;line-height:1.02">Barra no<br>caminho?</span></div></div>
          <div class="abs" id="b7b" style="left:330px;top:1150px"><div class="b-st" style="position:relative;rotate:1.5deg"><span class="disp" style="font-size:110px;line-height:1.02">Arrasta pra<br><i style="color:${RED}">borda.</i></span></div></div>
          <div class="abs" id="b7c1" style="left:404px;top:1094px"><div class="b-st" id="b7c1s" style="position:relative;rotate:-1deg"><span class="disp" style="font-size:112px">Ela fica</span></div></div>
          <div class="abs" id="b7c2" style="left:214px;top:1236px;transform-origin:0 0"><div class="b-st" style="position:relative;padding:4px 24px 12px"><span class="disp" style="font-size:150px;color:${RED}"><i>em pé.</i></span></div></div>
          <div class="abs" id="b7d" style="left:300px;top:300px"><div class="b-st" style="position:relative;rotate:-1.5deg"><span class="disp" style="font-size:120px"><span id="b7three">3</span> cores</span></div></div>
          <div class="abs" id="b7e" style="left:330px;top:470px"><div class="b-st" style="position:relative;rotate:1deg"><span class="disp" style="font-size:112px"><i>a um toque.</i></span></div></div>
        </div>`;
      this.v = K.viewer(root.querySelector("#b7v"));
      this.v.ann.innerHTML = `<div class="b-ring" id="b7r0"></div><div class="b-ring" id="b7r1"></div><div class="b-ring" id="b7r2"></div><div class="b-ring" id="b7r3"></div>`;
      const tm = f => +K.timeOf(c07, f).toFixed(3);
      window.EVENTS.taps.push(tm(67), 14.0, 14.4, 14.8);
      window.EVENTS.pen.push([tm(98), tm(119), "pen"], [tm(124), tm(139), "pen"], [tm(144), tm(150), "pen"]);
      window.EVENTS.notes.push([12.8, "whoosh diagonal + deslize com fricção (barra sendo arrastada)"], [13.6, "CLACK de madeira + grave (barra fica em pé)"],
        [14.0, "clique de cor afinado Fá5 (preto)"], [14.4, "clique de cor afinado Láb5 (vermelho)"], [14.8, "clique de cor afinado Dó6 (azul)"]);
    },
    async render(t, root) {
      const f = K.frameAt(c07, t);
      const P = plan07(t);
      const pl = await this.v.set(c07, f, camOf([P[0] * bounce(t), P[1], P[2]]));
      const inv = 1 / pl.s;
      const [vx, vy] = camVel(plan07, t);
      this.v.inner.style.filter = t < 14.0 ? blurOf(vx, vy) : "";

      // entrada: chicote diagonal (vem de baixo-direita)
      const all = root.querySelector("#b7all");
      const wk = 1 - out5(seg(t, 12.8, 12.8 + 4 / 30));
      all.style.translate = `${wk * 700}px ${wk * 1000}px`;
      all.style.filter = wk > .08 ? `url(#blurX${wk > .4 ? 40 : 20}) url(#blurY${wk > .4 ? 40 : 20})` : "";

      // anéis de toque: botão caneta (67) e as 3 cores na batida
      ring(this.v.ann.querySelector("#b7r0"), t, K.timeOf(c07, 67), 163, 282, inv);
      DOTS.forEach(([x, y, c], i) => ring(this.v.ann.querySelector("#b7r" + (i + 1)), t, 14.0 + i * .4, x, y, inv, i === 0 ? "#fff" : c));

      // textos 12,8 / 13,2
      const a = root.querySelector("#b7a"), b = root.querySelector("#b7b");
      if (show(a, t < 13.6)) K.slam(a.firstElementChild, t, 12.8 + 1 / 30, { rot: -4 });
      if (show(b, on(t, 13.2, 13.6))) K.slam(b.firstElementChild, t, 13.2, { rot: 4 });

      // 13,6: barra em pé — contorno rosa piscando 3 quadros
      const outl = root.querySelector("#b7out");
      const fr = Math.floor((t - 13.6) * 30 + 1e-6);
      if (show(outl, fr >= 0 && fr < 4)) {
        const [x0, y0] = proj(pl, BAR[0] - 12, BAR[1] - 12), [x1, y1] = proj(pl, BAR[0] + BAR[2] + 12, BAR[1] + BAR[3] + 12);
        Object.assign(outl.style, { left: x0 + "px", top: y0 + "px", width: x1 - x0 + "px", height: y1 - y0 + "px", opacity: [1, .25, 1, .5][fr] });
      }
      // "Ela fica" + "em pé." que se levanta (gira de 0° a -90° com mola, como a barra)
      const c1 = root.querySelector("#b7c1"), c2 = root.querySelector("#b7c2");
      if (show(c1, on(t, 13.6, 14.0))) K.slam(c1.firstElementChild, t, 13.6, { rot: -3 });
      if (show(c2, on(t, 13.6, 14.0))) {
        const k = back(seg(t, 13.62, 13.62 + 7 / 30), 1.8);
        c2.style.transform = `rotate(${-90 * k}deg)`;
        c2.style.opacity = 1;
        c2.style.filter = k < .7 ? "url(#blurY20)" : "";
      }

      // 14,0–15,2: cores na batida — borda interna brilha 4 quadros + leve mudança de matiz
      const glow = root.querySelector("#b7glow"), tint = root.querySelector("#b7tint");
      if (t >= 14.0) {
        const i = Math.min(2, Math.floor((t - 14.0) / .4 + 1e-6)), c = DOTS[i][2];
        const g = 1 - seg(t, 14.0 + i * .4, 14.0 + i * .4 + 4 / 30);
        glow.style.boxShadow = `inset 0 0 0 ${18 * g}px ${c}, inset 0 0 ${120 * g}px ${c}`;
        glow.style.opacity = g;
        tint.style.background = DOTS[i][3]; tint.style.opacity = .06 + .1 * g;
      } else { glow.style.opacity = 0; tint.style.opacity = 0; }

      const d = root.querySelector("#b7d"), e = root.querySelector("#b7e");
      if (show(d, t >= 14.4)) {
        K.slam(d.firstElementChild, t, 14.4, { rot: -3 });
        const cyc = [APPRED, BLUE, INK][Math.floor((t - 14.4) / .2 + 1e-6) % 3];
        root.querySelector("#b7three").style.color = cyc;
      }
      if (show(e, t >= 14.8)) K.slam(e.firstElementChild, t, 14.8, { rot: 3 });
    },
  });
  K.hit(12.8, .55);
  K.hit(13.2, .3);
  K.hit(13.6, .9);
  K.flash(13.6, "#FFC9D1", .3, 1);
  [14.0, 14.4, 14.8].forEach(a => K.hit(a, .3));

  // ===================================================================================
  // S08 — 6 pincéis: bandeja real, grade 2x3 de ladrilhos que vira uma nota só
  // ===================================================================================
  const S08 = { start: 15.2, end: 17.6 };
  const c08 = clip([[15.2, 15.6, 157, 170]]);
  const FINAL = 320;                                       // quadro com as 6 células prontas
  const VIEW_B = R.views[FINAL];
  // centro de cada ladrilho = centro da palavra escrita dentro da célula (anchor b1..b6)
  const TILES = [
    ["b1", 171, 194, "Tinteiro", INK, "rgba(246,244,239,.95)", 352],
    ["b2", 195, 218, "Caneta", BLUE, "rgba(246,244,239,.95)", 868],
    ["b3", 219, 249, "Lápis", "#5F6368", "rgba(246,244,239,.95)", 398],
    ["b4", 250, 285, "Caligrafia", APPRED, "rgba(246,244,239,.95)", 894],
    ["b5", 286, 310, "Pincel", "#8E44AD", "rgba(246,244,239,.95)", 342],
    ["b6", 311, 318, "Marca-texto", INK, YEL, 860],
  ].map(([a, from, to, label, color, bg, wx], i) => {
    const r = AN[a], t0 = 15.6 + .2 * i, dur = Math.min(.5, 16.8 - t0);
    return { a, i, col: i % 2, row: Math.floor(i / 2), A: [wx, r[1] + r[3] / 2 + 6], label, color, bg, t0, from, to,
      clip: clip([[t0, t0 + dur, from, to]]) };
  });
  // mapeamento global (gravação -> vídeo) da grade: escala 1, centrada; ladrilhos 493x420, vãos 24
  const TW = 493, TH = 420, GAP = 24;
  const KA = 1.2, KEND = .66;                               // escala da gravação nos ladrilhos / no app inteiro
  const DES = TILES.map(T => [540 + (T.col - .5) * (TW + GAP), 662 + T.row * (TH + GAP)]);   // centros desenhados

  K.scene({
    id: "s08", ...S08,
    setup(root) {
      injectCSS();
      root.innerHTML = `
        <div class="b-full dots" id="b8bg" style="background-color:#F6F4EF"></div>
        <div class="viewer" id="b8base" style="display:none;box-shadow:0 40px 80px -30px rgba(27,27,31,.55),0 0 0 2px rgba(27,27,31,.1)"></div>
        <div class="b-full" id="b8grid">${TILES.map(T => `<div class="b-tile" id="b8t${T.i}"><div class="viewer" id="b8v${T.i}" style="left:0;top:0;width:100%;height:100%"></div><div class="b-lab" style="color:${T.color};background:${T.bg}">${T.label}</div></div>`).join("")}</div>
        <div class="viewer" id="b8tray" style="left:0;top:0;width:1080px;height:1920px"></div>
        <div class="abs" id="b8title" style="left:0;top:0"><div class="b-st" id="b8tst" style="position:relative;rotate:-2deg;background:#fff"><span class="disp" style="font-size:150px"><span style="color:${RED}">6</span> pincéis.</span></div></div>
        <div class="abs" id="b8end" style="left:250px;top:250px">
          <div class="disp" id="b8l1" style="font-size:118px;white-space:nowrap">Letra bonita</div>
          <div class="hand" id="b8l2" style="position:relative;font-size:120px;line-height:1;color:${RED};margin:-4px 0 0 150px;transform:rotate(-4deg);white-space:nowrap">(ou não).
            <svg style="position:absolute;left:-10px;top:104px;width:430px;height:60px;overflow:visible" viewBox="0 0 430 60"><path id="b8scr" pathLength="1" d="M5,30 C40,5 60,55 95,28 S150,8 180,34 S240,52 270,24 S330,6 360,34 S410,46 425,22" fill="none" stroke="${RED}" stroke-width="9" stroke-linecap="round"/></svg>
          </div>
        </div>`;
      this.tray = K.viewer(root.querySelector("#b8tray"));
      this.base = K.viewer(root.querySelector("#b8base"));
      this.tv = TILES.map(T => K.viewer(root.querySelector("#b8v" + T.i)));
      this.tray.ann.innerHTML = `<div class="b-ring" id="b8ring"></div>`;
      window.EVENTS.taps.push(+K.timeOf(c08, 157).toFixed(3));
      TILES.forEach(T => {
        const end = Math.min(T.t0 + .5, 16.8);
        window.EVENTS.pen.push([+T.t0.toFixed(3), +end.toFixed(3), T.a === "b6" ? "marker" : "pen"]);
      });
      const notes = ["Dó5", "Ré5", "Mib5", "Fá5", "Sol5", "Láb5"];
      window.EVENTS.notes.push([15.2, "crash + bandeja de canetas abrindo (clique + ar)"]);
      TILES.forEach((T, i) => window.EVENTS.notes.push([+T.t0.toFixed(3), `pop afinado ${notes[i]} (ladrilho ${T.label})`]));
      window.EVENTS.notes.push([16.8, "click-lock dos ladrilhos"], [17.2, "thump"]);
    },
    async render(t, root) {
      // ---- 15,2–15,6: bandeja real bate na tela ----
      const trayOn = t < 15.6 + 5 / 30;
      if (show(this.tray.el, trayOn)) {
        const f = K.frameAt(c08, t);
        const z = lerp(1.12, 1.45, out5(seg(t, 15.3, 15.47)));
        const cam = camOf([z * bounce(t), lerp(560, 720, out5(seg(t, 15.3, 15.47))), lerp(980, 1010, seg(t, 15.2, 15.6))]);
        const pl = await this.tray.set(c08, f, cam);
        ring(this.tray.ann.querySelector("#b8ring"), t, K.timeOf(c08, 157), 108, 891, 1 / pl.s);
        const k = out5(seg(t, 15.2, 15.2 + 4 / 30));
        this.tray.el.style.transform = `scale(${lerp(1.25, 1, k)})`;
        if (t < 15.6) { this.tray.el.style.translate = ""; this.tray.el.style.filter = ""; this.tray.el.style.opacity = 1; }
        K.whipOut(this.tray.el, t, 15.6, [0, -1], 5 / 30, 2000);
      }

      // ---- título "6 pincéis.": slam embaixo, sobe para o topo em 15,6 ----
      const title = root.querySelector("#b8title");
      if (show(title, t < 16.8 + 5 / 30)) {
        const up = back(seg(t, 15.6, 15.6 + 7 / 30), 1.4);
        title.style.left = "232px";
        title.style.top = lerp(1300, 238, up) + "px";
        K.slam(root.querySelector("#b8tst"), t, 15.2, { from: 1.5, rot: -4 });
        if (t < 16.8) { title.style.translate = ""; title.style.filter = ""; title.style.opacity = 1; }
        K.whipOut(title, t, 16.8, [0, -1], 5 / 30, 900);
      }

      // ---- grade 2x3: ladrilhos são janelas sobre a MESMA gravação (mapeamento global) ----
      const m = out5(seg(t, 16.8, 16.8 + 4 / 30));                  // vãos fecham
      const pb = out5(seg(t, 16.8 + 4 / 30, 16.8 + 8 / 30));        // câmera recua
      const drift = seg(t, 17.07, 17.6);
      let k = lerp(lerp(KA, 1, m), KEND, pb) * (1 + .03 * drift);
      const cxV = 540, cyV = lerp(1106, 960, pb);
      // mantém o centro da grade (678.8, 1340.4 na gravação) estável durante o recuo
      const gcx = lerp(619, IW / 2, pb), gcy = lerp(1346, IH / 2, pb);
      const G = { k, ox: cxV - gcx * k, oy: cyV - gcy * k };
      const gridBounce = t < 16.8 ? bounce(t) : 1;

      K.syncGrid(root.querySelector("#b8bg"), VIEW_B, { s: G.k, tx: G.ox, ty: G.oy }, 0, 0, 80);

      const tilesOn = t >= 15.6 && t < 16.8 + 4 / 30;
      for (const T of TILES) {
        const el = root.querySelector("#b8t" + T.i), vw = this.tv[T.i];
        if (!show(el, tilesOn && t >= T.t0)) continue;
        const pc = [G.ox + T.A[0] * G.k, G.oy + T.A[1] * G.k];      // posição real no mapeamento
        const d = [(DES[T.i][0] - pc[0]) * (1 - m), (DES[T.i][1] - pc[1]) * (1 - m)];
        const w = lerp(TW, 518 * G.k + 2, m), h = lerp(TH, 501 * G.k + 2, m);
        const cx = pc[0] + d[0], cy = pc[1] + d[1];
        el.style.left = cx - w / 2 + "px"; el.style.top = cy - h / 2 + "px";
        el.style.width = w + "px"; el.style.height = h + "px";
        el.style.borderRadius = lerp(28, 0, m) + "px";
        el.style.boxShadow = m > .6 ? "none" : "";
        // POP: escala 0,6 -> 1 e rotateY 30° -> 0
        const pk = back(seg(t, T.t0, T.t0 + 6 / 30), 1.6);
        el.style.transform = `perspective(2000px) rotateY(${30 * (1 - clamp(pk))}deg) scale(${lerp(.6, 1, pk) * gridBounce})`;
        el.querySelector(".b-lab").style.display = t < 16.8 ? "" : "none";
        const f = t >= 16.8 ? FINAL : K.frameAt(T.clip, t);
        await vw.set(T.clip, f, { zoom: G.k / (w / IW), focus: T.A, clamp: false, cursors: t < 16.8, penScale: .9 });
      }

      // ---- base: o app inteiro aparece por baixo quando os vãos fecham ----
      const base = this.base;
      if (show(base.el, t >= 16.8)) {
        Object.assign(base.el.style, { left: G.ox + "px", top: G.oy + "px", width: IW * G.k + "px", height: IH * G.k + "px", borderRadius: 46 * pb + "px", opacity: m });
        await base.set(c08, FINAL, { zoom: 1, cursors: false });
      }

      // ---- "Letra bonita (ou não)." ----
      const end = root.querySelector("#b8end");
      if (show(end, t >= 17.2)) {
        K.slam(root.querySelector("#b8l1"), t, 17.2, { rot: -2 });
        const l2 = root.querySelector("#b8l2");
        l2.style.opacity = t >= 17.27 ? 1 : 0;
        K.wipe(l2, t, 17.27, 17.42);
        K.draw(root.querySelector("#b8scr"), t, 17.36, 17.5);
      }
    },
  });
  K.hit(15.2, .75);
  K.flash(15.2, "#fff", .55, 2);
  TILES.forEach(T => K.hit(T.t0, .22));
  K.hit(16.8, .5);
  K.hit(17.2, .4);

  // ===================================================================================
  // S09 — Circula. Move. Gira. Aumenta. mitocondira? Errou? kkkk Apaga.
  // ===================================================================================
  const S09 = { start: 17.6, end: 20.8 };
  const c09 = clip([[17.6, 18.0, "laco", 359], [18.0, 18.4, "move", 374], [18.4, 18.8, "gira", 387], [18.8, 19.2, "aumenta", 399],
    [19.2, 19.6, 399, 403], [19.6, 19.8, 403, 409], [19.8, 20.0, 409, 433], [20.0, 20.8, 523, 530]]);
  const MV0 = T2[360].p[0];                          // início do arrasto da seleção
  const SEL0 = [305, 756, 943, 1375];                // caixa da seleção (com alças) antes de mover
  const selDelta = g => { const tc = T2[Math.max(360, Math.min(372, g))]; return g < 360 ? [0, 0] : [tc.p[0][0] - MV0[0], tc.p[0][1] - MV0[1]]; };
  const WORD = { x0: 293, x1: 726, y0: 1659, y1: 1723 };
  const MITO = [740, 994];                           // centro da mitocôndria depois de mover
  function plan09(t) {
    const f = K.frameAt(c09, t);
    if (t < 18.0) { const p = K.focus(c09, Math.max(325, f), 10, 4, 325, 356) || [616, 1060]; return [lerp(1.46, 1.4, seg(t, 17.6, 18.0)), lerp(616, p[0], .12), lerp(1060, p[1], .12)]; }
    if (t < 18.4) { const p = K.focus(c09, f, 5, 2, 360, 372) || MV0; return [1.4, 615 + p[0] - MV0[0], 1120 + p[1] - MV0[1]]; }
    if (t < 18.8) return [1.4, 736, 997];
    if (t < 19.2) return [lerp(1.4, 1.32, out3(seg(t, 18.8, 19.1))), 738, 994];
    const pW = [lerp(1.6, 1.66, seg(t, 19.35, 20.0)), 515, 1554];
    if (t < 20.0) return mix(cc(lerp(1.4, 1.32, 1), 738, 994), cc(...pW), out5(seg(t, 19.2, 19.36)));
    return [1.66 * (1 + .1 * back(seg(t, 20.0, 20.12), 2)) * (1 + .04 * seg(t, 20.12, 20.8)), 515, 1554];
  }
  const VERBS = [["Circula.", 17.6, "L"], ["Move.", 18.0, "R"], ["Gira.", 18.4, "L"], ["Aumenta.", 18.8, "R"]];
  const DUST = Array.from({ length: 46 }, (_, j) => {
    const x = WORD.x0 + (WORD.x1 - WORD.x0) * hash(j + 1), y = WORD.y0 + (WORD.y1 - WORD.y0) * hash(j + 31);
    const fb = 403.6 + Math.max(0, Math.min(5, (x - 330) / 78));      // quadro em que a borracha passa por x
    return { x, y, fb, s: 5 + hash(j + 7) * 9, dx: (hash(j + 11) - .5) * 90, rise: 110 + hash(j + 13) * 230, life: .55 + hash(j + 17) * .5, rot: hash(j + 5) * 360 };
  });

  K.scene({
    id: "s09", ...S09,
    setup(root) {
      injectCSS();
      root.innerHTML = `
        <div class="b-full dots" style="background-color:#F6F4EF"></div>
        <div class="b-full" id="b9all">
          <div class="b-full" id="b9cam">
            <div class="viewer" id="b9v" style="left:0;top:0;width:1080px;height:1920px"></div>
            <div class="viewer" id="b9g1" style="left:0;top:0;width:1080px;height:1920px;background:transparent;mix-blend-mode:multiply;opacity:.25;display:none"></div>
            <div class="viewer" id="b9g2" style="left:0;top:0;width:1080px;height:1920px;background:transparent;mix-blend-mode:multiply;opacity:.1;display:none"></div>
          </div>
          ${VERBS.map(([w, , side], i) => `<div class="abs" id="b9verb${i}" style="top:262px;${side === "L" ? "left:84px" : "right:122px"}"><div class="b-verb" style="transform-origin:${side === "L" ? "0" : "100%"} 60%;rotate:${side === "L" ? -2 : 2}deg">${w}</div></div>`).join("")}
          <div class="abs" id="b9err" style="left:96px;top:560px"><div class="b-st" style="position:relative;rotate:-2deg"><span class="disp" style="font-size:124px">Errou?</span></div></div>
          <div class="abs hand" id="b9kk" style="left:560px;top:560px;font-size:130px;line-height:1;color:${RED};transform:rotate(12deg);transform-origin:0 50%;white-space:nowrap">kkkk</div>
          <div class="abs" id="b9apaga" style="left:0;top:1062px;width:1080px;text-align:center"><div class="b-st" style="position:relative;display:inline-block;rotate:-2deg"><span class="disp" style="font-size:170px">Apaga.</span></div></div>
        </div>`;
      this.v = K.viewer(root.querySelector("#b9v"));
      this.g1 = K.viewer(root.querySelector("#b9g1"));
      this.g2 = K.viewer(root.querySelector("#b9g2"));
      const [mx, my] = MITO, rr = 405;
      const pt = a => [mx + rr * Math.cos(a * Math.PI / 180), my + rr * Math.sin(a * Math.PI / 180)];
      const [ax, ay] = pt(-14), [bx, by] = pt(-78);
      this.v.ann.innerHTML = `
        <svg style="position:absolute;left:0;top:0;width:1px;height:1px;overflow:visible">
          <defs><mask id="b9arcm" maskUnits="userSpaceOnUse" x="0" y="0" width="${IW}" height="${IH}"><path id="b9arcr" pathLength="1" d="M${ax},${ay} A${rr},${rr} 0 0 0 ${bx},${by}" fill="none" stroke="#fff" stroke-width="30"/></mask></defs>
          <path id="b9arc" d="M${ax},${ay} A${rr},${rr} 0 0 0 ${bx},${by}" fill="none" stroke="${RED}" stroke-width="9" stroke-linecap="round" stroke-dasharray="26 20" mask="url(#b9arcm)"/>
          <path id="b9arch" pathLength="1" d="M${bx + 44},${by - 22} L${bx},${by} L${bx + 34},${by + 34}" fill="none" stroke="${RED}" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"/>
          <path id="b9wa" pathLength="1" d="M 640 1432 C 700 1462, 748 1520, 742 1600" fill="none" stroke="${RED}" stroke-width="6" stroke-linecap="round"/>
          <path id="b9wah" pathLength="1" d="M 718 1574 L 742 1604 L 762 1570" fill="none" stroke="${RED}" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
          <path id="b9q" pathLength="1" d="M 752 1650 C 756 1622, 800 1614, 806 1640 C 812 1664, 782 1668, 780 1692" fill="none" stroke="${RED}" stroke-width="7" stroke-linecap="round"/>
          <circle id="b9qd" cx="780" cy="1716" r="6" fill="${RED}"/>
        </svg>
        <div id="b9dust">${DUST.map((_, j) => `<i class="b-dust" id="b9d${j}"></i>`).join("")}</div>`;
      const tm = f => +K.timeOf(c09, f).toFixed(3);
      window.EVENTS.pen.push([tm(325), tm(356), "pen"], [19.6, 20.0, "eraser"]);
      window.EVENTS.taps.push(tm(400));
      window.EVENTS.notes.push([17.6, "zip pontilhado do laço"], [18.0, "swish (seleção movida)"], [18.4, "catraca reco-reco (gira)"],
        [18.8, "boing curto subindo (aumenta)"], [19.2, "whoosh vertical"], [19.4, "ba-dum-tss leve (kkkk)"], [19.6, "borracha esfregando"], [20.0, "poof (ruído com pitch subindo)"], [20.4, "downlifter (entrada da madrugada)"]);
    },
    async render(t, root) {
      const f = K.frameAt(c09, t);
      const P = plan09(t);
      const cam = camOf([P[0] * bounce(t), P[1], P[2]]);
      const pl = await this.v.set(c09, f, cam);
      const [vx, vy] = camVel(plan09, t);
      this.v.inner.style.filter = blurOf(vx, vy);

      // câmera gira um pouco junto com a seleção (18,4–18,8)
      const rot = -3.2 * inOut(seg(t, 18.4, 18.75)) * (1 - out3(seg(t, 18.8, 19.0)));
      root.querySelector("#b9cam").style.transform = rot ? `rotate(${rot}deg) scale(${1 + Math.abs(rot) * .022})` : "";

      // rastro fantasma de 2 cópias recortadas pela caixa (18,0–18,4)
      const ghosts = [[this.g1, 3], [this.g2, 6]];
      for (const [g, lag] of ghosts) {
        if (!show(g.el, on(t, 18.0, 18.45))) continue;
        const gf = Math.max(359, f - lag);
        const [dx, dy] = selDelta(gf);
        g.inner.style.clipPath = `inset(${SEL0[1] + dy}px ${IW - SEL0[2] - dx}px ${IH - SEL0[3] - dy}px ${SEL0[0] + dx}px)`;
        await g.set(c09, gf, Object.assign({}, cam, { cursors: false }));
      }

      // arco tracejado do "Gira."
      const arcOn = on(t, 18.4, 19.2);
      K.draw(this.v.ann.querySelector("#b9arcr"), t, 18.42, 18.62, x => x);
      K.draw(this.v.ann.querySelector("#b9arch"), t, 18.6, 18.67);
      ["#b9arc", "#b9arch"].forEach(id => { this.v.ann.querySelector(id).style.visibility = arcOn ? "" : "hidden"; });

      // verbos alternando os cantos
      VERBS.forEach(([, at], i) => {
        const el = root.querySelector("#b9verb" + i);
        if (show(el, on(t, at, at + .4))) K.slam(el.firstElementChild, t, at, { from: 1.5, rot: i % 2 ? 4 : -4 });
      });

      // 19,2: "mitocondira?" — seta + "?" Caveat, "Errou?" e "kkkk"
      const wOn = on(t, 19.2, 20.0);
      const wa = this.v.ann.querySelector("#b9wa"), wah = this.v.ann.querySelector("#b9wah"), q = this.v.ann.querySelector("#b9q"), qd = this.v.ann.querySelector("#b9qd");
      [wa, wah, q].forEach(el => el.style.visibility = wOn ? "" : "hidden");
      qd.style.visibility = wOn && t >= 19.38 ? "" : "hidden";
      K.draw(q, t, 19.25, 19.25 + 4 / 30); K.draw(wa, t, 19.3, 19.3 + 4 / 30); K.draw(wah, t, 19.43, 19.43 + 2 / 30);
      const err = root.querySelector("#b9err"), kk = root.querySelector("#b9kk");
      if (show(err, wOn)) K.slam(err.firstElementChild, t, 19.2 + 1 / 30, { rot: -4 });
      if (show(kk, on(t, 19.4, 20.0))) { kk.style.opacity = 1; K.wipe(kk, t, 19.4, 19.52); kk.style.translate = `${Math.sin(t * 70) * 3}px ${Math.cos(t * 55) * 3}px`; }

      // poeira de tinta subindo de onde a borracha passa
      const inv = 1 / pl.s;
      DUST.forEach((p, j) => {
        const el = this.v.ann.querySelector("#b9d" + j);
        const tb = K.timeOf(c09, p.fb);
        const age = t - tb;
        if (tb === null || age < 0 || age > p.life || t >= 20.8) { el.style.display = "none"; return; }
        const k = age / p.life;
        el.style.display = "";
        el.style.left = p.x + p.dx * out3(k) + Math.sin(age * 9 + j) * 8 + "px";
        el.style.top = p.y - p.rise * out3(k) + "px";
        el.style.width = el.style.height = p.s * (1 - k * .5) + "px";
        el.style.opacity = 1 - k * k;
        el.style.transform = `rotate(${p.rot + age * 400}deg)`;
      });

      // 20,0: "Apaga." com punch-in
      const ap = root.querySelector("#b9apaga");
      if (show(ap, t >= 20.0)) K.slam(ap.firstElementChild, t, 20.0, { from: 1.6, rot: -3 });

      // 20,4–20,8: dessatura e escurece até 70% (entrada da madrugada)
      const dk = inOut(seg(t, 20.4, 20.8));
      root.querySelector("#b9all").style.filter = dk > 0 ? `saturate(${1 - dk}) brightness(${1 - .3 * dk})` : "";
    },
  });
  K.hit(17.6, .6);
  K.hit(18.0, .3);
  K.hit(18.4, .3);
  K.hit(18.8, .35);
  K.hit(19.2, .4);
  K.hit(20.0, .65);
})();

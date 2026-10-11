// Renderiza index.html quadro a quadro em vários processos do Chromium (render(t) não
// guarda estado, então cada página pode cuidar de uma faixa de quadros).
// Uso: node render.js            -> out/v00000.jpg ...
//      node render.js 1.2,3.4    -> test_1.2.jpg (só esses tempos)
const { chromium } = require('playwright');
const path = require('path'); const fs = require('fs');
(async () => {
  const dir = __dirname, fps = 30;
  const only = process.argv[2] ? process.argv[2].split(',').map(Number) : null;
  const workers = Number(process.env.WORKERS || 3);
  const b = await chromium.launch({ args: ['--disable-gpu-vsync'] });
  async function page() {
    const p = await b.newPage({ viewport: { width: 1080, height: 1920 } });
    p.on('pageerror', e => console.log('ERRO na página:', e.message));
    p.on('console', m => { if (m.type() === 'error') console.log('console:', m.text()); });
    await p.goto('file://' + path.join(dir, 'index.html'));
    await p.evaluate(() => document.fonts.ready);
    await p.evaluate(() => window.ready);
    await p.waitForTimeout(500);
    return p;
  }
  const first = await page();
  const dur = await first.evaluate(() => window.DURATION);
  fs.writeFileSync(path.join(dir, 'events.json'), JSON.stringify(await first.evaluate(() => window.EVENTS || {})));
  if (only) {
    // TESTDIR=pasta separa os quadros de teste (vários agentes podem testar ao mesmo tempo)
    const td = process.env.TESTDIR || dir;
    fs.mkdirSync(td, { recursive: true });
    for (const t of only) {
      await first.evaluate(t => window.render(t), t);
      await first.screenshot({ path: path.join(td, `test_${t.toFixed(2)}.jpg`), type: 'jpeg', quality: 90 });
    }
    console.log('teste ok, duração', dur);
    await b.close(); return;
  }
  const total = Math.round(dur * fps);
  fs.mkdirSync(path.join(dir, 'out'), { recursive: true });
  const pages = [first];
  for (let i = 1; i < workers; i++) pages.push(await page());
  let next = 0;
  await Promise.all(pages.map(async p => {
    while (true) {
      const i = next++;
      if (i >= total) break;
      await p.evaluate(t => window.render(t), i / fps);
      await p.screenshot({ path: path.join(dir, `out/v${String(i).padStart(5, '0')}.jpg`), type: 'jpeg', quality: 93 });
    }
  }));
  console.log('duração', dur, 'quadros', total);
  await b.close();
})();

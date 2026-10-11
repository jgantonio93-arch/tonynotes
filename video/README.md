# Vídeo "Escreva fora da margem" (motion graphics do RosaNotes)

Projeto do vídeo de lançamento (vertical 1080x1920, 30 fps, 40 s, 150 BPM).

- `storyboard.json`: roteiro final (escolhido por um painel de 3 roteiros + 3 jurados).
- `engine.js`, `kit.js`, `index.html`: motor de animação (tudo é função do tempo, render quadro a quadro).
- `scenes/`: as cenas S01–S17.
- `synth.py` (+ `music.py` quando pronto): trilha original sintetizada em numpy.
- `render.js`: renderiza com o Chromium (Playwright), em vários processos.
- `rec-data.js`: dados das gravações (toques, câmera, marcos). Os quadros em si ficam
  no anexo da versão `demo-frames` do GitHub (gerados pelo workflow `demo.yml` a partir de
  `app/src/test/.../DemoRecordingTest.kt`); descompacte em `rec/` e copie `rec-data.js`
  para `rec/data.js` para renderizar.

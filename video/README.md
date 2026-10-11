# Vídeo "Escreva fora da margem" (motion graphics do RosaNotes)

Projeto do vídeo de lançamento (vertical 1080x1920, 30 fps, 40 s, 150 BPM).

- `storyboard.json`: roteiro final (escolhido por um painel de 3 roteiros + 3 jurados).
- `engine.js`, `kit.js`, `index.html`: motor de animação (tudo é função do tempo, render quadro a quadro).
- `scenes/`: as cenas S01–S17.
- `synth.py` + `music.py`: trilha original sintetizada em numpy (`python3 music.py` gera
  `trilha.wav`; os efeitos sonoros vêm de `events.json`, que o `render.js` escreve).
  Masterização: EQ de presença + `loudnorm` em duas passadas para −14 LUFS / −1 dBTP.
- `render.js`: renderiza com o Chromium (Playwright), em vários processos.
- `rec-data.js`: dados das gravações (toques, câmera, marcos). Os quadros em si ficam
  no anexo da versão `demo-frames` do GitHub (gerados pelo workflow `demo.yml` a partir de
  `app/src/test/.../DemoRecordingTest.kt`); descompacte em `rec/` e copie `rec-data.js`
  para `rec/data.js` para renderizar.

Montagem final:

```
WORKERS=3 node render.js             # out/v00000.jpg … v01199.jpg
python3 music.py                     # trilha.wav (+ masterizar para trilha_master.wav)
ffmpeg -framerate 30 -i out/v%05d.jpg -i trilha_master.wav -c:v libx264 -pix_fmt yuv420p \
  -crf 18 -preset slow -c:a aac -b:a 192k -shortest -movflags +faststart RosaNotes-escreva-fora-da-margem.mp4
```

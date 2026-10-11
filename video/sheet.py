# Uso: python3 sheet.py PASTA saida.jpg [colunas]  -> monta os test_*.jpg da pasta numa folha (com o tempo)
import sys, glob, os
from PIL import Image, ImageDraw
d, out = sys.argv[1], sys.argv[2]
cols = int(sys.argv[3]) if len(sys.argv) > 3 else 6
fs = sorted(glob.glob(os.path.join(d, 'test_*.jpg')), key=lambda s: float(os.path.basename(s)[5:-4]))
tw, th = 300, 533
rows = (len(fs) + cols - 1) // cols
sh = Image.new('RGB', (tw * cols, (th + 24) * rows), 'black'); dr = ImageDraw.Draw(sh)
for i, f in enumerate(fs):
    im = Image.open(f).resize((tw, th)); x, y = (i % cols) * tw, (i // cols) * (th + 24)
    sh.paste(im, (x, y + 24)); dr.text((x + 6, y + 5), os.path.basename(f)[5:-4] + ' s', fill='yellow')
sh.save(out); print(out, len(fs))

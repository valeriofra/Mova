"""Gera os ícones do MOVA para desktop: mova.png (Linux, 512 px) e mova.ico (Windows).

Usa o mesmo símbolo do Android (app/src/main/res/drawable/ic_mova_logo.xml) sobre
um quadrado arredondado escuro com brilho azul, como o ícone adaptável.

Uso: python3 tools/gen_desktop_icon.py desktop/packaging
"""
import os, re, sys
from PIL import Image, ImageDraw, ImageFilter

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
here = os.path.dirname(os.path.abspath(__file__))
logo = open(os.path.join(here, '..', 'app/src/main/res/drawable/ic_mova_logo.xml'), encoding='utf-8').read()
paths = re.findall(r'pathData="([^"]+)"', logo)
colors = [re.findall(r'color="#FF([0-9A-Fa-f]{6})"', block) for block in logo.split('<path')[1:]]

def points(d):
    return [(float(x), float(y)) for x, y in re.findall(r'([\d.]+),([\d.]+)', d)]

S = 1024                      # desenho em alta resolução, reduzido no fim (antisserrilhado)
img = Image.new('RGBA', (S, S), (0, 0, 0, 0))

# Fundo: quadrado arredondado #0B0F1A com brilho radial azul.
bg = Image.new('RGBA', (S, S), (11, 15, 26, 255))
glow = Image.new('RGBA', (S, S), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse((S * 0.12, S * 0.12, S * 0.88, S * 0.88), fill=(37, 99, 235, 90))
glow = glow.filter(ImageFilter.GaussianBlur(S * 0.12))
bg = Image.alpha_composite(bg, glow)
mask = Image.new('L', (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.22), fill=255)
img.paste(bg, (0, 0), mask)

# Símbolo: viewport 56x56 centrado e ocupando ~62% do ícone.
scale = S * 0.62 / 56.0
offset = (S - 56.0 * scale) / 2

def gradient_fill(poly, c0, c1, start, end):
    layer = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    m = Image.new('L', (S, S), 0)
    d = ImageDraw.Draw(m)
    pts = [(offset + x * scale, offset + y * scale) for x, y in poly]
    d.polygon(pts, fill=255)
    stroke = int(5.0 * scale)          # traço de 5 do vetor: arredonda os cantos
    d.line(pts + [pts[0]], fill=255, width=stroke, joint='curve')
    for x, y in pts:
        d.ellipse((x - stroke / 2, y - stroke / 2, x + stroke / 2, y + stroke / 2), fill=255)
    (sx, sy), (ex, ey) = [(offset + a * scale, offset + b * scale) for a, b in (start, end)]
    vx, vy = ex - sx, ey - sy
    length = vx * vx + vy * vy
    a = tuple(int(c0[i:i + 2], 16) for i in (0, 2, 4))
    b = tuple(int(c1[i:i + 2], 16) for i in (0, 2, 4))
    grad = Image.new('RGBA', (S, S))
    px = grad.load()
    for yy in range(0, S, 2):
        for xx in range(0, S, 2):
            t = max(0.0, min(1.0, ((xx - sx) * vx + (yy - sy) * vy) / length))
            c = tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)
            px[xx, yy] = c
            if xx + 1 < S: px[xx + 1, yy] = c
            if yy + 1 < S:
                px[xx, yy + 1] = c
                if xx + 1 < S: px[xx + 1, yy + 1] = c
    layer.paste(grad, (0, 0), m)
    return layer

grads = re.findall(r'startX="([\d.]+)" android:startY="([\d.]+)" android:endX="([\d.]+)" android:endY="([\d.]+)"', logo)
for i, d in enumerate(paths):
    sx, sy, ex, ey = map(float, grads[i * 2])
    img = Image.alpha_composite(img, gradient_fill(points(d), colors[i][0], colors[i][1], (sx, sy), (ex, ey)))

png = img.resize((512, 512), Image.LANCZOS)
png.save(os.path.join(out, 'mova.png'))
img.resize((256, 256), Image.LANCZOS).save(
    os.path.join(out, 'mova.ico'), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print('OK:', os.path.join(out, 'mova.png'), os.path.join(out, 'mova.ico'))

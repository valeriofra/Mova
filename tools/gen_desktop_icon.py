"""Gera os ícones do MOVA para desktop: mova.png (Linux, 512 px), mova.ico (Windows)
e a tela de abertura app-resources/common/splash.png.

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

# Tela de abertura (aparece antes do Java carregar): cartão escuro com o símbolo e o nome.
from PIL import ImageFont
W, H, R = 880, 520, 48                      # 2x; reduzido para 440x260
splash = Image.new('RGBA', (W, H), (0, 0, 0, 0))
card = Image.new('RGBA', (W, H), (11, 15, 26, 255))
glow2 = Image.new('RGBA', (W, H), (0, 0, 0, 0))
ImageDraw.Draw(glow2).ellipse((W * 0.25, -H * 0.2, W * 0.75, H * 0.75), fill=(37, 99, 235, 70))
card = Image.alpha_composite(card, glow2.filter(ImageFilter.GaussianBlur(70)))
cmask = Image.new('L', (W, H), 0)
ImageDraw.Draw(cmask).rounded_rectangle((0, 0, W - 1, H - 1), radius=R, fill=255)
splash.paste(card, (0, 0), cmask)
ImageDraw.Draw(splash).rounded_rectangle((1, 1, W - 2, H - 2), radius=R, outline=(36, 48, 73, 255), width=3)
symbol = Image.new('RGBA', (S, S), (0, 0, 0, 0))
for i, d in enumerate(paths):
    sx, sy, ex, ey = map(float, grads[i * 2])
    symbol = Image.alpha_composite(symbol, gradient_fill(points(d), colors[i][0], colors[i][1], (sx, sy), (ex, ey)))
symbol = symbol.crop(symbol.getbbox()).resize((150, 150), Image.LANCZOS)
splash.alpha_composite(symbol, ((W - 150) // 2, 120))
font_path = '/usr/share/fonts/opentype/inter/Inter-Bold.otf'
font = ImageFont.truetype(font_path, 64) if os.path.exists(font_path) else ImageFont.load_default()
text, spacing = 'MOVA', 10
widths = [ImageDraw.Draw(splash).textlength(c, font=font) for c in text]
x = (W - (sum(widths) + spacing * (len(text) - 1))) / 2
for c, w in zip(text, widths):
    ImageDraw.Draw(splash).text((x, 320), c, font=font, fill=(232, 236, 244, 255))
    x += w + spacing
res = os.path.join(out, 'app-resources', 'common')
os.makedirs(res, exist_ok=True)
splash.resize((W // 2, H // 2), Image.LANCZOS).save(os.path.join(res, 'splash.png'))
print('OK:', os.path.join(res, 'splash.png'))

png = img.resize((512, 512), Image.LANCZOS)
png.save(os.path.join(out, 'mova.png'))
img.resize((256, 256), Image.LANCZOS).save(
    os.path.join(out, 'mova.ico'), sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print('OK:', os.path.join(out, 'mova.png'), os.path.join(out, 'mova.ico'))

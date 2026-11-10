"""Gera os vetores do símbolo MOVA (adaptive icon, monocromático, notificação, logo)."""
import sys, os
out = sys.argv[1]
W, H = 46.0, 48.0
CX, CY = 54.0, 54.0
L = 52.0 - W / 3          # centróide levemente à esquerda do centro (equilíbrio óptico)
T, B = CY - H / 2, CY + H / 2
TIPX = L + W
K = 0.2                   # inclinação do corte (para frente)
X0 = L + 0.30 * W         # posição do corte na altura do centro
GAP = 10.0                # vão horizontal antes do traço
STROKE = 5.0              # traço arredonda os cantos

def top(x):  return T + (CY - T) / W * (x - L)
def bot(x):  return B - (B - CY) / W * (x - L)

def cut_with(edge, x0):
    # linha do corte: x(y) = x0 + K*(CY - y). Resolve interseção com a aresta.
    x = x0
    for _ in range(60):
        y = edge(x)
        x = x0 + K * (CY - y)
    return (x, edge(x))

a1, b1 = cut_with(top, X0), cut_with(bot, X0)
a2, b2 = cut_with(top, X0 + GAP), cut_with(bot, X0 + GAP)
left = [(L, T), a1, b1, (L, B)]
right = [a2, (TIPX, CY), b2]

def path(pts, dx=0.0, dy=0.0):
    p = ["M%.2f,%.2f" % (pts[0][0] + dx, pts[0][1] + dy)]
    p += ["L%.2f,%.2f" % (x + dx, y + dy) for x, y in pts[1:]]
    return "".join(p) + "Z"

def grad(x1, y1, x2, y2, c1, c2, dx=0.0, dy=0.0):
    return ('<gradient android:type="linear" android:startX="%.2f" android:startY="%.2f" '
            'android:endX="%.2f" android:endY="%.2f">'
            '<item android:offset="0" android:color="%s"/><item android:offset="1" android:color="%s"/>'
            '</gradient>') % (x1 + dx, y1 + dy, x2 + dx, y2 + dy, c1, c2)

def colored_paths(dx=0.0, dy=0.0):
    lg = grad(L, T, b1[0], B, "#FF67E8F9", "#FF06B6D4", dx, dy)
    rg = grad(a2[0], a2[1], TIPX, B, "#FF3B82F6", "#FF1D4ED8", dx, dy)
    s = ""
    for pts, g in ((left, lg), (right, rg)):
        s += ('    <path android:pathData="%s" android:strokeWidth="%.1f" android:strokeLineJoin="round">\n'
              '        <aapt:attr name="android:fillColor">%s</aapt:attr>\n'
              '        <aapt:attr name="android:strokeColor">%s</aapt:attr>\n'
              '    </path>\n') % (path(pts, dx, dy), STROKE, g, g)
    return s

def solid_paths(color, dx=0.0, dy=0.0):
    s = ""
    for pts in (left, right):
        s += ('    <path android:pathData="%s" android:fillColor="%s" android:strokeColor="%s" '
              'android:strokeWidth="%.1f" android:strokeLineJoin="round" />\n') % (path(pts, dx, dy), color, color, STROKE)
    return s

HEADER = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Gerado por tools/gen_icon.py: símbolo MOVA. -->\n'
AAPT = 'xmlns:aapt="http://schemas.android.com/aapt"'

def write(name, body):
    with open(os.path.join(out, name), "w") as f:
        f.write(HEADER + body)

write("ic_launcher_foreground.xml",
      '<vector xmlns:android="http://schemas.android.com/apk/res/android" %s\n'
      '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n%s</vector>\n'
      % (AAPT, colored_paths()))

write("ic_launcher_monochrome.xml",
      '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
      '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n%s</vector>\n'
      % solid_paths("#FF000000"))

write("ic_launcher_background.xml",
      '<vector xmlns:android="http://schemas.android.com/apk/res/android" %s\n'
      '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
      '    <path android:pathData="M0,0h108v108h-108z" android:fillColor="#FF0B0F1A" />\n'
      '    <path android:pathData="M0,0h108v108h-108z">\n'
      '        <aapt:attr name="android:fillColor"><gradient android:type="radial" android:centerX="54" android:centerY="54" android:gradientRadius="58">'
      '<item android:offset="0" android:color="#592563EB"/><item android:offset="0.55" android:color="#1A2563EB"/><item android:offset="1" android:color="#000B0F1A"/>'
      '</gradient></aapt:attr>\n'
      '    </path>\n'
      '</vector>\n' % AAPT)

# Recorte justo para uso dentro do app e na notificação.
PAD = STROKE / 2 + 1.5
minx, maxx = L - PAD, TIPX + PAD
miny, maxy = T - PAD, B + PAD
vw, vh = maxx - minx, maxy - miny
side = max(vw, vh)
dx = -minx + (side - vw) / 2
dy = -miny + (side - vh) / 2

write("ic_mova_logo.xml",
      '<vector xmlns:android="http://schemas.android.com/apk/res/android" %s\n'
      '    android:width="48dp" android:height="48dp" android:viewportWidth="%.2f" android:viewportHeight="%.2f">\n%s</vector>\n'
      % (AAPT, side, side, colored_paths(dx, dy)))

write("ic_stat_mova.xml",
      '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
      '    android:width="24dp" android:height="24dp" android:viewportWidth="%.2f" android:viewportHeight="%.2f">\n%s</vector>\n'
      % (side, side, solid_paths("#FFFFFFFF", dx, dy)))

print("left", left, "\nright", right, "\nlogo side", side)

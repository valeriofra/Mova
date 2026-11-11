"""Gera strings.xml traduzidos (en, es, fr), validando chaves e marcadores contra o português.

Uso:
  python3 tools/i18n/gen.py android     # app Android (app/src/main/res)
  python3 tools/i18n/gen.py desktop     # app Windows/Linux (desktop/src/main/composeResources)

O português (values-pt) é a referência; o inglês é o idioma padrão (values).
- Android: traduções em tools/i18n/{en,es,fr}.py.
- Desktop: traduções em tools/i18n/desktop/{en,es,fr}.py. Textos idênticos aos do
  Android (mesma chave e mesmo português) reaproveitam a tradução do Android.
"""
import importlib.util
import os
import re
import sys

here = os.path.dirname(os.path.abspath(__file__))
root = os.path.abspath(os.path.join(here, '..', '..'))
target = sys.argv[1] if len(sys.argv) > 1 else 'android'
desktop = target == 'desktop'
if desktop:
    res = os.path.join(root, 'desktop/src/main/composeResources')
elif target == 'android':
    res = os.path.join(root, 'app/src/main/res')
else:  # compatibilidade: caminho da pasta res do Android
    res = target

PH = re.compile(r'%(?:\d+\$)?[sd]')


def read_xml(path):
    text = open(path, encoding='utf-8').read()
    strings = dict(re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', text, re.S))
    plurals = {m.group(1): dict(re.findall(r'<item quantity="(\w+)">(.*?)</item>', m.group(2)))
               for m in re.finditer(r'<plurals name="([^"]+)">(.*?)</plurals>', text, re.S)}
    order = re.findall(r'<(?:string|plurals) name="([^"]+)"', text)
    return text, strings, plurals, order


def unescape(t):
    return (t.replace("\\'", "'").replace('\\"', '"')
            .replace('&lt;', '<').replace('&gt;', '>').replace('&amp;', '&'))


def positional(t):
    """'%d item' -> '%1$d item' (só quando há um único marcador sem posição)."""
    bare = re.findall(r'%[sd]', t)
    if len(bare) == 1 and not re.search(r'%\d+\$', t):
        return re.sub(r'%([sd])', r'%1$\1', t, count=1)
    return t


def esc(t):
    t = t.replace('&', '&amp;').replace('<', '&lt;')
    if not desktop:  # o Android exige escapar aspas; o Compose Multiplatform não
        t = t.replace('\\"', '"').replace('"', '\\"').replace("'", "\\'")
    return t


def load(path):
    spec = importlib.util.spec_from_file_location(os.path.basename(path)[:-3], path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return dict(m.S), dict(m.P)


_, pt_strings, pt_plurals, order = read_xml(os.path.join(res, 'values-pt/strings.xml'))
pt_strings = {k: unescape(v) for k, v in pt_strings.items()}
pt_plurals = {k: {q: unescape(t) for q, t in v.items()} for k, v in pt_plurals.items()}

if desktop:
    _, a_strings, a_plurals, _ = read_xml(os.path.join(root, 'app/src/main/res/values-pt/strings.xml'))
    android_pt = {k: positional(unescape(v)) for k, v in a_strings.items()}
    android_pt_plurals = {k: {q: positional(unescape(t)) for q, t in v.items()} for k, v in a_plurals.items()}

problems = []
for lang, folder in [('en', 'values'), ('es', 'values-es'), ('fr', 'values-fr')]:
    if desktop:
        S, P = load(os.path.join(here, 'desktop', f'{lang}.py'))
        base_s, base_p = load(os.path.join(here, f'{lang}.py'))
        for k, v in pt_strings.items():
            if k not in S and android_pt.get(k) == v and k in base_s:
                S[k] = positional(base_s[k])
        for k, v in pt_plurals.items():
            same = android_pt_plurals.get(k, {}).get('other') == v.get('other')
            if k not in P and same and k in base_p:
                P[k] = {q: positional(t) for q, t in base_p[k].items()}
    else:
        S, P = load(os.path.join(here, f'{lang}.py'))
    missing = set(pt_strings) - set(S)
    extra = set(S) - set(pt_strings)
    pmissing = set(pt_plurals) - set(P)
    pextra = set(P) - set(pt_plurals)
    for k in sorted(missing | extra | pmissing | pextra):
        problems.append(f'{lang}: chave divergente {k}')
    for k, v in S.items():
        ref = pt_strings.get(k, '')
        if sorted(PH.findall(v)) != sorted(PH.findall(ref)):
            problems.append(f'{lang}: marcadores diferentes em {k}: {PH.findall(v)} vs {PH.findall(ref)}')
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             f'<!-- Gerado a partir de values-pt (tools/i18n). Idioma: {lang}. -->', '<resources>']
    for k in order:
        if k in S:
            lines.append(f'    <string name="{k}">{esc(S[k])}</string>')
        elif k in P:
            ref = sorted(PH.findall(pt_plurals[k].get('other', '')))
            lines.append(f'    <plurals name="{k}">')
            for q, t in P[k].items():
                if sorted(PH.findall(t)) != ref:
                    problems.append(f'{lang}: plural {k}/{q} com marcadores diferentes do português')
                lines.append(f'        <item quantity="{q}">{esc(t)}</item>')
            lines.append('    </plurals>')
    lines.append('</resources>')
    os.makedirs(os.path.join(res, folder), exist_ok=True)
    open(os.path.join(res, folder, 'strings.xml'), 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    print(lang, len(S), 'textos +', len(P), 'plurais ->', os.path.relpath(os.path.join(res, folder), root))
if problems:
    print('\n'.join(problems))
    sys.exit(1)
print('OK: chaves e marcadores conferem com o português')

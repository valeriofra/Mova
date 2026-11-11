"""Gera strings.xml traduzidos (en, es, fr), validando chaves e marcadores contra o português.

Uso: python3 tools/i18n/gen.py app/src/main/res
O português (values-pt) é a referência; o inglês é o idioma padrão (values).
"""
import re, sys, importlib.util, os
here = os.path.dirname(os.path.abspath(__file__))
res = sys.argv[1]
pt = open(os.path.join(res, 'values-pt/strings.xml'), encoding='utf-8').read()
pt_strings = dict(re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', pt, re.S))
pt_plurals = {m.group(1): dict(re.findall(r'<item quantity="(\w+)">(.*?)</item>', m.group(2)))
              for m in re.finditer(r'<plurals name="([^"]+)">(.*?)</plurals>', pt, re.S)}
PH = re.compile(r'%(?:\d+\$)?[sd]')

def esc(t):
    t = t.replace('&', '&amp;').replace('<', '&lt;')
    t = t.replace('\\"', '"').replace('"', '\\"').replace("'", "\\'")
    return t

def load(lang):
    spec = importlib.util.spec_from_file_location(lang, os.path.join(here, f'{lang}.py'))
    m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
    return m.S, m.P

problems = []
for lang, folder in [('en', 'values'), ('es', 'values-es'), ('fr', 'values-fr')]:
    S, P = load(lang)
    missing = set(pt_strings) - set(S); extra = set(S) - set(pt_strings)
    pmissing = set(pt_plurals) - set(P); pextra = set(P) - set(pt_plurals)
    for k in sorted(missing | extra | pmissing | pextra):
        problems.append(f'{lang}: chave divergente {k}')
    for k, v in S.items():
        ref = pt_strings.get(k, '')
        if sorted(PH.findall(v)) != sorted(PH.findall(ref)):
            problems.append(f'{lang}: marcadores diferentes em {k}: {PH.findall(v)} vs {PH.findall(ref)}')
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             f'<!-- Gerado a partir de values-pt (tools/i18n). Idioma: {lang}. -->', '<resources>']
    for k in re.findall(r'<(?:string|plurals) name="([^"]+)"', pt):
        if k in S:
            attrs = ''
            lines.append(f'    <string name="{k}"{attrs}>{esc(S[k])}</string>')
        elif k in P:
            lines.append(f'    <plurals name="{k}">')
            for q, t in P[k].items():
                if sorted(PH.findall(t)) != ['%d']:
                    problems.append(f'{lang}: plural {k}/{q} sem %d')
                lines.append(f'        <item quantity="{q}">{esc(t)}</item>')
            lines.append('    </plurals>')
    lines.append('</resources>')
    os.makedirs(os.path.join(res, folder), exist_ok=True)
    open(os.path.join(res, folder, 'strings.xml'), 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    print(lang, len(S), 'textos +', len(P), 'plurais ->', folder)
if problems:
    print('\n'.join(problems)); sys.exit(1)
print('OK: chaves e marcadores conferem com o português')

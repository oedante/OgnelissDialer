#!/usr/bin/env python3
"""
«ВОПРОСЫ И ОТВЕТЫ» ДЛЯ САЙТА — ИЗ СТРОК ПРИЛОЖЕНИЯ (1.6.0).

  python scripts/faq_site.py            → docs/www/faq.html
  python scripts/faq_site.py --check    → только сверить, ничего не писать

Один текст на приложение и сайт. Вопросы, ответы и их порядок —
в приложении: ui/help/Faq.kt и строки faq_* в values*/strings.xml
(en, ru, de, fr). Скрипт собирает из них многоязычную страницу
docs/www/faq.html — в том же виде, что privacy.html и terms.html:
блоки <div id="lang-xx">. Её берёт сборщик сайта (site/build_site.py
в репозитории Ogneliss Mail) — файл кладётся в site/src_multilang/faq.html.

Пути к настройкам («Настройки → Вызовы → «Ответ на вызов»») собираются
так же, как в приложении (FaqScreen.settingPath): из названий раздела
и пункта (с 1.8.0 пункты не прячутся под «Дополнительно»). Кнопки
приложения на сайте — ссылками: письмо, лицензия, исходный код;
кнопки, ведущие в настройки, на сайте не нужны — путь есть в тексте.

Правка текста — в strings.xml, затем этот скрипт, затем сборка сайта.
"""
import html
import re
import sys
import xml.etree.ElementTree as ET
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'app/src/main/java/com/ogneliss/dialer'
RES = ROOT / 'app/src/main/res'
OUT = ROOT / 'docs/www/faq.html'

LANGS = {'ru': 'values-ru', 'en': 'values', 'de': 'values-de', 'fr': 'values-fr'}
MONTHS = {
    'ru': ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа',
           'сентября', 'октября', 'ноября', 'декабря'],
    'en': ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August',
           'September', 'October', 'November', 'December'],
    'de': ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August',
           'September', 'Oktober', 'November', 'Dezember'],
    'fr': ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août',
           'septembre', 'octobre', 'novembre', 'décembre'],
}
# Вступление на сайте: где те же ответы в приложении. {menu} — подпись
# кнопки меню на панели, {faq} — название экрана
LEAD = {
    'ru': 'Короткие ответы на частые вопросы. Те же ответы есть в самом приложении: «{menu}» на нижней панели → «{faq}», — и там кнопка под ответом сразу открывает нужную настройку.',
    'en': 'Short answers to common questions. The same answers are in the app itself: “{menu}” on the bottom bar → “{faq}” — and there the button under an answer opens the setting right away.',
    'de': 'Kurze Antworten auf häufige Fragen. Dieselben Antworten finden Sie in der App: „{menu}“ auf der unteren Leiste → „{faq}“ — dort öffnet die Taste unter einer Antwort gleich die passende Einstellung.',
    'fr': 'Des réponses courtes aux questions fréquentes. Les mêmes réponses figurent dans l’application : « {menu} » dans la barre du bas → « {faq} » — et le bouton sous une réponse y ouvre directement le réglage concerné.',
}
VERSION = {'ru': 'версия {v}', 'en': 'version {v}', 'de': 'Version {v}', 'fr': 'version {v}'}
BACK = {'ru': 'Назад к документам'}


def strings(lang):
    """Строки одного языка: имя → текст, с разбором экранирования Android."""
    tree = ET.parse(RES / LANGS[lang] / 'strings.xml')
    out = {}
    for el in tree.getroot().iter('string'):
        text = ''.join(el.itertext())
        text = re.sub(r"\\(['\"@?])", r'\1', text).replace('\\n', '\n')
        out[el.get('name')] = text
    return out


def android_format(fmt, *args):
    """%1$s, %2$s… → значения; в ответах других спецификаторов нет."""
    def repl(m):
        return str(args[int(m.group(1)) - 1])
    return re.sub(r'%(\d+)\$s', repl, fmt)


def registry():
    """id пункта → (раздел, строка названия, advanced); раздел → строка названия."""
    src = (SRC / 'ui/settings/SettingsRegistry.kt').read_text(encoding='utf-8')
    # Комментарии — вон: перед id пункта бывает разбор в /* … */
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'//[^\n]*', '', src)
    cats = dict(re.findall(r'^\s{4}([A-Z_]+)\(\s*"settings/[a-z_]+",\s*R\.string\.(\w+)', src, re.M))
    entries = {}
    for m in re.finditer(r'SettingEntry\((.*?)\n\s{8}\)', src, re.S):
        body = m.group(1)
        head = re.match(r'\s*"(\w+)",\s*SettingsCategory\.([A-Z_]+),\s*R\.string\.(\w+)', body)
        if head:
            entries[head.group(1)] = (head.group(2), head.group(3), bool(re.search(r'advanced\s*=\s*true', body)))
    return cats, entries


def faq_items():
    """[(строка группы, [(id, вопрос, ответ, [(вид, аргументы)…])…])…] — порядок Faq.kt."""
    src = (SRC / 'ui/help/Faq.kt').read_text(encoding='utf-8')
    body = src[src.index('val sections'):]
    starts = [(m.start(), m.group(1)) for m in re.finditer(r'FaqSection\(\s*R\.string\.(\w+)', body)]
    item_rx = re.compile(r'FaqItem\(\s*"(\w+)",\s*R\.string\.(\w+),\s*R\.string\.(\w+)'
                         r'(?:,\s*listOf\(((?:[^()]|\([^()]*\))*)\))?\s*\)')
    sections = []
    for i, (pos, title) in enumerate(starts):
        end = starts[i + 1][0] if i + 1 < len(starts) else len(body)
        items = []
        for qid, q, a, links in item_rx.findall(body[pos:end]):
            items.append((qid, q, a, re.findall(r'FaqLink\.(\w+)(?:\(([^()]*)\))?', links or '')))
        sections.append((title, items))
    if not sections or not all(items for _, items in sections):
        raise SystemExit('Faq.kt: не удалось разобрать группы и вопросы')
    return sections


def source_code_url():
    src = (SRC / 'util/AppLinks.kt').read_text(encoding='utf-8')
    return re.search(r'SOURCE_CODE\s*=\s*"([^"]+)"', src).group(1)


def support_email():
    src = (SRC / 'util/AppLinks.kt').read_text(encoding='utf-8')
    return re.search(r'SUPPORT_EMAIL\s*=\s*"([^"]+)"', src).group(1)


def app_version():
    gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
    return re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)


def path_text(s, link_kind, args, cats, entries):
    if link_kind == 'Section':
        cat = re.search(r'SettingsCategory\.([A-Z_]+)', args).group(1)
        return android_format(s['faq_path_section'], s['nav_settings'], s[cats[cat]])
    sid = re.match(r'\s*"(\w+)"', args).group(1)
    cat, title_res, _ = entries[sid]
    # С 1.8.0 пунктов под «Дополнительно» нет — путь всегда из трёх частей
    return android_format(s['faq_path'], s['nav_settings'], s[cats[cat]], s[title_res])


def block(lang, sections, cats, entries, today, version):
    s = strings(lang)
    e = html.escape
    mail, github = support_email(), source_code_url()
    when = f'{today.day} {MONTHS[lang][today.month - 1]} {today.year}' + (' г.' if lang == 'ru' else '')
    if lang == 'de':
        when = f'{today.day}. {MONTHS[lang][today.month - 1]} {today.year}'
    out = [f'<h1>{e(s["faq_title"])}</h1>',
           f'<p><strong>Ogneliss Dialer</strong> · {VERSION[lang].format(v=version)} · {when}</p>',
           '',
           f'<p class="lead">{e(LEAD[lang].format(menu=s["bar_menu"], faq=s["faq_title"]))}</p>']
    for title, items in sections:
        out += ['', f'<h2>{e(s[title])}</h2>']
        for qid, q, a, links in items:
            paths = [path_text(s, k, args, cats, entries) for k, args in links if k in ('Setting', 'Section')]
            answer = android_format(s[a], *paths)
            if re.search(r'%\d+\$s', answer):
                raise SystemExit(f'{lang}: {a} — осталась подстановка')
            out += ['', f'<h3 id="{qid}-{lang}">{e(s[q])}</h3>', f'<p>{e(answer)}</p>']
            refs = []
            for k, args in links:
                if k == 'Mail':
                    refs.append(f'<a href="mailto:{mail}">{e(s["faq_write"])}</a>')
                elif k == 'Document' and 'LICENSE' in args:
                    refs.append(f'<a href="/dialer/eula">{e(s["settings_license"])}</a>')
                elif k == 'SourceCode':
                    refs.append(f'<a href="{github}">{e(s["settings_source_code"])}</a>')
            if refs:
                out.append(f'<p class="faq-links">{" ".join(refs)}</p>')
    return '\n'.join(out)


def page(blocks):
    names = {'ru': 'Русская версия', 'en': 'Английская версия', 'de': 'Немецкая версия', 'fr': 'Французская версия'}
    parts = []
    for i, (lang, b) in enumerate(blocks.items()):
        hidden = '' if i == 0 else ' style="display:none;"'
        parts.append(f'<!-- {names[lang]} -->\n<div id="lang-{lang}"{hidden}>\n{b}\n</div><!-- /lang-{lang} -->')
    body = '\n\n\n'.join(parts)
    return f'''<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<!-- Собрано scripts/faq_site.py из строк приложения — править strings.xml и Faq.kt. -->
<title>Вопросы и ответы / Questions and answers — Ogneliss Dialer</title>
<link rel="stylesheet" href="/dialer/style.css">
<link rel="icon" href="/dialer/favicon.svg" type="image/svg+xml">
<link rel="alternate icon" href="/dialer/favicon-32.png" sizes="32x32">
<link rel="apple-touch-icon" href="/dialer/apple-touch-icon.png">
<link rel="stylesheet" href="/dialer/lang.css">
</head>
<body>
<main>

<header class="page-bar">
  <a href="/dialer" class="back" data-back><span aria-hidden="true">←</span> <span class="back-label">{BACK["ru"]}</span></a>
  <div class="lang-switcher"></div>
</header>

{body}


<script src="/dialer/lang.js" defer></script>

</main>
</body>
</html>
'''


def main():
    cats, entries = registry()
    sections = faq_items()
    today = date.today()
    version = app_version()
    blocks = {lang: block(lang, sections, cats, entries, today, version) for lang in LANGS}
    text = page(blocks)
    n = sum(len(items) for _, items in sections)
    if '--check' in sys.argv:
        print(f'Вопросов: {n}, языков: {len(blocks)} — сходится.')
        return 0
    OUT.write_text(text, encoding='utf-8')
    print(f'{OUT.relative_to(ROOT)}: вопросов {n}, языков {len(blocks)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())

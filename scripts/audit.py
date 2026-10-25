#!/usr/bin/env python3
"""
АУДИТ ПРОЕКТА ПЕРЕД СБОРКОЙ.

Проверяет то, что взгляд пропускает:
  1. Локализация: захардкоженные строки, расхождение en/ru, неиспользуемые ресурсы
  2. Достижимость: экраны без навигации, функции ViewModel без вызовов
  3. Согласованность: ключи настроек между реестром и состоянием
  4. Комментарии: файлы без пояснений
"""
import os, re, sys, json
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'app/src/main/java/com/ogneliss/dialer')
RES = os.path.join(ROOT, 'app/src/main/res')

problems = defaultdict(list)

def kt_files():
    for dirpath, _, names in os.walk(SRC):
        for n in names:
            if n.endswith('.kt'):
                yield os.path.join(dirpath, n)

def strip_comments_and_strings_context(text):
    """Убираем комментарии, чтобы не ловить примеры внутри пояснений."""
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    text = re.sub(r'//.*', '', text)
    return text

# ── 1. ЛОКАЛИЗАЦИЯ ────────────────────────────────────────────────────────
def parse_strings(path):
    if not os.path.exists(path):
        return {}
    content = open(path, encoding='utf-8').read()
    out = {}
    for m in re.finditer(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', content, re.S):
        # translatable="false" — названия марок и приложения: одинаковы
        # во всех языках и переводчику (в том числе автопереводу Google
        # Play) не отдаются. В локалях их быть не должно — lint считает
        # такой «перевод» ошибкой
        if 'translatable="false"' in m.group(2):
            continue
        out[m.group(1)] = m.group(3)
    for m in re.finditer(r'<plurals name="([^"]+)"', content):
        out[m.group(1)] = 'PLURAL'
    return out

en = parse_strings(os.path.join(RES, 'values/strings.xml'))
en.update(parse_strings(os.path.join(RES, 'values/plurals.xml')))

# Проверяем ВСЕ локали, а не только русскую: неполный перевод даёт
# смешанный интерфейс, где часть строк на одном языке, часть на другом.
# Заметить это без проверки можно только переключив язык вручную
for folder in sorted(os.listdir(RES)):
    if not folder.startswith('values-') or folder.count('-') > 1:
        continue
    lang = folder.removeprefix('values-')
    # Папки вроде values-night не являются локалями
    if lang in ('night', 'land', 'port', 'v26', 'v31'):
        continue

    translated = parse_strings(os.path.join(RES, folder, 'strings.xml'))
    translated.update(parse_strings(os.path.join(RES, folder, 'plurals.xml')))
    if not translated:
        continue

    for k in sorted(set(en) - set(translated)):
        problems[f'Нет перевода на {lang}'].append(k)
    for k in sorted(set(translated) - set(en)):
        problems[f'Есть в {lang}, нет в базовой локали'].append(k)

# Захардкоженные строки кириллицей в UI-коде
for f in kt_files():
    rel = os.path.relpath(f, SRC)
    if not (rel.startswith('ui/') or rel.startswith('call/')):
        continue
    code = strip_comments_and_strings_context(open(f, encoding='utf-8').read())
    for line in code.split('\n'):
        # Строки в логах предназначены разработчику: они читаются
        # в файле журнала при разборе проблемы и локализации не требуют.
        # Переводить их значило бы получать отчёты на языке, которого
        # не понимает тот, кто разбирает
        # Пропускаем: вызовы логгера и объявления констант для лога.
        # Строки в логах читает разработчик при разборе проблемы,
        # переводить их значило бы получать отчёты на чужом языке
        if re.search(r'Logger\.[diwe]\(|Log\.[diwe]\(|reason\s*=|TAG|'
                     r'const val \w*(LABEL|SOURCE|CONTACT|RULES|BLOCK|TAGS|INFO|WHAT)', line):
            continue

        for m in re.finditer(r'"([^"\n]*[А-Яа-яЁё][^"\n]*)"', line):
            val = m.group(1)
            if val.strip() in ('АБВГ','ДЕЖЗ','ИЙКЛ','МНОП','РСТУ','ФХЦЧ','ШЩЪЫ','ЬЭЮЯ'):
                continue
            problems['Захардкоженная строка в UI'].append(f'{rel}: "{val[:40]}"')

# Используемые ресурсы
used = set()
for f in kt_files():
    code = open(f, encoding='utf-8').read()
    used |= set(re.findall(r'R\.string\.(\w+)', code))
    used |= set(re.findall(r'R\.plurals\.(\w+)', code))

unused = sorted(set(en) - used - {'app_name', 'app_short_name'})
for k in unused:
    problems['Ресурс не используется'].append(k)

# ── 2. ДОСТИЖИМОСТЬ ЭКРАНОВ ───────────────────────────────────────────────
root_screen = open(os.path.join(SRC, 'ui/RootScreen.kt'), encoding='utf-8').read()
screens = []
for f in kt_files():
    if '/ui/screens/' not in f.replace('\\', '/'):
        continue
    code = open(f, encoding='utf-8').read()
    for m in re.finditer(r'^fun (\w+Screen)\(', code, re.M):
        screens.append((m.group(1), os.path.relpath(f, SRC)))

all_code = '\n'.join(open(f, encoding='utf-8').read() for f in kt_files())
for name, rel in screens:
    # Экран должен где-то вызываться, кроме собственного определения
    uses = len(re.findall(r'\b' + name + r'\s*\(', all_code))
    if uses <= 1:
        problems['Экран недостижим из навигации'].append(f'{name} ({rel})')

# ── 3. СОГЛАСОВАННОСТЬ НАСТРОЕК ───────────────────────────────────────────
registry = open(os.path.join(SRC, 'ui/settings/SettingsRegistry.kt'), encoding='utf-8').read()
state = open(os.path.join(SRC, 'ui/SettingsState.kt'), encoding='utf-8').read()
vm = open(os.path.join(SRC, 'ui/DialerViewModel.kt'), encoding='utf-8').read()
# Применение настроек вынесено из ViewModel в SettingsCommands:
# проверять нужно там, где таблица «ключ → сеттер» живёт на самом деле
commands_path = os.path.join(SRC, 'settings/SettingsCommands.kt')
settings_commands = open(commands_path, encoding='utf-8').read() \
    if os.path.exists(commands_path) else ''

switch_keys = set(re.findall(r'SettingKind\.Switch\("(\w+)"\)', registry))
state_keys = set(re.findall(r'"(\w+)" ->', state))
for k in switch_keys - state_keys:
    problems['Переключатель не обрабатывается в SettingsState'].append(k)

# Ветка может быть как однострочной, так и блоком в фигурных скобках
vm_switch = set(re.findall(r'"(\w+)" -> prefs\.set', settings_commands))
vm_switch |= set(re.findall(r'"(\w+)" -> \{[^}]*prefs\.set', settings_commands, re.S))
vm_switch |= set(re.findall(r'"(\w+)" -> prefs\.set', vm))
for k in switch_keys - vm_switch:
    problems['Переключатель нигде не применяется'].append(k)

choice_keys = set(re.findall(r'SettingKind\.Choice\("(\w+)"\)', registry))
dialog = open(os.path.join(SRC, 'ui/screens/SettingsChoiceDialog.kt'), encoding='utf-8').read()
for k in choice_keys:
    if f'"{k}"' not in dialog:
        problems['Выбор без вариантов в диалоге'].append(k)

action_keys = set(re.findall(r'SettingKind\.Action\("(\w+)"\)', registry))
handled = set(re.findall(r'key == "(\w+)"', vm))
handled |= set(re.findall(r'kind\.key == "(\w+)"', all_code))
# Составные when-ветки вида «"a", "b", "c" -> nav.navigate(...)».
#
# openDocument добавлен сюда же: документы теперь открываются
# на сайте, а не встроенным экраном, и без него проверка считала
# бы политику и условия необработанными
for branch in re.finditer(
    # Присваивание тоже считается обработкой: часть действий
    # открывает диалог поверх экрана, а не переходит на другой
    # Ветка с телом в фигурных скобках — тоже обработка: действия,
    # которые пишут файл, сначала запоминают, что писать, и лишь потом
    # открывают системный выбор файла, а это две строки, а не одна.
    # 1.6.0: разбор вынесен в runSettingAction — выгрузки идут через
    # saveToFile (окно выбора файла у раздела настроек)
    r'((?:"\w+"\s*,\s*)*"\w+")\s*->\s*(?:nav\.navigate|vm\.|openDocument|saveToFile|\w+ = true|on[A-Z]\w*\(\)|\{)',
    root_screen
):
    handled |= set(re.findall(r'"(\w+)"', branch.group(1)))
for k in sorted(action_keys - handled):
    problems['Действие настройки не обработано'].append(k)

# ── 4. КОММЕНТАРИИ ────────────────────────────────────────────────────────
for f in kt_files():
    rel = os.path.relpath(f, SRC)
    text = open(f, encoding='utf-8').read()
    # Сгенерированный код (05.10.2026: OgnelissIcons — 5500 вызовов moveTo/
    # lineTo) — доля комментариев к нему неприменима; пометка в заголовке
    if '@generated' in text[:3000]:
        continue
    lines = [l for l in text.split('\n') if l.strip()]
    comment_lines = [l for l in lines if l.strip().startswith(('//', '*', '/*'))]
    # Порог 10%: файл длиннее 40 строк без пояснений почти наверняка
    # содержит неочевидные решения, которые забудутся через месяц
    if len(lines) > 40 and len(comment_lines) / max(len(lines), 1) < 0.10:
        problems['Мало комментариев'].append(
            f'{rel} ({len(comment_lines)}/{len(lines)} строк)'
        )
    if not re.search(r'/\*\*', text):
        problems['Нет KDoc в файле'].append(rel)

# ── ОТЧЁТ ─────────────────────────────────────────────────────────────────
total = sum(len(v) for v in problems.values())
print(f'\n{"="*70}\nАУДИТ ПРОЕКТА: найдено замечаний — {total}\n{"="*70}')
for category in sorted(problems):
    items = problems[category]
    print(f'\n▸ {category}: {len(items)}')
    for item in items[:25]:
        print(f'    {item}')
    if len(items) > 25:
        print(f'    … ещё {len(items) - 25}')
print()
sys.exit(0)

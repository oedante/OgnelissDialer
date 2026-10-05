#!/usr/bin/env python3
"""
СТАТИЧЕСКИЕ ПРОВЕРКИ ПЕРЕД СБОРКОЙ.

Ловят ошибки, которые иначе всплывут только при компиляции — а компиляция
занимает минуты и требует сети. Здесь секунды и без зависимостей.

Что проверяется:
  1. Классы из манифеста существуют в исходниках
  2. Ссылки на version catalog разрешаются
  3. Ресурсы drawable/mipmap/xml, упомянутые в коде и манифесте, на месте
  4. Нет дубликатов имён функций в одном файле
  5. Нет незакрытых скобок
  6. Нет оставленных заглушек TODO/FIXME
  7. Имена Dagger-функций не совпадают с ключевыми словами Java
  8. Нет дублирующихся объявлений в одном пакете
  9. Нет осиротевших файлов после рефакторинга
 10. Импорты между пакетами на месте
 11. Ресурсы в composable освобождаются
 12. Нет неиспользуемых импортов
 13. Вызванные функции объявлены или импортированы
 14. Нет параметров Room из новых версий
 15. Настройки не продублированы, режимы доступны для выбора
 16. Правила ProGuard для release на месте
 17. Модели для Compose помечены @Immutable
 18. Конфигурация сборки согласована
 19. Нет пропущенных запятых в параметрах
 20. Нет дубликатов строковых ресурсов
 21. Одноимённые параметры одного типа
 22. Нет аннотаций посреди цепочек вызовов
 23. Свойства не объявлены дважды
 24. Нет осиротевших аннотаций
 25. Нет дубликатов ресурсов и квалификаторов в именах файлов
 26. Зависимости Compose подчинены BOM
 27. Нет дублирующих аргументов
 28. TAG объявлен там, где используется
 29. Классы не перекрывают импортированные
 30. compileSdk совпадает между модулями
 31. Нет устаревшего DSL в файлах сборки
 14. Файлы assets, упомянутые в коде, существуют
 32. Значки Material импортированы поимённо (wildcard их не покрывает)
 33. FlowRow/FlowColumn под @OptIn(ExperimentalLayoutApi)
 34. Все R.string.* объявлены в values/strings.xml
 35. Окна собраны на OgnelissDialog, а не на AlertDialog
 36. Отступы кратны четырём (размеры значков не проверяются)
"""
import glob, os, re, sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'app/src/main/java')
RES = os.path.join(ROOT, 'app/src/main/res')
problems = defaultdict(list)

def kt_files(base=SRC):
    for dp, _, ns in os.walk(base):
        for n in ns:
            if n.endswith('.kt'):
                yield os.path.join(dp, n)

# ── 1. Классы из манифеста ────────────────────────────────────────────────
manifest = open(os.path.join(ROOT, 'app/src/main/AndroidManifest.xml'), encoding='utf-8').read()
pkg = 'com.ogneliss.dialer'
declared = re.findall(r'android:name="(\.[\w.$]+)"', manifest)
for cls in set(declared):
    # $Receiver — вложенный класс, ищем внешний
    outer = cls.lstrip('.').split('$')[0]
    path = os.path.join(SRC, pkg.replace('.', '/'), outer.replace('.', '/') + '.kt')
    if not os.path.exists(path):
        # Класс может лежать в файле с другим именем — ищем по объявлению
        simple = outer.split('.')[-1]
        found = any(
            re.search(rf'\b(class|object)\s+{simple}\b', open(f, encoding='utf-8').read())
            for f in kt_files()
        )
        if not found:
            problems['Класс из манифеста не найден'].append(cls)

# ── 2. Version catalog ────────────────────────────────────────────────────
catalog = open(os.path.join(ROOT, 'gradle/libs.versions.toml'), encoding='utf-8').read()
lib_names = set()
in_libs = False
for line in catalog.split('\n'):
    if line.strip() == '[libraries]': in_libs = True; continue
    if line.strip().startswith('[') and line.strip() != '[libraries]': in_libs = False
    if in_libs and '=' in line:
        lib_names.add(line.split('=')[0].strip().replace('-', '.'))

plugin_names = set()
in_plugins = False
for line in catalog.split('\n'):
    if line.strip() == '[plugins]': in_plugins = True; continue
    if line.strip().startswith('[') and line.strip() != '[plugins]': in_plugins = False
    if in_plugins and '=' in line:
        plugin_names.add(line.split('=')[0].strip().replace('-', '.'))

for gradle_file in ['app/build.gradle.kts', 'build.gradle.kts', 'benchmark/build.gradle.kts']:
    path = os.path.join(ROOT, gradle_file)
    if not os.path.exists(path): continue
    content = open(path, encoding='utf-8').read()
    for ref in re.findall(r'libs\.((?!plugins|versions)[\w.]+)', content):
        if ref not in lib_names:
            problems['Зависимость не объявлена в catalog'].append(f'{gradle_file}: libs.{ref}')
    for ref in re.findall(r'libs\.plugins\.([\w.]+)', content):
        if ref not in plugin_names:
            problems['Плагин не объявлен в catalog'].append(f'{gradle_file}: {ref}')

# ── 3. Ресурсы ────────────────────────────────────────────────────────────
def res_exists(kind, name):
    for dp, _, ns in os.walk(RES):
        folder = os.path.basename(dp)
        if not folder.startswith(kind): continue
        for n in ns:
            if os.path.splitext(n)[0] == name:
                return True
    return False

all_code = '\n'.join(open(f, encoding='utf-8').read() for f in kt_files()) + manifest
for kind in ('drawable', 'mipmap', 'xml', 'color'):
    for name in set(re.findall(rf'R\.{kind}\.(\w+)', all_code)) | set(
            re.findall(rf'@{kind}/(\w+)', manifest)):
        if not res_exists(kind, name) and not (kind == 'color' and res_exists('values', 'ic_launcher_background')):
            problems[f'Ресурс {kind} не найден'].append(name)

# ── 4. Дубликаты функций в файле ──────────────────────────────────────────
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    # Дубли ищем в пределах ОДНОГО объявления: одноимённые методы
    # в разных интерфейсах одного файла — норма, а не ошибка
    # Анонимные объекты (object : Callback() { ... }) тоже дают
    # одноимённые методы — это переопределения, а не дубли
    text_wo_anon = re.sub(r'object\s*:\s*\w+[^{]*\{', 'ANON{', text)
    # Содержимое companion object исключается целиком: фабричные методы
    # вроде from() и of() объявляют в каждом enum, и это норма, а не дубли
    def strip_companions(src: str) -> str:
        out, i = [], 0
        while True:
            j = src.find('companion object', i)
            if j == -1:
                out.append(src[i:])
                break
            out.append(src[i:j])
            brace = src.find('{', j)
            if brace == -1:
                break
            depth, k = 0, brace
            while k < len(src):
                if src[k] == '{':
                    depth += 1
                elif src[k] == '}':
                    depth -= 1
                    if depth == 0:
                        break
                k += 1
            i = k + 1
        return ''.join(out)

    text_wo_anon = strip_companions(text_wo_anon)
    for block in re.split(r'^(?=(?:@\w+\s*\n)*(?:abstract |sealed |data )?(?:class|object|interface) )',
                          text_wo_anon, flags=re.M):
        names = re.findall(r'^\s*(?:private |internal |public |suspend )*fun (\w+)\s*\(',
                           block, re.M)
        for d in {n for n in names if names.count(n) > 1}:
            problems['Возможный дубликат функции'].append(
                f'{os.path.relpath(f, SRC)}: {d}')

# ── 5. Баланс скобок ──────────────────────────────────────────────────────
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    # ПОРЯДОК ШАГОВ ЗДЕСЬ — ЧАСТЬ ПРОВЕРКИ, А НЕ ОФОРМЛЕНИЕ.
    #
    # Сначала символьные литералы. Иначе кавычка внутри такого литерала —
    # например, trim('"', '«') — открывает для следующего шага мнимую
    # строку, которая тянется до следующей кавычки в файле и «съедает»
    # попавшие внутрь скобки. Ровно так проверка однажды сообщила
    # о дисбалансе в файле, где скобки сходились.
    #
    # Потом тройные кавычки: иначе обычный шаблон обрывает многострочную
    # строку на первой внутренней кавычке.
    clean = re.sub(r"'(?:[^'\\]|\\.)'", "''", text)
    clean = re.sub(r'"""(?:.|\n)*?"""', '""', clean)
    clean = re.sub(r'"(?:[^"\\]|\\.)*"', '""', clean)
    clean = re.sub(r'/\*.*?\*/', '', clean, flags=re.S)
    clean = re.sub(r'//.*', '', clean)
    if clean.count('{') != clean.count('}'):
        problems['Дисбаланс фигурных скобок'].append(
            f'{os.path.relpath(f, SRC)}: {clean.count("{")} открывающих, {clean.count("}")} закрывающих')
    if clean.count('(') != clean.count(')'):
        problems['Дисбаланс круглых скобок'].append(os.path.relpath(f, SRC))

# ── 6. Заглушки ───────────────────────────────────────────────────────────
for f in kt_files():
    for i, line in enumerate(open(f, encoding='utf-8'), 1):
        if re.search(r'\bTODO\b|\bFIXME\b|TODO\(\)', line):
            problems['Оставлена заглушка'].append(f'{os.path.relpath(f, SRC)}:{i}')

# ── 7. Имена Dagger-функций против ключевых слов Java ─────────────────────
# Hilt превращает имя @Provides-функции в имя Java-метода. Совпадение
# с ключевым словом Java ломает генерацию с невнятным
# «KSP failed with exit code: PROCESSING_ERROR», а из Kotlin-кода
# причина не видна вообще: компилятор Kotlin не возражает
JAVA_KEYWORDS = {
    'abstract','assert','boolean','break','byte','case','catch','char','class',
    'const','continue','default','do','double','else','enum','extends','final',
    'finally','float','for','goto','if','implements','import','instanceof','int',
    'interface','long','native','new','package','private','protected','public',
    'return','short','static','strictfp','super','switch','synchronized','this',
    'throw','throws','transient','try','void','volatile','while','record','var',
}
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    if '@Provides' not in text and '@Binds' not in text:
        continue
    for m in re.finditer(r'@(?:Provides|Binds)[^\n]*\n(?:\s*@[\w()@:.\s]*\n)*\s*(?:abstract )?fun (\w+)',
                         text):
        name = m.group(1)
        if name in JAVA_KEYWORDS:
            problems['Имя Dagger-функции — ключевое слово Java'].append(
                f'{os.path.relpath(f, SRC)}: fun {name}() → переименовать в provide{name.capitalize()}')

# Заодно проверяем имена параметров и свойств, попадающих в генерацию
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    if '@Inject' not in text:
        continue
    for m in re.finditer(r'(?:val|var)\s+(\w+)\s*:', text):
        if m.group(1) in JAVA_KEYWORDS:
            problems['Имя поля — ключевое слово Java'].append(
                f'{os.path.relpath(f, SRC)}: {m.group(1)}')

# ── 8. Дубликаты объявлений в одном пакете ────────────────────────────────
# Kotlin запрещает два одинаковых объявления верхнего уровня в пакете.
# Возникает при рефакторинге: новый файл создан, старый забыт удалить.
# Компилятор скажет «Conflicting overloads», но найти оба места сложно —
# они могут лежать в разных папках
package_decls = defaultdict(set)
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    pkg_match = re.search(r'^package ([\w.]+)', text, re.M)
    if not pkg_match:
        continue
    pkg = pkg_match.group(1)
    rel = os.path.relpath(f, SRC)
    for m in re.finditer(
        r'^(?:@\w+\s*\n)*(?:internal )?(?:val|fun|class|object|data class|enum class|interface)\s+(\w+)',
        text, re.M
    ):
        package_decls[(pkg, m.group(1))].add(rel)

for (pkg, name), places in package_decls.items():
    if len(places) > 1:
        problems['Дублирующееся объявление в пакете'].append(
            f'{name}: {", ".join(sorted(places))}')

# ── 9. Осиротевшие файлы ──────────────────────────────────────────────────
# Файл, ни одна функция которого нигде не вызывается, скорее всего
# остался от рефакторинга. Сам по себе он безвреден, но конфликтует
# именами и путает при чтении
all_text_local = '\n'.join(open(f, encoding='utf-8').read() for f in kt_files())
for f in kt_files():
    rel = os.path.relpath(f, SRC)
    text = open(f, encoding='utf-8').read()
    public_funs = re.findall(r'^(?:@\w+\s*\n)*fun ([A-Z]\w+)\s*\(', text, re.M)
    if not public_funs:
        continue
    # Composable часто вызывают с завершающей лямбдой: Name { ... }
    if all(len(re.findall(r'\b' + re.escape(fn) + r'\s*[({]', all_text_local)) <= 1
           for fn in public_funs):
        problems['Файл ни разу не используется'].append(rel)

# ── 10. Импорты между пакетами ────────────────────────────────────────────
# Composable-функция из другого пакета требует импорта. Забыть его легко
# при переносе кода между файлами, а сообщение компилятора
# «Unresolved reference» не подсказывает, где искать объявление
declared_in = {}
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    pkg_match = re.search(r'^package ([\w.]+)', text, re.M)
    if not pkg_match:
        continue
    for m in re.finditer(r'^(?:@\w+\s*\n)*fun ([A-Z]\w+)\s*\(', text, re.M):
        declared_in[m.group(1)] = (pkg_match.group(1), os.path.relpath(f, SRC))

for f in kt_files():
    text = open(f, encoding='utf-8').read()
    pkg_match = re.search(r'^package ([\w.]+)', text, re.M)
    if not pkg_match:
        continue
    pkg = pkg_match.group(1)
    rel = os.path.relpath(f, SRC)

    explicit = set(re.findall(r'^import ([\w.]+)$', text, re.M))
    # Импорт со звёздочкой: пакет без завершающей точки и звезды
    wildcard = set(re.findall(r'^import ([\w.]+)\.\*$', text, re.M))

    for m in re.finditer(r'(?<![\w.])([A-Z]\w+)\s*\(', text):
        name = m.group(1)
        if name not in declared_in:
            continue
        dpkg, drel = declared_in[name]
        if dpkg == pkg or drel == rel:
            continue
        if f'{dpkg}.{name}' in explicit or dpkg in wildcard:
            continue
        problems['Нет импорта для функции из другого пакета'].append(
            f'{rel}: {name} объявлена в {dpkg}')

# ── 11. Ресурсы, требующие освобождения ───────────────────────────────────
# ToneGenerator, MediaPlayer и подобные держат системные ресурсы.
# Создание в composable без DisposableEffect означает утечку: экземпляры
# копятся при каждом входе на экран, и система перестаёт выдавать новые
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    rel = os.path.relpath(f, SRC)
    for cls in ('DialpadFeedback', 'ToneGenerator', 'MediaPlayer'):
        creates = re.search(rf'remember\s*\{{\s*{cls}\(', text)
        if creates and 'onDispose' not in text:
            problems['Ресурс создан в composable без освобождения'].append(
                f'{rel}: {cls} без DisposableEffect')

# ── 12. Неиспользуемые импорты ────────────────────────────────────────────
# Сами по себе безвредны, но это след рефакторинга: импорт остался,
# а код, который его использовал, переехал. Скопившись, они мешают
# понять, от чего файл зависит на самом деле
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    rel = os.path.relpath(f, SRC)
    body = re.sub(r'^import .*$', '', text, flags=re.M)
    for m in re.finditer(r'^import [\w.]+\.(\w+)$', text, re.M):
        cls = m.group(1)
        if cls == '*':
            continue
        # Эти импорты используются неявно — через синтаксис делегирования
        # `by mutableStateOf(...)`. Имя в коде не встречается, но без
        # импорта делегат не компилируется
        if cls in ('getValue', 'setValue', 'provideDelegate'):
            continue
        if not re.search(r'\b' + re.escape(cls) + r'\b', body):
            problems['Неиспользуемый импорт'].append(f'{rel}: {cls}')

# ── 13. Вызов необъявленной функции внутри файла ──────────────────────────
# Composable с большой буквы, вызванный в файле и не объявленный ни здесь,
# ни в другом файле проекта, ни импортированный — гарантированная ошибка
# «Unresolved reference». Возникает при переносе кода между файлами:
# вызов остался, объявление уехало
project_funs = set()
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    project_funs |= set(re.findall(r'^\s*(?:@\w+\s*\n\s*)*(?:private |internal )?fun ([A-Z]\w+)\s*\(',
                                   text, re.M))

for f in kt_files():
    text = open(f, encoding='utf-8').read()
    rel = os.path.relpath(f, SRC)
    declared_here = set(re.findall(r'fun ([A-Z]\w+)\s*\(', text))
    imported = set(re.findall(r'^import [\w.]+\.([A-Z]\w+)$', text, re.M))
    wildcard_used = bool(re.search(r'^import [\w.]+\.\*$', text, re.M))

    # Функции того же пакета видны без импорта: собираем их по соседям
    package = re.search(r'^package ([\w.]+)', text, re.M)
    same_package = set()
    if package:
        for other in kt_files():
            if other == f:
                continue
            other_text = open(other, encoding='utf-8').read()
            other_pkg = re.search(r'^package ([\w.]+)', other_text, re.M)
            if other_pkg and other_pkg.group(1) == package.group(1):
                same_package |= set(re.findall(
                    r'^\s*(?:@\w+\s*\n\s*)*(?:private |internal )?fun ([A-Z]\w+)\s*\(',
                    other_text, re.M))

    for m in re.finditer(r'^\s{8,}([A-Z]\w+)\(', text, re.M):
        name = m.group(1)
        if name in declared_here or name in imported or name in same_package:
            continue
        # При импорте со звёздочкой проверить нельзя — пропускаем
        if wildcard_used:
            continue
        # Функция объявлена где-то в проекте, но сюда не импортирована
        if name in project_funs:
            problems['Функция вызвана, но не объявлена и не импортирована'].append(
                f'{rel}: {name}()')

# ── 14. Параметры Room, доступные не во всех версиях ──────────────────────
# dropAllTables добавлен в Room 2.7. На более ранних версиях вызов
# с этим параметром не компилируется, а сообщение — «No parameter
# with name» — не подсказывает, что дело в версии библиотеки
#
# Версия Room берётся из каталога версий: раньше проверка ругалась
# на любой dropAllTables, потому что Room был 2.6.1 навсегда. С 1.1.3
# Room 2.8 — параметр законен, а на 2.6 запрещён по-прежнему.
_catalog = os.path.join(ROOT, 'gradle', 'libs.versions.toml')
_room = None
if os.path.exists(_catalog):
    _m = re.search(r'^room\s*=\s*"(\d+)\.(\d+)', open(_catalog, encoding='utf-8').read(), re.M)
    if _m:
        _room = (int(_m.group(1)), int(_m.group(2)))
if _room is None or _room < (2, 7):
    for f in kt_files():
        text = open(f, encoding='utf-8').read()
        code = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
        code = re.sub(r'//.*', '', code)
        if 'dropAllTables' in code:
            problems['Параметр Room недоступен в текущей версии'].append(
                f'{os.path.relpath(f, SRC)}: dropAllTables требует Room 2.7+')

# ── 15. Полнота настроек ──────────────────────────────────────────────────
# Реестр настроек — декларация, и в ней легко продублировать пункт
# при правках. Дубликат даёт две одинаковых строки на экране,
# а перечисление вариантов, отставшее от enum, — недоступный режим:
# он есть в коде, но выбрать его нельзя
registry_path = os.path.join(SRC, 'ui/settings/SettingsRegistry.kt')
dialog_path = os.path.join(SRC, 'ui/screens/SettingsChoiceDialog.kt')

if os.path.exists(registry_path):
    registry = open(registry_path, encoding='utf-8').read()
    ids = re.findall(r'SettingEntry\(\s*"(\w+)"', registry)
    for entry_id in {i for i in ids if ids.count(i) > 1}:
        problems['Настройка объявлена дважды в реестре'].append(entry_id)

# Все значения ThemeMode должны быть в списке выбора
prefs_path = os.path.join(SRC, 'settings/Prefs.kt')
if os.path.exists(prefs_path) and os.path.exists(dialog_path):
    prefs_src = open(prefs_path, encoding='utf-8').read()
    dialog = open(dialog_path, encoding='utf-8').read()
    theme_enum = re.search(r'enum class ThemeMode \{([^}]*)\}', prefs_src)
    if theme_enum:
        for value in re.findall(r'\b([A-Z_]{3,})\b', theme_enum.group(1)):
            if value not in dialog:
                problems['Режим темы недоступен для выбора'].append(
                    f'{value} есть в ThemeMode, но не в списке вариантов')

# ── 16. Правила ProGuard для release ──────────────────────────────────────
# Обфускация имён enum ломает valueOf при чтении настроек из хранилища:
# все настройки сбрасываются к умолчаниям после первой release-сборки,
# а в debug этого не видно вовсе
proguard_path = os.path.join(ROOT, 'app/proguard-rules.pro')
if os.path.exists(proguard_path):
    rules = open(proguard_path, encoding='utf-8').read()
    required = {
        'valueOf(java.lang.String)':
            'без него обфускация enum ломает чтение сохранённых настроек',
        'SourceFile':
            'без него стек падения нечитаем',
        'com.ogneliss.dialer.call':
            'сервисы телефонии создаются системой по имени и будут вырезаны',
    }
    for token, why in required.items():
        if token not in rules:
            problems['Отсутствует правило ProGuard'].append(f'{token} — {why}')

# ── 17. Стабильность моделей для Compose ──────────────────────────────────
# Класс с полем типа List, Map или Set Compose считает НЕСТАБИЛЬНЫМ:
# он не сравнивает такие объекты и перерисовывает всё, что от них
# зависит, при каждом обновлении. Один непомеченный класс делает
# нестабильным всё, что его содержит, — и вся работа по подготовке
# данных заранее пропадает впустую.
#
# Проверяются только модели, попадающие в интерфейс: внутренние
# структуры репозиториев в композицию не приходят
UI_MODEL_DIRS = ('data/', 'ui/')
for f in kt_files():
    rel = os.path.relpath(f, SRC)
    if not rel.startswith(UI_MODEL_DIRS):
        continue
    text = open(f, encoding='utf-8').read()

    for m in re.finditer(
        r'((?:@\w+\s*\n\s*)*)data class (\w+)\(((?:[^()]|\([^()]*\))*)\)',
        text
    ):
        annotations, name, body = m.group(1), m.group(2), m.group(3)
        if '@Immutable' in annotations or '@Stable' in annotations:
            continue
        # Коллекции и функциональные типы одинаково делают класс
        # нестабильным: за List может стоять изменяемая реализация,
        # за лямбдой — захват изменяемого состояния
        unstable_field = None
        if re.search(r':\s*(List|Map|Set)<', body):
            unstable_field = 'коллекция'
        elif re.search(r'->\s*\w+', body):
            unstable_field = 'лямбда'

        if unstable_field:
            problems['Модель без @Immutable'].append(
                f'{rel}: {name} ({unstable_field}) — Compose будет '
                f'перерисовывать всё, что её содержит'
            )

# ── 18. Согласованность конфигурации сборки ───────────────────────────────
gradle_path = os.path.join(ROOT, 'app/build.gradle.kts')
manifest_path = os.path.join(ROOT, 'app/src/main/AndroidManifest.xml')

if os.path.exists(gradle_path) and os.path.exists(manifest_path):
    gradle = open(gradle_path, encoding='utf-8').read()
    manifest = open(manifest_path, encoding='utf-8').read()

    # Подстановки манифеста должны быть объявлены для ВСЕХ типов сборки,
    # иначе сборка одного из них падает с «placeholder not found»,
    # и обнаруживается это только при попытке собрать именно его
    # applicationId и подобные подставляет сам AGP — объявлять не нужно
    BUILTIN_PLACEHOLDERS = {'applicationId', 'versionCode', 'versionName'}

    for ph in set(re.findall(r'\$\{(\w+)\}', manifest)) - BUILTIN_PLACEHOLDERS:
        declared = len(re.findall(rf'manifestPlaceholders\["{ph}"\]', gradle))
        if declared == 0:
            problems['Подстановка манифеста не объявлена'].append(ph)
        elif declared < 2:
            problems['Подстановка объявлена не для всех типов сборки'].append(
                f'{ph} — объявлена {declared} раз; нужна и в debug, и в release')

    # resValue не должен дублировать строку из ресурсов: сборка падает
    # с «duplicate resources», причём только при сборке того типа,
    # где resValue объявлен
    strings_path = os.path.join(RES, 'values/strings.xml')
    if os.path.exists(strings_path):
        existing = set(re.findall(r'<string name="([^"]+)"',
                                  open(strings_path, encoding='utf-8').read()))
        for name in re.findall(r'resValue\("string",\s*"(\w+)"', gradle):
            if name in existing:
                problems['resValue дублирует строку из ресурсов'].append(name)

    # versionName и versionCode должны быть заданы
    if 'versionCode' not in gradle:
        problems['Конфигурация сборки'].append('не задан versionCode')

# ── 19. Пропущенные запятые в конструкторе ────────────────────────────────
# Вставка поля в середину data class без запятой — частая ошибка,
# а сообщение компилятора о ней невнятное. Проверяем ТОЛЬКО тело
# конструктора: обычные свойства класса запятых не требуют
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    rel = os.path.relpath(f, SRC)

    for cls in re.finditer(r'(?:data )?class \w+[^(]*\(', text):
        # Находим границы списка параметров по балансу скобок
        start = cls.end() - 1
        depth, end = 0, start
        for i in range(start, min(len(text), start + 4000)):
            if text[i] == '(':
                depth += 1
            elif text[i] == ')':
                depth -= 1
                if depth == 0:
                    end = i
                    break
        params = text[start + 1:end]

        # Параметр без запятой, за которым идёт следующий
        for m in re.finditer(
            r'^(\s+(?:@\w+\s+)?(?:val|var) [^,\n]+[^,\s])$\n(?:\s*(?://|/\*\*?).*$\n)*\s+(?:@\w+|val|var)',
            params, re.M
        ):
            line_no = text[:start + m.start()].count('\n') + 1
            problems['Пропущена запятая в конструкторе'].append(f'{rel}:{line_no}')

# ── 20. Дубликаты строковых ресурсов ──────────────────────────────────────
# Два объявления с одним именем в файле — ошибка сборки «Found item
# String/name more than once». Возникает при добавлении строки без
# проверки, что она уже есть: имена вроде reminder_title или error_title
# напрашиваются сами, и совпадение случается легко.
#
# Проверяются и <string>, и <plurals>: они делят одно пространство имён,
# и строка с именем существующего plurals тоже даст конфликт
from collections import Counter as _Counter

for folder in sorted(os.listdir(RES)):
    if not folder.startswith('values'):
        continue
    for fname in ('strings.xml', 'plurals.xml'):
        path = os.path.join(RES, folder, fname)
        if not os.path.exists(path):
            continue
        content = open(path, encoding='utf-8').read()
        names = re.findall(r'<(?:string|plurals) name="([^"]+)"', content)
        for name, count in _Counter(names).items():
            if count > 1:
                problems['Дубликат строкового ресурса'].append(
                    f'{folder}/{fname}: {name} объявлен {count} раза')

# ── 21. Согласованность типов ВНУТРИ одного файла ──────────────────────────
# Параметр с одним именем в разных функциях ОДНОГО файла почти всегда
# означает одно и то же: такие функции вызывают друг друга и передают
# значение по цепочке. Расхождение типов там — след частичной правки:
# сигнатуру поменяли в одной функции, в соседней забыли.
#
# Между разными файлами такой проверки нет намеренно: имена вроде
# entries или numbers законно означают разное в разных местах,
# и предупреждения о них были бы шумом
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    rel = os.path.relpath(f, SRC)

    local = {}
    for m in re.finditer(r'^\s+(\w{6,}):\s*((?:Map|Set|List)<[^>]+>)', text, re.M):
        local.setdefault(m.group(1), set()).add(m.group(2))

    for name, types in local.items():
        # Полное имя типа и краткое — одно и то же
        normalized = {t.split('<')[0] + '<' + t.split('<', 1)[1].split('.')[-1] for t in types}
        if len(normalized) > 1:
            problems['Одноимённый параметр разных типов в файле'].append(
                f'{rel}: {name} — {", ".join(sorted(types))}')

# ── 22. Аннотация посреди цепочки вызовов ─────────────────────────────────
# @Suppress или другая аннотация перед строкой, начинающейся с точки,
# недопустима: компилятор ждёт там продолжение выражения, а получает
# объявление — и выдаёт «Expecting member declaration» на КАЖДЫЙ
# последующий токен. Одна такая строка даёт восемь сообщений подряд,
# и понять по ним причину трудно
for f in kt_files():
    lines = open(f, encoding='utf-8').read().split('\n')
    rel = os.path.relpath(f, SRC)
    for i, line in enumerate(lines[:-1]):
        stripped = line.strip()
        if stripped.startswith('@') and lines[i + 1].strip().startswith('.'):
            problems['Аннотация посреди цепочки вызовов'].append(
                f'{rel}:{i + 1} — {stripped}: перенесите её на функцию целиком')

# ── 23. Повторное объявление свойства в одном классе ──────────────────────
# Два `val` с одним именем в ОДНОМ классе — ошибка «Conflicting
# declarations», а обращения к ним дают «Overload resolution ambiguity».
# Возникает при вставке свойства без проверки, что оно уже есть.
#
# Границы класса определяются по балансу скобок: без этого поля разных
# классов одного файла считались бы дубликатами, а они законны
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    rel = os.path.relpath(f, SRC)

    for cls in re.finditer(r'^(?:@\w+\s*\n)*(?:abstract |open |sealed )?class (\w+)',
                           text, re.M):
        brace = text.find('{', cls.end())
        if brace == -1:
            continue
        depth, end_pos = 0, brace
        for i in range(brace, len(text)):
            if text[i] == '{':
                depth += 1
            elif text[i] == '}':
                depth -= 1
                if depth == 0:
                    end_pos = i
                    break
        body = text[brace:end_pos]

        # Только свойства первого уровня вложенности: у них ровно
        # четыре пробела отступа. Локальные переменные внутри функций
        # законно повторяются
        props = re.findall(r'^    (?:private |internal )?(?:val|var) (\w+)\s*[:=]',
                           body, re.M)
        for name, count in _Counter(props).items():
            if count > 1:
                line = text[:brace].count('\n') + 1
                problems['Свойство объявлено дважды'].append(
                    f'{rel}: {name} в классе {cls.group(1)} (строка ~{line}) — '
                    f'{count} объявления')

# ── 24. Осиротевшая аннотация ─────────────────────────────────────────────
# Аннотация, за которой идёт КОММЕНТАРИЙ, — след удаления функции:
# её тело и документацию убрали, а аннотацию забыли. Она прилипает
# к следующему объявлению, у которого своя аннотация, и компилятор
# сообщает «This annotation is not repeatable».
#
# Правило простое: между аннотацией и объявлением комментарий стоять
# не может. Если он там есть — аннотация относилась к чему-то другому.
#
# Указание компилятора при этом сбивает с толку: он показывает
# на ВТОРУЮ аннотацию, тогда как лишняя — первая
for f in kt_files():
    lines = open(f, encoding='utf-8').read().split('\n')
    rel = os.path.relpath(f, SRC)

    for i, line in enumerate(lines[:-1]):
        stripped = line.strip()
        if not stripped.startswith('@'):
            continue

        # Объявление на той же строке — аннотация на месте.
        # Например: @Retention(...) @Qualifier annotation class IoDispatcher
        if re.search(r'\b(class|fun|val|var|object|interface)\b', stripped):
            continue

        next_line = lines[i + 1].strip()
        if next_line.startswith(('/**', '/*')):
            problems['Осиротевшая аннотация'].append(
                f'{rel}:{i + 1} — {stripped.split("(")[0]}: между аннотацией '
                f'и объявлением комментарий, значит объявление удалили')

# ── 25. Дубликат ресурса в одной папке ────────────────────────────────────
# Один ресурс, объявленный в ДВУХ файлах ОДНОЙ папки, — ошибка сборки
# «Duplicate resources». В разных квалифицированных папках
# (values и values-night) это норма: система выбирает нужный сама.
#
# Частая причина — файл с суффиксом в имени вместо папки:
# values/colors-night.xml вместо values-night/colors.xml. Суффикс
# в Android относится к ПАПКЕ, и в имени файла он ничего не значит
for folder in sorted(os.listdir(RES)):
    folder_path = os.path.join(RES, folder)
    if not folder.startswith('values') or not os.path.isdir(folder_path):
        continue

    seen = {}
    for fname in sorted(os.listdir(folder_path)):
        if not fname.endswith('.xml'):
            continue
        content = open(os.path.join(folder_path, fname), encoding='utf-8').read()
        for m in re.finditer(r'<(string|color|dimen|integer|bool|plurals) name="([^"]+)"',
                             content):
            key = f'{m.group(1)}/{m.group(2)}'
            if key in seen and seen[key] != fname:
                problems['Дубликат ресурса в одной папке'].append(
                    f'{folder}: {key} объявлен и в {seen[key]}, и в {fname}')
            seen.setdefault(key, fname)

    # Файл с квалификатором в имени — почти всегда ошибка
    for fname in os.listdir(folder_path):
        if re.search(r'-(night|v\d+|land|ru|de|en)\.xml$', fname):
            problems['Квалификатор в имени файла вместо папки'].append(
                f'{folder}/{fname} — суффикс должен быть у ПАПКИ, '
                f'например values-night/{re.sub(r"-\w+\.xml$", ".xml", fname)}')

# ── 26. Зависимости Compose без BOM ───────────────────────────────────────
# Зависимость androidx.compose без версии берёт последнюю доступную,
# если в её конфигурации нет BOM. Новая версия может требовать AGP
# и compileSdk выше, чем у проекта, — сборка падает на проверке
# метаданных AAR с десятком сообщений сразу.
#
# Коварство в том, что одна такая строка поднимает ВЕСЬ Compose:
# версии внутри группы согласованы между собой
gradle_path = os.path.join(ROOT, 'app/build.gradle.kts')
if os.path.exists(gradle_path):
    gradle_text = open(gradle_path, encoding='utf-8').read()

    # 1.3.3: BOM задаётся и строкой с версией из переключателя
    # -PcomposeLegacy — platform("androidx.compose:compose-bom:$...")
    configs_with_bom = set(
        re.findall(r'(\w+)\(platform\(libs\.compose\.bom\)\)', gradle_text)
    ) | set(
        re.findall(r'(\w+)\(platform\("androidx\.compose:compose-bom:[^"]+"\)\)', gradle_text)
    )

    for m in re.finditer(
        r'(\w+)\("(androidx\.compose[.\w-]*:[\w-]+)"\)', gradle_text
    ):
        config, dep = m.group(1), m.group(2)
        if ':' in dep and dep.count(':') > 1:
            continue  # версия указана явно
        if config not in configs_with_bom:
            problems['Зависимость Compose без BOM'].append(
                f'{config}("{dep}") — добавьте {config}(platform(libs.compose.bom))')

# ── Про item вне LazyColumn: проверки НЕТ ──────────────────────────────────
#
# Такая проверка была написана и удалена. Причина в честном итоге
# испытания: на искусственно внесённой ошибке она не срабатывала.
#
# Определить, находится ли вызов item внутри LazyColumn, по тексту
# без разбора синтаксиса нельзя: список бывает в той же функции, но
# в другой ветке, лямбды вкладываются друг в друга, вызовы занимают
# несколько строк. Любая эвристика здесь либо ругается на верный код,
# либо пропускает ошибочный — и второе хуже, потому что даёт ложную
# уверенность.
#
# Ошибка при этом безобидна с точки зрения последствий: компилятор
# её ловит всегда. Сообщение только неудачное — «Unresolved reference
# 'item'», а следом «@Composable invocations can only happen from
# the context of a @Composable function», указывающее не на причину.
#
# Если увидите эту пару сообщений: item и items существуют ТОЛЬКО
# внутри LazyColumn, LazyRow и LazyVerticalGrid. Вне списка содержимое
# вызывается напрямую.

# ── 27. Дублирующий именованный аргумент ──────────────────────────────────
# Одна и та же строка «имя = значение,» дважды подряд — след двойной
# вставки: правку применили, потом применили ещё раз.
#
# Компилятор сообщает «Argument already passed for this parameter»,
# но найти строку глазами в вызове на двадцать параметров трудно
for f in kt_files():
    lines = open(f, encoding='utf-8').read().split('\n')
    rel = os.path.relpath(f, SRC)

    for i in range(len(lines) - 1):
        current = lines[i].strip()
        following = lines[i + 1].strip()

        if (re.match(r'^\w+\s*=\s*', current)
                and current == following
                and current.endswith(',')):
            problems['Дублирующий аргумент'].append(
                f'{rel}:{i + 1} — {current[:40]}')

# ── 28. TAG используется, но не объявлен ──────────────────────────────────
# Logger.w(TAG, ...) без объявления константы. Возникает при переносе
# кода между файлами: вызов скопировали, а companion object с меткой —
# нет. Ошибка простая, но в длинном файле её причина неочевидна
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    rel = os.path.relpath(f, SRC)

    code = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    code = re.sub(r'//.*', '', code)

    if re.search(r'Logger\.\w+\(TAG', code) and 'val TAG' not in code:
        problems['TAG не объявлен'].append(rel)

# ── 29. Класс перекрывает импортированный ─────────────────────────────────
# Вложенный класс с тем же именем, что у импортированного, перекрывает
# его — и компилятор молча использует не тот.
#
# Ошибка коварная: импорт на месте, объявление на месте, а обращение
# к полю не разрешается. Сообщение говорит «нет такого поля», хотя
# в настоящей модели оно есть.
#
# Так было с ShortcutTarget: копия внутри ContactShortcuts не имела
# поля photoUri, и значки ярлыков не собирались
for f in kt_files():
    text = open(f, encoding='utf-8').read()
    rel = os.path.relpath(f, SRC)

    imported = set(re.findall(r'^import [\w.]+\.(\w+)$', text, re.M))
    declared = set(re.findall(r'^\s+(?:private |internal )?(?:data )?class (\w+)',
                              text, re.M))

    for name in imported & declared:
        problems['Класс перекрывает импортированный'].append(
            f'{rel}: {name} объявлен внутри файла и импортирован — '
            f'компилятор возьмёт вложенный')

# ── 30. Версии SDK совпадают между модулями ───────────────────────────────
# Модуль замеров, собранный под другой compileSdk, — источник двух бед
# сразу: AGP выдаёт предупреждение о непроверенной версии, а профиль
# описывает не то приложение, которое поедет пользователям.
#
# Так и было: в benchmark стоял 37 при 35 в app, и generateBaselineProfile
# не доходил до прогона
sdk_by_module = {}
for module in ('app', 'benchmark'):
    path = os.path.join(ROOT, module, 'build.gradle.kts')
    if not os.path.exists(path):
        continue
    text = open(path, encoding='utf-8').read()
    code = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
    code = re.sub(r'//.*', '', code)

    m = re.search(r'compileSdk\s*=\s*(\d+)', code)
    if m:
        sdk_by_module[module] = m.group(1)

if len(set(sdk_by_module.values())) > 1:
    details = ', '.join(f'{k} = {v}' for k, v in sdk_by_module.items())
    problems['Разные compileSdk в модулях'].append(
        f'{details} — профиль опишет не то приложение, что поедет '
        f'пользователям')

# ── 31. Устаревший DSL в файлах сборки ────────────────────────────────────
# Эти конструкции меняются от версии к версии и дают ошибку компиляции
# самого скрипта сборки — то есть проект не собирается вообще,
# ещё до компиляции кода
DEPRECATED_DSL = {
    'kotlinOptions': 'заменить на блок kotlin { compilerOptions { ... } } вне android { }',
    'localeFilters': 'нет в AGP до 8.9 — либо убрать, либо defaultConfig.resourceConfigurations',
    'packagingOptions': 'переименовано в packaging',
    'buildToolsVersion': 'задаётся автоматически, указывать не нужно',
}
for gradle_file in ['app/build.gradle.kts', 'build.gradle.kts', 'benchmark/build.gradle.kts']:
    path = os.path.join(ROOT, gradle_file)
    if not os.path.exists(path):
        continue
    content = open(path, encoding='utf-8').read()
    # Комментарии не считаем: там эти слова упоминаются как пояснение
    code = re.sub(r'/\*.*?\*/', '', content, flags=re.S)
    code = re.sub(r'//.*', '', code)
    for token, hint in DEPRECATED_DSL.items():
        if re.search(r'^\s*' + token + r'\b', code, re.M):
            problems['Устаревший DSL в файле сборки'].append(f'{gradle_file}: {token} — {hint}')

# ── 14. Assets ────────────────────────────────────────────────────────────
assets_dir = os.path.join(ROOT, 'app/src/main/assets')
# Юридические документы лежат по языкам: legal/<код>/<файл>.
# Достаточно, чтобы файл нашёлся хотя бы в одной языковой папке —
# на остальные языки предусмотрен откат
for name in set(re.findall(r'"(\w+\.txt)"', all_code)):
    found = os.path.exists(os.path.join(assets_dir, name))
    if not found:
        for root, _, files in os.walk(assets_dir):
            if name in files:
                found = True
                break
    if not found:
        problems['Файл assets не найден'].append(name)

# ══════════════════════════════════════════════════════════════════════════
# 32-34. ТО, ЧТО УРОНИЛО СБОРКУ 1.0.8
# ══════════════════════════════════════════════════════════════════════════
# Три вида ошибок, которые ловятся чтением файла, а обнаружились только
# компилятором — то есть спустя минуты сборки и на чужой машине:
#
#   • Icons.AutoMirrored.Filled.X без импорта. Значки — расширяющие
#     свойства, wildcard-импорт filled.* их НЕ покрывает. Ошибка выглядит
#     обманчиво: «receiver type mismatch», как будто значка нет в версии
#     библиотеки, хотя не хватает одной строки импорта.
#   • Использование Icons без импорта самого объекта Icons.
#   • FlowRow без @OptIn(ExperimentalLayoutApi::class) — файл не собирается.
#
# ── 32. Импорты значков ───────────────────────────────────────────────────
ICON_GROUPS = [
    (r'Icons\.AutoMirrored\.Filled\.(\w+)', 'automirrored.filled'),
    (r'Icons\.(?:Default|Filled)\.(\w+)', 'filled'),
    (r'Icons\.Outlined\.(\w+)', 'outlined'),
    (r'Icons\.Rounded\.(\w+)', 'rounded'),
]
for path in kt_files():
    src = open(path, encoding='utf-8').read()
    body = re.sub(r'//.*', '', re.sub(r'/\*.*?\*/', '', src, flags=re.S))
    imports = set(re.findall(r'^import\s+([\w.*]+)', src, re.M))

    def imported(full):
        return full in imports or full.rsplit('.', 1)[0] + '.*' in imports

    rel = os.path.relpath(path, SRC)
    if re.search(r'\bIcons\.', body) and not imported('androidx.compose.material.icons.Icons'):
        problems['Значок без импорта'].append(f'{rel}: нет import ...material.icons.Icons')
    for pattern, package in ICON_GROUPS:
        for name in sorted(set(re.findall(pattern, body))):
            full = f'androidx.compose.material.icons.{package}.{name}'
            if not imported(full):
                problems['Значок без импорта'].append(f'{rel}: нет import ...{package}.{name}')

# ── 33. FlowRow без согласия на экспериментальный API ──────────────────────
for path in kt_files():
    src = open(path, encoding='utf-8').read()
    # Файл со своей обёрткой FlowRow проверяем по ней самой, а не по вызовам
    if re.search(r'^\s*(private |internal )?fun FlowRow\(', src, re.M):
        continue
    if '@file:OptIn' in src and 'ExperimentalLayoutApi' in src:
        continue
    lines = src.split('\n')
    fun_starts = [i for i, l in enumerate(lines)
                  if re.match(r'\s*(private |internal |public )?fun \w+', l)]
    for i, line in enumerate(lines):
        if not re.match(r'\s*Flow(Row|Column)\(', line):
            continue
        prev = [p for p in fun_starts if p < i]
        if not prev:
            continue
        head = '\n'.join(lines[max(0, prev[-1] - 8):prev[-1] + 1])
        if 'ExperimentalLayoutApi' not in head:
            problems['FlowRow без @OptIn'].append(
                f'{os.path.relpath(path, SRC)}:{i + 1}'
            )

# ── 34. Строковые ресурсы, которых нет ────────────────────────────────────
base_strings = os.path.join(RES, 'values/strings.xml')
if os.path.exists(base_strings):
    declared_strings = set(re.findall(r'<(?:string|plurals)\s+name="(\w+)"',
                                      open(base_strings, encoding='utf-8').read()))
    for path in kt_files():
        body = re.sub(r'//.*', '', re.sub(r'/\*.*?\*/', '',
                                          open(path, encoding='utf-8').read(), flags=re.S))
        for name in sorted(set(re.findall(r'R\.string\.(\w+)', body))):
            if name not in declared_strings:
                problems['Строковый ресурс не объявлен'].append(
                    f'{os.path.relpath(path, SRC)}: R.string.{name}'
                )

# ── 35. AlertDialog вместо общего контейнера ──────────────────────────────
# Все окна приложения переведены на OgnelissDialog: у AlertDialog отступы
# фиксированные (24 dp по краям), и рядом с остальным интерфейсом, где
# расстояния 4–12 dp, такое окно выглядит из другого приложения.
#
# Проверка нужна, чтобы новое окно не завели снова на AlertDialog:
# по одному они добавляются незаметно, а несоответствие видно только
# на устройстве.
for path in kt_files():
    body = re.sub(r'//.*', '', re.sub(r'/\*.*?\*/', '', open(path, encoding='utf-8').read(), flags=re.S))
    if re.search(r'\bAlertDialog\s*\(', body):
        problems['AlertDialog вместо OgnelissDialog'].append(os.path.relpath(path, SRC))

# ── 36. Отступы по сетке ──────────────────────────────────────────────────
# Отступы кратны четырём: 4, 8, 12, 16, 24, 32. Значение вроде 6 или 14
# в отступе — почти всегда след подгонки на глаз, который потом
# расходится по файлам копированием.
#
# Размеры значков (size/width/height) проверка НЕ трогает: у них своя
# шкала (14, 18, 20, 24), и совпадать с сеткой отступов она не обязана.
# Разбор — в FlowUi рядом с iconSizeInline.
SPACING_CALLS = ('padding', 'spacedBy', 'height', 'width')
for path in kt_files():
    body = re.sub(r'//.*', '', re.sub(r'/\*.*?\*/', '', open(path, encoding='utf-8').read(), flags=re.S))
    for match in re.finditer(r'(\w+)\((?:[\w =]*?)(\d+)\.dp', body):
        call, value = match.group(1), int(match.group(2))
        if call not in ('padding', 'spacedBy'):
            continue
        if value <= 3 or value % 4 == 0:
            continue
        line = body[:match.start()].count('\n') + 1
        problems['Отступ не по сетке'].append(
            f'{os.path.relpath(path, SRC)}:{line}: {value}.dp'
        )

# ── 37. Единый дизайн: контейнеры, разделители, меню ──────────────────────
#
# ══════════════════════════════════════════════════════════════════════════
# ЗАЧЕМ ЭТИ ТРИ ПРОВЕРКИ
# ══════════════════════════════════════════════════════════════════════════
# В 1.1.0 интерфейс сведён к общим кирпичам: OgnelissPanel вместо Card,
# OgnelissDivider вместо HorizontalDivider со своей прозрачностью,
# FlowUi.menuItemHeight у пунктов меню.
#
# Свести — половина дела. Через месяц кто-нибудь (в том числе я) добавит
# экран со своим Card, потом второй, и разнобой вернётся. Подсчёт
# на 17 сентября 2026 показал ровно это: три способа сделать контейнер
# и три разные прозрачности линии — каждый появился «всего один раз».
#
# Поэтому правила проверяются машиной, а не памятью.
#
# ⚠️ Исключения перечислены явно и с причиной. Если файл попадает
# в список, значит для него есть довод, записанный в UI_STYLE.md.

# Card допустим только там, где нужен приподнятый блок с тенью —
# такого в приложении нет ни одного места. Список пуст намеренно.
CARD_ALLOWED = {
    # Строка журнала: у неё есть выделение и приподнятость при выборе,
    # чего OgnelissPanel не делает — панель это блок, а не строка списка
    os.path.join('com', 'ogneliss', 'dialer', 'ui', 'components', 'CallLogCard.kt'),
    # Строка контакта: то же самое
    os.path.join('com', 'ogneliss', 'dialer', 'ui', 'screens', 'ContactsScreen.kt'),
}

for path in kt_files():
    rel = os.path.relpath(path, SRC)
    source = open(path, encoding='utf-8').read()

    # Комментарии убираются С СОХРАНЕНИЕМ переводов строк: иначе номера
    # в отчёте не совпадают с файлом, и на них нельзя перейти
    def blank(match):
        return '\n' * match.group(0).count('\n')

    body = re.sub(r'/\*.*?\*/', blank, source, flags=re.S)
    body = re.sub(r'//[^\n]*', '', body)

    if re.search(r'(?<![\w.])Card\(', body) and rel not in CARD_ALLOWED:
        line = body[:re.search(r'(?<![\w.])Card\(', body).start()].count('\n') + 1
        problems['Card вместо OgnelissPanel'].append(f'{rel}:{line}')

    # Разделитель со своей прозрачностью — тот самый разнобой
    # В самом OgnelissDivider прозрачность и задаётся — это его работа.
    #
    # [\s\S], а не [^)]: первая версия ловила только разделитель, записанный
    # в одну строку. Многострочные — с цветом на следующей строке — она
    # пропускала, и пять таких жили в настройках и карточке с тремя разными
    # толщинами линии. Нашлось общим аудитом оформления, а не проверкой.
    #
    # Разрешён только подчёркивающий заголовок раздела в контактах: это
    # линия под буквой алфавита, а не между строками, — у неё своя роль.
    m = re.search(r'HorizontalDivider\((?:(?!HorizontalDivider\()[\s\S]){0,200}?outlineVariant', body)
    header_underline = rel.endswith('ContactsScreen.kt')
    if m and not rel.endswith('OgnelissPanel.kt') and not header_underline:
        line = body[:m.start()].count('\n') + 1
        problems['Свой разделитель вместо OgnelissDivider'].append(f'{rel}:{line}')

    # Пункт меню без общей высоты: рядом стоящие меню разъезжаются
    for m in re.finditer(r'DropdownMenuItem\(', body):
        # Берём вызов ЦЕЛИКОМ по балансу скобок: пункт с двухстрочной
        # подписью и своим значком занимает под сорок строк, и окно
        # фиксированной длины обрезало его на середине — проверка
        # ругалась на пункт, где всё в порядке
        depth = 0
        end = m.end()
        for i in range(m.end() - 1, len(body)):
            if body[i] == '(':
                depth += 1
            elif body[i] == ')':
                depth -= 1
                if depth == 0:
                    end = i
                    break
        chunk = body[m.start():end]

        if 'menuItemHeight' not in chunk and 'MENU_ITEM_HEIGHT' not in chunk:
            line = body[:m.start()].count('\n') + 1
            problems['Пункт меню без FlowUi.menuItemHeight'].append(f'{rel}:{line}')

# ── 38. Каркас экрана ─────────────────────────────────────────────────────
#
# ══════════════════════════════════════════════════════════════════════════
# ПОЧЕМУ НЕ «ВЕЗДЕ ОДИН SCREENSCAFFOLD»
# ══════════════════════════════════════════════════════════════════════════
# В плане было: свести все экраны к ScreenScaffold. Разбор показал, что
# свести можно один — «О приложении». У остальных шапка честно разная:
#
#   журнал и контакты — нижние панели и режим выбора;
#   списки блокировки — шапка переключается на режим выделения;
#   карточка — свои действия и сворачивающийся заголовок;
#   поиск настроек — поле ввода вместо заголовка;
#   мастер и согласие — вообще без кнопки «назад».
#
# Втащить это в общий каркас можно было бы, добавив ему параметр
# «произвольная шапка» — то есть вернув обычный Scaffold, только длиннее.
#
# Поэтому правило другое и проверяемое: экран без особой шапки берёт
# ScreenScaffold, а у своего Scaffold ОБЯЗАН быть contentWindowInsets =
# WindowInsets.safeDrawing. Без него содержимое уезжает под системные
# панели — на этом мы уже спотыкались дважды, и оба раза замечал
# пользователь, а не проверка.

for path in kt_files():
    rel = os.path.relpath(path, SRC)
    source = open(path, encoding='utf-8').read()

    for m in re.finditer(r'(?<![\w.])Scaffold\(', source):
        depth = 0
        end = m.end()
        for i in range(m.end() - 1, len(source)):
            if source[i] == '(':
                depth += 1
            elif source[i] == ')':
                depth -= 1
                if depth == 0:
                    end = i
                    break

        chunk = source[m.start():end]
        if 'contentWindowInsets' not in chunk:
            line = source[:m.start()].count('\n') + 1
            problems['Scaffold без safeDrawing'].append(f'{rel}:{line}')

# ── 39. Символ используется, а импорт удалён ──────────────────────────────
#
# ══════════════════════════════════════════════════════════════════════════
# ЗАЧЕМ
# ══════════════════════════════════════════════════════════════════════════
# Проверка «неиспользуемый импорт» у нас была, а обратной — не было.
# При переводе экранов на общие компоненты я убирал импорты пачками,
# и в SettingsWidgets clickable оказался нужен глубже в файле —
# в приватной функции toggleableRow. Компилятор это поймал,
# но через два дня и после тринадцати заходов вслепую.
#
# Список нарочно короткий: только те символы, импорты которых
# я убирал при переделке интерфейса. Полная проверка «все символы
# всех библиотек» потребовала бы разбора Kotlin, а не регулярных
# выражений, и давала бы ложные срабатывания на каждом шагу.

# Расширения вызываются ЧЕРЕЗ ТОЧКУ (`Modifier.clickable {}`), поэтому
# у них перед именем стоит точка — и обычная проверка «не после точки»
# их не видит. На этом первая версия проверки и промахнулась: она
# пропустила ровно тот случай, ради которого писалась.
EXTENSIONS = {'clickable', 'background', 'clip'}

IMPORT_NEEDED = {
    'clickable': 'androidx.compose.foundation.clickable',
    'background': 'androidx.compose.foundation.background',
    'clip': 'androidx.compose.ui.draw.clip',
    'Dimens': 'com.ogneliss.dialer.ui.theme.Dimens',
    'ListItem': 'androidx.compose.material3.ListItem',
    'ListItemDefaults': 'androidx.compose.material3.ListItemDefaults',
    'HorizontalDivider': 'androidx.compose.material3.HorizontalDivider',
    'Card': 'androidx.compose.material3.Card',
    'CardDefaults': 'androidx.compose.material3.CardDefaults',
    'FlowUi': 'com.ogneliss.dialer.ui.theme.FlowUi',
    # Формы и рисование: при сборке 1.1.1 упал именно RoundedCornerShape —
    # его ввели в карточку, а импорт не добавили. Теперь проверяются все
    # такие символы, а не только те, что я однажды убирал
    'RoundedCornerShape': 'androidx.compose.foundation.shape.RoundedCornerShape',
    'CircleShape': 'androidx.compose.foundation.shape.CircleShape',
    'Brush': 'androidx.compose.ui.graphics.Brush',
    'TextAlign': 'androidx.compose.ui.text.style.TextAlign',
    'TextOverflow': 'androidx.compose.ui.text.style.TextOverflow',
    'FontWeight': 'androidx.compose.ui.text.font.FontWeight',
}

for path in kt_files():
    rel = os.path.relpath(path, SRC)
    source = open(path, encoding='utf-8').read()

    package = re.search(r'^package ([\w.]+)', source, re.M)
    package = package.group(1) if package else ''

    # Точное совпадение строки импорта: «data.SpamMark» не должно
    # считаться найденным из-за «data.SpamMarkType» — на этой ловушке
    # подстроки уже терялся импорт
    imports = set(re.findall(r'^import ([\w.*]+)\s*$', source, re.M))

    body = re.sub(r'^import .*$', '', source, flags=re.M)
    body = re.sub(r'/\*.*?\*/', '', body, flags=re.S)
    body = re.sub(r'//[^\n]*', '', body)

    for symbol, full in IMPORT_NEEDED.items():
        if symbol in EXTENSIONS:
            pattern = r'\.' + symbol + r'\s*[({]'
        else:
            pattern = r'(?<![\w.])' + symbol + r'[(.\s]'

        found = re.search(pattern, body)
        if not found:
            continue

        home = full.rsplit('.', 1)[0]
        if package == home or full in imports or f'{home}.*' in imports:
            continue

        line = body[:found.start()].count('\n') + 1
        problems['Символ используется, импорта нет'].append(f'{rel}:{line} — {symbol}')

# ── 40. Экран умеет листаться, а ему не передали, куда ─────────────────────
#
# ══════════════════════════════════════════════════════════════════════════
# ЗАЧЕМ
# ══════════════════════════════════════════════════════════════════════════
# У экранов заметок и напоминаний параметры onSwipeNext и onSwipePrevious
# со значением по умолчанию null. При null жест выключен. RootScreen их
# не передавал — и свайп не работал ни разу с момента появления экранов.
#
# Хуже всего то, что это выглядело как ошибка распознавания жеста:
# палец шёл вбок, касание уходило полю поиска. Три захода чинили
# распознавание, а дело было в одной строке в месте вызова.
#
# Правило: экран с параметром onSwipePrevious обязан получать его
# в каждом вызове из RootScreen. Значение по умолчанию null удобно
# для превью, но молча выключает функцию в приложении.

root_path = os.path.join(SRC, 'com', 'ogneliss', 'dialer', 'ui', 'RootScreen.kt')
if os.path.exists(root_path):
    root_src = open(root_path, encoding='utf-8').read()

    # Параметры берутся по балансу скобок: в них есть типы вроде
    # `(() -> Unit)?`, и простое «до первой закрывающей» обрезало
    # список на середине — первая версия проверки не нашла ни одного
    # экрана и потому молчала
    swipe_screens = []
    for path in kt_files():
        src = open(path, encoding='utf-8').read()
        for m in re.finditer(r'fun (\w+Screen)\(', src):
            depth = 0
            params = ''
            for i in range(m.end() - 1, len(src)):
                if src[i] == '(':
                    depth += 1
                elif src[i] == ')':
                    depth -= 1
                    if depth == 0:
                        params = src[m.end():i]
                        break
            if 'onSwipePrevious' in params:
                swipe_screens.append(m.group(1))

    for name in swipe_screens:
        for call in re.finditer(r'(?<![\w.])' + name + r'\(', root_src):
            depth = 0
            end = call.end()
            for i in range(call.end() - 1, len(root_src)):
                if root_src[i] == '(':
                    depth += 1
                elif root_src[i] == ')':
                    depth -= 1
                    if depth == 0:
                        end = i
                        break
            args = root_src[call.start():end]
            if 'onSwipePrevious' not in args:
                line = root_src[:call.start()].count('\n') + 1
                problems['Экрану не передан свайп'].append(f'RootScreen.kt:{line} — {name}')

# ── 40. Отступ числом со шкалы (1.3.4, дизайн-система) ────────────────────
# 301 отступ-число переведены на токены Space (4·8·12·16·24·32 dp).
# Новое «16.dp» в padding/Spacer/spacedBy/PaddingValues — снова число
# вместо ступени шкалы. Значения вне шкалы (2, 6, 20…) не трогаем:
# это осознанные исключения, их решают «под глаз».
_SPACE = {'4': 'xs', '8': 's', '12': 'm', '16': 'l', '24': 'xl', '32': 'xxl'}
_SPACE_CTX = re.compile(r'(padding\([^()]*\)|Spacer\(Modifier\.(?:height|width)\([^()]*\)\)'
                        r'|spacedBy\([^()]*\)|PaddingValues\([^()]*\))')
for f in kt_files():
    if os.sep + 'theme' + os.sep in f:
        continue
    text = open(f, encoding='utf-8').read()
    rel = os.path.relpath(f, SRC)
    for m in _SPACE_CTX.finditer(text):
        for n in re.findall(r'\b(4|8|12|16|24|32)\.dp\b', m.group(0)):
            line = text[:m.start()].count('\n') + 1
            problems['Отступ числом со шкалы — нужен токен Space'].append(
                f'{rel}:{line} — {n}.dp → Space.{_SPACE[n]}')

# ── 41. Недопустимые символы в имени теста в обратных кавычках (05.10.2026) ─
# На JVM имя метода в `…` не может содержать . ; [ ] / < > : \ — тесты
# PinnedControllerTest с «перевод: …» не скомпилировались.
for f in glob.glob(os.path.join(os.path.dirname(SRC), '..', 'test', '**', '*.kt'), recursive=True) + \
         glob.glob(os.path.join(os.path.dirname(SRC), '..', 'androidTest', '**', '*.kt'), recursive=True):
    text = open(f, encoding='utf-8').read()
    for m in re.finditer(r'fun `([^`]*)`', text):
        bad = sorted(set(c for c in m.group(1) if c in '.;[]/<>:\\'))
        if bad:
            line = text[:m.start()].count('\n') + 1
            problems['Недопустимый символ в имени теста'].append(
                f'{os.path.relpath(f, os.path.dirname(SRC))}:{line} — «{"".join(bad)}» в `{m.group(1)[:50]}`')

# ── ОТЧЁТ ─────────────────────────────────────────────────────────────────
total = sum(len(v) for v in problems.values())
print(f'\n{"="*70}\nПРОВЕРКА ПЕРЕД СБОРКОЙ: замечаний — {total}\n{"="*70}')
for cat in sorted(problems):
    items = problems[cat]
    print(f'\n▸ {cat}: {len(items)}')
    for i in items[:20]:
        print(f'    {i}')
    if len(items) > 20:
        print(f'    … ещё {len(items)-20}')
if total == 0:
    print('\nЗамечаний нет. Это не заменяет компиляцию, но снимает\nбольшую часть причин, по которым она падает.\n')
sys.exit(0)

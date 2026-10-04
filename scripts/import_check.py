#!/usr/bin/env python3
"""
Проверка импортов символов проекта (1.3.2, 01.10.2026).

Зачем: kotlin_syntax_check.sh проверяет только синтаксис. 01.10.2026
сборка упала на 17 файлах: suspendRunCatching использовался без импорта.
Скрипт находит верхнеуровневые функции, классы и объекты проекта,
которые используются в файле другого пакета без импорта.

Ограничения: только объявления верхнего уровня; имя, совпадающее
с явно импортированным чужим символом, пропускается.
"""
import os, re, sys

ROOTS = ["app/src/main/java", "app/src/test/java", "app/src/androidTest/java"]
DECL = re.compile(r'^(?:(?:private|internal|public|inline|suspend|data|sealed|abstract|open|enum|annotation|value|fun)\s+)*'
                  r'(fun|class|object|interface)\s+(?:<[^>]+>\s+)?(?:[\w.]+\.)?(\w+)', re.M)

def strip(code):
    code = re.sub(r'/\*.*?\*/', '', code, flags=re.S)
    code = re.sub(r'//[^\n]*', '', code)
    code = re.sub(r'"""(.*?)"""', '""', code, flags=re.S)
    code = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', code)
    return code

files = []
for root in ROOTS:
    for r, _, fs in os.walk(root):
        for f in fs:
            if f.endswith('.kt'):
                p = os.path.join(r, f)
                t = open(p, encoding='utf-8').read()
                m = re.search(r'^package\s+([\w.]+)', t, re.M)
                files.append((p, m.group(1) if m else '', t))

decls = {}
for p, pkg, t in files:
    if '/main/' not in p:
        continue
    for kind, name in DECL.findall(strip(t)):
        if not re.search(r'^private\s', t[t.find(name)-40:t.find(name)], re.M):
            decls.setdefault(name, set()).add(pkg)

problems = []
for p, pkg, t in files:
    imports = re.findall(r'^import\s+([\w.*]+)(?:\s+as\s+\w+)?', t, re.M)
    explicit = {i.rsplit('.', 1)[-1]: i for i in imports}
    stars = {i[:-2] for i in imports if i.endswith('.*')}
    body = strip(re.sub(r'^(package|import)\s[^\n]*\n', '', t, flags=re.M))
    for name, pkgs in decls.items():
        if pkg in pkgs or len(name) < 3:
            continue
        if name in explicit:
            continue
        if any(q in stars for q in pkgs):
            continue
        # объявлено в самом файле (вложенное) — пропускаем
        if re.search(r'\b(fun|class|object|interface|val|var)\s+' + name + r'\b', body):
            continue
        for m in re.finditer(r'(?<![\w.])' + name + r'\b', body):
            problems.append(f"{p}: {name} (объявлен в {', '.join(sorted(pkgs))}) — нет импорта")
            break

# ── private верхнего уровня, использованный в другом файле (04.10.2026) ──
# Сборка упала: RevealState объявлен private в SwipeRevealRow.kt,
# а использовался в RevealFlingBehavior.kt того же пакета. Импорт тут
# ни при чём — private на верхнем уровне виден только в своём файле.
PRIV = re.compile(r'^private\s+(?:(?:enum|data|sealed|abstract|open|inline|value)\s+)*'
                  r'(?:class|object|interface|fun|val|var)\s+(?:<[^>]+>\s+)?'
                  r'(?:[\w.<>?, ]+\.)?(\w+)', re.M)  # у расширения имя — после получателя: fun Modifier.x()
private_decls = {}
for p, pkg, t in files:
    if '/main/' not in p:
        continue
    for name in PRIV.findall(t):
        private_decls.setdefault((pkg, name), set()).add(p)
for p, pkg, t in files:
    body = strip(re.sub(r'^(package|import)\s[^\n]*\n', '', t, flags=re.M))
    for (dpkg, name), owners in private_decls.items():
        if dpkg != pkg or p in owners or len(name) < 3:
            continue
        if re.search(r'\b(fun|class|object|interface|val|var)\s+' + name + r'\b', body):
            continue
        if re.search(r'(?<![\w.])' + name + r'\b', body):
            owner = sorted(owners)[0].rsplit('/', 1)[-1]
            problems.append(f"{p}: {name} — private в {owner}, из другого файла не виден")

print("=" * 70)
print(f"ПРОВЕРКА ИМПОРТОВ: замечаний — {len(problems)}")
print("=" * 70)
for x in problems:
    print("  " + x)
sys.exit(1 if problems else 0)

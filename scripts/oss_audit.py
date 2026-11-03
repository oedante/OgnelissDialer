#!/usr/bin/env python3
"""
ПРОВЕРКА РЕПОЗИТОРИЯ ПЕРЕД ПУБЛИКАЦИЕЙ (1.5.7, код открыт по Apache 2.0).

Публикация необратима: выложенное в открытый репозиторий копируют
за минуты, и удалить это потом нельзя. Скрипт ищет то, что не должно
уйти в открытый доступ:

  1. Ключи и пароли — файлы подписи, keystore.properties, токены.
  2. Номера телефонов в текстах — в заметках и комментариях могли
     остаться настоящие номера со снимков экрана.
  3. Адреса почты, кроме адреса автора.
  4. Картинки в docs/ и store/ — макеты делались по снимкам экрана
     с настоящим журналом: имена, номера, фото. Их нужно просмотреть
     глазами — скрипт только перечисляет.
  5. Крупные файлы.

Запуск из корня репозитория:  python3 scripts/oss_audit.py
Код выхода 1 — найдены ключи или пароли (это нужно убрать обязательно);
остальное — список для просмотра.

Найденное печатается в консоль и никуда не сохраняется: отчёт с номерами
в репозитории сам стал бы утечкой.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

SKIP_DIRS = {".git", ".gradle", ".idea", "build", ".cxx", "captures", "__pycache__",
             # Замеры запуска: длинные числа там — время, а не номера
             "baseline"}
TEXT_EXT = {
    ".kt", ".kts", ".java", ".xml", ".md", ".txt", ".py", ".sh", ".yml", ".yaml",
    ".json", ".properties", ".toml", ".html", ".css", ".js", ".csv", ".conf", ".gradle",
}
IMAGE_EXT = {".png", ".jpg", ".jpeg", ".webp", ".gif"}

# Файлы, которым не место в открытом репозитории ни при каком содержимом
FORBIDDEN_NAMES = {"keystore.properties", "local.properties", "google-services.json"}
FORBIDDEN_EXT = {".jks", ".keystore", ".p12", ".pem", ".apk", ".aab"}

SECRET_PATTERNS = [
    # Пароль подписи — если это значение, а не чтение из keystore.properties,
    # не ссылка на секрет GitHub (${{ secrets.… }}, сборка на GitHub, 1.8.2)
    # и не заглушка из шаблона («ваш_пароль», «ЗАМЕНИТЬ»)
    (re.compile(r"(storePassword|keyPassword|store_password|key_password)\s*[=:]\s*"
                r"(?!keystoreProperties|System\.getenv|providers\.|project\.|\$\{\{\s*secrets\.|ваш_пароль|ЗАМЕНИТЬ|CHANGE_ME)\S+",
                re.I), "пароль подписи"),
    (re.compile(r"-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"), "закрытый ключ"),
    (re.compile(r"AIza[0-9A-Za-z\-_]{35}"), "ключ Google API"),
    (re.compile(r"gh[pousr]_[A-Za-z0-9]{36,}"), "токен GitHub"),
    (re.compile(r"xox[baprs]-[A-Za-z0-9-]{10,}"), "токен Slack"),
]

# Российские номера в привычных записях: +7 917 555-66-77, 8 (495) 123-45-67,
# +79175556677. Короткие и служебные (112, *100#) не ловятся — и не нужно
PHONE = re.compile(
    r"(?<![\w/])(?:\+7|8)[\s\-(]*\d{3}[\s\-)]*\d{3}[\s\-]*\d{2}[\s\-]*\d{2}(?!\d)"
)
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
ALLOWED_EMAILS = {"oedante@gmail.com"}

BIG_FILE = 1_000_000


def looks_synthetic(number):
    """
    Номер, заведомо придуманный для примера: абонентская часть вида
    111-22-33, 777-77-77, 123-45-67 (и 123-45-68 рядом с ним) или рекламный
    8 800 555-35-35.
    Такие в тестах и комментариях — норма; искать нужно остальные.
    """
    digits = re.sub(r"\D", "", number)[-7:]
    if len(digits) < 7:
        return False
    a, b, c = digits[:3], digits[3:5], digits[5:]
    if len(set(a)) == 1 and len(set(b)) == 1 and len(set(c)) == 1:
        return True
    return digits.startswith("123456") or digits in {"9998877", "5553535", "0000000"}


def walk():
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            yield os.path.join(dirpath, name)


def rel(path):
    return os.path.relpath(path, ROOT)


def main():
    forbidden, secrets, phones, emails, images, big = [], [], {}, {}, [], []

    for path in walk():
        name = os.path.basename(path)
        ext = os.path.splitext(name)[1].lower()
        r = rel(path)

        if name in FORBIDDEN_NAMES or ext in FORBIDDEN_EXT:
            forbidden.append(r)
        if os.path.getsize(path) > BIG_FILE:
            big.append((r, os.path.getsize(path)))
        if ext in IMAGE_EXT and (r.startswith("docs") or r.startswith("store")):
            images.append(r)
        if ext not in TEXT_EXT:
            continue

        try:
            with open(path, encoding="utf-8") as f:
                lines = f.readlines()
        except (UnicodeDecodeError, OSError):
            continue

        for i, line in enumerate(lines, 1):
            for pattern, what in SECRET_PATTERNS:
                if pattern.search(line) and "example" not in r:
                    secrets.append((r, i, what))
            for m in PHONE.finditer(line):
                # Слово «пример» рядом номер не обезличивает: 22.10.2026 так
                # пропустился настоящий номер в комментарии Logger.maskKey.
                # Пропускаются только заведомо придуманные номера
                if looks_synthetic(m.group(0)):
                    continue
                phones.setdefault(r, []).append((i, m.group(0).strip()))
            for m in EMAIL.finditer(line):
                addr = m.group(0)
                # this@label.call — метка в Kotlin, не адрес
                if addr.lower() in ALLOWED_EMAILS or addr.startswith("this@") \
                        or addr.endswith((".png", ".jpg")):
                    continue
                emails.setdefault(r, []).append((i, addr))

    print("=" * 70)
    print("ПРОВЕРКА ПЕРЕД ПУБЛИКАЦИЕЙ РЕПОЗИТОРИЯ")
    print("=" * 70)

    def section(title, items, fmt):
        print(f"\n▸ {title}: {len(items)}")
        for it in items[:200]:
            print("    " + fmt(it))
        if len(items) > 200:
            print(f"    … и ещё {len(items) - 200}")

    section("Файлы, которые нельзя публиковать", forbidden, lambda x: x)
    section("Похоже на ключи и пароли", secrets, lambda x: f"{x[0]}:{x[1]} — {x[2]}")

    phone_rows = [(f, n, v) for f, rows in sorted(phones.items()) for n, v in rows]
    section("Номера телефонов в текстах (проверить: чужой ли номер)",
            phone_rows, lambda x: f"{x[0]}:{x[1]}  {x[2]}")

    email_rows = [(f, n, v) for f, rows in sorted(emails.items()) for n, v in rows]
    section("Адреса почты, кроме адреса автора", email_rows, lambda x: f"{x[0]}:{x[1]}  {x[2]}")

    section("Картинки в docs/ и store/ — просмотреть глазами (имена, номера, фото)",
            sorted(images), lambda x: x)
    section("Файлы крупнее 1 МБ", sorted(big), lambda x: f"{x[0]}  {x[1] // 1024} КБ")

    print()
    if forbidden or secrets:
        print("ЕСТЬ ТО, ЧТО НУЖНО УБРАТЬ ДО ПУБЛИКАЦИИ (ключи, пароли, файлы подписи).")
        return 1
    print("Ключей и паролей не найдено. Номера, почту и картинки — просмотреть.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

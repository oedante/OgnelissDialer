#!/usr/bin/env python3
"""
ПОРОГ РАЗМЕРА APK (2.9.13, техдолг, этап 1.5).

Аудит 28.11.2026: «бюджет размера APK в CI». Размер растёт незаметно: одна
новая библиотека или картинка — и сборка тяжелее на мегабайт. Порог ловит это
в PR, а не после выпуска.

Пороги — с запасом около 15 % над размером 2.9.12:
  • release (R8, сжатые ресурсы): 8,5 МБ → порог 10 МБ — проверяется в release.yml;
  • debug (без R8): 23,5 МБ → порог 27 МБ — проверяется в android.yml на каждом PR.
Порог подняли — запись в docs/RELEASE_NOTES_<версия>.md: за счёт чего.

Запуск: python3 scripts/apk_size_check.py <путь к apk> <порог в МБ>
"""
import os
import sys
import zipfile


def main() -> None:
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    path, limit_mb = sys.argv[1], float(sys.argv[2])
    size_mb = os.path.getsize(path) / 1024 / 1024
    print(f'{os.path.basename(path)}: {size_mb:.2f} МБ (порог {limit_mb:.1f} МБ)')
    if size_mb > limit_mb:
        # Самое тяжёлое внутри — чтобы сразу видеть, что выросло
        with zipfile.ZipFile(path) as z:
            items = sorted(z.infolist(), key=lambda i: i.compress_size, reverse=True)[:10]
        print('Самое тяжёлое (сжатый размер):')
        for i in items:
            print(f'  {i.compress_size / 1024:8.0f} КБ  {i.filename}')
        print('::error::APK больше порога')
        sys.exit(1)


if __name__ == '__main__':
    main()

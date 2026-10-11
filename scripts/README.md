# Скрипты проекта

| Скрипт | Назначение |
|---|---|
| `check_no_internet.sh` | Проверяет, что в merged manifest не появилось разрешение `INTERNET`. Вся модель приватности приложения держится на его отсутствии — запускать перед каждым релизом. |

| `check_ringing_flag.sh` | Проверяет, что флаг `IN_CALL_SERVICE_RINGING` не объявлен без собственного проигрывателя мелодии. Флаг без проигрывателя = беззвучные входящие, и на эмуляторе это не обнаружить. |

| `prebuild_check.py` | Статические ошибки сборки: классы из манифеста, ссылки на version catalog, ресурсы, баланс скобок, заглушки. Ловит за секунды то, на что компиляция тратит минуты. |
| `audit.py` | Локализация, недостижимые экраны, несогласованность настроек, объём комментариев. |
| `make_icons.py` | Пересобирает все растровые значки (15 файлов `mipmap-*`, значок магазина, значок сайта `docs/www/app-icon.png`) из одного исходника `store/source/icon_master_1024.png`: отделяет рисунок от фона для адаптивного значка и сверяет цвет фона в ресурсах. Нужны `pillow`, `numpy`. |
| `build_numbering.py` | Собирает `app/src/main/assets/numbering_plan.bin` из выгрузок Россвязи (`store/source/numbering/`). Запускать при обновлении выгрузок. |
| `make_monochrome.py` | Пересобирает `drawable/ic_launcher_monochrome.xml` из того же исходника: трубка и пламя залиты, лиса — вырез. Нужны `scipy`, `scikit-image`. |
| `build_feature_graphic.py` | Баннер Google Play 1024×500 из `store/play_icon_512.png`; фон — фон значка. Нужен `cairosvg`. |

## Смена значка

1. Положить новый PNG 1024×1024 без прозрачности (рисунок на ровном фоне)
   в `store/source/icon_master_1024.png`.
2. Если у нового значка другой фон или цвета рисунка — поправить `BG` и `INKS`
   в `make_icons.py`, цвета в `make_monochrome.py`, `ic_launcher_background`
   и `splash_background` в `res/values`. `make_icons.py` сверяет цвет в ресурсах
   и падает, если он расходится с фоном исходника: слой `foreground` рассчитан
   поверх этого цвета, и расхождение видно как рамка.
3. `python3 scripts/make_icons.py && python3 scripts/make_monochrome.py && python3 scripts/build_feature_graphic.py`
4. Значок сайта — `docs/www/app-icon.png` → в `site/` репозитория Mail (сайт собирается там).
5. Имена файлов не меняются, поэтому в манифесте править нечего.

## Как добавить проверку в git-хук

```bash
echo './scripts/check_no_internet.sh' >> .git/hooks/pre-push
chmod +x .git/hooks/pre-push
```

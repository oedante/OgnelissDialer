# Ogneliss Dialer — передача в новый чат

Документ для продолжения работы в новом чате. Приложить к первому
сообщению вместе с архивом проекта.

## Что это

Android-звонилка (замена системной) — `com.ogneliss.dialer`, бесплатная,
без рекламы и **без доступа в интернет** (это принцип, проверяется
`scripts/check_no_internet.sh`). В Google Play, сайт — ogneliss.ru/dialer.
Автор — Олег (oedante@gmail.com), отзывы — Google Play и форум.

## Состояние

- **Версия в работе: 1.3.0** (versionCode 28). В магазине — 1.2.6
  (или 1.2.5 — уточнить у автора).
- `docs/RELEASE_NOTES_1.3.0.md` — что уже сделано в 1.3.0.
- `docs/ROADMAP.md` — план, отложенное, решения «по отзывам».

## Сборка и окружение

- minSdk 26 · compileSdk 36 · targetSdk 36
- AGP 9.3.1 · Kotlin 2.2.10 · KSP 2.3.6 · Room 2.8.5 · Hilt 2.59.2
- Compose (Material 3 1.3), одна активность + `InCallActivity`
- `android.newDsl=false` в gradle.properties — нужен плагину Baseline
  Profile 1.3.3; предупреждения при сборке про это — ожидаемые
- Профиль запуска (`generateBaselineProfile`) — **с отключённым
  от компьютера телефоном**, иначе ошибка подписи

## Как работаем (сложившиеся правила)

- Общение — по-русски. Комментарии в коде — по-русски, с причиной
  решения («было — стало — почему»), с версией и датой находки.
- Перед упаковкой каждый раз: `python3 scripts/prebuild_check.py`,
  `scripts/kotlin_syntax_check.sh` (kotlinc в окружении Claude),
  `python3 scripts/audit.py` — все без замечаний.
- Строки интерфейса — в 4 языках вручную (en, ru, de, fr); остальные
  44 — автоперевод Google Play (не добавлять туда наши 4!). Названия
  марок — `translatable="false"`.
- Строки для журнала разработчика — не в пакетах `ui/` и `call/`
  (аудит считает их строками интерфейса): общие — в `log/`.
- Номера в журнале приложения — только через `Logger.mask` / `maskKey`.
- Каждый выпуск: `docs/RELEASE_NOTES_x.y.z.md` (сводка, разделы
  с причинами, проверки) и `store/whats_new_x.y.z.txt` — «Что нового»
  для **28 языков консоли** в формате меток `<ru-RU>…</ru-RU>`, до 500
  знаков на язык. Шаблон языков — как в консоли (ru-RU, ar, cs-CZ,
  da-DK, de-DE, en-US, es-419, es-ES, fi-FI, fr-FR, hi-IN, hu-HU, id,
  it-IT, iw-IL, ja-JP, ko-KR, nl-NL, no-NO, pl-PL, pt-BR, ro, sk, sv-SE,
  th, tr-TR, vi, zh-CN).
- Версию поднимаем, когда предыдущая ушла в консоль; правки после
  публикации — в файл изменений следующей версии.
- Архив в выдаче один, итоговый: `OgnelissDialer_x_y_z.zip`
  (`_wip` — пока выпуск собирается).

## Ключевые места в коде

| Что | Где |
|---|---|
| Журнал, нижняя панель, меню «Ещё» | `ui/screens/MainDialerScreen.kt` |
| Карточка абонента, «Написать» | `ui/screens/SubscriberScreen.kt` |
| Меню записи (долгое нажатие) | `ui/screens/ContactActionSheet.kt` |
| Шторки: группы, строки | `ui/components/SheetParts.kt` |
| Свайпы: строка, панели, действия | `ui/components/swipe/*`, `DialerViewModel.handleSwipe / revealActionsFor` |
| Выбор действий свайпа (до 3) | `ui/screens/SwipeActionsDialog.kt` |
| Мессенджеры контакта (по книге) | `data/ContactMessengers.kt` |
| Экран вызова | `call/InCallActivity.kt`, `call/CallManager.kt` |
| Авто-громкая связь | `call/SmartSpeakerController.kt` |
| Пропущенные + счётчик на значке | `call/MissedCallsNotifier.kt` |
| Диагностика вызовов в журнал | `log/CallDiagnostics.kt` |
| Настройки (реестр, команды, состояние) | `ui/settings/SettingsRegistry.kt`, `settings/*` |
| Выбор языка | `ui/screens/LanguagePickerDialog.kt`, `res/xml/locales_config.xml` |
| Раскладки (телефон/лёжа/планшет) | `ui/adaptive/WindowLayout.kt`, `RootScreen.kt` |
| Сайт и nginx | `docs/www/` (выкладка — `build-pages.sh`) |
| Описание в магазине | `store/LISTING_*.md` |

## Принятые решения (не пересматривать без причины)

- Мелодию звонка играет система (мелодии контактов, «Не беспокоить»).
- Редактирование контактов — системное приложение «Контакты».
- Видеозвонки — отложены до просьб в отзывах (`ROADMAP.md`).
- Флагов в выборе языка нет (язык ≠ страна).
- Зелёный цвет — только звонок; управление — основной (синий).
- Меню — шторками с группами (`SheetGroup`), не выпадающими меню.

## Обои рабочего стола как фон — сделано в 1.3.0

Устройство и причины решений — `ui/theme/WallpaperBackground.kt`
и `RELEASE_NOTES_1.3.0.md` (раздел 2). Коротко: картинку обоев не читаем
(закрытые разрешения), система рисует обои под прозрачным окном
(`FLAG_SHOW_WALLPAPER`); размытие — `setBackgroundBlurRadius` (Android
12+); приглушение — слой цвета фона темы; в AMOLED и «Высоком контрасте»
— выключено.

**Проверить на телефоне в первую очередь:** работает ли размытие
(см. ⚠️ в `RELEASE_NOTES_1.3.0.md`), читаемость заголовков дней
и пустых состояний поверх обоев, экраны настроек.

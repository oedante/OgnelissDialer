# Ogneliss Dialer 2.8.4 (89) — сборка без предупреждений компилятора (18.11.2026)

> Автор, с журналом своей сборки (Windows): три предупреждения компилятора — два
> в `FakeTelecomCall.kt`, одно в `ScreenGalleryTest.kt`.
>
> versionCode 89. Поведение приложения не менялось. Разрешения, зависимости, база
> и настройки — без изменений.

## Предупреждения

- `FakeTelecomCall.start`: в Android 16 (compileSdk 36) `PhoneAccount.CAPABILITY_SELF_MANAGED`
  и `EXTRA_LOG_SELF_MANAGED_CALLS` помечены устаревшими — Google советует библиотеку Jetpack
  `core-telecom` (`CallsManager`). На всех версиях Android, включая 16, прежний способ
  работает; переход — новая зависимость, только по решению автора. Подавление
  (`@Suppress("DEPRECATION")`) — на одной функции, с объяснением.
- `ScreenGalleryTest`: правило `createAndroidComposeRule` — версия v2
  (`androidx.compose.ui.test.junit4.v2`), как советует библиотека. v2 ставит задачи корутин
  в очередь, а не выполняет сразу; галерея и так ведёт часы вручную (`settle`) — снимки те же.
- Строки `W HWUI : Trying to start new animators … without an animation handle` в журнале
  тестов — служебный журнал графики Robolectric (рябь нажатия в тестах), не предупреждения
  сборки; на тесты и приложение не влияют.

## Проверки

Здесь: сборка без предупреждений компилятора (`compileDebugKotlin`,
`compileDebugUnitTestKotlin`), галерея на v2 — снимки те же; модульные тесты — 754,
без ошибок (с 2.7.5 их стало больше на 10: картинки вместо фото, подписи кнопок).

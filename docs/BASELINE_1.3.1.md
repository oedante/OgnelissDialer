# Базовая точка 1.3.1 — замеры до переработки

Заполняется один раз, на сборке 1.3.1 (29) и том же телефоне, что
и все последующие замеры (nubia NX721J, Android 15). После каждого шага
фаз «Новые инструменты» и «Архитектура и интерфейс» — та же таблица,
колонки «после» и «изменение».

Порог регрессии (раздел «Правила работы» плана): без записанной причины
не хуже базовой точки больше чем на 5% по времени, памяти и размеру;
новых падений и ANR — ноль. Порог уточнить по разбросу первых замеров.

## Подготовка

1. Git: `git tag baseline-1.3.1` на коммите сборки 1.3.1.
2. Телефон: зарядка выше 50%, режим энергосбережения выключен,
   «Не беспокоить» включён, остальные приложения закрыты.
3. Отключить телефон от компьютера только для генерации профиля
   (`generateBaselineProfile`), для замеров — подключён.

## Команды

```
# Запуск, прокрутка, клавиатура, карточка (Macrobenchmark, по 10 повторов)
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.cardNumber=+7XXXXXXXXXX

# Размер выпуска
./gradlew :app:bundleRelease   # app/build/outputs/bundle/release/*.aab
./gradlew :app:assembleRelease # app/build/outputs/apk/release/*.apk

# Память через 5 минут работы (журнал, карточка, клавиатура)
adb shell dumpsys meminfo com.ogneliss.dialer | grep -E "TOTAL PSS|Java Heap|Native Heap"

# Предупреждения Lint
./gradlew :app:lintRelease     # app/build/reports/lint-results-release.html

# Время сборки (вторая подряд, с прогретым кэшем)
./gradlew :app:assembleRelease --profile   # build/reports/profile/
```

Результаты Macrobenchmark — `benchmark/build/outputs/connected_android_test_additional_output/`
(файлы `*-benchmarkData.json`); в таблицу — медиана.

Входящий вызов (вручную, Perfetto): System Tracing в настройках
разработчика, позвонить на телефон, остановить запись; в трассе —
время от `onCallAdded` до первого кадра `InCallActivity`.

## Таблица

| Показатель | Как меряем | 1.3.1 (до) | После | Изменение |
|---|---|---|---|---|
| Холодный запуск, без профиля, мс | `StartupBenchmark.startupNoCompilation`, медиана | | | |
| Холодный запуск, с профилем, мс | `StartupBenchmark.startupWithBaselineProfile` | | | |
| Тёплый запуск, мс | `ScreenBenchmark.startupWarm` | | | |
| Открытие клавиатуры, мс | `ScreenBenchmark.openDialpad` | | | |
| Открытие карточки, мс | `ScreenBenchmark.openCard` | | | |
| Прокрутка журнала: медленные кадры, % | `StartupBenchmark.scrollCallLog`, `frameOverrunMs` P90 | | | |
| Входящий → экран вызова, мс | Perfetto, вручную | | | |
| Память (TOTAL PSS) через 5 мин, МБ | `dumpsys meminfo` | | | |
| Размер AAB, МБ | `bundleRelease` | | | |
| Размер APK release, МБ | `assembleRelease` | | | |
| Предупреждения Lint | `lintRelease` | | | |
| Время сборки release, с | `--profile`, вторая сборка | | | |
| Сбои и ANR (Android vitals) | Play Console, 28 дней | | | |


# 1. Телефон виден?
adb devices

# 2. Точное имя задачи замеров (зависит от плагина Baseline Profile)
./gradlew :benchmark:tasks --all | Select-String "connected"

# 3. Замеры: запуск, прокрутка, клавиатура, карточка (около 15–25 минут)
#    Номер — любой из ваших контактов. Кавычки обязательны:
#    без них PowerShell разрежет аргумент по точке
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.cardNumber=+79001112233"

# 4. Размер выпуска
./gradlew :app:bundleRelease
./gradlew :app:assembleRelease
Get-ChildItem app\build\outputs -Recurse -Include *.aab,*.apk | Select-Object Name, Length

# 5. Память: поработать в приложении 5 минут (журнал, карточка, клавиатура), затем
adb shell dumpsys meminfo com.ogneliss.dialer | Select-String "TOTAL PSS|Java Heap|Native Heap"

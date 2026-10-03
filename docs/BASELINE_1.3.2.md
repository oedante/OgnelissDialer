# Базовая точка 1.3.2 — замеры до перехода на новые инструменты

Сняты 03.10.2026 на сборке 1.3.2 (30), вариант `benchmarkRelease`,
nubia NX721J, Android 15 (API 35). Базовая точка — 1.3.2, а не 1.3.1:
замеры экранов (`ScreenBenchmark`) есть только в её коде, а правки 1.3.2
на скорость практически не влияют. Все последующие замеры — на том же
телефоне. Исходные данные — `docs/baseline/benchmarkData-1.3.2.json`. После каждого шага
фаз «Новые инструменты» и «Архитектура и интерфейс» — та же таблица,
колонки «после» и «изменение».

Порог регрессии (раздел «Правила работы» плана): без записанной причины
не хуже базовой точки больше чем на 5% по времени, памяти и размеру;
новых падений и ANR — ноль. Порог уточнить по разбросу первых замеров.

## Подготовка

1. Git: `git tag baseline-1.3.2` на коммите этой сборки.
2. Телефон: зарядка выше 50%, режим энергосбережения выключен,
   «Не беспокоить» включён, остальные приложения закрыты.
3. Отключить телефон от компьютера только для генерации профиля
   (`generateBaselineProfile`), для замеров — подключён.

## Команды

```
# Запуск, прокрутка, клавиатура, карточка (Macrobenchmark, по 10 повторов)
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
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

Медиана из 10 повторов.

| Показатель | Как меряем | 1.3.2 (до) | После | Изменение |
|---|---|---|---|---|
| Холодный запуск, без профиля, мс | `StartupBenchmark.startupNoCompilation` | не снят ¹ | | |
| Холодный запуск, с профилем, мс | `StartupBenchmark.startupWithBaselineProfile` | не снят ¹ | | |
| Тёплый запуск, мс | `ScreenBenchmark.startupWarm` | **82,7** (67,1–135,8) | | |
| Открытие клавиатуры, мс | `ScreenBenchmark.openDialpad` | **119,0** (103,9–165,4) | | |
| Открытие карточки, мс | `ScreenBenchmark.openCard` | **94,3** (71,8–112,1) | | |
| Прокрутка журнала, превышение кадра P90, мс | `StartupBenchmark.scrollCallLog` | 17,8 — недостоверно ² | | |
| Входящий → экран вызова, мс | Perfetto, вручную | не снят | | |
| Память, TOTAL PSS, МБ | `dumpsys meminfo` после 5 мин | **160,4** (Java 40,4; Native 48,1) | | |
| Размер AAB, МБ | `bundleRelease` | **10,14** | 10,15 (1.3.3, шаг 1) | +0,1% |
| Размер APK release, МБ | `assembleRelease` | **6,75** | | |
| Предупреждения Lint | `lintRelease` | не снят | | |
| Время сборки release, с | `--profile`, вторая сборка | не снят | | |
| Сбои и ANR | Android vitals, 28 дней | смотреть в Play Console | | |

¹ Холодный запуск не измерен: телефон не принял служебную команду
`profileinstaller` (сброс кэша шейдеров и установка профиля). Библиотека
в сборке есть (1.4.1). Вероятная причина — прошивка nubia не будит
остановленные приложения. Проверка:
`adb shell am broadcast -a androidx.profileinstaller.action.BENCHMARK_OPERATION -e EXTRA_BENCHMARK_OPERATION DROP_SHADER_CACHE com.ogneliss.dialer/androidx.profileinstaller.ProfileInstallReceiver`
— должно напечатать `result=14`.

² В журнале телефона мало записей (он очищался 30.09): за прокрутку
всего 5–10 кадров, статистики нет. Повторить, когда в журнале будет
хотя бы 100 записей. Заполнять системный журнал тестовыми записями
не будем — это настоящий журнал звонков автора.

## Что видно уже сейчас

- Клавиатура открывается дольше карточки (119 против 94 мс), хотя
  экран проще. Кандидат для разбора в фазе «Архитектура и интерфейс».
- Разброс тёплого запуска велик (67–136 мс): повторы со «сбросом»
  телефона дадут более устойчивую медиану, но для сравнения «до/после»
  на одном телефоне этого достаточно.

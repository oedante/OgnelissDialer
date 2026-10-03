# Проверка сборки на телефоне — в безопасном порядке (1.3.3, 03.10.2026)
#
# Зачем: сборка для замеров подписана отладочным ключом. После прогона
# Gradle удаляет приложение вместе с данными (заметки, закреплённые,
# напоминания, списки блокировки, настройки) и ролью приложения для набора
# номера по умолчанию. Скрипт напоминает сделать резервную копию до замеров
# и сам ставит выпускную сборку после них.
#
# Запуск из папки ogneliss-dialer:
#   powershell -ExecutionPolicy Bypass -File scripts\phone_check.ps1 -CardNumber +79XXXXXXXXX
param([Parameter(Mandatory = $true)][string]$CardNumber)
$ErrorActionPreference = "Continue"
$pkg = "com.ogneliss.dialer"

function Step($text) { Write-Host "`n=== $text ===" -ForegroundColor Cyan }
function Wait($text) { Read-Host "$text`n[Enter — продолжить]" | Out-Null }

Step "0. Телефон"
adb devices
Wait "Сделайте резервную копию в Ogneliss: Настройки -> Данные и диагностика -> Экспорт. Замеры удалят приложение вместе с данными."

Step "1. Юнит-тесты"
./gradlew :app:testDebugUnitTest
if ($LASTEXITCODE -ne 0) {
    Write-Host "Юнит-тесты не прошли — пришлите вывод выше. Замеры не запускаю." -ForegroundColor Red
    exit 1
}

Step "2. Замеры экранов (5–10 минут, телефон не трогать)"
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.ogneliss.benchmark.ScreenBenchmark" "-Pandroid.testInstrumentationRunnerArguments.cardNumber=$CardNumber"
$json = Get-ChildItem benchmark\build\outputs\connected_android_test_additional_output -Recurse -Filter *benchmarkData.json -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime | Select-Object -Last 1
if ($json) {
    Write-Host "Результаты: $($json.FullName)"
    $base = "docs\baseline\benchmarkData-base.json"
    if (-not (Test-Path $base)) {
        Copy-Item $json.FullName $base
        Write-Host "Базовой точки не было — этот прогон стал базовой точкой: $base" -ForegroundColor Yellow
    } elseif (Get-Command python -ErrorAction SilentlyContinue) {
        python scripts\compare_benchmarks.py $base $json.FullName
    } else {
        Write-Host "Python не найден — пришлите этот файл, сравню сам."
    }
} else {
    Write-Host "Файл результатов не найден — пришлите вывод замеров." -ForegroundColor Yellow
}

Step "3. Установка выпускной сборки"
./gradlew :app:installRelease
Wait "Откройте Ogneliss, сделайте его приложением для набора номера по умолчанию и восстановите резервную копию. Затем 5 минут поработайте: журнал, карточка, клавиатура."

Step "4. Память (приложение должно быть открыто)"
adb shell dumpsys meminfo $pkg | Select-String "TOTAL PSS|Java Heap|Native Heap"

Step "5. Ручная проверка — раздел «Что проверить» в docs\RELEASE_NOTES текущей версии"

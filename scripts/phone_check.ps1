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
param(
    [Parameter(Mandatory = $true)][string]$CardNumber,
    # Метка прогона: результаты сохраняются как docs\baseline\benchmarkData-<метка>.json
    [string]$Label = (Get-Date -Format "yyyyMMdd-HHmm")
)
$ErrorActionPreference = "Continue"
$pkg = "com.ogneliss.dialer"

function Step($text) { Write-Host "`n=== $text ===" -ForegroundColor Cyan }
function Wait($text) { Read-Host "$text`n[Enter — продолжить]" | Out-Null }

Step "0. Телефон"
adb devices
# Экран не гаснет, пока телефон на USB (04.10.2026: за 21 минуту замеров
# экран заблокировался, и тёплый запуск упал). В конце — как было.
adb shell svc power stayon usb
Wait "Сделайте резервную копию в Ogneliss: Настройки -> Данные и диагностика -> Экспорт. Замеры удалят приложение вместе с данными."

Step "1. Юнит-тесты"
./gradlew :app:testDebugUnitTest
if ($LASTEXITCODE -ne 0) {
    Write-Host "Юнит-тесты не прошли — пришлите вывод выше. Замеры не запускаю." -ForegroundColor Red
    exit 1
}

# 1.3.3: и холодный запуск (StartupBenchmark) — команда profileinstaller
# на телефоне проходит (result=14), раньше этих цифр не было
# Весь пакет, а не список классов (04.10.2026): «ScreenBenchmark,StartupBenchmark»
# через -P до телефона не доходил — запускался только первый класс,
# холодный запуск не мерился. Генератор профиля в обычном прогоне
# пропускается сам.
$benchFilter = "-Pandroid.testInstrumentationRunnerArguments.package=com.ogneliss.benchmark"
# nubia не доставляет служебные команды profileinstaller остановленному
# приложению (04.10.2026): холодный запуск падал на сбросе кэша шейдеров.
# Без сброса холодный запуск меряется; «с профилем» на этом телефоне
# не измерить — установка профиля идёт той же командой, замер исключён.
$benchExtra = @(
    "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dropShaders.enable=false",
    "-Pandroid.testInstrumentationRunnerArguments.notClass=com.ogneliss.benchmark.StartupBenchmark#startupWithBaselineProfile"
)

# Итог каждого замера — прямо в консоль и в файл docs\baseline\tests-<метка>.txt
# (04.10.2026): причина падения была только в HTML-отчёте, а logcat
# на телефоне пуст. Источник — XML-результаты Gradle этого прогона.
function Show-TestResults([string]$name, [datetime]$started) {
    $xmls = Get-ChildItem benchmark\build\outputs\androidTest-results -Recurse -Filter "TEST-*.xml" -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -gt $started }
    $lines = @()
    foreach ($x in $xmls) {
        [xml]$doc = Get-Content $x.FullName -Encoding UTF8
        foreach ($tc in $doc.SelectNodes("//testcase")) {
            $title = "$($tc.classname.Split('.')[-1]).$($tc.name)"
            $fail = $tc.SelectSingleNode("failure")
            # Штатный пропуск (генератор профиля вне генерации) Gradle
            # записывает как провал с AssumptionViolatedException
            if ($tc.SelectSingleNode("skipped") -or ($fail -and $fail.InnerText -like "*AssumptionViolatedException*")) {
                $lines += "  пропуск $title"
            } elseif ($fail) {
                $text = ($fail.InnerText.Trim() -split "`n" | Select-Object -First 3) -join " | "
                if (-not $text) { $text = $fail.GetAttribute("message") }
                if (-not $text) { $text = "нет текста (вероятно, процесс замеров аварийно завершился)" }
                $lines += "  ПРОВАЛ  $title — $text"
            } else {
                $lines += "  ок      $title"
            }
        }
    }
    if ($lines.Count -eq 0) { $lines = @("  результатов тестов не найдено") }
    $lines | ForEach-Object { if ($_ -like "*ПРОВАЛ*") { Write-Host $_ -ForegroundColor Yellow } else { Write-Host $_ } }
    $lines | Out-File -Encoding utf8 "docs\baseline\tests-$name.txt"
}

# Один прогон замеров; возвращает путь к сохранённому файлу или $null.
# Файл берётся только свежий — старый от прошлого прогона не подхватится.
function Run-Bench([string]$name, [string[]]$extra) {
    Write-Host "--- Замеры: $name ---" -ForegroundColor Cyan
    $started = Get-Date
    adb logcat -c   # чистый logcat: при сбое сохраним только этот прогон
    ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest @extra $benchFilter @benchExtra "-Pandroid.testInstrumentationRunnerArguments.cardNumber=$CardNumber" | Out-Host
    # | Out-Host: иначе вывод Gradle стал бы возвращаемым значением функции
    Show-TestResults $name $started
    $json = Get-ChildItem benchmark\build\outputs\connected_android_test_additional_output -Recurse -Filter *benchmarkData.json -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -gt $started } | Sort-Object LastWriteTime | Select-Object -Last 1
    if (-not $json) {
        # Сбой процесса замеров («Test failed with status -1») причину
        # не показывает — она только в logcat телефона
        $log = "docs\baseline\logcat-$name.txt"
        adb logcat -d -v threadtime | Out-File -Encoding utf8 $log
        if ((Get-Item $log).Length -lt 1024) {
            # 04.10.2026: на nubia logcat был пуст — журнал выключен в настройках
            Write-Host "Logcat пуст: на телефоне не ведётся системный журнал. Настройки -> Для разработчиков -> «Размер буфера журнала» — выбрать 1 МБ или больше (не «Выкл») и повторить." -ForegroundColor Yellow
        } else {
            Write-Host "Замеры не дали результата. Logcat сохранён: $log — пришлите его." -ForegroundColor Yellow
        }
        Write-Host "Пришлите также отчёт: benchmark\build\reports\androidTests\connected\benchmarkRelease\com.ogneliss.benchmark.ScreenBenchmark.html" -ForegroundColor Yellow
        return $null
    }
    $dest = "docs\baseline\benchmarkData-$name.json"
    Copy-Item $json.FullName $dest -Force
    Write-Host "Сохранено: $dest"
    return $dest
}

$hasPython = [bool](Get-Command python -ErrorAction SilentlyContinue)
$base = "docs\baseline\benchmarkData-base.json"

Step "2. Замеры: экраны и холодный запуск (15–25 минут, телефон не трогать)"
$now = Run-Bench $Label @()
if ($now) {
    if (-not (Test-Path $base)) {
        Copy-Item $now $base
        Write-Host "Базовой точки не было — этот прогон стал базовой точкой: $base" -ForegroundColor Yellow
    } elseif ($hasPython) {
        python scripts\compare_benchmarks.py $base $now --merge-new
    } else {
        Write-Host "Python не найден — пришлите этот файл, сравню сам."
    }
}

Step "3. Установка выпускной сборки"
./gradlew :app:installRelease
Wait "Откройте Ogneliss, сделайте его приложением для набора номера по умолчанию и восстановите резервную копию."

# Память — одинаковым сценарием, а не «5 минут работы» (1.3.3, 03.10.2026):
# ручной сценарий каждый раз разный, TOTAL PSS прыгал на 20% при меньших
# кучах. Здесь: холодный запуск журнала, 10 секунд, замер; три раза, медиана.
# 04.10.2026: первый запуск после восстановления копии дал 162 МБ против
# 110–116 у остальных и сдвинул медиану — теперь один разогревочный запуск
# не считается.
Step "4. Память: разогрев + 3 холодных запуска журнала"
adb shell am force-stop $pkg
adb shell am start -W -n "$pkg/$pkg.MainActivity" | Out-Null
Start-Sleep -Seconds 10
$pss = @()
foreach ($i in 1..3) {
    adb shell am force-stop $pkg
    adb shell am start -W -n "$pkg/$pkg.MainActivity" | Out-Null
    Start-Sleep -Seconds 10
    $out = adb shell dumpsys meminfo $pkg
    $m = [regex]::Match(($out -join "`n"), 'TOTAL PSS:\s+(\d+)')
    $j = [regex]::Match(($out -join "`n"), 'Java Heap:\s+(\d+)')
    $n = [regex]::Match(($out -join "`n"), 'Native Heap:\s+(\d+)')
    if ($m.Success) {
        $pss += [int]$m.Groups[1].Value
        Write-Host ("  повтор {0}: TOTAL PSS {1:N1} МБ, Java {2:N1}, Native {3:N1}" -f $i, ($m.Groups[1].Value / 1024), ($j.Groups[1].Value / 1024), ($n.Groups[1].Value / 1024))
    }
}
if ($pss.Count -gt 0) {
    $median = ($pss | Sort-Object)[[int][math]::Floor($pss.Count / 2)]
    Write-Host ("Медиана TOTAL PSS: {0:N1} МБ" -f ($median / 1024)) -ForegroundColor Green
    $memBase = "docs\baseline\memory-base.txt"
    if (Test-Path $memBase) {
        $was = [int](Get-Content $memBase)
        Write-Host ("База памяти {0:N1} МБ, изменение {1:+0.0;-0.0}%" -f ($was / 1024), (($median / $was - 1) * 100))
    } else {
        Set-Content $memBase $median
        Write-Host "Базы памяти не было — эта цифра стала базой: $memBase" -ForegroundColor Yellow
    }
    Set-Content "docs\baseline\memory-$Label.txt" $median
}

Step "5. Ручная проверка — раздел «Что проверить» в docs\RELEASE_NOTES текущей версии"

adb shell svc power stayon false   # экран снова гаснет как обычно

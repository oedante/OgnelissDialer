/**
 * МОДУЛЬ ЗАМЕРОВ ПРОИЗВОДИТЕЛЬНОСТИ.
 *
 * Назначение: Macrobenchmark-тесты и генерация Baseline Profile.
 *
 * ── Почему отдельный модуль ────────────────────────────────────────────────
 * Macrobenchmark запускает приложение как внешний процесс и измеряет его
 * снаружи — так, как это видит пользователь. Тесты не могут жить внутри
 * измеряемого приложения: их присутствие исказило бы результат.
 *
 * ── Что такое Baseline Profile ─────────────────────────────────────────────
 * Список методов, которые Android компилирует заранее (AOT) при установке
 * вместо интерпретации при первом запуске. Для приложения на Compose это
 * даёт 20–30 % к скорости холодного старта.
 *
 * Для диалера холодный старт критичнее, чем для большинства приложений:
 * его открывают, когда нужно позвонить прямо сейчас.
 *
 * ── Модуль не нужен для обычной сборки ─────────────────────────────────────
 * Если он мешает (несовместимость версий плагина Baseline Profile с AGP,
 * отсутствие устройства для замеров), его можно временно отключить,
 * закомментировав include(":benchmark") в settings.gradle.kts и строку
 * baselineProfile(project(":benchmark")) в app/build.gradle.kts.
 * На работу приложения это не влияет — только на скорость холодного старта.
 */
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.ogneliss.benchmark"
    /*
     * ВЕРСИИ SDK СОВПАДАЮТ С ПРИЛОЖЕНИЕМ.
     *
     * ── Почему 36, а не 37 ─────────────────────────────────────────────────
     * AGP 9.3.1 поддерживает и 37, но модуль обязан совпадать
     * с приложением, а приложение пока собирается под 36: иначе профиль
     * описывает не то приложение, которое поедет пользователям.
     */
    // Совпадает с приложением: иначе профиль опишет не то,
    // что поедет пользователям
    compileSdk = 37  // 1.3.3: вместе с app

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // Macrobenchmark требует API 28 и выше: на более старых
        // нет нужных системных трассировок
        minSdk = 28
        targetSdk = 37  // 1.5.1: вместе с app
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    /**
     * Тип сборки для замеров.
     *
     * Измерять на debug бессмысленно: там отключены R8 и предкомпиляция,
     * и цифры не имеют отношения к тому, что увидит пользователь.
     * Поэтому берём release-конфигурацию, но с отладочной подписью.
     */
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    testOptions {
        managedDevices {
            /*
             * localDevices, а не devices.
             *
             * Контейнер devices с create<ManagedVirtualDevice> — старый API,
             * в AGP 9 его нет: сборка падает на «Unresolved reference
             * 'devices'». localDevices — нынешний вид, в нём тип устройства
             * уже известен, и указывать его не нужно.
             */
            localDevices {
                create("benchmarkDevice") {
                    device = "Pixel 6"
                    apiLevel = 34
                    // aosp-atd — образ без сервисов Google: он легче,
                    // быстрее стартует и даёт более стабильные цифры,
                    // потому что фоновые процессы Google не мешают замеру
                    systemImageSource = "aosp-atd"
                    // Явно: по умолчанию сейчас x86_64, а в AGP 10 станет
                    // arm64-v8a — такой образ эмулятора ARM-код не запустит,
                    // и замеры упадут. Фиксируем нынешнее поведение.
                    // AGP 9.4.1 всё равно пишет «does not specify a testedAbi»:
                    // задаче подготовки устройства он значение не передаёт —
                    // ошибка AGP, см. docs/BUILD_TROUBLESHOOTING.md
                    testedAbi = "x86_64"
                }
            }
        }
    }
}

// jvmTarget для Kotlin берётся из compileOptions: Kotlin встроен в AGP 9

baselineProfile {
    /*
     * managedDevices — это изменяемая коллекция строк, а не Property.
     * Поэтому += , а не .set(...). Аналогично useConnectedDevices —
     * обычное булево свойство, присваивается через =.
     */
    managedDevices += "benchmarkDevice"

    // false: профиль собирается на виртуальном устройстве из блока выше.
    // Поставить true, если хотите собирать на подключённом телефоне —
    // цифры будут ближе к реальности, но нужен физический аппарат
    useConnectedDevices = false

    /*
     * Блок warnings убран.
     *
     * Он существовал в ранних версиях плагина baselineprofile,
     * а в текущих его нет — сборка падает с «Unresolved reference».
     *
     * Предупреждение о непроверенной версии AGP, ради которого блок
     * добавлялся, информационное: если сборка идёт, его можно
     * игнорировать.
     */
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.espresso)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}

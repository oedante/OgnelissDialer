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
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.ogneliss.benchmark"
    /*
     * ВЕРСИИ SDK СОВПАДАЮТ С ПРИЛОЖЕНИЕМ.
     *
     * ── Почему не 37 ───────────────────────────────────────────────────────
     * AGP 8.7.3 проверен до compileSdk 35, а на 37 выдаёт
     * предупреждение и может вести себя непредсказуемо: генерация
     * профиля падает, не дойдя до прогона.
     *
     * Поднять AGP нельзя без Gradle 9 и новой версии Kotlin — это
     * отдельная работа, и делать её ради модуля замеров перед релизом
     * незачем.
     *
     * ── Почему важно именно совпадение ─────────────────────────────────────
     * Профиль записывает, какие методы выполняются при запуске.
     * Если модуль собран под другой SDK, часть путей кода отличается,
     * и профиль описывает не то приложение, которое поедет
     * пользователям.
     */
    // Совпадает с приложением: иначе профиль опишет не то,
    // что поедет пользователям
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // Macrobenchmark требует API 28 и выше: на более старых
        // нет нужных системных трассировок
        minSdk = 28
        targetSdk = 36
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
            devices {
                create<com.android.build.api.dsl.ManagedVirtualDevice>("benchmarkDevice") {
                    device = "Pixel 6"
                    apiLevel = 34
                    // aosp-atd — образ без сервисов Google: он легче,
                    // быстрее стартует и даёт более стабильные цифры,
                    // потому что фоновые процессы Google не мешают замеру
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

/**
 * jvmTarget задаётся через compilerOptions.
 *
 * Прежний kotlinOptions { jvmTarget = "17" } объявлен устаревшим
 * в Kotlin 2.x и в новых версиях даёт ошибку компиляции скрипта сборки.
 */
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

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

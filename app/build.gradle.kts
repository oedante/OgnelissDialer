import java.util.Properties
import java.io.FileInputStream

/**
 * КОНФИГУРАЦИЯ СБОРКИ ПРИЛОЖЕНИЯ.
 *
 * ── О подписи релиза ───────────────────────────────────────────────────────
 * Ключ и пароли НЕ хранятся в репозитории. Они читаются из файла
 * keystore.properties, который лежит рядом с проектом и внесён в .gitignore.
 *
 * Причина прямая: попавший в репозиторий ключ подписи означает, что любой,
 * у кого есть доступ к коду, может выпустить обновление вашего приложения.
 * Отозвать ключ нельзя — Google Play привязывает приложение к нему навсегда.
 *
 * Если файла нет, релизная сборка подписывается отладочным ключом:
 * так можно собрать и проверить сборку локально, но опубликовать её
 * не получится, и это правильно.
 */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

/** Свойства подписи из внешнего файла. Отсутствие файла — не ошибка. */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.ogneliss.dialer"
    /*
     * compileSdk 36 — требование Google Play.
     *
     * Магазин требует собирать под свежий API, иначе новые версии
     * не принимаются. Само по себе это не меняет поведение: targetSdk
     * остаётся 35, и приложение работает по правилам, под которые
     * проверено.
     *
     * Поднимать targetSdk отдельно — работа с проверкой на устройствах:
     * каждый шаг вводит новые ограничения фона, уведомлений
     * и разрешений.
     */
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ogneliss.dialer"
        minSdk = 26
        /*
         * targetSdk 36 — требование Google Play.
         *
         * ── Что меняется ───────────────────────────────────────────────────
         * targetSdk, в отличие от compileSdk, влияет на поведение:
         * система применяет к приложению правила той версии, под которую
         * оно заявлено.
         *
         * Для диалера на 36 значимо ограничение фоновых запусков
         * активности. У нас экран вызова открывается из фона — это
         * разрешено держателю роли звонилки, но проверить на устройстве
         * обязательно: см. PRE_RELEASE_TEST.md, пункт про входящий
         * при заблокированном экране.
         */
        targetSdk = 36
        /*
         * ВЕРСИЯ ПРИЛОЖЕНИЯ.
         *
         * versionCode — целое, которое видит только Google Play.
         * Он обязан расти с каждой загрузкой: загрузить сборку
         * с прежним или меньшим номером магазин откажется, а изменить
         * номер уже загруженной сборки нельзя.
         *
         * versionName — то, что видит пользователь в «О приложении»
         * и на странице магазина. Здесь оно соответствует стадии
         * подготовки к публикации: rc означает release candidate.
         *
         * Перед публикацией в открытый доступ сменить на 1.0.0
         * и поднять versionCode.
         */
        versionCode = 20
        versionName = "1.1.3"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // V1 отключаем: minSdk 26 его не требует, а он замедляет
                // установку и увеличивает размер
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // В релизе метка берётся из ресурсов — она локализована
            manifestPlaceholders["appLabel"] = "@string/app_name"

            /*
             * ОТЛАДОЧНЫЕ СИМВОЛЫ ДЛЯ НАТИВНОГО КОДА.
             *
             * ══════════════════════════════════════════════════════════════
             * ЗАЧЕМ
             * ══════════════════════════════════════════════════════════════
             * В бандл попадают нативные библиотеки — не наши, а от
             * зависимостей: libandroidx.graphics.path.so
             * и libdatastore_shared_counter.so.
             *
             * Если приложение упадёт внутри них, Play Console покажет
             * стек из голых адресов вида 0x7f2a1c. Понять по нему
             * ничего нельзя — ни функции, ни файла.
             *
             * С символами тот же отчёт превращается в читаемые имена
             * функций, и причина видна сразу.
             *
             * ── Почему SYMBOL_TABLE, а не FULL ─────────────────────────────
             * FULL добавляет полную отладочную информацию DWARF —
             * это десятки мегабайт к загружаемому файлу.
             *
             * SYMBOL_TABLE даёт имена функций, а этого достаточно, чтобы
             * прочитать стек падения. Полная отладка нужна тем, кто пишет
             * нативный код сам и собирается ставить точки останова, —
             * а библиотеки не наши, править их мы всё равно не будем.
             *
             * ── На размер приложения не влияет ─────────────────────────────
             * Символы хранятся в бандле рядом, а не в APK, который
             * скачивает человек. Google Play использует их для отчётов
             * и на устройство не отправляет.
             */
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }

            // Подпись релизным ключом, если он настроен. Иначе сборка
            // соберётся с отладочной подписью — проверить можно,
            // опубликовать нельзя
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"

            /*
             * Отличаем отладочную сборку на устройстве.
             *
             * applicationIdSuffix позволяет держать обе сборки рядом,
             * но в списке приложений они выглядят одинаково — и легко
             * проверять скорость на debug, думая, что это release.
             * Ровно эта путаница уже приводила к неверным выводам
             * о производительности.
             *
             * Имя задаётся через manifestPlaceholders, а НЕ через
             * resValue: строка app_name уже есть в ресурсах, и resValue
             * создал бы вторую с тем же именем — сборка упала бы
             * с «duplicate resources».
             */
            manifestPlaceholders["appLabel"] = "Ogneliss (debug)"
            versionNameSuffix = "-debug"
        }

        /**
         * Сборка для замеров производительности.
         *
         * Отладочная подпись, но БЕЗ debug-оптимизаций: цифры должны
         * соответствовать тому, что увидит пользователь. Измерять
         * на debug-сборке бессмысленно — там отключены R8 и предкомпиляция.
         */
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            /*
             * Символы здесь не нужны.
             *
             * initWith копирует настройки release, включая сбор
             * отладочных символов. Но эта сборка никуда не публикуется —
             * она только замеряет скорость запуска на устройстве,
             * а сбор символов добавляет к ней время и место впустую.
             */
            ndk {
                debugSymbolLevel = "NONE"
            }

            // Профиль в этой сборке не применяем: иначе замер «без профиля»
            // измерял бы профиль
            proguardFiles("benchmark-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    /**
     * Разделение по ABI не используется: App Bundle делает это сам,
     * а Google Play доставляет пользователю только нужную часть.
     */
    bundle {
        language {
            // Языки НЕ разделяем: пользователь должен иметь возможность
            // переключить язык приложения независимо от системного,
            // а при разделении в APK попадёт только системный
            enableSplit = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }

    /**
     * НАСТРОЙКА СТАТИЧЕСКОГО АНАЛИЗА.
     *
     * abortOnError для релиза: ошибка lint означает реальную проблему —
     * обращение к API выше minSdk, утечку, неверную работу с разрешениями.
     * Пропустить такое в релиз хуже, чем задержать сборку.
     *
     * Предупреждения ошибками НЕ делаем: среди них много информационных
     * от библиотек, и «все предупреждения — ошибки» приводит к тому,
     * что их начинают массово подавлять, теряя и настоящие.
     */
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false

        // Отчёт в файл: в CI консоль обрезается, и причина падения теряется
        htmlReport = true
        textReport = true

        disable += setOf(
            // Переводы добавляются вместе с новыми строками, а не заранее:
            // проверка мешает при разработке и дублирует наш audit.py,
            // который сверяет локали строже
            "MissingTranslation",
            // Тексты для Google Play лежат в store/, а не в ресурсах
            "GoogleAppIndexingWarning",
            /*
             * ProduceStateDoesNotAssignValue — ложное срабатывание.
             *
             * Проверка утверждает, что внутри produceState не присваивается
             * value. Она отметила семь мест сразу, и во всех присваивание
             * стоит первой же строкой лямбды:
             *
             *     val text by produceState(initialValue = "", document) {
             *         value = withContext(Dispatchers.IO) { ... }
             *     }
             *
             * Проверка приехала с Compose 1.7 и не понимает разбор кода
             * компилятором Kotlin 2.x — присваивания она попросту не видит.
             * Отключено целиком, потому что верных срабатываний у неё
             * в этом проекте не бывает: правило слишком грубое, чтобы
             * подавлять его по одному месту.
             *
             * Вернуть проверку стоит после обновления Compose: если
             * ложные срабатывания исчезнут, она полезная — produceState
             * без присваивания действительно навсегда остаётся
             * на начальном значении.
             */
            "ProduceStateDoesNotAssignValue"
        )
    }

    /*
     * ОГРАНИЧЕНИЕ СПИСКА ЛОКАЛЕЙ — НАМЕРЕННО ОТКЛЮЧЕНО.
     *
     * Оно убирает из сборки строки библиотек AndroidX на языках, которые
     * приложение не поддерживает, и экономит около сотни килобайт.
     * Экономия небольшая, а имя свойства менялось трижды:
     *
     *   AGP до 8.9   defaultConfig { resourceConfigurations += listOf("en", "ru") }
     *   AGP 8.9+     androidResources { localeFilters += listOf("en", "ru") }
     *
     * Ошибиться версией — значит получить ошибку сборки на ровном месте.
     * Ради сотни килобайт это того не стоит, поэтому строка убрана.
     *
     * Если решите вернуть: список ОБЯЗАН совпадать с
     * res/xml/locales_config.xml, иначе переключатель языка предложит
     * язык, которого в сборке нет, и интерфейс останется прежним.
     */
    ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    /*
     * isReturnDefaultValues — почему включено.
     *
     * Классы из android.jar в обычных JVM-тестах не реализованы: любой
     * вызов бросает RuntimeException «Method … not mocked». Через
     * Logger, который зовёт android.util.Log, это задевало почти
     * всё: политика фильтрации, блокировка, журнал заблокированных,
     * окно после звонка — тридцать с лишним тестов падали
     * не по своей сути, а на первой же строке логирования.
     *
     * С этим флагом заглушки возвращают значения по умолчанию
     * (null, 0, false) вместо исключения. Для логирования это ровно
     * то, что нужно: в тесте оно просто ничего не делает.
     *
     * ⚠️ Обратная сторона: тест, случайно положившийся на поведение
     * настоящего Android, получит тихий null вместо громкой ошибки.
     * Поэтому логику, зависящую от системных классов, проверяем
     * инструментальными тестами, а не этими.
     */
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // Схемы Room нужны инструментальным тестам миграций.
    // Файлы из app/schemas ОБЯЗАТЕЛЬНО коммитить в репозиторий.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

/**
 * ЦЕЛЕВАЯ ВЕРСИЯ JVM ДЛЯ KOTLIN.
 *
 * Задаётся здесь, а не внутри `android { }`.
 *
 * Прежний `kotlinOptions { jvmTarget = "17" }` объявлен устаревшим
 * в Kotlin 2.x: в новых версиях он даёт ошибку компиляции самого
 * скрипта сборки, а не предупреждение.
 *
 * Значение обязано совпадать с `compileOptions` выше: расхождение
 * между Java и Kotlin даёт невнятную ошибку про несовместимые версии
 * байт-кода уже на этапе компоновки.
 */
/**
 * ОТЧЁТ О СТАБИЛЬНОСТИ COMPOSE.
 *
 * ── Зачем ──────────────────────────────────────────────────────────────────
 * Компилятор Compose умеет выписать, какие функции пропускаемы,
 * а какие перерисовываются всегда, и какие классы он считает
 * нестабильными.
 *
 * Без этого отчёта нестабильность обнаруживается только по ощущению
 * «интерфейс подтормаживает», и причину приходится угадывать: один
 * класс с полем List делает нестабильным всё, что его содержит,
 * а компилятор об этом молчит.
 *
 * Отчёт собирается командой:
 *   ./gradlew assembleRelease -Pcompose.metrics=true
 *
 * Результат: app/build/compose_metrics/
 * Искать в *-classes.txt строки «unstable class» и
 * в *-composables.txt — «restartable skippable: false».
 */
composeCompiler {
    val metricsEnabled = project.findProperty("compose.metrics") == "true"
    if (metricsEnabled) {
        metricsDestination.set(layout.buildDirectory.dir("compose_metrics"))
        reportsDestination.set(layout.buildDirectory.dir("compose_metrics"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

/**
 * ЖЁСТКОЕ ОГРАНИЧЕНИЕ ВЕРСИЙ COMPOSE.
 *
 * ══════════════════════════════════════════════════════════════════════════
 * ЗАЧЕМ, ЕСЛИ ЕСТЬ BOM
 * ══════════════════════════════════════════════════════════════════════════
 * BOM задаёт версии как рекомендацию. Если любая зависимость — своя
 * или транзитивная — потребует версию выше, Gradle возьмёт высшую:
 * таково правило разрешения конфликтов, и BOM его не отменяет.
 *
 * Так в сборку попал Compose 1.12.0. Он требует Android Gradle Plugin 9.1
 * и compileSdk 37, тогда как у проекта AGP 8.7.3 и compileSdk 35 —
 * сборка падает на проверке метаданных AAR с десятком сообщений сразу.
 *
 * `strictly` действует иначе рекомендации: попытка поднять версию
 * не проходит молча, а роняет сборку с указанием, кто её потребовал.
 * Это и нужно — узнать источник, а не обнаружить последствие.
 *
 * ── Почему обновиться нельзя ───────────────────────────────────────────────
 * compileSdk 37 требует AGP 9.x, а тот — Gradle 9 и новую версию Kotlin.
 * Обновление всей цепочки посреди подготовки к публикации означало бы
 * заново проверять всё приложение. Compose 1.7.6 полностью закрывает
 * наши задачи.
 *
 * ── Как найти виновника, если сборка упадёт ────────────────────────────────
 *   ./gradlew :app:dependencyInsight \
 *       --dependency androidx.compose.ui:ui-tooling \
 *       --configuration debugRuntimeClasspath
 *
 * Команда покажет цепочку: кто именно требует версию выше.
 */
configurations.configureEach {
    resolutionStrategy {
        eachDependency {
            if (requested.group.startsWith("androidx.compose.ui") ||
                requested.group == "androidx.compose.foundation" ||
                requested.group == "androidx.compose.animation" ||
                requested.group == "androidx.compose.runtime"
            ) {
                useVersion(libs.versions.composeUi.get())
                because("AGP 8.7.3 и compileSdk 35 не поддерживают Compose 1.12+")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Положение раскладушки: разбор в ui/adaptive/WindowLayout.kt
    implementation(libs.androidx.window)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.splashscreen)
    // Устанавливает Baseline Profile при первом запуске на устройствах,
    // где системный установщик этого не делает
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.datastore)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.coil.compose)
    implementation(libs.libphonenumber)

    /*
     * ZXing: генерация QR-кодов.
     *
     * ── Почему библиотека, а не свой код ───────────────────────────────────
     * Кодировщик QR — это маски, служебные поля и коррекция Рида —
     * Соломона. Написать его можно, но проверить без сканирования
     * нельзя: неверный код выглядит правильно и просто не считывается.
     *
     * ZXing проверен годами и используется повсеместно.
     *
     * ── Про сеть ───────────────────────────────────────────────────────────
     * Берём только core — это чистые вычисления на Java, без Android
     * и без сети. Приложение по-прежнему не имеет разрешения
     * на интернет, и это можно проверить в свойствах.
     */
    implementation(libs.zxing.core)
    implementation(libs.libphonenumber.geocoder)

    /*
     * Оператор по номеру за пределами России.
     *
     * ⚠️ Весит несколько мегабайт: данные всех стран на всех языках.
     * Если прирост размера сборки окажется неприемлемым, зависимость
     * убирается одной строкой — в NumberInfoRepository обращение
     * к ней обёрнуто и при отсутствии просто ничего не вернёт.
     */
    implementation(libs.libphonenumber.carrier)
    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.accompanist.permissions)

    // Тесты на JVM: быстрые, гоняются на каждый коммит
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14")

    // Тесты на устройстве
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    baselineProfile(project(":benchmark"))

    /*
     * BOM ОБЯЗАТЕЛЕН И ДЛЯ ОТЛАДОЧНЫХ ЗАВИСИМОСТЕЙ.
     *
     * ── Что было не так ────────────────────────────────────────────────────
     * ui-test-manifest объявлялся без версии, а BOM подключался только
     * к androidTestImplementation. Для debugImplementation его не было,
     * и Gradle брал последнюю доступную версию — 1.12.0.
     *
     * Она требует Android Gradle Plugin 9.1 и compileSdk 37, тогда как
     * у проекта AGP 8.7.3 и compileSdk 35. Отсюда двенадцать сообщений
     * о несовместимости метаданных AAR.
     *
     * Одна зависимость без версии подтягивает за собой весь Compose:
     * версии внутри группы согласованы, и Gradle поднимает остальные
     * до той же.
     */
    debugImplementation(platform(libs.compose.bom))
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

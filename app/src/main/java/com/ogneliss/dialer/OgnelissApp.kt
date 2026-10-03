package com.ogneliss.dialer

import com.ogneliss.dialer.concurrency.suspendRunCatching

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import coil.request.CachePolicy
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ogneliss.dialer.background.NotesCleanupWorker
import com.ogneliss.dialer.background.T9IndexWorker
import com.ogneliss.dialer.call.CallNotifications
import com.ogneliss.dialer.concurrency.StrictModePolicy
import com.ogneliss.dialer.log.CrashHandler
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PhoneFormatter
import com.ogneliss.dialer.settings.Prefs
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ТОЧКА ВХОДА ПРИЛОЖЕНИЯ.
 *
 * Создаётся системой один раз при запуске процесса — в том числе когда
 * процесс поднимается ради входящего вызова, без запуска интерфейса.
 * Поэтому здесь только быстрая инициализация: тяжёлого кода быть не должно,
 * иначе пострадает время отклика на звонок.
 *
 * Порядок инициализации важен:
 *  1. Логгер — чтобы всё последующее уже писалось в журнал.
 *  2. Перехватчик падений — чтобы поймать сбой на самой ранней стадии.
 *  3. Каналы уведомлений — нужны до показа входящего вызова.
 *  4. Фоновая индексация T9 — можно отложить, запускается через WorkManager.
 */
@HiltAndroidApp
class OgnelissApp : Application(), Configuration.Provider, ImageLoaderFactory {

    /**
     * ЗАГРУЗЧИК ИЗОБРАЖЕНИЙ ДЛЯ АВАТАРОВ.
     *
     * ── Зачем настраивать вручную ──────────────────────────────────────────
     * По умолчанию Coil отводит под кэш в памяти долю от доступной
     * приложению памяти и хранит на диске до 250 МБ. Для диалера это
     * и много, и мало одновременно:
     *
     * • дисковый кэш ЛИШНИЙ. Фотографии контактов уже лежат на устройстве,
     *   в провайдере контактов. Копировать их к себе — занимать место
     *   ради данных, которые никуда не денутся;
     *
     * • кэш в памяти НУЖЕН и должен быть предсказуемым. Аватары
     *   декодируются при каждой прокрутке мимо, а декодирование
     *   изображения — самая дорогая операция при отрисовке строки.
     *
     * 15 % памяти хватает на несколько экранов прокрутки в обе стороны.
     * Больше отдавать нельзя: приложение должно уступать память
     * телефонии, когда идёт разговор.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.15)
                .build()
        }
        // Дисковый кэш выключен: оригиналы и так на устройстве
        .diskCachePolicy(CachePolicy.DISABLED)
        .build()

    /** Фабрика воркеров с внедрением зависимостей (Hilt + WorkManager). */
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** Настройки нужны, чтобы узнать, включена ли запись лога в файл. */
    @Inject lateinit var prefs: Prefs

    /**
     * ОБЛАСТЬ ФОНОВЫХ ЗАДАЧ УРОВНЯ ПРИЛОЖЕНИЯ.
     *
     * Используется для инициализации и для постановки периодических
     * задач WorkManager. Последнее вынесено сюда намеренно: WorkManager
     * при первом обращении читает свою базу с диска синхронно,
     * а Application.onCreate блокирует показ первого экрана.
     *
     * SupervisorJob: падение одной задачи не отменяет остальные.
     * Очистка заметок и индексация T9 независимы, и сбой первой
     * не повод не ставить вторую.
     *
     * Область живёт всё время работы приложения и отменять её не нужно:
     * процесс умрёт вместе с ней.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Конфигурация WorkManager. Автоматический инициализатор отключён
     * в манифесте, иначе воркеры создавались бы без зависимостей.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Порядок важен: StrictMode должен встать до первых обращений
        // к диску, иначе нарушения на старте останутся незамеченными
        StrictModePolicy.install()

        Logger.init(this)
        CrashHandler.install(this)

        /*
         * СТРАНА ДЛЯ РАЗБОРА МЕСТНЫХ НОМЕРОВ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ ЗДЕСЬ И ПОЧЕМУ ОДИН РАЗ
         * ══════════════════════════════════════════════════════════════════
         * Номер «030 12345678» — местный, и без страны его не привести
         * к E.164. А E.164 — ключ, по которому живут заметки, правила
         * блокировки и напоминания: номер, который не привёлся,
         * не совпадёт сам с собой, записанным иначе.
         *
         * Ставится до первого обращения к журналу, поэтому здесь,
         * а не в экране.
         *
         * Порядок источников: SIM-карта (где выдан номер владельца),
         * затем сеть (где телефон сейчас), затем страна из настроек
         * языка. Язык системы последним: у русскоязычного в Германии
         * он русский, а номера вокруг немецкие.
         */
        runCatching {
            val telephony = getSystemService(android.telephony.TelephonyManager::class.java)
            val region = telephony?.simCountryIso?.takeIf { it.isNotBlank() }
                ?: telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
                ?: java.util.Locale.getDefault().country

            if (!region.isNullOrBlank()) PhoneFormatter.setDefaultRegion(region)
        }.onFailure { Logger.w("App", "Не удалось определить страну SIM: ${it.message}") }

        // Если прошлый запуск закончился падением, сообщаем на каком этапе.
        // Строка попадает в начало нового лога — при разборе это первое,
        // что видит человек, открывший файл
        CrashHandler.consumeLastCrashStage(this)?.let { stage ->
            Logger.e("Crash", "ПРОШЛЫЙ ЗАПУСК ЗАВЕРШИЛСЯ ПАДЕНИЕМ на этапе: $stage")
        }

        // Настройка чтения асинхронна, до её загрузки пишем в файл по умолчанию
        appScope.launch {
            suspendRunCatching { Logger.setFileLogging(prefs.fileLogging.first()) }
                .onFailure { Logger.e("App", "Не удалось прочитать настройку логирования", it) }
        }

        // Каждый шаг обёрнут отдельно: сбой одного не должен мешать
        // остальным. Приложение обязано запуститься даже если фоновая
        // индексация или каналы уведомлений почему-то не создались —
        // звонить оно сможет и без них
        runCatching { CallNotifications.ensureChannels(this) }
            .onFailure { Logger.e("App", "Не удалось создать каналы уведомлений", it) }

        /*
         * ПОСТАНОВКА ФОНОВЫХ ЗАДАЧ — ВНЕ ГЛАВНОГО ПОТОКА.
         *
         * WorkManager при первом обращении инициализирует свою базу
         * данных и читает состояние ранее поставленных задач. Это работа
         * с диском, и она выполняется синхронно в вызывающем потоке.
         *
         * Здесь этот поток — главный, а Application.onCreate блокирует
         * показ первого экрана: пока он не завершится, пользователь
         * смотрит на заставку.
         *
         * Задачи периодические, ближайшая сработает через сутки —
         * задержка в несколько сотен миллисекунд на их постановку
         * никому не важна, а старт она удлиняет заметно.
         *
         * Область живёт всё время работы приложения, поэтому отменять
         * её не нужно: процесс умрёт вместе с ней.
         */
        appScope.launch {
            runCatching { NotesCleanupWorker.schedule(this@OgnelissApp) }
                .onFailure { Logger.e("App", "Не удалось поставить очистку заметок", it) }

            runCatching { T9IndexWorker.enqueuePeriodic(this@OgnelissApp) }
                .onFailure { Logger.e("App", "Не удалось поставить индексацию", it) }
        }

        Logger.i("App", "Инициализация завершена")
    }
}

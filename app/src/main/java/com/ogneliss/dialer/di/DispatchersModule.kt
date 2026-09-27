package com.ogneliss.dialer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * ДИСПЕТЧЕРЫ КОРУТИН.
 *
 * Назначение: единая точка выдачи диспетчеров через внедрение зависимостей.
 *
 * ── Зачем внедрять, а не писать Dispatchers.IO прямо в коде ────────────────
 * 1. Тестируемость. В тестах подставляется StandardTestDispatcher,
 *    и корутины выполняются детерминированно. С захардкоженным
 *    Dispatchers.IO тест либо флакует, либо требует задержек.
 * 2. Видно намерение. Явный @IoDispatcher в конструкторе сразу показывает,
 *    что класс делает блокирующие операции.
 *
 * ── Какой диспетчер для чего ───────────────────────────────────────────────
 * IO      — ContentResolver, файлы, база, DataStore. Пул растёт до 64 потоков,
 *           потому что эти операции в основном ждут, а не считают.
 * Default — вычисления: разбор номеров libphonenumber, построение T9-индекса,
 *           группировка большого журнала. Пул по числу ядер: больше потоков
 *           на счётной задаче только добавят переключений контекста.
 * Main    — только обновление UI.
 *
 * Частая ошибка — считать разбор 500 номеров «лёгкой» операцией и оставить
 * его на IO. Формально работает, но занимает поток из пула, который нужен
 * для чтения журнала, и всё замедляется.
 */

@Retention(AnnotationRetention.BINARY) @Qualifier annotation class IoDispatcher
@Retention(AnnotationRetention.BINARY) @Qualifier annotation class DefaultDispatcher
@Retention(AnnotationRetention.BINARY) @Qualifier annotation class MainDispatcher

/** Область жизни всего приложения — для работы, переживающей экраны. */
@Retention(AnnotationRetention.BINARY) @Qualifier annotation class ApplicationScope

/**
 * ── О ПРЕФИКСЕ provide В ИМЕНАХ ────────────────────────────────────────────
 * Имена методов здесь ОБЯЗАНЫ не совпадать с ключевыми словами Java.
 *
 * Hilt порождает Java-код, где имя @Provides-функции становится именем
 * Java-метода. Функция `default()` выглядит в Kotlin безобидно, но
 * `default` — ключевое слово Java (с версии 8, для методов интерфейсов),
 * и генерация падает с невнятным «KSP failed with exit code:
 * PROCESSING_ERROR».
 *
 * Из Kotlin-кода это не видно вообще: компилятор Kotlin не возражает,
 * ошибка приходит на этапе генерации. Поэтому все методы здесь названы
 * с префиксом provide — заодно защищает от `native`, `final`, `static`
 * и прочих слов, которые в Kotlin ключевыми не являются.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    /**
     * Область для операций, которые должны завершиться, даже если экран
     * закрыт: запись заметки, сохранение предпочтения SIM.
     *
     * SupervisorJob обязателен: падение одной операции не должно отменять
     * все остальные на весь оставшийся срок жизни процесса.
     */
    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher dispatcher: CoroutineDispatcher
    ): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}

package com.ogneliss.dialer.concurrency

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ТЕСТЫ ЗАДЕРЖЕК ОБРАБОТКИ СОБЫТИЙ.
 *
 * Дебаунс легко сломать незаметно: уберёшь оператор при рефакторинге —
 * приложение продолжит работать, просто станет перечитывать журнал
 * пять раз вместо одного. На эмуляторе с пустым журналом разницы нет,
 * у пользователя с пятью тысячами записей — заметное подвисание
 * сразу после завершения звонка.
 *
 * Виртуальное время runTest позволяет проверить это за миллисекунды,
 * не дожидаясь реальных задержек.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowTimingTest {

    @Test
    fun `пачка событий журнала схлопывается в одно`() = runTest {
        val source = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
        val received = mutableListOf<Unit>()

        val job = launch {
            source.debounceContentChanges().collect { received += it }
        }
        /*
         * runCurrent() обязателен.
         *
         * launch не запускает сопрограмму мгновенно: она встаёт в очередь
         * планировщика. MutableSharedFlow без replay выбрасывает значения,
         * отправленные когда подписчиков ещё нет, — первая emit пропадала,
         * и тест на два раздельных события видел одно.
         *
         * Ошибка была в тесте, не в debounce: в приложении подписка живёт
         * задолго до первого события от системы.
         */
        runCurrent()

        // Система пишет запись, потом длительность, потом имя и фото —
        // всё в пределах сотни миллисекунд
        repeat(5) {
            source.emit(Unit)
            advanceTimeBy(50)
        }
        advanceUntilIdle()

        assertEquals("Пять системных правок должны дать одно обновление", 1, received.size)
        job.cancel()
    }

    @Test
    fun `разнесённые во времени события обрабатываются раздельно`() = runTest {
        val source = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
        val received = mutableListOf<Unit>()

        val job = launch {
            source.debounceContentChanges().collect { received += it }
        }
        /*
         * runCurrent() обязателен.
         *
         * launch не запускает сопрограмму мгновенно: она встаёт в очередь
         * планировщика. MutableSharedFlow без replay выбрасывает значения,
         * отправленные когда подписчиков ещё нет, — первая emit пропадала,
         * и тест на два раздельных события видел одно.
         *
         * Ошибка была в тесте, не в debounce: в приложении подписка живёт
         * задолго до первого события от системы.
         */
        runCurrent()

        source.emit(Unit)
        advanceTimeBy(FlowTiming.CONTENT_CHANGE_DEBOUNCE_MS + 100)
        source.emit(Unit)
        advanceUntilIdle()

        assertEquals("Два отдельных звонка — два обновления", 2, received.size)
        job.cancel()
    }

    @Test
    fun `быстрый набор цифр не порождает лишних поисков`() = runTest {
        val queries = flowOf("9", "96", "967", "9676")
        val result = mutableListOf<String>()

        val job = launch {
            queries.debounceSearchQuery().collect { result += it }
        }
        advanceUntilIdle()
        job.cancel()

        // Промежуточные состояния отбрасываются, ищем только по последнему
        assertEquals(listOf("9676"), result)
    }

    @Test
    fun `задержки заданы разумными значениями`() {
        // Фиксируем константы: случайное увеличение сделает интерфейс вялым,
        // уменьшение вернёт лишние перечитывания
        assertEquals(400L, FlowTiming.CONTENT_CHANGE_DEBOUNCE_MS)
        assertEquals(120L, FlowTiming.SEARCH_DEBOUNCE_MS)
    }
}

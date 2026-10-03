package com.ogneliss.dialer.concurrency

import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspendRunCatchingTest {

    @Test
    fun `успех возвращает значение`() {
        assertEquals(42, suspendRunCatching { 42 }.getOrNull())
    }

    @Test
    fun `обычная ошибка становится failure`() {
        val result = suspendRunCatching<Int> { error("сбой") }
        assertTrue(result.isFailure)
    }

    @Test(expected = CancellationException::class)
    fun `отмена пробрасывается`() {
        suspendRunCatching<Int> { throw CancellationException("отмена") }
    }
}

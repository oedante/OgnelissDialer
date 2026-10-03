package com.ogneliss.dialer.concurrency

import kotlin.coroutines.cancellation.CancellationException

/**
 * runCatching, который не глотает отмену корутины (1.3.2, 01.10.2026).
 *
 * Было: runCatching { … } вокруг suspend-вызовов ловил и
 * CancellationException. Отменённая работа записывалась в журнал как
 * ошибка («Не удалось обновить ярлыки … was cancelled», 01.10.2026),
 * а код после onFailure продолжал выполняться в уже отменённой корутине.
 *
 * Стало: отмена пробрасывается дальше, остальные ошибки — как у runCatching.
 *
 * Только для кода внутри корутин. В обычном коде оставлять runCatching:
 * там CancellationException (например, от Future) пробрасывать незачем.
 */
inline fun <R> suspendRunCatching(block: () -> R): Result<R> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

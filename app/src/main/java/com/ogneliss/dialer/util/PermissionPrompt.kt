package com.ogneliss.dialer.util

import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * СИСТЕМНОЕ ОКНО РАЗРЕШЕНИЯ — ПО МЕСТУ, БЕЗ СВОИХ ОБЪЯСНЕНИЙ (2.1.0).
 *
 * Было: «Позвонить» без разрешения «Телефон» молча ничего не делало
 * (CallActions.placeCall выходил первой строкой) — человек жал кнопку
 * и не понимал, что случилось. Стало по правилу автора «выполнить,
 * а не объяснять»: системное окно разрешения прямо в момент действия,
 * а после «Разрешить» — то же действие.
 *
 * Код без экрана (CallActions) просит [ask]; окно показывает корень
 * интерфейса (RootScreen собирает [requests] и отвечает [onResult]).
 * Приложение не на экране — просьба пропадает: действие начато с экрана,
 * а без него окно разрешения показать нельзя.
 */
object PermissionPrompt {

    private const val TAG = "PermissionPrompt"

    private val _requests = MutableSharedFlow<Array<String>>(extraBufferCapacity = 1)

    /** Какие разрешения спросить — собирает корень интерфейса. */
    val requests: SharedFlow<Array<String>> = _requests

    /** Что сделать после ответа: одно ожидание за раз, новое заменяет старое. */
    @Volatile
    private var pending: ((Boolean) -> Unit)? = null

    /** Спросить [permissions] системным окном; [then] — выданы ли все. */
    fun ask(permissions: Array<String>, then: (granted: Boolean) -> Unit) {
        if (_requests.subscriptionCount.value == 0) {
            Logger.w(TAG, "Окно разрешения показать некому — приложение не на экране")
            return
        }
        pending = then
        if (!_requests.tryEmit(permissions)) {
            pending = null
            Logger.w(TAG, "Окно разрешения уже открывается")
        }
    }

    /** Ответ системного окна. */
    fun onResult(result: Map<String, Boolean>) {
        val then = pending ?: return
        pending = null
        then(result.isNotEmpty() && result.values.all { it })
    }
}

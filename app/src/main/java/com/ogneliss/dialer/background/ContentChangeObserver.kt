package com.ogneliss.dialer.background

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.CallLog
import android.provider.ContactsContract
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PermissionGuard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ОТСЛЕЖИВАНИЕ ИЗМЕНЕНИЙ СИСТЕМНЫХ ДАННЫХ.
 *
 * ── Откуда вообще берутся данные ───────────────────────────────────────────
 * Приложение НЕ ведёт собственный журнал вызовов и не хранит копию
 * контактов. И то и другое читается из системных провайдеров:
 *   • CallLog.Calls — журнал, который заполняет телефония Android
 *   • ContactsContract — телефонная книга со всеми аккаунтами
 *
 * В своей базе лежит только то, чему в системе места нет: заметки, метки,
 * правила блокировки, предпочтения SIM.
 *
 * Следствие: данные меняются ВНЕ нашего приложения. Пользователь
 * отредактировал контакт в системном приложении, синхронизация Google
 * подтянула новые записи, пришёл звонок при выключенном экране —
 * обо всём этом мы узнаём только от провайдеров.
 *
 * ── Почему два раздельных потока, а не один ────────────────────────────────
 * Раньше был один общий поток изменений, и любое событие вызывало полную
 * перезагрузку: и журнала, и контактов, и пересборку T9-индекса.
 * Но это разные по стоимости операции:
 *
 *   журнал изменился  → перечитать страницу журнала (дёшево)
 *   контакты изменились → перечитать книгу И пересобрать T9-индекс (дорого)
 *
 * Синхронизация аккаунтов трогает контакты пачками по несколько раз в час.
 * Перечитывать при этом журнал незачем, и наоборот: после каждого звонка
 * пересобирать индекс из тысяч контактов — расточительно.
 *
 * ── Почему отдельный поток для обратных вызовов ────────────────────────────
 * ContentObserver вызывается на потоке своего Handler. Если передать
 * Looper главного потока, уведомления от синхронизации аккаунтов будут
 * будить UI-поток десятки раз подряд. Свой HandlerThread снимает эту нагрузку.
 */
@Singleton
class ContentChangeObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private companion object { const val TAG = "ContentObserver" }

    /** Отдельный поток: уведомления не должны будить главный. */
    private val handlerThread = HandlerThread("OgnelissContentObserver").apply { start() }
    private val handler = Handler(handlerThread.looper)

    private val _callLogChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val callLogChanges: SharedFlow<Unit> = _callLogChanges

    private val _contactsChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val contactsChanges: SharedFlow<Unit> = _contactsChanges

    private val callLogObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            // selfChange = true означает, что изменение внесли мы сами
            // (например, удалили запись). Реагировать на своё же действие
            // не нужно — список уже обновлён напрямую
            if (selfChange) return
            Logger.d(TAG, "Журнал вызовов изменился")
            _callLogChanges.tryEmit(Unit)
        }
    }

    private val contactsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (selfChange) return
            Logger.d(TAG, "Контакты изменились")
            _contactsChanges.tryEmit(Unit)
        }
    }

    /**
     * Подписка на изменения.
     *
     * ── Почему проверка разрешений здесь обязательна ───────────────────────
     * Без разрешения registerContentObserver бросает SecurityException.
     * Раньше он перехватывался, но подписка при этом не устанавливалась —
     * и после того как пользователь выдавал разрешение, список
     * не обновлялся сам до перезапуска приложения.
     *
     * Теперь подписка вызывается повторно после выдачи разрешений
     * (см. вызов в DialerViewModel).
     */
    fun register() {
        /*
         * Повторный вызов допустим и безопасен.
         *
         * Сначала снимаем прежнюю подписку: иначе при каждом возвращении
         * на передний план добавлялся бы ещё один наблюдатель, и одно
         * изменение журнала вызывало бы несколько перезагрузок подряд —
         * лечение превратилось бы в новый источник тормозов.
         */
        unregister()

        val cr = context.contentResolver

        if (PermissionGuard.canReadCallLog(context)) {
            runCatching {
                cr.registerContentObserver(CallLog.Calls.CONTENT_URI, true, callLogObserver)
            }.onFailure { Logger.w(TAG, "Не удалось подписаться на журнал вызовов", it) }
        } else {
            PermissionGuard.logDenied("наблюдение за журналом", "READ_CALL_LOG")
        }

        if (!PermissionGuard.canReadContacts(context)) {
            PermissionGuard.logDenied("наблюдение за контактами", "READ_CONTACTS")
            return
        }

        runCatching {
            // notifyForDescendants = true обязательно: правки приходят
            // на дочерние URI конкретных контактов, а не на корневой.
            // Без этого флага изменение имени контакта останется незамеченным
            cr.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI, true, contactsObserver
            )
        }.onFailure { Logger.w(TAG, "Не удалось подписаться на контакты", it) }

        Logger.i(TAG, "Наблюдение за изменениями включено")
    }

    fun unregister() {
        runCatching {
            context.contentResolver.unregisterContentObserver(callLogObserver)
            context.contentResolver.unregisterContentObserver(contactsObserver)
        }
    }
}

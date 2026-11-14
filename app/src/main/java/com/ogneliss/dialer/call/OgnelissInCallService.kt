package com.ogneliss.dialer.call

import com.ogneliss.dialer.log.CallDiagnostics
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.ogneliss.dialer.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import com.ogneliss.dialer.util.PhoneFormatter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * ОБЯЗАТЕЛЬНЫЙ КОМПОНЕНТ ДИАЛЕРА ПО УМОЛЧАНИЮ.
 *
 * Система передаёт сюда все вызовы. Наша задача — показать экран вызова
 * и сообщить остальному приложению, что происходит.
 *
 * ── Что делает система, а не мы ────────────────────────────────────────────
 * Мелодию входящего вызова проигрывает telecom. Флаг
 * IN_CALL_SERVICE_RINGING в манифесте намеренно НЕ объявлен, поэтому
 * система звонит сама и учитывает персональную мелодию контакта,
 * «Не беспокоить» с исключениями, вибро и беззвучный режимы,
 * рабочий профиль и настройки доступности.
 *
 * Взять рингтон на себя означало бы воспроизвести всё это заново
 * и по-разному на каждой прошивке — с гарантированными расхождениями.
 */
@AndroidEntryPoint
class OgnelissInCallService : InCallService() {

    /** Метка для лога при неудачном показе уведомления. Не интерфейсная строка. */
    private companion object {
        const val TAG = "InCallService"
        /** Дольше настройку плашки при первом вызове не ждём (incomingBannerWanted). */
        const val BANNER_PREF_WAIT_MS = 250L
        const val WHAT_INCOMING = "показ уведомления о входящем"

        /**
         * Паузы между попытками заглушить звонок: сразу, через 0,3; 0,9; 1,9
         * и 3,9 с. Последние две — для прошивок, где мелодия начинается
         * не сразу, а после вибрации (разбор у silenceRinger).
         */
        val SILENCE_RETRY_MS = longArrayOf(0L, 300L, 600L, 1_000L, 2_000L)

        /** Вибрация при ответе: короткая, как касание (2.5.0). */
        const val CONNECT_VIBRATION_MS = 120L
    }

    /**
     * Передаёт факт завершения вызова в интерфейс.
     * Сервис не знает об экранах, а Activity может быть не запущена —
     * посредник разрывает эту связь.
     */
    @Inject lateinit var postCall: PostCallCoordinator

    /** Заметки: попадают в уведомление после отклонённого вызова. */
    @Inject lateinit var notesRepo: com.ogneliss.dialer.data.NotesRepository

    /** Настройки автодозвона (1.8.0): копия держится в AutoRedial.config. */
    @Inject lateinit var prefs: com.ogneliss.dialer.settings.Prefs

    /** Имена от оператора (1.8.0): журнал покажет их после звонка. */
    @Inject lateinit var networkNames: com.ogneliss.dialer.data.NetworkNames

    /** Область приложения: запись имени переживёт остановку службы. */
    @Inject @com.ogneliss.dialer.di.ApplicationScope
    lateinit var appScope: CoroutineScope

    /** Живёт, пока система держит службу, — то есть пока есть вызовы. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /*
     * ИСХОДЯЩИЕ — ЗАПОМИНАЕМ ПРИ ПОЯВЛЕНИИ (1.8.0).
     *
     * До Android 10 направление вызова в Details не отдаётся, и
     * CallDetailsCompat угадывает его по состоянию: «набирается» —
     * значит исходящий. В onCallRemoved состояние уже «завершён», угадать
     * нечем — и на Android 8–9 любой несостоявшийся исходящий считался
     * сброшенным входящим: после «занято» выходило «Перезвонить?».
     * Теперь направление берётся в onCallAdded, пока вызов ещё набирается.
     */
    private val outgoingCalls = mutableSetOf<Call>()

    /** Настройка «Предлагать напоминание после сброса» — копия, как у автодозвона. */
    @Volatile private var callbackSuggestionOn = true
    /** «Входящий плашкой сверху», когда телефоном пользуются (2.1.0, Prefs.incomingBanner). */
    @Volatile private var incomingBannerOn = !com.ogneliss.dialer.util.IncomingCallAccess.restrictiveShell
    /** Значение плашки уже пришло из настроек — до того решает [incomingBannerWanted]. */
    @Volatile private var incomingBannerKnown = false
    /** «Вибрация при ответе» (2.5.0, Prefs.vibrateOnConnect) — копия, как у автодозвона. */
    @Volatile private var vibrateOnConnect = true

    override fun onCreate() {
        super.onCreate()
        /*
         * Настройки автодозвона читаются заранее: onCallRemoved — обычный
         * вызов в главном потоке, ждать в нём DataStore нельзя. Пока вызов
         * идёт, поток успевает отдать значение задолго до его конца.
         */
        serviceScope.launch {
            combine(prefs.autoRedialMode, prefs.autoRedialPace) { mode, pace ->
                AutoRedial.Config(mode, pace)
            }.collect { AutoRedial.config = it }
        }
        serviceScope.launch { prefs.callbackSuggestion.collect { callbackSuggestionOn = it } }
        serviceScope.launch { prefs.vibrateOnConnect.collect { vibrateOnConnect = it } }
        serviceScope.launch {
            prefs.incomingBanner.collect {
                incomingBannerOn = it
                incomingBannerKnown = true
            }
        }
        // Уведомление разговора — за текущей линией (2.4.3, разбор у followCurrentLine)
        serviceScope.launch { CallManager.revision.collect { followCurrentLine() } }
    }

    /*
     * УВЕДОМЛЕНИЕ РАЗГОВОРА — ЗА ТЕКУЩЕЙ ЛИНИЕЙ (2.4.3, обзор логики звонков).
     *
     * Было: уведомление пересобиралось при появлении вызова, при первом
     * ответе и после ухода вызова. «Переключить» на вторую линию, «поговорить
     * отдельно» с участником, сборка конференции — в шторке оставалось имя
     * прежнего собеседника, а у конференции — «Неизвестный номер» (своего
     * номера у неё нет). Стало: на каждое событие телефонии (CallManager.
     * revision) уведомление сверяется с текущим вызовом и пересобирается,
     * только если изменились подпись или начало разговора. Пока кто-то
     * звонит — не трогаем: там уведомление входящего с «Ответить».
     */
    /** Что сейчас в уведомлении разговора: подпись и начало; null — там не разговор. */
    private var shownOngoing: String? = null

    @Suppress("DEPRECATION")
    private fun followCurrentLine() {
        val calls = CallManager.calls.value
        if (calls.isEmpty()) return
        if (calls.any { it.state == Call.STATE_RINGING }) {
            shownOngoing = null
            return
        }
        val primary = CallManager.primary ?: return
        if (primary.state != Call.STATE_ACTIVE && primary.state != Call.STATE_HOLDING) return
        postOngoing(primary)
    }

    /** Подпись вызова в уведомлении: у конференции — «Конференция» и число участников. */
    private fun ongoingLabel(call: Call): String = if (CallManager.isConference(call)) {
        val n = call.children.size
        if (n > 0) getString(com.ogneliss.dialer.R.string.incall_conference_title, n)
        else getString(com.ogneliss.dialer.R.string.incall_conference)
    } else CallDetailsCompat.displayLabel(
        call.details,
        getString(com.ogneliss.dialer.R.string.call_unknown_number)
    )

    /** Уведомление разговора по [call]; то же самое второй раз не ставится. */
    private fun postOngoing(call: Call) {
        val label = ongoingLabel(call)
        val connectedAt = call.details?.connectTimeMillis ?: 0L
        val signature = "$label|$connectedAt"
        if (signature == shownOngoing) return
        shownOngoing = signature
        CallForegroundService.start(
            this, label,
            if (CallManager.isConference(call)) null else CallDetailsCompat.number(call.details),
            incoming = false,
            connectedAt = connectedAt
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /*
     * @Suppress("DEPRECATION") — про call.state.
     *
     * Замена, Call.getDetails().getState(), появилась только в API 31,
     * а minSdk у нас 26: ветка со старым свойством нужна в любом случае.
     * Подавление стоит на функции, а не на файле, чтобы новые
     * устаревшие вызовы в других местах по-прежнему были видны.
     */
    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.service = this
        CallManager.add(call)

        /*
         * КОНФЕРЕНЦИЯ — НЕ НОВЫЙ ВЫЗОВ (2.4.3). Её создаёт сеть, когда две
         * линии объединили: номера и направления у неё нет. Было: она шла
         * путём входящего — «Неизвестный номер» в уведомлении, отметка
         * «входящий» для пропущенных. Экран вызова уже открыт; уведомление —
         * по текущей линии.
         */
        if (CallManager.isConference(call)) {
            Logger.i(TAG, "Конференция собрана")
            followCurrentLine()
            return
        }
        // Новый вызов ставит своё уведомление (набор, входящий) — прежнего
        // разговора в шторке больше нет: вернуться к нему — пересобрать
        shownOngoing = null

        // Исходящий звонок — человек у телефона: пропущенные увидены,
        // счётчик на значке сбрасываем (MissedCallsNotifier)
        @Suppress("DEPRECATION")
        val outgoing = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            call.details.callDirection == Call.Details.DIRECTION_OUTGOING
        } else {
            // 2.1.0: и «выбор SIM» — исходящий (CallScreenRules.outgoingOnAdd)
            CallScreenRules.outgoingOnAdd(call.state)
        }
        if (outgoing) {
            MissedCallsNotifier.clear(this)
            outgoingCalls += call
        }
        // Автодозвон: своя попытка идёт, чужой вызов — сеанс окончен
        AutoRedial.onCallAdded(CallDetailsCompat.number(call.details), outgoing)
        // Имя от оператора приходит с входящим — пишем сразу: системный журнал
        // запишет вызов раньше, чем служба узнает о его конце
        if (!outgoing) rememberNetworkName(call.details)

        /*
         * ОТСЛЕЖИВАНИЕ СОСТОЯНИЯ ВЫЗОВА.
         *
         * ── Зачем ──────────────────────────────────────────────────────────
         * Входящий мы больше не открываем сами — это делает уведомление.
         * Но если человек ответил ПРЯМО ИЗ уведомления, не разворачивая
         * его, экран вызова не появится вовсе: разговор идёт, а управлять
         * им нечем — ни отключить микрофон, ни включить громкую связь.
         *
         * Здесь мы ловим переход из «звонит» в «разговор» и открываем
         * экран. Для человека это выглядит естественно: ответил —
         * появился экран разговора.
         *
         * ── Почему обработчик снимается ────────────────────────────────────
         * Call живёт дольше нашей подписки только формально: система
         * держит объект до конца вызова. Не сняв обработчик, мы оставили
         * бы ссылку на службу после её остановки.
         */
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) {
                // Перестал звонить — подсветка экрана больше не нужна (1.8.1)
                if (state != Call.STATE_RINGING) IncomingScreen.ringingStopped()
                if (state == Call.STATE_ACTIVE) {
                    // Автодозвон (1.8.0): попытка соединилась — сеанс окончен
                    AutoRedial.onCallConnected(CallDetailsCompat.number(c.details))
                    // Вызываемый снял трубку — короткая вибрация (2.5.0, Prefs.vibrateOnConnect)
                    if (outgoing && vibrateOnConnect) vibrateConnected()
                    /*
                     * Уведомление входящего снимается при ответе.
                     *
                     * Раньше оно убиралось только в конце вызова —
                     * то есть висело весь разговор с кнопками
                     * «Ответить» и «Отклонить». Нажатие на «Ответить»
                     * во время разговора ничего бы не сделало,
                     * а «Отклонить» сбросило бы идущий звонок:
                     * подпись обещает не то, что произойдёт.
                     */
                    /*
                     * Уведомление сервиса переключается на обычное.
                     *
                     * Снимать его нельзя: оно держит передний сервис,
                     * а тот — процесс на время разговора. Поэтому
                     * не отменяем, а заменяем — тем же идентификатором.
                     */
                    // Имя берём из самого вызова: переменная выше
                    // объявляется позже этого обработчика
                    // Ошибку запуска служба разбирает сама: экран разговора
                    // откроется в любом случае (2.1.0). Таймер — от соединения;
                    // подпись — по текущей линии (2.4.3, postOngoing)
                    shownOngoing = null
                    postOngoing(CallManager.primary ?: c)
                    openCallScreen()
                    c.unregisterCallback(this)
                }
            }
        })

        // contactDisplayName появился только в API 30, прямое обращение
        // роняет приложение на старых устройствах при входящем вызове
        val name = CallDetailsCompat.displayLabel(
            call.details,
            getString(com.ogneliss.dialer.R.string.call_unknown_number)
        )
        /*
         * ТИХИЙ ВХОДЯЩИЙ (1.8.0, тихие часы для незнакомых).
         *
         * Проверка вызовов решила «без звука» и оставила метку (QuietCallGate).
         * Тогда экран вызова НЕ открываем: он зажёг бы экран посреди ночи —
         * ровно то, от чего тихие часы защищают. Уведомление — тихое,
         * с кнопками ответа; звонок системы глушим сами: без роли
         * определителя проверка сделать этого не может.
         *
         * Ответ из уведомления откроет экран разговора обычным путём —
         * обработчиком выше, по переходу в «разговор».
         */
        @Suppress("DEPRECATION")
        val quietMarked = !outgoing && call.state == Call.STATE_RINGING &&
            QuietCallGate.take(PhoneFormatter.toE164(CallDetailsCompat.number(call.details).orEmpty()))
        /*
         * Второй входящий во время разговора — не тихий: человек не спит,
         * он у телефона. А тихое уведомление заняло бы место уведомления
         * разговора — с кнопками «Ответить» и «Отклонить» вместо «Завершить»,
         * и так и осталось бы после второго звонка. Метку при этом всё равно
         * забираем: чужая метка не должна дожить до следующего звонка.
         */
        val quiet = quietMarked && CallManager.calls.value.none { it != call }
        // Каким был входящий — для уведомления о пропущенном (QuietCallGate)
        if (!outgoing) QuietCallGate.noteIncoming(quiet)
        val number = CallDetailsCompat.number(call.details)
        // Каналы — до первого уведомления и до проверки плашки (важность канала)
        runCatching { CallNotifications.ensureChannels(this) }
            .onFailure { Logger.w(TAG, "Каналы уведомлений не созданы", it) }
        /*
         * ПРОПУЩЕННЫЕ, КОГДА НАШИ УВЕДОМЛЕНИЯ ЗАПРЕЩЕНЫ (2.1.0) — до конца
         * звонка: Telecom решает, кому рисовать пропущенный, в момент сброса.
         * Разбор — MissedCallsNotifier.syncReceiver.
         */
        if (!outgoing) appScope.launch(Dispatchers.IO) { MissedCallsNotifier.syncReceiver(applicationContext) }
        if (quiet) {
            Logger.i(TAG, "Тихий входящий: экран не открываем, звонок глушим")
            IncomingScreen.markQuiet(call)
            CallForegroundService.start(this, name, number, incoming = true, quiet = true)
            silenceRinger()
            return
        }

        /*
         * ВХОДЯЩИЙ ПРИ ВЫКЛЮЧЕННОМ ИЛИ ЗАБЛОКИРОВАННОМ ЭКРАНЕ (1.8.1).
         *
         * Отзыв: «при звонке не загорается экран». Окно вызова просит
         * систему включить экран само, но часть прошивок эту просьбу
         * не выполняет или молча не даёт открыть окно из фона. Поэтому
         * экран выключен — включаем его сами, а не показалось окно
         * за 1,5 с — уведомление с кнопками ответа (IncomingScreen.checkShown).
         *
         * ── Без всплывающего уведомления сразу (1.8.3) ─────────────────────
         * В 1.8.1–1.8.2 при выключенном или заблокированном экране сразу
         * ставилось всплывающее уведомление входящего. Жалоба: «сначала
         * сигнал сообщения, потом звонок» — и в 1.8.2, где у канала
         * входящих звука уже нет: часть прошивок озвучивает всплывающее
         * уведомление сама. Теперь в обычном случае — только тихое
         * уведомление вызова, как до 1.8.1; всплывающее — лишь когда окно
         * вызова действительно не показалось.
         */
        @Suppress("DEPRECATION")
        val ringing = !outgoing && call.state == Call.STATE_RINGING
        val screenOff = ringing && IncomingScreen.screenOff(this)
        val locked = ringing && IncomingScreen.locked(this)
        if (!ringing) Logger.i(TAG, "Вызов: открываю экран")
        else Logger.i(TAG, "Входящий: экран ${if (screenOff) "выключен" else "включён"}${if (locked) ", заблокирован" else ""}")
        if (ringing && !com.ogneliss.dialer.util.IncomingCallAccess.fullScreenAllowed(this)) Logger.w(TAG, "Полноэкранные уведомления запрещены — на блокировке входящий будет только плашкой")
        if (ringing) IncomingScreen.startRinging(this)
        // Во время разговора экран погашен датчиком у уха — не будим (2.2.3, IncomingScreen.talkingNow);
        // первый вызов на удержании (телефон отложен) — будим, как обычно (2.3.0)
        if (screenOff && !IncomingScreen.talkingNow()) IncomingScreen.wake(this, number)

        /*
         * ИСХОДЯЩИЙ И ИДУЩИЙ ВЫЗОВ — СРАЗУ ЭКРАН (служба и уведомление
         * «Текущий вызов»). Запуск экрана не зависит от службы (2.1.0):
         * не запустилась она — экран вызова всё равно открывается.
         */
        if (!ringing) {
            CallForegroundService.start(this, name, number, incoming = false)
            openCallScreen()
            return
        }

        /*
         * ВХОДЯЩИЙ ПЛАШКОЙ СВЕРХУ (2.1.0, настройка «Входящий поверх
         * приложений»). Пользователь, 07.11.2026: «а такое — компактное окно
         * сверху с „Ответить“ и „Отклонить“ — вы специально не делали?».
         * Телефон разблокирован и экран горит — только уведомление-плашка
         * с кнопками (CallStyle), экран вызова — касанием по плашке.
         * Погасший или заблокированный экран — во весь экран: иначе ответить
         * нечем.
         *
         * Безопасно само (2.1.0): плашка — только где она наверняка всплывёт
         * и продержится, пока звонит (IncomingScreen.bannerSafe). Включена
         * по умолчанию (автор, 08.11.2026): так ведут себя стандартные
         * звонилки. Экран погас и его включили, пока звонит, — экран вызова
         * целиком (IncomingScreen.onScreenOn).
         */
        if (!screenOff && !locked && incomingBannerWanted() && IncomingScreen.bannerSafe(this)) {
            Logger.i(TAG, "Входящий: плашкой сверху (настройка)")
            IncomingScreen.bannerOnly = true
            /*
             * Второй входящий, а экран вызова на виду (2.4.3): там уже плашка
             * второго вызова с тремя кнопками — всплывающее уведомление поверх
             * неё дублировало бы кнопки и закрывало экран. Тихо, в шторке.
             */
            IncomingScreen.notify(this, call, alert = !IncomingScreen.visible)
            return
        }
        IncomingScreen.bannerOnly = false

        /*
         * Уведомление входящего — всегда с «Ответить» и «Отклонить» (2.1.0):
         * тихое, пока открывается экран вызова; всплывающее сразу — на MIUI,
         * где запрет подтверждён и экран вызова почти наверняка не покажется.
         * Было: тихим вариантом здесь ставился «Текущий вызов» с таймером
         * и «Завершить» — без кнопки ответа.
         */
        val blocked = com.ogneliss.dialer.util.IncomingCallAccess.likelyBlocked(this)
        if (blocked) Logger.i(TAG, "MIUI запрещает экран вызова — кнопки ответа сразу")
        IncomingScreen.notify(this, call, alert = blocked)

        /*
         * ЭКРАН ОТКРЫВАЕТСЯ НАПРЯМУЮ — ВКЛЮЧАЯ ВХОДЯЩИЙ.
         *
         * Приложение — диалер по умолчанию, и система разрешает ему
         * открывать экран вызова из фона (служба вызовов, к которой
         * подключилась система, — исключение из запрета). Так делают
         * системные диалеры. Активность помечена setShowWhenLocked
         * и setTurnScreenOn: разбудит экран и покажется поверх блокировки.
         * Полноэкранное намерение уведомления службы система разворачивает
         * не всегда — поэтому не полагаемся на него одно.
         *
         * Уведомление при этом остаётся: оно держит передний сервис
         * и нужно, когда человек ушёл с экрана вызова.
         *
         * ── «Запуск удался» ещё не значит «экран показан» (1.8.1) ──────────
         * startActivity не бросает ошибку, когда прошивка молча запрещает
         * окно из фона. Решает само окно — получило ли фокус
         * (IncomingScreen.visible); нет за SHOW_TIMEOUT_MS — уведомление
         * с кнопками всплывает (IncomingScreen.checkShown).
         */
        if (!openCallScreen()) {
            IncomingScreen.launchFailed(this, call)
            return
        }
        watchCallScreen()
    }

    /** Открыть экран вызова; false — запуск бросил ошибку (она в журнале). */
    /*
     * НАСТРОЙКА ПЛАШКИ — ВЫБОР ЧЕЛОВЕКА, А НЕ НАЧАЛЬНАЯ КОПИЯ (2.1.0).
     *
     * Система создаёт службу вызовов вместе с первым вызовом, и onCallAdded
     * приходит раньше, чем DataStore отдаёт значение: решала бы начальная
     * копия, и выключенная плашка нет-нет да появлялась бы. Пока значения
     * нет — ждём его, но не дольше BANNER_PREF_WAIT_MS (обычно это единицы
     * миллисекунд); не дождались — значение по умолчанию.
     */
    private fun incomingBannerWanted(): Boolean {
        if (incomingBannerKnown) return incomingBannerOn
        return runCatching {
            runBlocking { withTimeoutOrNull(BANNER_PREF_WAIT_MS) { prefs.incomingBanner.first() } }
        }.onFailure { Logger.w(TAG, "Настройка плашки не прочиталась", it) }
            .getOrNull() ?: incomingBannerOn
    }

    private fun openCallScreen(): Boolean = runCatching {
        startActivity(
            Intent(this, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        true
    }.getOrElse {
        Logger.w(TAG, "Не удалось открыть экран вызова", it)
        false
    }

    /** Через SHOW_TIMEOUT_MS — показался ли экран вызова (IncomingScreen.checkShown). */
    private fun watchCallScreen() {
        serviceScope.launch {
            delay(IncomingScreen.SHOW_TIMEOUT_MS)
            IncomingScreen.checkShown(this@OgnelissInCallService)
        }
    }

    /*
     * ЗАГЛУШИТЬ ЗВОНОК СИСТЕМЫ — НЕСКОЛЬКО РАЗ ПОДРЯД.
     *
     * Мелодию играет Telecom (разбор в шапке класса), а silenceRinger
     * гасит только уже начавшуюся — так прямо сказано в описании метода:
     * вызов приходит сюда раньше, чем мелодия зазвучит. Поэтому три
     * попытки за первую секунду и ещё две до четвёртой — пока вызов ещё
     * звонит: на части прошивок мелодия вступает после вибрации. Диалеру
     * по умолчанию этот вызов разрешён; если роль определителя уже
     * заглушила звонок, повтор безвреден.
     *
     * @SuppressLint("MissingPermission"): метод помечен MODIFY_PHONE_STATE —
     * системным разрешением, которого у приложений нет. Telecom пускает
     * без него диалер по умолчанию (isPrivilegedDialerCalling), а сюда
     * система приходит только к нему. Отказ — SecurityException — ловим.
     */
    @android.annotation.SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun silenceRinger() {
        val tm = getSystemService(android.telecom.TelecomManager::class.java) ?: return
        serviceScope.launch {
            for (wait in SILENCE_RETRY_MS) {
                delay(wait)
                if (CallManager.calls.value.none { it.state == Call.STATE_RINGING }) break
                runCatching { tm.silenceRinger() }
                    .onFailure { Logger.w(TAG, "Не удалось заглушить звонок", it) }
            }
        }
    }

    /**
     * ВИБРАЦИЯ ПРИ ОТВЕТЕ (2.5.0, форум: «добавить бы вибрацию при снятии трубки
     * вызываемым абонентом»). Одна короткая — как касание: у уха её чувствуешь,
     * а разговору она не мешает. Нет вибромотора или система отказала — молча
     * (в журнал): это удобство, не критично.
     */
    @Suppress("DEPRECATION")
    private fun vibrateConnected() {
        runCatching {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
            } else getSystemService(android.os.Vibrator::class.java)
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(CONNECT_VIBRATION_MS, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }.onFailure { Logger.w(TAG, "Вибрация при ответе не выполнена", it) }
    }

    /*
     * ИМЯ ОТ ОПЕРАТОРА — В БАЗУ (1.8.0, «маркировка звонков»).
     *
     * Система держит имя, переданное сетью, только пока идёт вызов, и в журнал
     * его не пишет. Запоминаем сами, чтобы журнал и карточка показали его
     * и через неделю. Разбор — data/NetworkNames.kt.
     */
    private fun rememberNetworkName(details: Call.Details?) {
        val name = CallDetailsCompat.networkName(details) ?: return
        val e164 = PhoneFormatter.toE164(CallDetailsCompat.number(details).orEmpty())
        if (e164.isBlank()) return
        appScope.launch(Dispatchers.IO) { networkNames.remember(e164, name) }
    }

    /** Можно ли добавить вызов — плитка «Добавить» на экране вызова (2.3.0, CallManager.canAddCall). */
    override fun onCanAddCallChanged(canAddCall: Boolean) {
        super.onCanAddCallChanged(canAddCall)
        CallManager.canAddCallChanged()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        // Кто и почему завершил, куда шёл звук — разбор в CallDiagnostics
        CallDiagnostics.logEnded(call, CallManager.audioState.value)
        val details = call.details
        rememberNetworkName(details)

        // Сброшенный входящий: предлагаем создать напоминание.
        // Именно «сбросил на бегу и забыл» — сценарий, ради которого
        // функция и нужна
        // Направление вызова доступно в Details только с API 29;
        // ниже определяется по состоянию вызова
        val wasOutgoing = outgoingCalls.remove(call) || CallDetailsCompat.isOutgoing(details, call)
        /*
         * СБРОШЕН — ЭТО «ОТКЛОНИТЬ», А НЕ «НЕ УСПЕЛ» (1.8.0).
         *
         * Было: любой входящий без разговора — и пропущенный тоже — давал
         * «Перезвонить?», а настройка «Предлагать напоминание после сброса»
         * нигде не читалась: выключить уведомление было нельзя. У пропущенного
         * своё уведомление (MissedCallsNotifier), и ночью, в тихие часы,
         * второе со звуком будило бы. Теперь — только отклонённый человеком
         * (REJECTED; на части прошивок — LOCAL) и только при включённой настройке.
         */
        val cause = details?.disconnectCause?.code ?: RedialPolicy.CAUSE_UNKNOWN
        // Конференция (2.4.3): итоги разговора, «Перезвонить?» и автодозвон —
        // у её участников, у неё самой номера нет
        val conference = CallManager.isConference(call)
        val wasRejected = !conference && details != null &&
            !wasOutgoing &&
            details.connectTimeMillis <= 0 &&
            (cause == RedialPolicy.CAUSE_REJECTED || cause == RedialPolicy.CAUSE_LOCAL) &&
            callbackSuggestionOn

        // Итоги разговора — для экрана заметки
        if (details != null && !conference) {
            val connectTime = details.connectTimeMillis
            val duration = if (connectTime > 0)
                (System.currentTimeMillis() - connectTime) / 1000 else 0L

            postCall.onCallCompleted(
                number = details.handle?.schemeSpecificPart,
                displayName = CallDetailsCompat.contactDisplayName(details),
                durationSec = duration,
                type = if (wasOutgoing)
                    com.ogneliss.dialer.data.CallType.OUTGOING
                else com.ogneliss.dialer.data.CallType.INCOMING
            )
        }

        if (wasRejected) {
            val rejectedNumber = CallDetailsCompat.number(details)
            val rejectedName = CallDetailsCompat.contactDisplayName(details)

            /*
             * Заметка читается из базы, поэтому в сопрограмме.
             *
             * Область не привязана к службе: она живёт ровно до конца
             * вызова, а мы как раз в этот момент. Уведомление показывается
             * приложением, а не службой, и переживёт её остановку.
             *
             * Ошибку чтения глотаем сознательно: предложение перезвонить
             * важнее заметки, и без неё оно должно выйти всё равно.
             */
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val note = runCatching {
                    notesRepo.get(PhoneFormatter.toE164(rejectedNumber.orEmpty()))?.note
                }.getOrNull()

                com.ogneliss.dialer.background.CallbackSuggestion.show(
                    applicationContext, rejectedNumber, rejectedName, note
                )
            }
        }

        // Постобработка уходит в WorkManager: сервис живёт ровно до конца
        // вызова, и система вправе убить процесс сразу после
        com.ogneliss.dialer.background.CallEndWorker.enqueue(
            this, call.details?.handle?.schemeSpecificPart
        )

        /*
         * Автодозвон (1.8.0) — до удаления из списка: экран вызова
         * закрывается, когда список пуст и сеанса нет, и сеанс должен
         * появиться раньше, чем опустеет список. Разбор — в AutoRedial.
         */
        if (!conference) AutoRedial.onCallEnded(
            context = this,
            number = CallDetailsCompat.number(details),
            displayName = CallDetailsCompat.contactDisplayName(details),
            account = details?.accountHandle,
            outgoing = wasOutgoing,
            connected = (details?.connectTimeMillis ?: 0L) > 0L,
            cause = cause,
            otherCalls = CallManager.calls.value.any { it != call }
        )
        CallManager.remove(call)
        // Звонков больше нет — подсветку и отметки входящего снимаем (1.8.1)
        IncomingScreen.forget(call)

        /*
         * Отдельное уведомление больше не снимаем: его нет.
         *
         * Единственное уведомление вызова принадлежит переднему сервису
         * и уходит вместе с ним — ниже, когда закончился последний
         * вызов. Снять его отдельно значило бы остановить сервис
         * раньше времени.
         */
        if (CallManager.calls.value.isEmpty()) {
            CallManager.service = null
            CallForegroundService.stop(this)
        } else {
            refreshNotificationFor(CallManager.primary)
        }
    }

    /*
     * УВЕДОМЛЕНИЕ ПОСЛЕ УХОДА ОДНОГО ИЗ ВЫЗОВОВ (2.1.0).
     *
     * Было: уведомление переднего сервиса пересобиралось только при
     * появлении вызова и при ответе. Отклонили второй входящий или
     * вышел участник конференции — в шторке оставались чужое имя или
     * кнопки «Ответить/Отклонить» от уже ушедшего звонка. Стало: после
     * удаления вызова уведомление строится заново по оставшемуся
     * текущему вызову; таймер — от его соединения, а не с нуля.
     */
    @Suppress("DEPRECATION")
    private fun refreshNotificationFor(call: Call?) {
        call ?: return
        // Остался звонящий — входящее с кнопками: всплывающее, если экрана
        // вызова не видно (2.1.0, IncomingScreen.notify)
        if (call.state == Call.STATE_RINGING) {
            shownOngoing = null
            IncomingScreen.notify(this, call, alert = !IncomingScreen.visible)
            return
        }
        // Подпись (у конференции — по числу участников) и таймер — postOngoing (2.4.3)
        postOngoing(call)
    }

    /*
     * УСТАРЕВШИЙ МЕТОД ОСТАЁТСЯ НАМЕРЕННО.
     *
     * ── Почему не переходим на новый ───────────────────────────────────────
     * Замена — onCallEndpointChanged — появилась в Android 14. Наш minSdk
     * 26: на телефонах старше система вызовет только этот метод,
     * и без него звук не переключится вовсе.
     *
     * Правильный переход означает две реализации сразу и выбор
     * по версии — работа с проверкой на устройствах обеих эпох.
     * Пока метод работает везде, менять его перед релизом незачем.
     *
     * @Deprecated здесь — не признак заброшенности, а требование
     * компилятора: переопределяя устаревшее, надо это признать.
     */
    /*
     * Метод объявлен устаревшим начиная с Android 14, но остаётся
     * единственным способом узнать маршрут звука на API 26–33.
     * Подавление точечное: убрать его можно будет вместе с подъёмом
     * minSdk до 34.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Replaced by onCallEndpointChanged in Android 14, still needed for API 26-33")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.updateAudio(audioState)
    }
}

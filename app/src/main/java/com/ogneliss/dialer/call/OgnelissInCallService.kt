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
import kotlinx.coroutines.launch
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
        const val WHAT_INCOMING = "показ уведомления о входящем"

        /**
         * Паузы между попытками заглушить звонок: сразу, через 0,3; 0,9; 1,9
         * и 3,9 с. Последние две — для прошивок, где мелодия начинается
         * не сразу, а после вибрации (разбор у silenceRinger).
         */
        val SILENCE_RETRY_MS = longArrayOf(0L, 300L, 600L, 1_000L, 2_000L)
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

        // Исходящий звонок — человек у телефона: пропущенные увидены,
        // счётчик на значке сбрасываем (MissedCallsNotifier)
        @Suppress("DEPRECATION")
        val outgoing = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            call.details.callDirection == Call.Details.DIRECTION_OUTGOING
        } else {
            call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING
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
                if (state == Call.STATE_ACTIVE) {
                    // Автодозвон (1.8.0): попытка соединилась — сеанс окончен
                    AutoRedial.onCallConnected(CallDetailsCompat.number(c.details))
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
                    val label = CallDetailsCompat.displayLabel(
                        c.details,
                        getString(com.ogneliss.dialer.R.string.call_unknown_number)
                    )
                    CallForegroundService.start(
                        this@OgnelissInCallService, label, incoming = false
                    )

                    runCatching {
                        startActivity(
                            Intent(this@OgnelissInCallService, InCallActivity::class.java)
                                .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                )
                        )
                    }.onFailure {
                        Logger.w(TAG, "Не удалось открыть экран разговора", it)
                    }
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
        if (quiet) {
            Logger.i(TAG, "Тихий входящий: экран не открываем, звонок глушим")
            CallNotifications.ensureChannels(this)
            CallForegroundService.start(this, name, incoming = true, quiet = true)
            silenceRinger()
            return
        }

        // Foreground-сервис держит процесс живым на всё время вызова
        /*
         * Сервис запускается обычным уведомлением.
         *
         * Вид уточняется ниже, когда станет известно, открылся ли экран
         * вызова: если да — кнопки в уведомлении лишние.
         */
        CallForegroundService.start(this, name, incoming = false)


        /*
         * Отдельного уведомления входящего больше нет.
         *
         * Его показывал этот блок, и оно висело ВТОРЫМ рядом
         * с уведомлением переднего сервиса: человек видел
         * «Входящий вызов» и «Текущий вызов» об одном звонке.
         *
         * Хуже того, система урезала его до строчки без кнопок —
         * CallStyle разворачивается только у уведомления переднего
         * сервиса с типом PHONE_CALL.
         *
         * Теперь сервис сам показывает нужное уведомление: с кнопками
         * при входящем, обычное при разговоре. Одно на весь вызов.
         */
        CallNotifications.ensureChannels(this)
        /*
         * ВХОДЯЩИЙ ОТКРЫВАЕТ ЭКРАН ЧЕРЕЗ УВЕДОМЛЕНИЕ, А НЕ НАПРЯМУЮ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ЧТО БЫЛО НЕ ТАК
         * ══════════════════════════════════════════════════════════════════
         * startActivity вызывался при любом состоянии, включая входящий.
         * А уведомление входящего уже содержит full-screen intent,
         * который открывает тот же экран.
         *
         * В итоге показывались оба: система рисовала уведомление сверху
         * с кнопками «Ответить» и «Отклонить», а под ним открывался
         * экран вызова со своими такими же кнопками. Две пары кнопок
         * на одном экране — человеку неясно, какая настоящая.
         *
         * ── Почему решение принимает система ───────────────────────────────
         * Она знает то, чего не знаем мы: заблокирован ли экран, идёт ли
         * полноэкранная игра, включён ли режим «не беспокоить».
         *
         * При заблокированном экране full-screen intent открывает экран
         * вызова во весь экран — иначе ответить было бы нечем.
         * При разблокированном показывает уведомление сверху, не отнимая
         * экран у того, чем человек занят.
         *
         * Это правильное поведение: звонок не должен выбрасывать
         * из карты посреди поворота или из видеозвонка.
         *
         * ── Исходящие и активные открываем сами ────────────────────────────
         * Там уведомления с full-screen intent нет, и без startActivity
         * экран не появится вовсе.
         */
        /*
         * ЭКРАН ОТКРЫВАЕТСЯ НАПРЯМУЮ — ВКЛЮЧАЯ ВХОДЯЩИЙ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ Я ВЕРНУЛ ПРЯМОЙ ЗАПУСК
         * ══════════════════════════════════════════════════════════════════
         * Раньше здесь стояло условие «кроме входящего»: считалось,
         * что экран откроет полноэкранное намерение уведомления.
         *
         * Оно и открывало — пока уведомление было отдельным. После
         * объединения с уведомлением переднего сервиса система перестала
         * разворачивать намерение: у служебных уведомлений оно
         * срабатывает не всегда, и это не наша прихоть, а поведение
         * платформы.
         *
         * Итог: на заблокированном экране не открывалось ничего.
         * Разговор шёл, ответить было нечем.
         *
         * ── Почему прямой запуск здесь допустим ────────────────────────────
         * Приложение — диалер по умолчанию, и система разрешает ему
         * открывать экран вызова из фона. Именно так делают системные
         * диалера.
         *
         * Активность помечена setShowWhenLocked и setTurnScreenOn:
         * она разбудит экран и покажется поверх блокировки.
         *
         * ── Уведомление при этом остаётся ──────────────────────────────────
         * Оно нужно, когда человек ушёл с экрана вызова — свернул
         * или переключился. И оно же держит передний сервис.
         */
        val screenOpened = runCatching {
            startActivity(
                Intent(this, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            true
        }.getOrElse {
            Logger.w(TAG, "Не удалось открыть экран вызова", it)
            false
        }

        /*
         * УВЕДОМЛЕНИЕ С КНОПКАМИ — ТОЛЬКО ЕСЛИ ЭКРАН НЕ ОТКРЫЛСЯ.
         *
         * ══════════════════════════════════════════════════════════════════
         * ПОЧЕМУ ОНО ВЫСКАКИВАЛО ПОВЕРХ
         * ══════════════════════════════════════════════════════════════════
         * Уведомление входящего показывалось всегда — и система выводила
         * его шторкой поверх экрана вызова, который к тому моменту уже
         * открылся сам.
         *
         * Получалось две пары кнопок «Ответить» и «Отклонить»: одна
         * в шторке, другая на экране под ней. Человеку неясно, какая
         * настоящая, и половина экрана закрыта.
         *
         * ── Когда уведомление всё же нужно ─────────────────────────────────
         * Если запуск экрана не удался — прошивка запретила открывать
         * активность из фона, такое бывает. Тогда уведомление
         * с кнопками остаётся единственным способом ответить,
         * и показать его обязательно.
         *
         * ── Почему сервису всё равно нужно уведомление ─────────────────────
         * Передний сервис без уведомления существовать не может: система
         * его остановит. Поэтому показываем обычное — с именем
         * и длительностью, без кнопок и без всплытия.
         */
        CallForegroundService.start(
            this,
            name,
            // call.state объявлено устаревшим; разбор — в аннотации
            // над onCallAdded
            incoming = call.state == Call.STATE_RINGING && !screenOpened
        )
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
        val wasRejected = details != null &&
            !wasOutgoing &&
            details.connectTimeMillis <= 0 &&
            (cause == RedialPolicy.CAUSE_REJECTED || cause == RedialPolicy.CAUSE_LOCAL) &&
            callbackSuggestionOn

        // Итоги разговора — для экрана заметки
        if (details != null) {
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
        AutoRedial.onCallEnded(
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
        }
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

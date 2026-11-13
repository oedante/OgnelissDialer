package com.ogneliss.dialer.call

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.telecom.Call
import android.telecom.CallAudioState
import com.ogneliss.dialer.log.Logger

/**
 * АВТОМАТИЧЕСКОЕ ПЕРЕКЛЮЧЕНИЕ НА ГРОМКУЮ СВЯЗЬ.
 *
 * Назначение: подписаться на датчики, скормить показания политике
 * и применить её решение.
 *
 * ── Разрешений не требуется ────────────────────────────────────────────────
 * Акселерометр и датчик приближения не относятся к чувствительным
 * и доступны без запроса. Это важно: диалеру, который просит доступ
 * к датчикам, пользователь верит меньше.
 *
 * ── Частота опроса ─────────────────────────────────────────────────────────
 * SENSOR_DELAY_UI, а не FASTEST. Нам не нужна игровая частота: смена
 * положения телефона — событие в масштабе секунд. Высокая частота
 * заметно расходует батарею, а разговор может длиться час.
 *
 * ── Учёт ручного переключения ──────────────────────────────────────────────
 * Если пользователь сам нажал кнопку динамика, датчики замолкают
 * до конца разговора. Иначе получилось бы, что приложение спорит
 * с пользователем: он включил громкую связь, поднёс телефон к уху,
 * чтобы что-то сказать, — и связь выключилась против его воли.
 */
class SmartSpeakerController(
    private val context: Context,
    private val onRouteChange: (route: Int) -> Unit
) : SensorEventListener {

    private companion object { const val TAG = "SmartSpeaker" }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var accelerationZ = 0f
    private var proximityNear = false

    /** Когда текущее сочетание показаний стало устойчивым. */
    private var stableSince = 0L
    private var lastDecisionInput: Pair<Boolean, Boolean>? = null

    /** Пользователь вмешался вручную — датчики больше не командуют. */
    @Volatile
    var manualOverride: Boolean = false
        private set

    private var speakerOn = false

    /*
     * ЗАПРОШЕННЫЙ, НО ЕЩЁ НЕ ПРИМЕНЁННЫЙ МАРШРУТ (1.2.4).
     *
     * Лог 30.09.2026: «Телефон положили — включаю громкую связь» дважды
     * за 59 мс. Переключение звука система выполняет не мгновенно:
     * между запросом и новым состоянием приходило обновление со старым
     * маршрутом (разговорный динамик), updateCallState сбрасывал
     * speakerOn обратно в false — и следующее показание датчика
     * включало громкую связь второй раз. Безвредно (запрос тот же),
     * но при неудаче могло бы «качаться» туда-обратно.
     *
     * Теперь на время ожидания (до 1,5 с) обновления со старым
     * маршрутом не перебивают решение.
     */
    private var pendingRoute: Int? = null
    private var pendingUntil = 0L
    private var headsetConnected = false
    private var callActive = false
    private var running = false

    /** Есть ли на устройстве нужные датчики. */
    fun isSupported(): Boolean = accelerometer != null && proximity != null

    fun start() {
        if (running) return
        if (!isSupported()) {
            Logger.i(TAG, "Датчики недоступны, авто-динамик выключен")
            return
        }
        // SENSOR_DELAY_UI: смена положения телефона — событие в масштабе
        // секунд, игровая частота только жгла бы батарею весь разговор
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager?.registerListener(this, proximity, SensorManager.SENSOR_DELAY_UI)
        running = true
        Logger.d(TAG, "Слежение за датчиками включено")
    }

    fun stop() {
        if (!running) return
        sensorManager?.unregisterListener(this)
        running = false
        manualOverride = false
        lastDecisionInput = null
        Logger.d(TAG, "Слежение за датчиками выключено")
    }

    /**
     * Вызывается при изменении состояния вызова и аудиомаршрута.
     *
     * @Suppress("DEPRECATION") — про Call.state, как в CallManager:
     * замена появилась в API 31 при minSdk 26.
     */
    @Suppress("DEPRECATION")
    fun updateCallState(call: Call?, audioState: CallAudioState?) {
        callActive = call?.state == Call.STATE_ACTIVE
        val route = audioState?.route
        val pending = pendingRoute
        if (pending != null) {
            if (route == pending || System.currentTimeMillis() > pendingUntil) {
                pendingRoute = null
            } else {
                // Старый маршрут, пока система переключает, — не перебивает
                // наше решение (разбор у pendingRoute)
                return
            }
        }
        speakerOn = route == CallAudioState.ROUTE_SPEAKER
        headsetConnected = audioState?.route == CallAudioState.ROUTE_BLUETOOTH ||
            audioState?.route == CallAudioState.ROUTE_WIRED_HEADSET

        // Гарнитуру подключили посреди разговора — датчики больше не нужны
        if (headsetConnected && running) stop()
    }

    /** Пользователь нажал кнопку динамика — уступаем ему до конца вызова. */
    fun notifyManualToggle() {
        manualOverride = true
        Logger.i(TAG, "Пользователь переключил звук вручную, авто-режим уступает")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accelerationZ = event.values[2]
            Sensor.TYPE_PROXIMITY -> {
                // У датчиков разный максимум: у одних 1 см, у других 10.
                // Правило — как у самого Android (разбор у SmartSpeakerPolicy.isNear)
                proximityNear = SmartSpeakerPolicy.isNear(event.values[0], event.sensor.maximumRange)
            }
            else -> return
        }

        evaluate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun evaluate() {
        val now = System.currentTimeMillis()

        // Отслеживаем, изменилось ли качественное состояние.
        // Мелкие колебания акселерометра не сбрасывают таймер устойчивости
        val flatNow = accelerationZ >= SmartSpeakerPolicy.FLAT_THRESHOLD
        val currentInput = flatNow to proximityNear

        if (currentInput != lastDecisionInput) {
            lastDecisionInput = currentInput
            stableSince = now
        }

        val decision = SmartSpeakerPolicy.decide(
            SmartSpeakerPolicy.SensorState(
                accelerationZ = accelerationZ,
                proximityNear = proximityNear,
                stableForMs = now - stableSince,
                speakerOn = speakerOn,
                headsetConnected = headsetConnected,
                manualOverride = manualOverride,
                callActive = callActive
            )
        )

        when (decision) {
            SmartSpeakerPolicy.Route.SPEAKER -> {
                Logger.i(TAG, "Телефон положили — включаю громкую связь")
                speakerOn = true
                pendingRoute = CallAudioState.ROUTE_SPEAKER
                pendingUntil = System.currentTimeMillis() + PENDING_MS
                onRouteChange(CallAudioState.ROUTE_SPEAKER)
            }
            SmartSpeakerPolicy.Route.EARPIECE -> {
                Logger.i(TAG, "Телефон подняли — возвращаю разговорный динамик")
                speakerOn = false
                pendingRoute = CallAudioState.ROUTE_EARPIECE
                pendingUntil = System.currentTimeMillis() + PENDING_MS
                onRouteChange(CallAudioState.ROUTE_EARPIECE)
            }
            SmartSpeakerPolicy.Route.UNCHANGED -> Unit
        }
    }
}

/** Сколько ждать, пока система применит запрошенный маршрут звука. */
private const val PENDING_MS = 1_500L

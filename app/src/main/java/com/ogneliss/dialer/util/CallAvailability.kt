package com.ogneliss.dialer.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ogneliss.dialer.data.SimSlot

/**
 * МОЖНО ЛИ СЕЙЧАС ЗВОНИТЬ И ПИСАТЬ SMS (2.1.0).
 *
 * Автор, 07.11.2026: «если позвонить нельзя, кнопки звонка и отправки SMS
 * должны быть серыми». Было: зелёная трубка и синяя SMS горели и без
 * SIM-карты, и в режиме полёта — нажатие заканчивалось системной ошибкой.
 *
 * Нельзя, когда:
 *  • устройство без голосовой связи (планшет с данными) — звонки;
 *  • нет разрешения на звонки — звонки;
 *  • режим полёта — звонки и SMS;
 *  • ни одной активной SIM — звонки и SMS. Список карт читается только
 *    с разрешением «Телефон»: без него карт «нет», но это незнание, а не
 *    отсутствие, — тогда не гасим.
 *
 * ⚠️ Серый — только вид, касание остаётся: экстренный номер набирается
 * и без SIM, и в режиме полёта (система выключит его сама), а при
 * звонках по Wi-Fi режим полёта звонкам не мешает. Запрещать касание
 * значило бы однажды не дать вызвать скорую.
 */
@Immutable
data class CallAvailability(
    val canCall: Boolean = true,
    val canSms: Boolean = true
) {
    /** Цвет звонка: зелёный, а когда звонить нельзя — серый. */
    @Composable
    fun callTint(green: Color): Color = if (canCall) green else MaterialTheme.colorScheme.outline

    /** Цвет SMS: свой, а когда писать нельзя — серый. */
    @Composable
    fun smsTint(color: Color): Color = if (canSms) color else MaterialTheme.colorScheme.outline
}

val LocalCallAvailability = staticCompositionLocalOf { CallAvailability() }

/** Следит за режимом полёта и картами; пересчёт — при каждом изменении. */
@Composable
fun rememberCallAvailability(sims: List<SimSlot>): CallAvailability {
    val context = LocalContext.current
    var airplane by remember { mutableStateOf(isAirplane(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { airplane = isAirplane(c) }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val voice = remember(context) {
        context.getSystemService(TelephonyManager::class.java)?.isVoiceCapable ?: true
    }
    val callPermission = PermissionGuard.canCall(context)
    // Без разрешения «Телефон» список карт пуст всегда — это не «карт нет»
    val noSim = sims.isEmpty() && PermissionGuard.canReadPhoneState(context)
    return remember(airplane, voice, callPermission, noSim) {
        CallAvailability(
            canCall = voice && callPermission && !airplane && !noSim,
            canSms = !airplane && !noSim
        )
    }
}

private fun isAirplane(context: Context): Boolean =
    runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }.getOrDefault(false)

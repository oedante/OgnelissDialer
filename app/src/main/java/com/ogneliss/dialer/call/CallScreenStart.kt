package com.ogneliss.dialer.call

import com.ogneliss.dialer.settings.SwitchSetting
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.settings.AnswerMode
import com.ogneliss.dialer.settings.AvatarScope
import com.ogneliss.dialer.settings.CallPhotoSize
import com.ogneliss.dialer.settings.CallPhotoStyle
import com.ogneliss.dialer.settings.CarrierInfoMode
import com.ogneliss.dialer.settings.ContactNameFormat
import com.ogneliss.dialer.settings.Prefs
import com.ogneliss.dialer.settings.ThemeLook
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.settings.UiScale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ЭКРАН ВЫЗОВА — СРАЗУ В СВОЁМ ВИДЕ (2.9.7).
 *
 * Письмо пользователя, 24.11.2026: «экран текущего вызова открывается
 * с мельканием классической темы, а потом переходит в цвет, выбранный для
 * экрана вызова».
 *
 * ── Что было не так ────────────────────────────────────────────────────────
 * Экран вызова читал два десятка настроек вида потоками с начальными
 * значениями (collectAsStateWithLifecycle(initialValue = …)). Первый кадр
 * рисуется до того, как поток отдаст сохранённое значение, — то есть
 * со значениями по умолчанию: тема «Как в системе», облик «Классический»,
 * живой фон в тонах темы, без своего снимка. Через кадр-другой приходили
 * настоящие — и экран перекрашивался у человека на глазах. При холодном
 * запуске (входящий разбудил приложение) настройки читаются с диска,
 * и «чужой» вид держался заметно дольше. Свой снимок фона вдобавок
 * раскодировался уже после первого кадра — до него был виден живой фон.
 *
 * ── Как теперь ─────────────────────────────────────────────────────────────
 * Активность до setContent читает снимок настроек вида ([read]) и, если
 * у этого направления вызова свой фон, раскодирует его. Эти значения — начальные
 * у потоков: первый кадр сразу в своём виде, а дальше всё меняется живьём,
 * как раньше (настройку меняют, пока экран открыт).
 *
 * Ждать дольше [PREFS_BUDGET_MS] и [BACKGROUND_BUDGET_MS] нельзя: это главный
 * поток, а звонок должен появиться сразу. Не успели — как было, значения
 * по умолчанию и снимок после первого кадра; строка в журнале. Обычно
 * настройки уже в памяти (их читала служба вызовов) и чтение занимает
 * миллисекунды, снимок экрана раскодируется за десятки.
 */
internal data class CallScreenStart(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val uiScale: UiScale = UiScale.DEFAULT,
    val fontScale: Float = 1f,
    val themeLook: ThemeLook = ThemeLook(),
    val buttonLabels: Boolean = true,
    val callAnimation: Boolean = true,
    val ambientStyle: AmbientStyle = AmbientStyle.THEME,
    /** null — настройка не прочитана: фото не показывается ни так, ни так (как раньше). */
    val callPhotoStyle: CallPhotoStyle? = null,
    val callPhotoSize: CallPhotoSize = CallPhotoSize.LARGE,
    val hideContactNumber: Boolean = false,
    val statusLabels: Boolean = true,
    val callBgIncoming: Long = 0L,
    val callBgOutgoing: Long = 0L,
    val avatarNoPhoto: Long = 0L,
    val avatarUnknown: Long = 0L,
    val avatarScope: AvatarScope = AvatarScope.EVERYWHERE,
    val answerMode: AnswerMode = AnswerMode.TAP,
    val swapAnswer: Boolean = false,
    val nameFormat: ContactNameFormat = ContactNameFormat.GIVEN_FIRST,
    val carrierInfo: CarrierInfoMode = CarrierInfoMode.UNKNOWN_ONLY,
    val scamMemo: Boolean = true,
    /** Свой фон того направления, что на экране первым; null — нет или не успели. */
    val background: StartBackground? = null
) {
    companion object {
        private const val TAG = "CallScreenStart"

        /** Сколько ждать настроек на главном потоке. */
        const val PREFS_BUDGET_MS = 250L

        /** Сколько ждать свой снимок фона — отдельно: без него настройки всё равно нужны. */
        const val BACKGROUND_BUDGET_MS = 250L

        /**
         * Снимок до первого кадра. [incoming] — направление вызова на экране:
         * какой свой фон раскодировать заранее; null — не раскодировать.
         */
        fun read(context: Context, prefs: Prefs, incoming: Boolean?): CallScreenStart = runBlocking {
            val started = System.nanoTime()
            val base = withTimeoutOrNull(PREFS_BUDGET_MS) {
                runCatching { readPrefs(prefs) }
                    .onFailure { Logger.w(TAG, "Настройки вида не прочитаны", it) }
                    .getOrNull()
            }
            if (base == null) {
                Logger.i(TAG, "Настройки вида не успели за $PREFS_BUDGET_MS мс — первый кадр по умолчанию")
                return@runBlocking CallScreenStart()
            }
            val background = if (incoming == null) null else preloadBackground(context, base, incoming)
            val ms = (System.nanoTime() - started) / 1_000_000
            Logger.d(TAG, "Вид экрана вызова прочитан за $ms мс, свой фон — ${if (background != null) "да" else "нет"}")
            base.copy(background = background)
        }

        private suspend fun readPrefs(prefs: Prefs) = CallScreenStart(
            theme = prefs.theme.first(),
            dynamicColor = prefs.flow(SwitchSetting.DYNAMIC_COLOR).first(),
            uiScale = prefs.uiScale.first(),
            fontScale = prefs.fontScale.first(),
            themeLook = prefs.themeLook.first(),
            buttonLabels = prefs.flow(SwitchSetting.BUTTON_LABELS).first(),
            callAnimation = prefs.flow(SwitchSetting.CALL_ANIMATION).first(),
            ambientStyle = prefs.callAmbientStyle.first(),
            callPhotoStyle = prefs.callPhotoStyle.first(),
            callPhotoSize = prefs.callPhotoSize.first(),
            hideContactNumber = prefs.flow(SwitchSetting.CALL_HIDE_CONTACT_NUMBER).first(),
            statusLabels = prefs.flow(SwitchSetting.CALL_STATUS_LABELS).first(),
            callBgIncoming = prefs.callBgIncoming.first(),
            callBgOutgoing = prefs.callBgOutgoing.first(),
            avatarNoPhoto = prefs.avatarNoPhoto.first(),
            avatarUnknown = prefs.avatarUnknown.first(),
            avatarScope = prefs.avatarScope.first(),
            answerMode = prefs.answerMode.first(),
            swapAnswer = prefs.flow(SwitchSetting.SWAP_ANSWER).first(),
            nameFormat = prefs.contactNameFormat.first(),
            carrierInfo = prefs.carrierInfo.first(),
            scamMemo = prefs.flow(SwitchSetting.SCAM_MEMO).first()
        )

        /** Свой снимок фона заранее; в высоком контрасте своего фона нет (InCallActivity). */
        private suspend fun preloadBackground(context: Context, base: CallScreenStart, incoming: Boolean): StartBackground? {
            val kind = if (incoming) CallBackgrounds.Kind.INCOMING else CallBackgrounds.Kind.OUTGOING
            val mark = if (incoming) base.callBgIncoming else base.callBgOutgoing
            if (mark <= 0L || base.theme == ThemeMode.HIGH_CONTRAST) return null
            val image = withTimeoutOrNull(BACKGROUND_BUDGET_MS) { loadCustomCallBackground(context, kind) }
            if (image == null) Logger.i(TAG, "Свой фон ${kind.name} не успел к первому кадру")
            return image?.let { StartBackground(kind, mark, it) }
        }
    }
}

/** Свой снимок фона, раскодированный до первого кадра: для какого направления и какой версии. */
internal class StartBackground(
    val kind: CallBackgrounds.Kind,
    /** Отметка снимка (Prefs.callBgIncoming/Outgoing): сменили снимок — заготовка не годится. */
    val mark: Long,
    val image: CustomCallBackground
)

/** Заготовка своего фона для rememberCustomCallBackground; null — нет. */
internal val LocalStartBackground = staticCompositionLocalOf<StartBackground?> { null }

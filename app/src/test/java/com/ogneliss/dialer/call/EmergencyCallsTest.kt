package com.ogneliss.dialer.call

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import com.ogneliss.dialer.data.SimRepository
import com.ogneliss.dialer.util.EmergencyNumbers
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowServiceManager
import org.robolectric.shadows.ShadowSubscriptionManager

/**
 * ЭКСТРЕННЫЕ НОМЕРА (2.9.13, техдолг, этап 1.1).
 *
 * До 2.9.13 у экстренных номеров не было ни одного теста, хотя от них
 * зависит самое важное в звонилке: 112 должен набираться всегда — без
 * разрешения «Телефон», без выбора карты по правилу, без повтора автодозвоном.
 *
 * Робоэлектрик честно повторяет систему не во всём: его TelecomManager.placeCall
 * требует CALL_PHONE и от диалера по умолчанию (настоящий Telecom пускает
 * диалер к экстренному и без разрешения). Поэтому без разрешения проверяем
 * запасной путь — системный набор с этим номером (CallActions.dialEmergencyInSystemDialer).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmergencyCallsTest {

    private val app: Application = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        ShadowServiceManager.setServiceAvailability(PHONE_SERVICE, true)
        AutoRedial.stop()
        AutoRedial.config = AutoRedial.Config()
    }

    // ── Проверка номера ─────────────────────────────────────────────────────

    @Test
    fun `112 и 911 — экстренные в любой записи`() {
        listOf("112", "911", " 1 1 2 ", "(112)", "1-1-2", "9 1 1").forEach {
            assertTrue("«$it» — экстренный", EmergencyNumbers.isEmergency(app, it))
        }
    }

    @Test
    fun `пустой номер и буквы — не экстренные`() {
        listOf(null, "", "   ", "abc", "-()").forEach {
            assertFalse("«$it» — не экстренный", EmergencyNumbers.isEmergency(app, it))
        }
    }

    @Test
    fun `112 — экстренный, даже когда телефония не отвечает`() {
        ShadowServiceManager.setServiceAvailability(PHONE_SERVICE, false)
        assertTrue(EmergencyNumbers.isEmergency(app, "112"))
        assertTrue(EmergencyNumbers.isEmergency(app, "911", ifUnknown = false))
    }

    @Test
    fun `телефония не ответила — решает вызывающий`() {
        ShadowServiceManager.setServiceAvailability(PHONE_SERVICE, false)
        // Фильтр и подсказки: «не знаю» — обычный номер
        assertFalse(EmergencyNumbers.isEmergency(app, ORDINARY))
        // Автодозвон: «не знаю» — экстренный, повторять нельзя
        assertTrue(EmergencyNumbers.isEmergency(app, ORDINARY, ifUnknown = true))
    }

    @Test
    fun `без журнала вызовов недавнего экстренного нет`() {
        // Разрешения READ_CALL_LOG нет — фильтр решает сам, без исключения
        assertFalse(EmergencyNumbers.recentlyCalled(app))
    }

    // ── Звонок ──────────────────────────────────────────────────────────────

    @Test
    fun `без разрешения «Телефон» 112 всё равно набирается`() {
        val actions = CallActions(app, simRepository())
        actions.placeCall("112")
        val telecom = shadowOf(app.getSystemService(TelecomManager::class.java))
        val started = shadowOf(app).nextStartedActivity
        // Либо вызов принял Telecom, либо открылся системный набор с 112
        val viaTelecom = telecom.allOutgoingCalls.any { it.address == Uri.fromParts("tel", "112", null) }
        val viaSystemDialer = started?.action == Intent.ACTION_DIAL && started.data?.schemeSpecificPart == "112"
        assertTrue("112 не ушёл никуда", viaTelecom || viaSystemDialer)
    }

    @Test
    fun `без разрешения «Телефон» обычный номер не набирается`() {
        val actions = CallActions(app, simRepository())
        actions.placeCall(ORDINARY)
        val telecom = shadowOf(app.getSystemService(TelecomManager::class.java))
        assertTrue(telecom.allOutgoingCalls.isEmpty())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun `экстренному карту по правилу не подставляем`() {
        withTwoSims()
        // Правило «Карты по номеру» сказало бы «SIM 2» для любого номера
        val actions = CallActions(app, simRepository()) { 1 }

        actions.placeCall(ORDINARY)
        val telecom = shadowOf(app.getSystemService(TelecomManager::class.java))
        val ordinary = telecom.allOutgoingCalls.single()
        // Проверка самой проверки: обычному номеру карта по правилу подставилась
        assertEquals(SIM2.id, accountOf(ordinary.extras)?.id)

        actions.placeCall("112")
        val emergency = telecom.allOutgoingCalls.last()
        assertEquals(Uri.fromParts("tel", "112", null), emergency.address)
        // Карту для 112 выбирает система: она знает, какая сеть сейчас доступна
        assertNull(accountOf(emergency.extras))
    }

    @Test
    fun `явно выбранная карта у экстренного остаётся`() {
        withTwoSims()
        val actions = CallActions(app, simRepository()) { 1 }
        actions.placeCall("112", slotIndex = 0)
        val telecom = shadowOf(app.getSystemService(TelecomManager::class.java))
        assertEquals(SIM1.id, accountOf(telecom.allOutgoingCalls.single().extras)?.id)
    }

    // ── Автодозвон ──────────────────────────────────────────────────────────

    @Test
    fun `автодозвон предлагает повтор обычного номера после «занято»`() {
        AutoRedial.config = AutoRedial.Config(mode = AutoRedialMode.OFFER)
        endBusy(ORDINARY)
        assertNotNull(AutoRedial.state.value)
    }

    @Test
    fun `автодозвон не повторяет, если не знает, экстренный ли номер`() {
        AutoRedial.config = AutoRedial.Config(mode = AutoRedialMode.AUTO)
        ShadowServiceManager.setServiceAvailability(PHONE_SERVICE, false)
        endBusy(ORDINARY)
        assertNull(AutoRedial.state.value)
    }

    @Test
    fun `автодозвон не повторяет 112`() {
        AutoRedial.config = AutoRedial.Config(mode = AutoRedialMode.AUTO)
        endBusy("112")
        assertNull(AutoRedial.state.value)
    }

    // ── Подготовка ──────────────────────────────────────────────────────────

    private fun endBusy(number: String) {
        AutoRedial.onCallEnded(
            context = app,
            number = number,
            displayName = null,
            account = null,
            outgoing = true,
            connected = false,
            cause = RedialPolicy.CAUSE_BUSY,
            otherCalls = false
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun simRepository() = SimRepository(app, Dispatchers.Unconfined)

    /** Две карты: подписки 1 и 2 в слотах 0 и 1, у Telecom — аккаунты с теми же id. */
    private fun withTwoSims() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
        shadowOf(app.getSystemService(SubscriptionManager::class.java)).setActiveSubscriptionInfos(
            ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder()
                .setId(1).setSimSlotIndex(0).setDisplayName("SIM 1").buildSubscriptionInfo(),
            ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder()
                .setId(2).setSimSlotIndex(1).setDisplayName("SIM 2").buildSubscriptionInfo()
        )
        val telecom = app.getSystemService(TelecomManager::class.java)
        listOf(SIM1, SIM2).forEach { handle ->
            val builder = PhoneAccount.builder(handle, handle.id)
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER or PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
            // Включённость аккаунта — скрытый API (её ставит сама телефония); подставной
            // Telecom отдаёт в callCapablePhoneAccounts только включённые
            PhoneAccount.Builder::class.java.getMethod("setIsEnabled", Boolean::class.javaPrimitiveType)
                .invoke(builder, true)
            telecom.registerPhoneAccount(builder.build())
        }
    }

    private fun accountOf(extras: android.os.Bundle?): PhoneAccountHandle? = extras?.let {
        androidx.core.os.BundleCompat.getParcelable(it, TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, PhoneAccountHandle::class.java)
    }

    private companion object {
        const val PHONE_SERVICE = "phone"
        const val ORDINARY = "+7 495 123-45-67"
        val COMPONENT = ComponentName("com.android.phone", "TelephonyConnectionService")
        val SIM1 = PhoneAccountHandle(COMPONENT, "1")
        val SIM2 = PhoneAccountHandle(COMPONENT, "2")
    }
}

package com.ogneliss.dialer.call

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.ogneliss.dialer.R

/**
 * Открытие внешних приложений по номеру/адресу.
 * Приложение НИЧЕГО не отправляет само — только передаёт намерение системе,
 * пользователь завершает действие в приложении по умолчанию.
 */
object ExternalApps {

    private const val PKG_WHATSAPP = "com.whatsapp"
    private const val PKG_TELEGRAM = "org.telegram.messenger"
    private const val PKG_VIBER = "com.viber.voip"
    private const val PKG_MAX = "ru.oneme.app"

    private fun isInstalled(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(pkg, 0); true
    }.getOrDefault(false)

    /** Системное SMS-приложение по умолчанию. Роль SMS-handler не нужна. */
    fun sms(context: Context, number: String, body: String? = null) = launch(
        context,
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            body?.let { putExtra("sms_body", it) }
        }
    )

    /**
     * SMS С ВЫБРАННОЙ SIM (1.7.0) — ПОДСКАЗКОЙ, А НЕ ПРИКАЗОМ.
     *
     * Отправляет сообщение приложение SMS, а не мы: роли SMS у нас нет
     * и не будет (без доступа к сети и переписке). Общего способа сказать
     * ему «с этой карты» Android не даёт. Кладём в намерение подсказки,
     * которые понимают разные приложения сообщений: номер подписки
     * (subscription, android.telephony.extra.SUBSCRIPTION_INDEX) и слот.
     * Кто их не понимает — откроет переписку как обычно, и карту
     * человек выберет в самом приложении: там такая кнопка есть всегда.
     */
    fun smsFromSim(context: Context, number: String, subscriptionId: Int, slotIndex: Int) = launch(
        context,
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            putExtra("subscription", subscriptionId)
            putExtra("subscription_id", subscriptionId)
            putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", subscriptionId)
            putExtra("slot", slotIndex)
            putExtra("simSlot", slotIndex)
            putExtra("com.android.phone.extra.slot", slotIndex)
        }
    )

    fun whatsapp(context: Context, number: String) =
        launch(context, Intent(Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/${number.filter { it.isDigit() }}")))

    fun telegram(context: Context, number: String) =
        launch(context, Intent(Intent.ACTION_VIEW,
            Uri.parse("https://t.me/+${number.filter { it.isDigit() }}")))

    fun viber(context: Context, number: String) =
        launch(context, Intent(Intent.ACTION_VIEW,
            Uri.parse("viber://chat?number=${number.filter { it.isDigit() }}")))

    /**
     * Чат в мессенджере Max.
     *
     * Ссылка вида max.ru/<номер> — тот же приём, что у WhatsApp
     * и Telegram: приложение перехватывает свой домен и открывает
     * чат само. Если приложение не установлено, ссылка откроется
     * в браузере, и человек увидит предложение установить —
     * это лучше, чем молчаливое бездействие.
     */
    fun max(context: Context, number: String) =
        launch(context, Intent(Intent.ACTION_VIEW,
            Uri.parse("https://max.ru/${number.filter { it.isDigit() }}")))

    /**
     * Установленные мессенджеры — для «Написать» по любому номеру (1.3.5,
     * настройка «Мессенджеры для любого номера»). Порядок — как в выборе
     * мессенджеров контакта (ContactMessengers): Telegram, WhatsApp, Max, Viber.
     */
    fun installedMessengers(context: Context): List<com.ogneliss.dialer.settings.SwipeAction> = listOf(
        com.ogneliss.dialer.settings.SwipeAction.TELEGRAM to PKG_TELEGRAM,
        com.ogneliss.dialer.settings.SwipeAction.WHATSAPP to PKG_WHATSAPP,
        com.ogneliss.dialer.settings.SwipeAction.MAX to PKG_MAX,
        com.ogneliss.dialer.settings.SwipeAction.VIBER to PKG_VIBER
    ).filter { (_, pkg) -> isInstalled(context, pkg) }.map { it.first }

    /** Чат по номеру в выбранном мессенджере (без записи в книге). */
    fun messengerByNumber(context: Context, kind: com.ogneliss.dialer.settings.SwipeAction, number: String) {
        when (kind) {
            com.ogneliss.dialer.settings.SwipeAction.WHATSAPP -> whatsapp(context, number)
            com.ogneliss.dialer.settings.SwipeAction.TELEGRAM -> telegram(context, number)
            com.ogneliss.dialer.settings.SwipeAction.VIBER -> viber(context, number)
            com.ogneliss.dialer.settings.SwipeAction.MAX -> max(context, number)
            else -> Unit
        }
    }

    /** Установлен ли Max — для показа действия только когда оно сработает. */
    fun isMaxInstalled(context: Context): Boolean = isInstalled(context, PKG_MAX)

    fun email(context: Context, address: String) =
        launch(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))

    fun share(context: Context, text: String) = launch(
        context,
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
            context.getString(R.string.action_share_number)
        )
    )

    /** Если приложения нет — тихо ничего не делаем, а не падаем. */
    private fun launch(context: Context, intent: Intent) {
        val i = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(context.packageManager) != null ||
            context.packageManager.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        ) {
            runCatching { context.startActivity(i) }
        }
    }
}

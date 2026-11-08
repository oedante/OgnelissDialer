package com.ogneliss.dialer.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ogneliss.dialer.MainActivity
import com.ogneliss.dialer.R
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.sms.SmsRole
import com.ogneliss.dialer.ui.components.MainSection
import java.util.concurrent.Executors

/**
 * ЗНАЧКИ РАЗДЕЛОВ НА ГЛАВНОМ ЭКРАНЕ (2.1.0).
 *
 * Автор, 08.11.2026: «нужен ярлык на рабочем столе для открытия SMS —
 * с изменённым логотипом и точкой или цифрами о SMS; такой же для контактов».
 *
 * ── Почему виджет, а не ярлык ──────────────────────────────────────────────
 * Ярлык (pinned shortcut) — картинка, которую рисует лаунчер: число на нём
 * не показать и не обновить. Виджет 1 × 1 рисует приложение само: тот же
 * адаптивный значок (форма — маской лаунчера), подпись под ним и красный
 * счётчик непрочитанных. Лаунчер, который не умеет закреплять виджеты из
 * приложения, получает обычный ярлык (без счётчика).
 *
 * ── Что открывает ──────────────────────────────────────────────────────────
 * «SMS» — раздел SMS; если SMS ведёт другое приложение (Google Сообщения,
 * Samsung Messages — режим «спутник», SmsRole), сразу его: человек ждёт
 * переписку, а не кнопку «Открыть». «Контакты» — раздел контактов.
 *
 * ── Когда обновляется ──────────────────────────────────────────────────────
 * Изменилось хранилище SMS (MessageStore: пришло, прочитано, удалено),
 * пришло SMS, вернулись в приложение. Отдельного расписания нет
 * (updatePeriodMillis = 0): число меняется только вместе с SMS.
 */
object SectionWidgets {

    private const val TAG = "Widgets"

    /** Раздел для MainActivity: значение — MainSection.route. */
    const val EXTRA_OPEN_SECTION = "open_section"

    /** Счёт непрочитанных читает хранилище — не в главном потоке. */
    private val io = Executors.newSingleThreadExecutor()

    /** Обновить значки «SMS» (счётчик и куда ведёт касание). */
    fun updateSms(context: Context) {
        val app = context.applicationContext
        io.execute {
            runCatching { render(app, MainSection.MESSAGES, SmsWidgetProvider::class.java) }
                .onFailure { Logger.w(TAG, "Значок SMS не обновился", it) }
        }
    }

    fun updateAll(context: Context) {
        updateSms(context)
        val app = context.applicationContext
        io.execute {
            runCatching { render(app, MainSection.CONTACTS, ContactsWidgetProvider::class.java) }
                .onFailure { Logger.w(TAG, "Значок контактов не обновился", it) }
        }
    }

    internal fun render(context: Context, section: MainSection, provider: Class<out AppWidgetProvider>) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, provider))
        if (ids.isEmpty()) return
        val sms = section == MainSection.MESSAGES
        val unread = if (sms) unreadCount(context) else null
        val label = context.getString(section.labelRes)
        val views = RemoteViews(context.packageName, R.layout.widget_section).apply {
            setImageViewResource(R.id.widget_icon, if (sms) R.drawable.ic_shortcut_sms else R.drawable.ic_shortcut_contacts)
            setTextViewText(R.id.widget_label, label)
            if (unread != null && unread > 0) {
                setViewVisibility(R.id.widget_badge, View.VISIBLE)
                setTextViewText(R.id.widget_badge, if (unread > 99) "99+" else unread.toString())
                setContentDescription(
                    R.id.widget_root,
                    context.resources.getQuantityString(R.plurals.widget_sms_unread, unread, label, unread)
                )
            } else {
                setViewVisibility(R.id.widget_badge, View.GONE)
                setContentDescription(R.id.widget_root, label)
            }
            setOnClickPendingIntent(R.id.widget_root, openIntent(context, section))
        }
        manager.updateAppWidget(ids, views)
    }

    /**
     * Непрочитанные входящие SMS и MMS — по всему хранилищу. null —
     * читать нельзя (диалер не приложение SMS): счётчика нет, а не «0».
     */
    private fun unreadCount(context: Context): Int? {
        if (!SmsRole.canRead(context)) return null
        fun count(uri: android.net.Uri, selection: String): Int = runCatching {
            context.contentResolver.query(uri, arrayOf("_id"), selection, null, null)?.use { it.count } ?: 0
        }.getOrDefault(0)
        return count(Telephony.Sms.CONTENT_URI, "read = 0 AND type = ${Telephony.Sms.MESSAGE_TYPE_INBOX}") +
            count(Telephony.Mms.CONTENT_URI, "read = 0 AND msg_box = ${Telephony.Mms.MESSAGE_BOX_INBOX}")
    }

    private fun openIntent(context: Context, section: MainSection): PendingIntent {
        val intent = Intent(context, SectionLaunchActivity::class.java)
            .setAction("com.ogneliss.dialer.OPEN_" + section.route.uppercase())
            .putExtra(EXTRA_OPEN_SECTION, section.route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, section.ordinal, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Можно ли поставить значок из приложения (лаунчер закрепляет виджеты или ярлыки). */
    fun pinSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true ||
            ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * Поставить значок раздела на главный экран: виджет со счётчиком, а если
     * лаунчер не закрепляет виджеты — ярлык. Подтверждает система своим окном.
     */
    fun requestPin(context: Context, section: MainSection): Boolean {
        val provider = if (section == MainSection.MESSAGES) SmsWidgetProvider::class.java else ContactsWidgetProvider::class.java
        val manager = AppWidgetManager.getInstance(context)
        if (manager?.isRequestPinAppWidgetSupported == true) {
            val ok = runCatching { manager.requestPinAppWidget(ComponentName(context, provider), null, null) }
                .onFailure { Logger.w(TAG, "Виджет не закрепился", it) }
                .getOrDefault(false)
            if (ok) return true
        }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val sms = section == MainSection.MESSAGES
        val shortcut = ShortcutInfoCompat.Builder(context, "section_${section.route}")
            .setShortLabel(context.getString(section.labelRes))
            .setIcon(IconCompat.createWithResource(context, if (sms) R.drawable.ic_shortcut_sms else R.drawable.ic_shortcut_contacts))
            .setIntent(
                Intent(context, SectionLaunchActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .putExtra(EXTRA_OPEN_SECTION, section.route)
            )
            .build()
        return runCatching { ShortcutManagerCompat.requestPinShortcut(context, shortcut, null) }
            .onFailure { Logger.w(TAG, "Ярлык не закрепился", it) }
            .getOrDefault(false)
    }
}

/** Значок «SMS» с числом непрочитанных. */
class SmsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        SectionWidgets.updateSms(context)
    }
}

/** Значок «Контакты». */
class ContactsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        SectionWidgets.updateAll(context)
    }
}

/**
 * Переходник касания по значку: «SMS» при чужом приложении SMS — оно само,
 * иначе — раздел в диалере. Окна у переходника нет (Theme.NoDisplay):
 * решает и закрывается до первого кадра.
 */
class SectionLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val route = intent?.getStringExtra(SectionWidgets.EXTRA_OPEN_SECTION) ?: MainSection.JOURNAL.route
        val handedOff = route == MainSection.MESSAGES.route &&
            !SmsRole.isDefault(this) && SmsRole.openCompanionApp(this)
        if (!handedOff) {
            runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(SectionWidgets.EXTRA_OPEN_SECTION, route)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }.onFailure { Logger.w("Widgets", "Раздел не открылся", it) }
        }
        finish()
    }
}

package com.ogneliss.dialer.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.ui.components.MainSection

/**
 * ОТДЕЛЬНЫЕ ЗНАЧКИ «SMS» И «КОНТАКТЫ» В СПИСКЕ ПРИЛОЖЕНИЙ (2.6.0).
 *
 * Форум, oleg_p: «было бы неплохо сделать три отдельных ярлыка для запуска
 * соответствующих форм: номеронабиратель, SMS-менеджер, контакты».
 *
 * ── Что уже было и почему мало ─────────────────────────────────────────────
 * С 2.1.0 «SMS» и «Контакты» ставятся на главный экран из «Меню» раздела
 * (SectionWidgets) — но это виджеты 1 × 1: со счётчиком, зато в док
 * (нижний ряд) и в папку лаунчер виджет не кладёт. А телефон, SMS
 * и контакты держат именно там.
 *
 * ── Как теперь ─────────────────────────────────────────────────────────────
 * Два входа в манифесте (activity-alias `.launcher.Messages`
 * и `.launcher.Contacts`) с категорией LAUNCHER — в списке приложений это
 * обычные значки: их можно положить в док, в папку, на главный экран.
 * Рисунок — тот же, что у значков разделов (ic_shortcut_sms,
 * ic_shortcut_contacts, в стиле значка приложения). Открывают — через
 * переходник SectionLaunchActivity, как виджеты: «SMS» при чужом приложении
 * SMS — его, иначе раздел в диалере.
 *
 * Третий значок — сам Ogneliss Dialer: пока отдельные значки включены, он
 * открывает журнал (MainActivity.sectionRouteOf). Без них — как раньше:
 * возвращает туда, где человек был.
 *
 * ── Выключены, пока их не включат ──────────────────────────────────────────
 * В манифесте android:enabled="false": обновление не должно само добавлять
 * значки в список приложений (а на оболочках без списка — на главный
 * экран). Включаются в «Настройках → Быстрый доступ → Главный экран»;
 * состояние хранит сам Android (PackageManager), настройка его повторяет
 * (Prefs.sectionIcons) и при запуске сверяет (OgnelissApp, [reconcile]).
 *
 * ── Сами значки приложение не убирает (2.9.4) ──────────────────────────────
 * Форум, oleg_p (Galaxy S23 Ultra), 23.11.2026: «после последнего обновления
 * пропало разделение по отдельным приложениям» — значки исчезли. До 2.9.4
 * сверка при запуске подгоняла Android под настройку: настройка «выкл.» —
 * значки убирались. Значит, если настройка почему-то читалась выключенной
 * (сброс настроек, перенос, потеря файла), значки, которые человек сам
 * положил в док и в папки, пропадали без его касания. Теперь наоборот:
 * включённые в Android значки — главнее, настройка подтягивается к ним;
 * убрать их можно только переключателем. Настройка «вкл.», а значков нет —
 * включаются, как раньше. Каждая сверка пишет строку в журнал: если значки
 * снова пропадут, будет видно, кто их убрал — мы или лаунчер.
 */
object SectionIcons {

    private const val TAG = "SectionIcons"

    /** Полные имена входов из манифеста (пакет кода, а не applicationId). */
    const val MESSAGES_ALIAS = "com.ogneliss.dialer.launcher.Messages"
    const val CONTACTS_ALIAS = "com.ogneliss.dialer.launcher.Contacts"

    private val ALIASES = listOf(MESSAGES_ALIAS, CONTACTS_ALIAS)

    /** Раздел по входу, которым открыли; null — не значок раздела. */
    fun sectionOf(className: String?): MainSection? = when (className) {
        MESSAGES_ALIAS -> MainSection.MESSAGES
        CONTACTS_ALIAS -> MainSection.CONTACTS
        else -> null
    }

    /** Включены ли значки разделов — по Android, а не по настройке. */
    fun isEnabled(context: Context): Boolean = runCatching {
        context.packageManager.getComponentEnabledSetting(
            ComponentName(context.packageName, MESSAGES_ALIAS)
        ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }.getOrDefault(false)

    /**
     * Включить или выключить оба значка. Выключенные — «как в манифесте»
     * (DEFAULT), а не DISABLED: так Android помнит меньше. DONT_KILL_APP —
     * без него Android закрыл бы приложение прямо в настройках.
     */
    fun apply(context: Context, enabled: Boolean) {
        val pm = context.packageManager
        val wanted = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        ALIASES.forEach { name ->
            runCatching {
                val component = ComponentName(context.packageName, name)
                if (pm.getComponentEnabledSetting(component) != wanted) {
                    pm.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP)
                    Logger.i(TAG, "Значок ${name.substringAfterLast('.')}: ${if (enabled) "включён" else "выключен"}")
                }
            }.onFailure { Logger.w(TAG, "Значок ${name.substringAfterLast('.')} не переключился", it) }
        }
    }

    /** Что сделать при запуске, если настройка и Android расходятся (2.9.4). */
    enum class Reconcile {
        /** Совпадают — ничего. */
        NOTHING,
        /** Настройка «вкл.», значков нет — включить (данные перенесли, Android забыл). */
        SHOW,
        /** Значки есть, настройка «выкл.» — запомнить «вкл.», значки не трогать. */
        REMEMBER_ON
    }

    /** Решение сверки — без Android, для проверки (SectionIconsTest). */
    fun reconcile(settingOn: Boolean, shownByAndroid: Boolean): Reconcile = when {
        settingOn && !shownByAndroid -> Reconcile.SHOW
        !settingOn && shownByAndroid -> Reconcile.REMEMBER_ON
        else -> Reconcile.NOTHING
    }

    /**
     * Открыли значком приложения из лаунчера (не значком раздела, не из
     * уведомления): MAIN + LAUNCHER и вход самого приложения.
     */
    fun isAppIconLaunch(intent: Intent?): Boolean =
        intent != null && intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            sectionOf(intent.component?.className) == null
}

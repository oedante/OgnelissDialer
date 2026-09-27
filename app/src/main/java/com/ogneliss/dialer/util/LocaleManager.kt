package com.ogneliss.dialer.util

import com.ogneliss.dialer.log.Logger
import android.os.LocaleList
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ogneliss.dialer.R
import com.ogneliss.dialer.t9.T9Engine
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * Управление языком приложения.
 *
 * Реализовано через per-app languages (AppCompatDelegate.setApplicationLocales):
 *  • Android 13+ — язык хранит система, приложение видно в системном
 *    списке «Языки приложений»;
 *  • Android 12 и ниже — AppCompat хранит выбор сам и применяет при старте.
 * В обоих случаях один и тот же вызов, ручной подмены Configuration не нужно.
 *
 * Список поддерживаемых языков НЕ захардкожен: он читается из
 * res/xml/locales_config.xml. Чтобы добавить язык, достаточно создать
 * values-<code>/strings.xml и дописать строчку в тот XML.
 */
object LocaleManager {

    /** Пустой тег = «как в системе». */
    const val SYSTEM = ""

    data class Option(val tag: String, val nativeName: String)

    /**
     * Читает locales_config.xml. Именно этот файл — единственный источник
     * правды о наборе языков.
     */
    fun supportedTags(context: Context): List<String> {
        val tags = mutableListOf<String>()
        runCatching {
            context.resources.getXml(R.xml.locales_config).use { parser ->
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "locale") {
                        parser.getAttributeValue(
                            "http://schemas.android.com/apk/res/android", "name"
                        )?.let { tags += it }
                    }
                    event = parser.next()
                }
            }
        }
        return tags.ifEmpty { listOf("en") }
    }

    /** Варианты для экрана настроек: «Системный» + каждый язык на своём языке. */
    fun options(context: Context): List<Option> = buildList {
        add(Option(SYSTEM, context.getString(R.string.settings_language_system)))
        supportedTags(context).forEach { tag ->
            val locale = Locale.forLanguageTag(tag)
            add(Option(tag, locale.getDisplayLanguage(locale).replaceFirstChar {
                it.titlecase(locale)
            }))
        }
    }

    /** Текущий выбор. Пустая строка — следуем за системой. */
    fun current(): String =
        AppCompatDelegate.getApplicationLocales().toLanguageTags().split(",")
            .firstOrNull().orEmpty()

    /**
     * ПОДБОР ЯЗЫКА ПРИ ПЕРВОМ ЗАПУСКЕ.
     *
     * Вызывается один раз, до показа интерфейса. Дальше действует
     * выбор пользователя, и трогать его нельзя: человек мог сознательно
     * поставить английский при русской системе.
     *
     * @return выбранный тег языка
     */
    fun applySystemDefault(context: Context): String {
        val systemTags = LocaleListCompat.getDefault().toLanguageTags()
            .split(",")
            .filter { it.isNotBlank() }

        val chosen = LocaleMatcher.choose(systemTags, supportedTags(context))
        apply(chosen)
        return chosen
    }

    fun apply(tag: String) {
        /*
         * Кэши, зависящие от языка, сбрасываются.
         *
         * T9 переводит буквы в цифры по раскладке, а она у разных
         * алфавитов разная: кэш, набранный при русском интерфейсе,
         * после переключения на немецкий давал бы неверные совпадения.
         *
         * Форматирование номеров тоже зависит от локали — там свой кэш
         * с ключом по региону, но сбросить его при смене языка дешевле,
         * чем разбираться, совпал ли регион.
         */
        T9Engine.clearCache()
        PhoneFormatter.clearCache()

        /*
         * Смена языка требует пересоздания экранов.
         *
         * setApplicationLocales меняет конфигурацию, но уже созданная
         * композиция Compose об этом не узнаёт: строки читаются
         * при первой отрисовке и запоминаются. Активность нужно
         * пересоздать — тогда ресурсы перечитываются заново.
         *
         * Android делает это сам, только если в манифесте объявлено
         * android:configChanges без locale. У нас оно объявлено ради
         * поворота экрана, поэтому пересоздаём вручную.
         */
        AppCompatDelegate.setApplicationLocales(
            if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
    }

    /**
     * ПРИМЕНЕНИЕ ЯЗЫКА ЧЕРЕЗ СИСТЕМУ.
     *
     * ── Почему не только AppCompatDelegate ─────────────────────────────────
     * Он рассчитан на AppCompatActivity: сам перехватывает конфигурацию
     * и пересоздаёт экран. У нас ComponentActivity — Compose не требует
     * AppCompat, — и делегат меняет лишь своё внутреннее состояние.
     * Язык при этом сохраняется, но интерфейс остаётся прежним
     * до перезапуска.
     *
     * Начиная с Android 13 язык приложения умеет менять сама система
     * через LocaleManager. Она же пересоздаёт активность — и делает это
     * правильнее, чем ручной recreate(): сохраняет состояние экрана
     * и не мигает.
     *
     * На более старых версиях остаётся делегат плюс ручное пересоздание.
     *
     * @return true, если систему попросили сменить язык и она сама
     *         пересоздаст экран — тогда вызывающему коду делать нечего
     */
    fun applyViaSystem(context: Context, tag: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            apply(tag)
            return false
        }

        return runCatching {
            val manager = context.getSystemService(LocaleManager::class.java)
                ?: return false

            manager.applicationLocales =
                if (tag.isBlank()) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(tag)

            // Кэши, зависящие от раскладки, сбрасываем и здесь:
            // системный путь не проходит через apply()
            T9Engine.clearCache()
            PhoneFormatter.clearCache()
            true
        }.getOrElse {
            Logger.w("Locale", "Системная смена языка не удалась", it)
            apply(tag)
            false
        }
    }

    /**
     * Первый запуск: если системный язык русский — русский, иначе английский.
     *
     * Отдельно фиксировать этот выбор НЕ нужно: Android сам разрешает
     * системную локаль по res-квалификаторам, и values-ru подхватится
     * автоматически, а любой другой язык упадёт в базовую values/ (английский).
     * Метод оставлен для случая, когда потребуется явно записать выбор,
     * например при добавлении языков, близких к русскому (uk, be).
     */
    fun resolveInitialTag(context: Context): String {
        val system = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            context.resources.configuration.locales[0]
        else @Suppress("DEPRECATION") context.resources.configuration.locale

        return if (system.language.equals("ru", ignoreCase = true)) "ru" else "en"
    }
}

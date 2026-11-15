package com.ogneliss.dialer.ui

import android.content.Context
import android.content.res.Configuration
import com.ogneliss.dialer.ui.settings.SettingsCategory
import com.ogneliss.dialer.ui.settings.SettingsRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * ДЛИНА ПОЯСНЕНИЙ НАСТРОЕК.
 *
 * 2.7.0, обзор вида 15.11.2026: пояснения до пяти строк делали экран
 * настроек неровным — строки выходили высотой от 56 до 160 dp. Теперь
 * пояснение — одна фраза в одну-две строки на телефоне шириной 393 dp,
 * а подробности живут в «Вопросах и ответах» (Faq.kt).
 *
 * Читает настоящие ресурсы четырёх языков, поэтому Robolectric: длинная
 * строка в любом из языков ломает ровность экрана, а заметить это
 * можно только открыв его на каждом языке.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsTextLengthTest {

    /** Предел символов пояснения настройки по языкам. */
    private val limits = mapOf("en" to 70, "ru" to 70, "de" to 80, "fr" to 80)

    /** Предел для подписи раздела на главном экране настроек. */
    private val categoryLimit = 50

    private fun contextFor(lang: String): Context {
        val base = RuntimeEnvironment.getApplication()
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(lang))
        return base.createConfigurationContext(config)
    }

    @Test
    fun `пояснение настройки не длиннее предела`() {
        val tooLong = mutableListOf<String>()
        for ((lang, limit) in limits) {
            val context = contextFor(lang)
            for (entry in SettingsRegistry.entries) {
                val res = entry.summaryRes ?: continue
                val text = context.getString(res)
                if (text.length > limit) tooLong += "$lang ${entry.id}: ${text.length} > $limit — $text"
            }
        }
        assertTrue("Слишком длинные пояснения:\n" + tooLong.joinToString("\n"), tooLong.isEmpty())
    }

    @Test
    fun `подпись раздела настроек не длиннее предела`() {
        val tooLong = mutableListOf<String>()
        for (lang in limits.keys) {
            val context = contextFor(lang)
            for (category in SettingsCategory.entries) {
                val text = context.getString(category.summaryRes)
                if (text.length > categoryLimit) tooLong += "$lang ${category.name}: ${text.length} — $text"
            }
        }
        assertTrue("Слишком длинные подписи разделов:\n" + tooLong.joinToString("\n"), tooLong.isEmpty())
    }
}

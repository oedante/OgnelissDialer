package com.ogneliss.dialer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ТЕСТЫ ПОДБОРА ЯЗЫКА ПРИ ПЕРВОМ ЗАПУСКЕ.
 *
 * Ошибка здесь означает, что человек при первом открытии видит
 * незнакомый язык — и должен найти настройку языка в интерфейсе,
 * которого не понимает.
 */
class LocaleMatcherTest {

    private val supported = listOf("en", "ru")

    @Test
    fun `язык системы выбирается, если он поддержан`() {
        assertEquals("ru", LocaleMatcher.choose(listOf("ru-RU"), supported))
    }

    @Test
    fun `регион не влияет на выбор`() {
        // «ru-KZ» — это русский. Сравнение по полному тегу отправило бы
        // русскоязычного пользователя из Казахстана на английский
        assertEquals("ru", LocaleMatcher.choose(listOf("ru-KZ"), supported))
        assertEquals("ru", LocaleMatcher.choose(listOf("ru-BY"), supported))
        assertEquals("en", LocaleMatcher.choose(listOf("en-GB"), supported))
    }

    @Test
    fun `незнакомый язык даёт английский`() {
        assertEquals("en", LocaleMatcher.choose(listOf("kk-KZ"), supported))
        assertEquals("en", LocaleMatcher.choose(listOf("zh-Hans-CN"), supported))
    }

    @Test
    fun `порядок системных языков — это предпочтения пользователя`() {
        // На Android можно задать список языков. Первый совпавший
        // должен выиграть: перебирать наоборот значило бы навязать
        // человеку его второй язык вместо первого
        assertEquals(
            "ru",
            LocaleMatcher.choose(listOf("ru-RU", "en-US"), supported)
        )
        assertEquals(
            "en",
            LocaleMatcher.choose(listOf("en-US", "ru-RU"), supported)
        )
    }

    @Test
    fun `незнакомый первым, знакомый вторым — берётся второй`() {
        assertEquals(
            "ru",
            LocaleMatcher.choose(listOf("kk-KZ", "ru-RU"), supported)
        )
    }

    @Test
    fun `пустой список системных языков даёт английский`() {
        assertEquals("en", LocaleMatcher.choose(emptyList(), supported))
    }

    @Test
    fun `без английского среди поддержанных берётся первый`() {
        // Приложение без интерфейса оставить нельзя
        assertEquals("ru", LocaleMatcher.choose(listOf("kk"), listOf("ru", "de")))
    }

    @Test
    fun `пустой список поддержанных даёт запасной вариант`() {
        assertEquals(LocaleMatcher.FALLBACK, LocaleMatcher.choose(listOf("ru"), emptyList()))
    }

    @Test
    fun `код языка извлекается из тега`() {
        assertEquals("ru", LocaleMatcher.languageOf("ru-RU"))
        assertEquals("zh", LocaleMatcher.languageOf("zh-Hans-CN"))
        assertEquals("en", LocaleMatcher.languageOf("EN"))
    }

    @Test
    fun `проверка поддержки не зависит от регистра и региона`() {
        assertTrue(LocaleMatcher.isSupported(listOf("RU-ru"), supported))
        assertFalse(LocaleMatcher.isSupported(listOf("kk-KZ"), supported))
    }
}

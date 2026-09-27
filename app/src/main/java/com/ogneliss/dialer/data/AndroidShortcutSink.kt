package com.ogneliss.dialer.data

import android.content.Context
import com.ogneliss.dialer.util.ContactShortcuts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ЯРЛЫКИ ЧЕРЕЗ СИСТЕМНЫЙ API.
 *
 * Тонкая обёртка над `ContactShortcuts`. Нужна ради интерфейса:
 * логика выбора, кого показывать в ярлыках, живёт в контроллере
 * и должна проверяться тестом, а сам вызов системного API — нет.
 *
 * Без такого разделения тест на порядок ярлыков потребовал бы
 * устройства с лаунчером.
 */
@Singleton
class AndroidShortcutSink @Inject constructor(
    @ApplicationContext private val context: Context
) : ShortcutSink {

    override fun updateDynamic(targets: List<ShortcutTarget>) {
        ContactShortcuts.updateDynamic(
            context,
            /*
             * Цели передаются как есть.
             *
             * ── Что было не так ────────────────────────────────────────────
             * Здесь стояла пересборка ShortcutTarget из трёх полей —
             * имени, номера и e164. Фотография в этот список не входила
             * и терялась по дороге: до значка доходила цель без неё,
             * и все ярлыки рисовались одинаковой трубкой.
             *
             * Пересборка была нужна, пока ContactShortcuts объявлял свой
             * вложенный ShortcutTarget. Его давно нет — обе стороны
             * работают с одной моделью, и перекладывать поля незачем.
             */
            targets
        )
    }

    override fun requestPin(target: ShortcutTarget): Boolean =
        ContactShortcuts.requestPin(
            context,
            target
        )
}

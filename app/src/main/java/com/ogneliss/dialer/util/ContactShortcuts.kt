package com.ogneliss.dialer.util

import com.ogneliss.dialer.data.ShortcutTarget
import android.graphics.BitmapFactory
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ogneliss.dialer.R
import com.ogneliss.dialer.log.Logger

/**
 * ЯРЛЫКИ КОНТАКТОВ.
 *
 * ── Два вида ярлыков и разница между ними ──────────────────────────────────
 *
 * ДИНАМИЧЕСКИЕ — появляются в меню долгого нажатия по значку приложения.
 *   Обновляются нами автоматически: показываем избранных и тех, кому
 *   звонили чаще. Пользователь ничего не настраивает.
 *
 * ЗАКРЕПЛЁННЫЕ — значок прямо на рабочем столе. Создаёт пользователь
 *   явно, для конкретного человека. Один тап — звонок, минуя приложение.
 *
 * ── Почему ярлыки, а не виджет ─────────────────────────────────────────────
 * Виджет избранных выглядит богаче, но у него две проблемы: он занимает
 * место на экране постоянно, и его нужно рисовать через RemoteViews,
 * то есть отдельной вёрсткой вне Compose, которая не переиспользует
 * ни тему, ни компоненты.
 *
 * Ярлык решает ту же задачу — «позвонить одному человеку в один тап» —
 * штатными средствами системы, работает на всех лаунчерах и не требует
 * второй вёрстки.
 *
 * ── Ограничение системы ────────────────────────────────────────────────────
 * Динамических ярлыков не больше пяти на большинстве лаунчеров.
 * Показываем самых нужных, а не всех избранных.
 */
object ContactShortcuts {

    private const val TAG = "Shortcuts"

    /**
     * Целевой размер значка в пикселях.
     *
     * 256 с запасом: адаптивный значок обрезается лаунчером,
     * и часть изображения уходит под маску.
     */
    private const val ICON_TARGET_PX = 256
    private const val MAX_DYNAMIC = 4

    /**
     * Обновляет динамические ярлыки.
     *
     * @param contacts кандидаты в порядке важности
     */
    fun updateDynamic(
        context: Context,
        contacts: List<ShortcutTarget>
    ) {
        runCatching {
            /*
             * ПСЕВДОЗВОНОК — ПЕРВЫМ В СПИСКЕ.
             *
             * ══════════════════════════════════════════════════════════════
             * ПОЧЕМУ ОН БЫЛ В СЕРЕДИНЕ
             * ══════════════════════════════════════════════════════════════
             * Ярлык был статическим — объявленным в xml/shortcuts.xml.
             * Лаунчер смешивает статические и динамические ярлыки
             * по своим правилам, и псевдозвонок оказывался между
             * контактами.
             *
             * Теперь он динамический, как и контакты, и у него ранг 0 —
             * порядок определяем мы, а не догадки лаунчера.
             *
             * ── Почему именно первым ───────────────────────────────────────
             * Долгое нажатие на значок — самый быстрый путь к нему,
             * и им пользуются в неудобный момент, не разглядывая список.
             * Контакты в этом меню — удобство, псевдозвонок — причина,
             * по которой в него полезли.
             */
            val fakeCall = ShortcutInfoCompat.Builder(context, "fake_call")
                .setShortLabel(context.getString(R.string.fake_call_title))
                .setLongLabel(context.getString(R.string.fake_call_title))
                .setIcon(
                    // Адаптивный значок с подложкой, а не одноцветная фигура:
                    // разбор, почему был белый круг, — в самом ресурсе
                    IconCompat.createWithResource(context, R.drawable.ic_shortcut_fake_call)
                )
                .setIntent(
                    Intent(context, com.ogneliss.dialer.call.FakeCallActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                )
                /*
                 * РАНГ — ПОСЛЕДНИЙ, А НЕ НУЛЕВОЙ.
                 *
                 * Лаунчер ставит ярлык с рангом 0 ближе всего к значку.
                 * Значок звонилки почти всегда в нижнем ряду, меню
                 * раскрывается вверх — и ближний к значку пункт оказывается
                 * внизу списка. С рангом 0 псевдозвонок стоял вторым
                 * (под одним контактом) или последним, хотя задумывался
                 * первым. Последний ранг ставит его наверх меню,
                 * раскрытого вверх. Если значок вверху экрана и меню
                 * раскрывается вниз, псевдозвонок будет внизу — это цена
                 * решения, но звонилку туда кладут редко.
                 */
                .setRank(contacts.take(MAX_DYNAMIC - 1).size)
                .build()

            val shortcuts = contacts.take(MAX_DYNAMIC - 1).mapIndexed { index, target ->
                ShortcutInfoCompat.Builder(context, "call_${target.e164}")
                    .setShortLabel(target.name)
                    .setLongLabel(
                        context.getString(R.string.shortcut_call_label, target.name)
                    )
                    .setIcon(iconFor(context, target))
                    .setIntent(callIntent(target.number))
                    // Ранг определяет порядок в меню лаунчера: контакты
                    // первыми по рангу, псевдозвонок — последним (разбор выше)
                    .setRank(index)
                    .build()
            } + fakeCall
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
            Logger.d(TAG, "Обновлено динамических ярлыков: ${shortcuts.size}")
        }.onFailure { Logger.w(TAG, "Не удалось обновить ярлыки", it) }
    }

    /**
     * Предлагает закрепить ярлык контакта на рабочем столе.
     *
     * Система сама показывает диалог подтверждения — молча создать
     * значок на чужом рабочем столе нельзя, и это правильно.
     *
     * @return false, если лаунчер не поддерживает закрепление
     */
    fun requestPin(context: Context, target: ShortcutTarget): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Logger.i(TAG, "Лаунчер не поддерживает закрепление ярлыков")
            return false
        }

        return runCatching {
            val shortcut = ShortcutInfoCompat.Builder(context, "pin_${target.e164}")
                .setShortLabel(target.name)
                .setLongLabel(context.getString(R.string.shortcut_call_label, target.name))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_call))
                .setIntent(callIntent(target.number))
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.getOrElse {
            Logger.w(TAG, "Не удалось предложить закрепление", it)
            false
        }
    }

    /**
     * Intent ярлыка.
     *
     * ACTION_CALL сразу начинает вызов. Требует CALL_PHONE, который
     * у нас есть; альтернатива ACTION_DIAL открыла бы клавиатуру
     * с введённым номером, то есть добавила бы лишнее нажатие
     * и обессмыслила ярлык.
     */
    /**
     * ЗНАЧОК ЯРЛЫКА: ФОТОГРАФИЯ ИЛИ ТРУБКА.
     *
     * ── Зачем ──────────────────────────────────────────────────────────────
     * Раньше у всех ярлыков стояла одна иконка вызова, и в меню лаунчера
     * они различались только подписью. Смысл ярлыка при этом терялся:
     * он нужен, чтобы позвонить не читая, а лицо узнаётся быстрее имени.
     *
     * ── Почему адаптивный значок ───────────────────────────────────────────
     * createWithAdaptiveBitmap обрезает изображение по форме, принятой
     * в лаунчере: круг, скруглённый квадрат — как настроено у человека.
     * Прямоугольная фотография смотрелась бы чужеродно среди системных
     * ярлыков.
     *
     * ── Почему запасной вариант обязателен ─────────────────────────────────
     * Фотографии нет у большинства контактов, а путь к ней может
     * устареть: контакт удалён, снимок заменён. Ярлык без значка
     * лаунчер показывает пустым местом — это хуже, чем одинаковая
     * трубка у всех.
     */
    private fun iconFor(context: Context, target: ShortcutTarget): IconCompat {
        val fallback = IconCompat.createWithResource(context, R.drawable.ic_call)
        val uri = target.photoUri ?: return fallback

        return runCatching {
            /*
             * Уменьшение при чтении.
             *
             * Фотография контакта бывает в полном разрешении — несколько
             * мегапикселей. Значок ярлыка при этом размером с ноготь,
             * и декодировать оригинал целиком значит занять память
             * впустую: четыре таких снимка — это десятки мегабайт.
             *
             * inSampleSize уменьшает прямо при декодировании, не создавая
             * полноразмерный битмап ни на мгновение.
             */
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { probe ->
                BitmapFactory.decodeStream(probe, null, options)
            }

            options.inSampleSize = sampleSizeFor(options.outWidth, options.outHeight)
            options.inJustDecodeBounds = false

            context.contentResolver.openInputStream(Uri.parse(uri)).use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream, null, options)
                    ?: return@runCatching fallback
                IconCompat.createWithAdaptiveBitmap(bitmap)
            }
        }.getOrElse {
            // Путь мог устареть — это не ошибка, просто рисуем трубку
            Logger.d(TAG, "Фото для ярлыка недоступно, беру значок вызова")
            fallback
        }
    }

    /**
     * Во сколько раз уменьшать при чтении.
     *
     * Степень двойки: только их BitmapFactory обрабатывает без потери
     * качества и быстро. Целевой размер с запасом — лаунчеры
     * масштабируют значок сами, и слишком мелкий выглядел бы размытым.
     */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height

        while (w / 2 >= ICON_TARGET_PX && h / 2 >= ICON_TARGET_PX) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun callIntent(number: String): Intent =
        Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    /*
     * ShortcutTarget объявлен в data/DataSources.kt.
     *
     * Здесь был его дубликат — без поля photoUri. Вложенный класс
     * перекрывает импортированный, поэтому обращение к фотографии
     * не разрешалось, хотя в модели поле давно есть.
     *
     * Ошибка коварная: импорт на месте, объявление на месте,
     * а компилятор говорит «нет такого поля» — потому что видит
     * не тот класс.
     */
}

package com.ogneliss.dialer.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ogneliss.dialer.R
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.ui.platform.LocalContext
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.LocalAppearance
import com.ogneliss.dialer.util.DialString

/**
 * СТРОКА НАБРАННОГО НОМЕРА.
 *
 * ── Почему прямо над клавиатурой ───────────────────────────────────────────
 * Раньше строка была прибита к верху экрана. Глаза следят за пальцем
 * внизу, а результат появлялся в 15 см выше — приходилось переводить
 * взгляд через весь экран на каждую цифру, и ошибки набора замечались
 * с задержкой.
 *
 * Здесь номер оказывается в 1–2 см от клавиш: контроль ввода
 * периферийным зрением, без движения головы.
 *
 * ── Прокрутка вместо сжатия ────────────────────────────────────────────────
 * Длинный номер с донабором прокручивается вбок, а не ужимается
 * в нечитаемый мелкий шрифт. Прокрутка автоматически держится у правого
 * края — там, где появляются новые цифры.
 *
 * ── Кнопки по краям ────────────────────────────────────────────────────────
 * Слева добавление в контакты, справа удаление. Обе в зоне большого
 * пальца, обе не мешают читать номер по центру.
 */
@Composable
fun NumberDisplay(
    query: String,
    pauseLabel: String,
    waitLabel: String,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onAddContact: (String) -> Unit,
    /** Вставка номера из буфера обмена. */
    onPaste: (String) -> Unit
) {
    val appearance = LocalAppearance.current
    val view = LocalView.current
    val context = LocalContext.current
    /*
     * ToneGenerator держит аудиоресурс системы и обязан освобождаться.
     *
     * Здесь release() был забыт: экземпляры накапливались при каждом
     * открытии клавиатуры, и на части устройств тоны переставали звучать
     * вовсе — система отказывалась выдавать новый генератор.
     *
     * DisposableEffect привязывает освобождение к жизни композиции.
     */
    val feedback = remember { DialpadFeedback() }
    DisposableEffect(Unit) { onDispose { feedback.release() } }
    val scrollState = rememberScrollState()

    /*
     * СТРОКА НОМЕРА ПОКАЗЫВАЕТСЯ ВСЕГДА.
     *
     * ══════════════════════════════════════════════════════════════════════
     * ПОЧЕМУ БОЛЬШЕ НЕ КНОПКА «ВСТАВИТЬ»
     * ══════════════════════════════════════════════════════════════════════
     * Кнопка появлялась только при пустом поле и только если в буфере
     * что-то есть. Получалось непредсказуемо: человек открывает
     * клавиатуру, а на её месте то кнопка, то ничего — в зависимости
     * от того, копировал ли он что-то полчаса назад в другом приложении.
     *
     * Постоянная строка ведёт себя как строка ввода везде: она на месте,
     * в неё можно вставить длинным нажатием, а рядом стоит кнопка
     * вставки, когда вставлять есть что.
     *
     * ── Чем занята строка, пока номер не набран ────────────────────────────
     * Подсказкой «Номер» — тем же приёмом, что у поля поиска
     * в контактах. Место она занимает то же самое, что занимала кнопка,
     * и высота панели при первой же цифре не прыгает.
     */
    val clipboard = remember(context) {
        context.getSystemService(android.content.ClipboardManager::class.java)
    }

    /*
     * Наличие текста проверяется без чтения содержимого.
     *
     * С Android 12 любое чтение буфера показывает системное сообщение
     * «приложение вставило текст». Показывать его при каждом открытии
     * клавиатуры — пугать человека в приложении, которое обещает
     * не трогать данные. Само содержимое читается только по нажатию.
     */
    val hasClipboardText = clipboard?.hasPrimaryClip() == true &&
        clipboard.primaryClipDescription?.hasMimeType("text/plain") == true


    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /*
             * Слева — «в контакты» при наборе, вставка при пустом поле.
             *
             * Оба действия относятся к номеру и живут в одном месте;
             * показывать их одновременно незачем: добавлять в контакты
             * нечего, пока номер не набран, а вставлять некуда, когда
             * он уже есть.
             */
            if (query.isEmpty()) {
                IconButton(
                    onClick = {
                        /*
                         * Из буфера берутся только цифры и знаки набора.
                         *
                         * Копируют обычно вместе с лишним: «тел. +7 918
                         * 952-80-75, звонить после 18». Всё, кроме цифр,
                         * + * # и знаков донабора, отбрасывается — иначе
                         * в поле попадёт мусор, который потом вычищать
                         * по одному символу.
                         */
                        val text = runCatching {
                            clipboard?.primaryClip
                                ?.getItemAt(0)?.coerceToText(context)?.toString()
                        }.getOrNull().orEmpty()

                        val digits = text.filter { it.isDigit() || it in "+*#,;" }
                        if (digits.isNotEmpty()) onPaste(digits)
                    },
                    enabled = hasClipboardText
                ) {
                    Icon(
                        Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.action_paste_number),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                IconButton(onClick = { onAddContact(query) }) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = stringResource(R.string.action_add_contact),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState, reverseScrolling = true)
            ) {
                Text(
                    DialString.formatForDisplay(query).ifEmpty {
                        stringResource(R.string.dialpad_number_hint)
                    },
                    style = MaterialTheme.typography.displaySmall,
                    // Подсказка бледнее набранного: иначе пустое поле
                    // выглядит как уже введённое слово
                    color = if (query.isEmpty()) {
                        MaterialTheme.colorScheme.outlineVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )

                // Расшифровка донабора: символы «,» и «;» ничего
                // не говорят тем, кто их не знает, а знают немногие
                val steps = DialString.describeSequence(query)
                if (steps.isNotEmpty()) {
                    Text(
                        steps.joinToString(" → ") { step ->
                            when (step) {
                                DialString.Step.Pause -> pauseLabel
                                DialString.Step.Wait -> waitLabel
                                is DialString.Step.Digits -> step.value
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Короткое нажатие удаляет цифру, долгое очищает поле —
            // привычное поведение любой телефонной клавиатуры
            // Стирать нечего, пока поле пусто; место сохраняем,
            // чтобы номер не прыгал вбок при первой цифре
            if (query.isEmpty()) {
                Spacer(Modifier.size(Dimens.MIN_TOUCH_TARGET))
            } else Box(
                Modifier
                    .size(Dimens.MIN_TOUCH_TARGET)
                    .combinedClickableCompat(
                        onClick = {
                            feedback.onBackspace(view, appearance.haptic)
                            onBackspace()
                        },
                        onLongClick = {
                            feedback.onBackspace(view, appearance.haptic)
                            onClear()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.a11y_backspace),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OgnelissDivider()
    }
}

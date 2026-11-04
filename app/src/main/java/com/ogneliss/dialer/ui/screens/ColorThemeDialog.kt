@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ogneliss.dialer.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ogneliss.dialer.R
import com.ogneliss.dialer.settings.SettingsCommands
import com.ogneliss.dialer.settings.ThemeMode
import com.ogneliss.dialer.settings.ThemePreset
import com.ogneliss.dialer.ui.DialerViewModel
import com.ogneliss.dialer.ui.components.OgnelissDialog
import com.ogneliss.dialer.ui.components.SectionCaption
import com.ogneliss.dialer.ui.theme.Dimens
import com.ogneliss.dialer.ui.theme.OgnelissIcons
import com.ogneliss.dialer.ui.theme.Space
import com.ogneliss.dialer.ui.theme.ThemePresetMath
import com.ogneliss.dialer.ui.theme.ThemePresets

/**
 * ЦВЕТОВАЯ ТЕМА И СВОЙ АКЦЕНТ — ОДНО ОКНО (1.9.0).
 *
 * Автор, 03.11.2026: «переноси все» — из «Оформления» Ogneliss Mail
 * (appearance_screen.dart): «Цветовая тема» и «Акцент» там — соседние
 * разделы одного экрана. Здесь — одно окно настройки «Цветовая тема»:
 *
 * • вверху семь тем строками с кружком их акцента — выбор темы сбрасывает
 *   свой акцент (у темы свой, как pickPreset почты);
 * • ниже кружки акцентов: «Как в теме», двенадцать готовых (accentSwatches
 *   почты, ThemePresetMath.ACCENTS) и «Свой цвет» — тон и насыщенность
 *   ползунками. Кружки показаны той яркостью, какой акцент будет в текущей
 *   теме (подбор под AA — accentLight / accentDark), как в почте.
 *
 * Окно после выбора не закрывается: тему и акцент выбирают вместе
 * и сразу видят результат на самом окне.
 *
 * Отдельного пункта «Акцент» в разделе нет: в разделе не больше 14 пунктов
 * (SettingsRegistryTest), а тема и акцент — одно решение. Хранится одной
 * строкой «ТЕМА|#AARRGGBB» (SettingsCommands.themeChoice): «Вернуть как
 * было» сбрасывает и то и другое.
 */
@Composable
internal fun ColorThemeDialog(
    vm: DialerViewModel,
    current: String,
    onDismiss: () -> Unit
) {
    val (preset, accent) = SettingsCommands.parseThemeChoice(current)
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // На чём акцент будет текстом: самая тёмная (в тёмной — самая светлая)
    // поверхность темы, как AccentFit почты
    val on = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()
    fun shown(argb: Int) = Color(
        if (dark) ThemePresetMath.accentDark(argb, on) else ThemePresetMath.accentLight(argb, on)
    )
    fun pick(p: ThemePreset, a: Int?) = vm.setChoice("theme_preset", SettingsCommands.themeChoice(p, a))

    // Свой цвет: тон и насыщенность — от сохранённого или от акцента темы
    val start = ThemePresetMath.toHsl(accent ?: ThemePresets.spec(preset).accent)
    var hue by remember(current) { mutableFloatStateOf(start.h.toFloat()) }
    var sat by remember(current) { mutableFloatStateOf(start.s.toFloat().coerceIn(SAT_MIN, 1f)) }
    val custom = accent != null && accent !in ThemePresetMath.ACCENTS
    var editing by remember { mutableStateOf(custom) }
    fun customArgb() = ThemePresetMath.hsl(hue.toDouble(), sat.toDouble(), CUSTOM_LIGHT)

    OgnelissDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_theme_preset),
        content = {
            Column(
                Modifier
                    .heightIn(max = DIALOG_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState())
            ) {
                // Почему выбор может не подействовать — сразу, а не потом
                val note = when {
                    settings.theme == ThemeMode.HIGH_CONTRAST -> R.string.accent_note_contrast
                    settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                        R.string.accent_note_dynamic
                    else -> null
                }
                note?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Space.s)
                    )
                }

                SectionCaption(stringResource(R.string.color_theme_section))
                ThemePreset.entries.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.MIN_TOUCH_TARGET)
                            .selectable(
                                selected = p == preset && accent == null,
                                role = Role.RadioButton,
                                onClick = { pick(p, null) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = p == preset && accent == null, onClick = null)
                        Spacer(Modifier.width(Space.m))
                        Box(
                            Modifier
                                .size(SWATCH_SMALL)
                                .clip(CircleShape)
                                .background(shown(ThemePresets.spec(p).accent))
                        )
                        Spacer(Modifier.width(Space.m))
                        Text(
                            stringResource(SettingsCommands.themePresetLabelRes(p)),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(Modifier.height(Space.m))
                SectionCaption(stringResource(R.string.accent_title))
                FlowRow(
                    Modifier.fillMaxWidth().padding(vertical = Space.xs),
                    horizontalArrangement = Arrangement.spacedBy(Space.xxs)
                ) {
                    AccentSwatch(
                        color = shown(ThemePresets.spec(preset).accent),
                        selected = accent == null,
                        label = stringResource(R.string.accent_auto),
                        icon = OgnelissIcons.Palette,
                        onClick = { editing = false; pick(preset, null) }
                    )
                    ThemePresetMath.ACCENTS.forEachIndexed { i, c ->
                        AccentSwatch(
                            color = shown(c),
                            selected = accent == c,
                            label = stringResource(SettingsCommands.ACCENT_NAMES[i]),
                            onClick = { editing = false; pick(preset, c) }
                        )
                    }
                    AccentSwatch(
                        color = if (custom || editing) shown(customArgb()) else null,
                        selected = custom,
                        label = stringResource(R.string.accent_custom),
                        icon = OgnelissIcons.Edit,
                        onClick = {
                            editing = true
                            pick(preset, customArgb())
                        }
                    )
                }

                // Свой цвет: тон по кругу, насыщенность; яркость подбирается
                // под тему сама. Сохраняется, когда палец отпущен
                if (editing) {
                    Text(
                        stringResource(R.string.accent_hue),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = Space.s)
                    )
                    // Радуга над ползунком — где какой тон
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.s)
                            .height(Space.s)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    List(HUE_STOPS) { i ->
                                        shown(ThemePresetMath.hsl(i * 360.0 / (HUE_STOPS - 1), sat.toDouble(), CUSTOM_LIGHT))
                                    }
                                )
                            )
                    )
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..359f,
                        onValueChangeFinished = { pick(preset, customArgb()) }
                    )
                    Text(
                        stringResource(R.string.accent_saturation),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = sat,
                        onValueChange = { sat = it },
                        valueRange = SAT_MIN..1f,
                        onValueChangeFinished = { pick(preset, customArgb()) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_close)) }
        }
    )
}

/**
 * Кружок акцента 36 dp в области нажатия 48 dp (как _Swatch почты).
 * Выбранный — с кольцом цвета текста и галочкой; [color] null — «Свой
 * цвет», ещё не выбранный: пустой круг со значком.
 */
@Composable
private fun AccentSwatch(
    color: Color?,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(Dimens.MIN_TOUCH_TARGET)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(SWATCH)
                .clip(CircleShape)
                .background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(if (selected) Modifier.border(SWATCH_RING, ring, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            // На цветном кружке значок белый или тёмный — по яркости кружка
            val glyph = when {
                color == null -> MaterialTheme.colorScheme.onSurfaceVariant
                color.luminance() > 0.4f -> Color.Black
                else -> Color.White
            }
            when {
                selected -> Icon(OgnelissIcons.Check, contentDescription = null, tint = glyph, modifier = Modifier.size(ICON))
                icon != null -> Icon(icon, contentDescription = null, tint = glyph, modifier = Modifier.size(ICON))
            }
        }
    }
}

/** Кружок акцента и кружок темы в строке. */
private val SWATCH = 36.dp
private val SWATCH_SMALL = 24.dp
private val SWATCH_RING = 2.dp
private val ICON = 18.dp
/** Окно не выше этого — дальше прокрутка (семь тем, акценты и ползунки). */
private val DIALOG_MAX_HEIGHT = 520.dp
/** Свой цвет: яркость исходного цвета (под тему её подбирает AccentFit). */
private const val CUSTOM_LIGHT = 0.45
/** Насыщенность своего цвета — не ниже: совсем серый акцент не отличить от текста. */
private const val SAT_MIN = 0.15f
/** Ступени радуги над ползунком тона. */
private const val HUE_STOPS = 13

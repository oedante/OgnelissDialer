package com.ogneliss.dialer.ui.components

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.ogneliss.dialer.call.CallBackgrounds
import com.ogneliss.dialer.settings.AvatarScope

/**
 * КАРТИНКИ ВМЕСТО ФОТО (2.8.0).
 *
 * Форум, iKovalsky: «А можно сделать как в TruePhone общие картинки для
 * неизвестных контактов и контактов без фото?» Автор: «совместим варианты
 * А и Б и дадим выбор пользователю в настройках, где применять».
 *
 * Две картинки из галереи (Настройки → Экран вызова → «Картинки без фото»):
 * для сохранённых контактов без фото — вместо цветных букв имени, для
 * неизвестных номеров — вместо силуэта. Где — [scope]: везде, только на
 * экране вызова или только в списках и карточке.
 *
 * Сами файлы — рядом со своими фонами вызова (CallBackgrounds,
 * AVATAR_NO_PHOTO / AVATAR_UNKNOWN). Фото контакта всегда главнее:
 * картинка — только там, где фото нет.
 */
@Immutable
data class AvatarPictures(
    /** Адрес картинки для контактов без фото; null — буквы имени. */
    val noPhoto: String? = null,
    /** Адрес картинки для неизвестных номеров; null — силуэт. */
    val unknown: String? = null,
    val scope: AvatarScope = AvatarScope.EVERYWHERE
) {
    /** В списках и карточке: [known] — сохранённый контакт. null — обычный вид. */
    fun forLists(known: Boolean): String? =
        if (!scope.inLists) null else if (known) noPhoto else unknown

    /** На экране вызова: [known] — звонит сохранённый контакт. null — портрета нет. */
    fun forCallScreen(known: Boolean): String? =
        if (!scope.onCallScreen) null else if (known) noPhoto else unknown

    companion object {
        fun of(context: Context, noPhotoMark: Long, unknownMark: Long, scope: AvatarScope) = AvatarPictures(
            noPhoto = CallBackgrounds.uri(context, CallBackgrounds.Kind.AVATAR_NO_PHOTO, noPhotoMark),
            unknown = CallBackgrounds.uri(context, CallBackgrounds.Kind.AVATAR_UNKNOWN, unknownMark),
            scope = scope
        )
    }
}

/** Картинки вместо фото — задаются в корне приложения и в экранах вызова. */
val LocalAvatarPictures = staticCompositionLocalOf { AvatarPictures() }

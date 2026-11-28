package com.ogneliss.dialer.call

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.PermissionGuard
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.first

/**
 * ФОТО И ТИП НОМЕРА ЗВОНЯЩЕГО — ДЛЯ УВЕДОМЛЕНИЯ ВЫЗОВА (2.1.0).
 *
 * Автор, 08.11.2026, по вопросу пользователя («а такое — плашка сверху
 * с фото и „мобильный“ — вы не делали?»): плашка входящего — с фото
 * контакта и типом номера, как в стандартных звонилках. Было: в уведомлении
 * значок-силуэт и «Входящий вызов».
 *
 * ── Почему не сразу в уведомлении ──────────────────────────────────────────
 * Запрос к книге и разбор снимка — десятки миллисекунд, а при холодном
 * провайдере контактов и больше. Звонок ждать не должен: уведомление
 * ставится сразу, без фото, а когда фото готово — обновляется тем же
 * номером уведомления (без нового звука и всплытия: setOnlyAlertOnce).
 * Повторные показы того же звонка берут готовое из памяти.
 */
object CallerCard {

    /**
     * Фото (миниатюра книги), подпись номера («Мобильный») и имя из книги;
     * любое может отсутствовать.
     *
     * [name] (2.7.9, форум, Joker5649, realme XT: «в режиме всплывающего окна при
     * звонке не показывает имя абонента, только номер, хотя абонент занесён
     * в контакты»). Подпись уведомления бралась только из данных вызова
     * (CallDetailsCompat.displayLabel), а имя из книги туда кладёт система —
     * и не на всех прошивках: на ColorOS его там нет. Экран вызова ищет имя
     * в книге сам, поэтому там оно было. Теперь и уведомление берёт имя отсюда.
     */
    data class Info(val photo: Bitmap?, val typeLabel: String?, val name: String? = null)

    private const val TAG = "CallerCard"

    /**
     * Показывать тип номера в уведомлении о входящем (2.9.15, настройка
     * SwitchSetting.CALL_NOTIFICATION_NUMBER_TYPE). Копия настройки: уведомление
     * собирается сразу, без чтения DataStore; значение кладёт служба вызовов
     * (OgnelissInCallService), пока она жива. До первого значения — умолчание.
     */
    @Volatile var showNumberType: Boolean = true
    /**
     * Сторона снимка. Книга хранит миниатюры 96 px, а уведомление рисует
     * звонящего кругом в 40–48 dp; больше не нужно — снимок едет в уведомление
     * трижды (значок, звонящий CallStyle, список людей).
     */
    private const val MAX_SIDE_PX = 128
    private const val CACHE_SIZE = 6

    private val cache = object : LinkedHashMap<String, Info>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Info>?) = size > CACHE_SIZE
    }
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun key(number: String) = number.filter { it.isDigit() || it == '+' }

    /** Уже загруженное для этого номера; null — ещё не загружали. */
    fun cached(number: String?): Info? {
        if (number.isNullOrBlank()) return null
        return synchronized(cache) { cache[key(number)] }
    }

    /**
     * Загрузить в фоне; [onLoaded] — в главном потоке и только если нашлось
     * что показать (фото или тип номера). Без разрешения на контакты — ничего.
     */
    fun load(context: Context, number: String?, onLoaded: (Info) -> Unit) {
        if (number.isNullOrBlank() || cached(number) != null) return
        if (!PermissionGuard.canReadContacts(context)) return
        val app = context.applicationContext
        io.execute {
            val found = runCatching { query(app, number) }
                .onFailure { Logger.w(TAG, "Фото звонящего не загрузилось", it) }
                .getOrNull() ?: Info(null, null)
            // Фото нет — своя картинка (2.8.2, placeholder): контакт без фото или неизвестный
            val info = if (found.photo != null) found
            else found.copy(photo = placeholder(app, known = found.name != null))
            synchronized(cache) { cache[key(number)] = info }
            if (info.photo != null || info.typeLabel != null || info.name != null) main.post { onLoaded(info) }
        }
    }

    /**
     * Уведомление с портретом звонящего (2.8.2, автор: портрет в уведомлениях
     * о пропущенных и напоминаниях — вариант «А»: обычный вид Android, значок
     * приложения слева, портрет справа). [post] — сразу, с тем, что уже
     * в памяти (часто — ничего); загрузились фото или имя — ещё раз, тем же
     * номером уведомления. Повторного звука нет: у таких уведомлений
     * setOnlyAlertOnce.
     */
    fun notifyWithPhoto(context: Context, number: String?, post: (Info?) -> Unit) {
        val cached = cached(number)
        post(cached)
        if (cached == null) load(context, number) { post(it) }
    }

    /*
     * КАРТИНКА ВМЕСТО ФОТО В УВЕДОМЛЕНИЯХ (2.8.2, автор: «да» — подставлять
     * картинки из 2.8.0 и в уведомления). Только при «Где показывать картинки» =
     * «Везде»: отдельного пункта для уведомлений нет. Читается в фоновом потоке
     * (настройки — через точку входа Hilt, как у значков на главном экране);
     * не ответили за секунду — картинки нет. Уменьшенная копия держится
     * в памяти, пока картинку не заменили (отметка — в ключе).
     */
    private var placeholderCache: Pair<String, Bitmap?>? = null

    /** Своя картинка для звонящего без фото; null — не задана или показывать не везде. */
    fun placeholder(context: Context, known: Boolean): Bitmap? = runCatching {
        val prefs = dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, PrefsEntryPoint::class.java).prefs()
        val (scope, mark) = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(1_000L) {
                prefs.avatarScope.first() to (if (known) prefs.avatarNoPhoto.first() else prefs.avatarUnknown.first())
            }
        } ?: return@runCatching null
        if (scope != com.ogneliss.dialer.settings.AvatarScope.EVERYWHERE || mark <= 0L) return@runCatching null
        val kind = if (known) CallBackgrounds.Kind.AVATAR_NO_PHOTO else CallBackgrounds.Kind.AVATAR_UNKNOWN
        val key = "${kind.name}:$mark"
        synchronized(this) { placeholderCache?.takeIf { it.first == key }?.let { return@runCatching it.second } }
        val bitmap = portrait(context, Uri.fromFile(CallBackgrounds.file(context, kind)))
        synchronized(this) { placeholderCache = key to bitmap }
        bitmap
    }.onFailure { Logger.w(TAG, "Своя картинка для уведомления не прочитана", it) }.getOrNull()

    /** Настройки для картинок: объект живёт вне графа Hilt. */
    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface PrefsEntryPoint {
        fun prefs(): com.ogneliss.dialer.settings.Prefs
    }

    private fun query(context: Context, number: String): Info? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.TYPE,
            ContactsContract.PhoneLookup.LABEL,
            ContactsContract.PhoneLookup.DISPLAY_NAME
        )
        return context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val photoUri = c.getString(0) ?: c.getString(1)
            val label = if (c.isNull(2)) null else ContactsContract.CommonDataKinds.Phone
                .getTypeLabel(context.resources, c.getInt(2), c.getString(3))
                ?.toString()?.takeIf { it.isNotBlank() }
            val name = c.getString(4)?.takeIf { it.isNotBlank() }
            Info(photoUri?.let { portrait(context, Uri.parse(it)) }, label, name)
        }
    }

    /*
     * КРУГЛЫЙ ПОРТРЕТ (2.8.3, снимок автора: в уведомлении SMS портрет квадратный).
     * Обычный вид уведомления на части прошивок (ColorOS) рисует снимок как есть;
     * круг — как в списках приложения и у CallStyle. Середина квадрата, края — прозрачные.
     */
    fun portrait(context: Context, uri: Uri): Bitmap? = decode(context, uri)?.let(::circle)

    private fun circle(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawCircle(side / 2f, side / 2f, side / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, (side - src.width) / 2f, (side - src.height) / 2f, paint)
        return out
    }

    /** Снимок не больше [MAX_SIDE_PX] по стороне: уведомлению больше не нужно, а память — не лишняя. */
    private fun decode(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val side = maxOf(bounds.outWidth, bounds.outHeight)
        while (side / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        // Прореживание — только степенями двойки: остаток — точным уменьшением
        val big = maxOf(raw.width, raw.height)
        if (big <= MAX_SIDE_PX) return@runCatching raw
        val k = MAX_SIDE_PX.toFloat() / big
        Bitmap.createScaledBitmap(
            raw,
            (raw.width * k).toInt().coerceAtLeast(1),
            (raw.height * k).toInt().coerceAtLeast(1),
            true
        ).also { if (it !== raw) raw.recycle() }
    }.getOrNull()
}

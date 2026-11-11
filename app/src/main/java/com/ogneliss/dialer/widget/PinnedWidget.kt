package com.ogneliss.dialer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.provider.ContactsContract
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.ogneliss.dialer.MainActivity
import com.ogneliss.dialer.R
import com.ogneliss.dialer.call.CallNumberActivity
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.ui.theme.AvatarPalette
import com.ogneliss.dialer.util.PermissionGuard
import com.ogneliss.dialer.util.PhoneFormatter
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors

/**
 * ВИДЖЕТ «ЗАКРЕПЛЁННЫЕ» (2.2.0) — звонок в одно касание с главного экрана.
 *
 * До пяти номеров, закреплённых в журнале (Настройки → Закреплённые),
 * в том же порядке: портрет (фото из книги или буквы в тоне собеседника —
 * как в приложении) и имя. Касание — сразу вызов через CallNumberActivity:
 * без окна выбора приложения и без открытия журнала. Карту выбирает
 * приложение по своим правилам (как при звонке из журнала).
 *
 * Пусто — значок приложения: касание открывает приложение.
 * Обновляется при изменении закреплённых (Prefs.setPinnedNumbers),
 * после перезагрузки и при добавлении виджета. Расписания нет.
 */
object PinnedWidget {

    private const val TAG = "PinnedWidget"
    private const val SLOTS = 5

    /** Портрет рисуется в этом размере (px): чётко на xxxhdpi, не тяжело для RemoteViews. */
    private const val AVATAR_PX = 144

    private val io = Executors.newSingleThreadExecutor()

    private val slotIds = intArrayOf(R.id.pinned_slot_0, R.id.pinned_slot_1, R.id.pinned_slot_2, R.id.pinned_slot_3, R.id.pinned_slot_4)
    private val avatarIds = intArrayOf(R.id.pinned_avatar_0, R.id.pinned_avatar_1, R.id.pinned_avatar_2, R.id.pinned_avatar_3, R.id.pinned_avatar_4)
    private val nameIds = intArrayOf(R.id.pinned_name_0, R.id.pinned_name_1, R.id.pinned_name_2, R.id.pinned_name_3, R.id.pinned_name_4)

    fun update(context: Context) {
        val app = context.applicationContext
        io.execute {
            runCatching { render(app) }.onFailure { Logger.w(TAG, "Виджет закреплённых не обновился", it) }
        }
    }

    private fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, PinnedWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val numbers = pinned(context).take(SLOTS)
        val views = RemoteViews(context.packageName, R.layout.widget_pinned)
        views.setViewVisibility(R.id.pinned_empty, if (numbers.isEmpty()) View.VISIBLE else View.GONE)
        if (numbers.isEmpty()) {
            views.setOnClickPendingIntent(
                R.id.pinned_root,
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        for (i in 0 until SLOTS) {
            val number = numbers.getOrNull(i)
            if (number == null) {
                views.setViewVisibility(slotIds[i], View.GONE)
                continue
            }
            val (name, photo) = lookup(context, number)
            val label = name ?: PhoneFormatter.format(number)
            views.setViewVisibility(slotIds[i], View.VISIBLE)
            views.setTextViewText(nameIds[i], shortName(label, name != null))
            views.setImageViewBitmap(avatarIds[i], photo?.let(::circle) ?: initials(label, name != null))
            views.setContentDescription(slotIds[i], context.getString(R.string.widget_pinned_call, label))
            views.setOnClickPendingIntent(
                slotIds[i],
                PendingIntent.getActivity(
                    context, REQUEST_BASE + i,
                    CallNumberActivity.intent(context, number),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        manager.updateAppWidget(ids, views)
    }

    /** Закреплённые номера из настроек; не ответили за секунду — пусто. */
    private fun pinned(context: Context): List<String> = runCatching {
        val prefs = EntryPointAccessors
            .fromApplication(context, SectionWidgets.WidgetEntryPoint::class.java).prefs()
        runBlocking { withTimeoutOrNull(1_000L) { prefs.pinnedNumbers.first() } }
    }.getOrNull().orEmpty()

    /** Имя и фото из книги. Без доступа к книге — только номер. */
    private fun lookup(context: Context, number: String): Pair<String?, Bitmap?> {
        if (!PermissionGuard.canReadContacts(context)) return null to null
        return runCatching {
            val found = context.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null }
            val photo = found?.second?.let { uri ->
                context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)
            }
            found?.first to photo
        }.getOrDefault(null to null)
    }

    /** Под портретом — имя без фамилии: в ячейке помещается одно слово. */
    private fun shortName(label: String, isName: Boolean): String =
        if (isName) label.trim().substringBefore(' ') else label

    /** Фото — кругом. */
    private fun circle(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(AVATAR_PX, AVATAR_PX, Bitmap.Config.ARGB_8888)
        val scaled = Bitmap.createScaledBitmap(src, AVATAR_PX, AVATAR_PX, true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawCircle(AVATAR_PX / 2f, AVATAR_PX / 2f, AVATAR_PX / 2f, paint)
        return out
    }

    /** Буквы в тоне собеседника, как портрет без фото в приложении; у номера — значок не нужен, цифра. */
    private fun initials(label: String, isName: Boolean): Bitmap {
        val out = Bitmap.createBitmap(AVATAR_PX, AVATAR_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AvatarPalette.tone(label).toArgb() }
        canvas.drawCircle(AVATAR_PX / 2f, AVATAR_PX / 2f, AVATAR_PX / 2f, fill)
        val letters = if (isName) {
            label.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        } else "#"
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = AVATAR_PX * 0.4f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val y = AVATAR_PX / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(letters, AVATAR_PX / 2f, y, text)
        return out
    }

    private const val REQUEST_BASE = 0x5057
}

/** Виджет «Закреплённые». */
class PinnedWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        PinnedWidget.update(context)
    }
}

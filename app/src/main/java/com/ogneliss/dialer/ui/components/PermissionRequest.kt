package com.ogneliss.dialer.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.ogneliss.dialer.log.Logger
import com.ogneliss.dialer.util.AppPermissions

/**
 * ЗАПРОС ОДНОГО РАЗРЕШЕНИЯ СИСТЕМНЫМ ОКНОМ (2.1.0).
 *
 * Было: кнопка «Разрешить» в пустом журнале открывала настройки
 * приложения, а в пустых контактах — только сбрасывала поиск;
 * разрешение не запрашивал никто, и под кнопкой висел абзац о том,
 * зачем доступ нужен. Стало: кнопка показывает системный запрос.
 *
 * Система больше не показывает окно (дважды отказали, «Больше
 * не спрашивать») — ответ приходит сразу «нет», и тогда открываются
 * настройки приложения: выдать доступ теперь можно только там.
 * Отличие от обычного отказа — по shouldShowRequestPermissionRationale:
 * после отказа в окне он true, а когда окна уже нет — false.
 *
 * @param onGranted разрешение выдано — перечитать данные
 * @return что вызвать по нажатию кнопки
 */
@Composable
fun rememberPermissionRequest(permission: String, onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            granted -> onGranted()
            !canAskAgain(context, permission) -> {
                Logger.i("Permissions", "Окна запроса нет — настройки приложения: ${permission.substringAfterLast('.')}")
                AppPermissions.openAppSettings(context)
            }
            // Отказ в самом окне — человек решил; повторять незачем
            else -> Unit
        }
    }
    return remember(launcher, permission) { { launcher.launch(permission) } }
}

/** Покажет ли система окно запроса ещё раз. */
private fun canAskAgain(context: Context, permission: String): Boolean {
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

/** Activity под контекстом Compose (обычно им и является, но бывает обёрнут). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

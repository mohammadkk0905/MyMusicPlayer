package com.mohammadkk.mymusicplayer.extensions

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.TransactionTooLargeException
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.util.Util.isOnMainThread
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.providers.MusicPlaybackQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

val Context.notificationManager: NotificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
val Context.isLandscape: Boolean get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
val Context.musicPlaybackQueue: MusicPlaybackQueue get() = MusicPlaybackQueue.getInstance(this)

fun Context.hasPermission(permission: String?): Boolean {
    if (permission == null) return false
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
fun Activity.hasNotificationApi(): Boolean {
    if (Constant.isTiramisuPlus()) {
        if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                Constant.PERMISSION_REQUEST_NOTIFICATION
            )
            return false
        }
    }
    return true
}
fun <T> Fragment.collectImmediately(stateFlow: StateFlow<T>, block: (T) -> Unit) {
    block(stateFlow.value)
    launch { stateFlow.collect(block) }
}
private fun Fragment.launch(
    state: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend CoroutineScope.() -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(state, block) }
}
fun Context.getColorCompat(@ColorRes id: Int): Int {
    return ResourcesCompat.getColor(resources, id, theme)
}
fun Context.getDrawableCompat(@DrawableRes drawableRes: Int): Drawable {
    return requireNotNull(ContextCompat.getDrawable(this, drawableRes)) {
        "Invalid resource: Drawable was null"
    }
}
fun Context.getAttrColorCompat(@AttrRes attrRes: Int): Int {
    val resolvedAttr = TypedValue()
    theme.resolveAttribute(attrRes, resolvedAttr, true)
    val color = if (resolvedAttr.resourceId != 0) {
        resolvedAttr.resourceId
    } else {
        resolvedAttr.data
    }
    return getColorCompat(color)
}
fun AppCompatActivity.overridePendingTransitionCompat(isClose: Boolean, enterAnim: Int, exitAnim: Int) {
    if (Constant.isUpsideDownCakePlus()) {
        overrideActivityTransition(
            if (isClose) AppCompatActivity.OVERRIDE_TRANSITION_CLOSE else AppCompatActivity.OVERRIDE_TRANSITION_CLOSE,
            enterAnim, exitAnim
        )
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(enterAnim, exitAnim)
    }
}
fun Context.isMassUsbDeviceConnected(): Boolean {
    val usbManager = getSystemService(Context.USB_SERVICE) as? UsbManager
    val devices = usbManager?.deviceList ?: return false
    for (mDeviceName in devices) {
        val dc = mDeviceName.value
        for (i in 0 until dc.interfaceCount) {
            if (dc.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                return true
            }
        }
    }
    return false
}
fun Activity.shareSongsIntent(songs: List<Song>) {
    if (songs.size == 1) {
        shareSongIntent(songs.first())
    } else {
        Constant.ensureBackgroundThread {
            val uriPaths = arrayListOf<Uri>()
            songs.forEach { uriPaths.add(it.toProviderUri(this)) }
            Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                type = "audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uriPaths)
                try {
                    startActivity(Intent.createChooser(this, getString(R.string.share)))
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.no_app_found)
                } catch (e: RuntimeException) {
                    if (e.cause is TransactionTooLargeException) {
                        toast(R.string.maximum_share_reached)
                    } else {
                        errorToast(e)
                    }
                } catch (e: Exception) {
                    errorToast(e)
                }
            }
        }
    }
}
fun Activity.shareSongIntent(song: Song) {
    Constant.ensureBackgroundThread {
        val newUri = song.toProviderUri(this)
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, newUri)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            grantUriPermission("android", newUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                startActivity(Intent.createChooser(this, getString(R.string.share)))
            } catch (e: ActivityNotFoundException) {
                toast(R.string.no_app_found)
            } catch (e: RuntimeException) {
                if (e.cause is TransactionTooLargeException) {
                    toast(R.string.maximum_share_reached)
                } else {
                    errorToast(e)
                }
            } catch (e: Exception) {
                errorToast(e)
            }
        }
    }
}
fun Context.errorToast(exception: Exception, length: Int = Toast.LENGTH_LONG) {
    errorToast(exception.toString(), length)
}
fun Context.errorToast(message: String?, length: Int = Toast.LENGTH_LONG) {
    if (message == null) return
    toast(String.format(getString(R.string.error_symbol), message), length)
}
fun Context.toast(id: Int, length: Int = Toast.LENGTH_SHORT) {
    toast(getString(id), length)
}
fun Context.toast(message: String?, length: Int = Toast.LENGTH_SHORT) {
    try {
        if (isOnMainThread()) {
            doToast(this, message ?: "null", length)
        } else {
            Handler(Looper.getMainLooper()).post {
                doToast(this, message ?: "null", length)
            }
        }
    } catch (ignored: Exception) {
    }
}
private fun doToast(context: Context, message: String, length: Int) {
    if (context is Activity) {
        if (!context.isFinishing && !context.isDestroyed) {
            Toast.makeText(context, message, length).show()
        }
    } else {
        Toast.makeText(context, message, length).show()
    }
}
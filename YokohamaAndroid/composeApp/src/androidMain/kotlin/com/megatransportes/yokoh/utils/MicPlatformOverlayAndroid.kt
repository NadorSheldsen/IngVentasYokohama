package com.megatransportes.yokoh.utils

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

actual object MicPlatformOverlay {
    private var activityRef: WeakReference<Activity?> = WeakReference(null)
    private var overlayView: View? = null

    actual fun setHost(host: Any?) {
        activityRef = WeakReference(host as? Activity)
    }

    actual fun show(centerWindow: Offset, circleDp: Dp) {
        val activity = activityRef.get() ?: return
        val decor = activity.window?.decorView as? ViewGroup ?: return

        // remove previous
        overlayView?.let { decor.removeView(it); overlayView = null }

        val density = activity.resources.displayMetrics.density
        val sizePx = (circleDp.value * density).roundToInt()

        val v = View(activity)
        val gd = GradientDrawable()
        gd.shape = GradientDrawable.OVAL
        gd.setColor(0x40FF0000.toInt()) // translucent red
        v.background = gd
        v.isClickable = false
        v.isFocusable = false

        val params = FrameLayout.LayoutParams(sizePx, sizePx)
        // add to decor and position using translation (window coordinates)
        decor.addView(v, params)
        val x = (centerWindow.x - sizePx / 2f)
        val y = (centerWindow.y - sizePx / 2f)
        v.translationX = x
        v.translationY = y
        ViewCompat.setElevation(v, 1000f)

        overlayView = v
    }

    actual fun hide() {
        val activity = activityRef.get() ?: return
        val decor = activity.window?.decorView as? ViewGroup ?: return
        overlayView?.let {
            decor.removeView(it)
            overlayView = null
        }
    }
}

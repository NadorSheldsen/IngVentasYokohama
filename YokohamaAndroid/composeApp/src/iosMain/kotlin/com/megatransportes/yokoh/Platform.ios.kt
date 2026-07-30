package com.megatransportes.yokoh

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIDevice
import platform.UIKit.UIScrollView
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
actual fun getPlatformName(): String = "iOS"

actual fun disableIosScrollBounce(view: Any?) {
    val rootView = view as? UIView ?: return
    fun findScrollView(v: UIView): UIScrollView? {
        if (v is UIScrollView) return v
        for (i in 0 until v.subviews.count) {
            val subview = v.subviews[i]
            val found = findScrollView(subview)
            if (found != null) return found
        }
        return null
    }
    val scrollView = findScrollView(rootView)
    scrollView?.let {
        it.bounces = false
        it.alwaysBounceVertical = false
        it.alwaysBounceHorizontal = false
        println("[iOS] UIScrollView bounces disabled")
    }
}
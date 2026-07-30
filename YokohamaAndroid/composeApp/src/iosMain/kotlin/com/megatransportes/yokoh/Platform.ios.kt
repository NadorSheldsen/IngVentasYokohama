package com.megatransportes.yokoh

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIDevice
import platform.UIKit.UIScrollView
import platform.UIKit.UIView
import platform.UIKit.UIApplication

@OptIn(ExperimentalForeignApi::class)
actual fun getPlatformName(): String = "iOS"

actual fun disableIosScrollBounce() {
    val rootView = UIApplication.sharedApplication.keyWindow?.rootViewController?.view ?: return
    fun findScrollView(v: UIView): UIScrollView? {
        if (v is UIScrollView) return v
        for (i in 0 until v.subviews.size) {
            val subview = v.subviews[i] as? UIView ?: continue
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
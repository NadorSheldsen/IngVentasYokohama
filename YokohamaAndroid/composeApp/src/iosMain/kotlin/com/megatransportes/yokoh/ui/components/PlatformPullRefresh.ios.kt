package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIRefreshControl
import platform.UIKit.UIScrollView
import platform.UIKit.UIView
import platform.darwin.NSObject

private class RefreshTarget(private val onRefresh: () -> Unit) : NSObject() {
    fun handleRefresh(sender: Any?) {
        onRefresh()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun findUIScrollViews(view: UIView): List<UIScrollView> {
    val result = mutableListOf<UIScrollView>()
    if (view is UIScrollView) {
        result.add(view)
    }
    for (subview in view.subviews) {
        val sv = subview as? UIView ?: continue
        result.addAll(findUIScrollViews(sv))
    }
    return result
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    val scrollViews = remember { mutableListOf<UIScrollView>() }
    val refreshControl = remember { UIRefreshControl() }
    val target = remember { RefreshTarget(onRefresh) }

    Box(modifier = modifier.fillMaxSize()) {
        content()
    }

    LaunchedEffect(Unit) {
        delay(200)
        val window = UIApplication.sharedApplication.keyWindow
        if (window != null) {
            val found = findUIScrollViews(window)
            scrollViews.addAll(found)
            found.forEach { sv ->
                sv.refreshControl = refreshControl
                sv.alwaysBounceVertical = true
            }
        }
        refreshControl.addTarget(
            target,
            action = NSSelectorFromString("handleRefresh:"),
            forControlEvents = UIControlEventValueChanged
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            refreshControl.removeTarget(
                target,
                action = NSSelectorFromString("handleRefresh:"),
                forControlEvents = UIControlEventValueChanged
            )
            scrollViews.forEach { sv ->
                sv.refreshControl = null
            }
            scrollViews.clear()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            refreshControl.beginRefreshing()
        } else {
            refreshControl.endRefreshing()
        }
    }
}

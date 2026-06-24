package com.megatransportes.yokoh.utils

import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual object OpenFileUtils {
    private var lastDocController: UIDocumentInteractionController? = null

    private val previewDelegate = object : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
        override fun documentInteractionControllerViewControllerForPreview(
            controller: UIDocumentInteractionController
        ): UIViewController {
            return topViewController() ?: UIViewController()
        }
    }

    actual fun openFile(filePath: String, mimeType: String, platformContext: Any?) {
        val url = NSURL.fileURLWithPath(filePath)
        dispatch_async(dispatch_get_main_queue()) {
            val controller = UIDocumentInteractionController.interactionControllerWithURL(url)
            controller.delegate = previewDelegate
            lastDocController = controller
            val vc = (platformContext as? UIViewController) ?: topViewController()
            val view = vc?.view
            if (vc != null && view != null) {
                controller.presentOptionsMenuFromRect(view.bounds, view, true)
            } else {
                controller.presentPreviewAnimated(true)
            }
        }
    }

    actual fun shareFile(filePath: String, mimeType: String, platformContext: Any?) {
        val url = NSURL.fileURLWithPath(filePath)
        dispatch_async(dispatch_get_main_queue()) {
            val activity = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
            val vc = (platformContext as? UIViewController) ?: topViewController()
            vc?.presentViewController(activity, true, null)
        }
    }

    actual fun openUrl(url: String, platformContext: Any?) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.openURL(nsUrl)
        }
    }

    private fun topViewController(): UIViewController? {
        val keyWindow = UIApplication.sharedApplication.keyWindow
        var vc = keyWindow?.rootViewController
        while (vc?.presentedViewController != null) {
            vc = vc.presentedViewController
        }
        return vc
    }
}

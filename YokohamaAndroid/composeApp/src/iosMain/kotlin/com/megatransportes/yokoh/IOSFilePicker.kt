package com.megatransportes.yokoh.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.UIKit.*
import platform.Foundation.*
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
class IOSFilePicker : FilePicker {
    private var continuation: (kotlin.coroutines.Continuation<ByteArray?>)? = null

    override suspend fun pickImage(): ByteArray? =
        suspendCancellableCoroutine { cont ->
            continuation = cont
            dispatch_async(dispatch_get_main_queue()) {
                val pickerDelegate = PickerDelegate { bytes, picker ->
                    continuation?.resume(bytes)
                    continuation = null
                    picker.dismissViewControllerAnimated(true, null)
                }

                val picker = UIImagePickerController().apply {
                    sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
                    delegate = pickerDelegate
                }
                topViewController()?.presentViewController(picker, true, null)
            }
        }

    private class PickerDelegate(val callback: (ByteArray?, UIImagePickerController) -> Unit) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
        
        override fun imagePickerController(
            picker: UIImagePickerController,
            didFinishPickingMediaWithInfo: Map<Any?, *>
        ) {
            val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
            val data = image?.let { UIImagePNGRepresentation(it) }
            val bytes = data?.let { nsdata ->
                memScoped {
                    val bytes = ByteArray(nsdata.length.toInt())
                    bytes.usePinned { pinned ->
                        nsdata.bytes?.let { source ->
                            // En iosArm64, memcpy espera un ULong para la longitud. 
                            // Si marca error en 'nsdata.length', cámbialo a 'nsdata.length.toULong()'
                            memcpy(pinned.addressOf(0), source, nsdata.length)
                        }
                    }
                    bytes
                }
            }
            callback(bytes, picker)
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            callback(null, picker)
        }
    }

    private fun topViewController(): UIViewController? {
        val keyWindow = UIApplication.sharedApplication.keyWindow
        var vc = keyWindow?.rootViewController
        while (vc?.presentedViewController != null) {
            vc = vc?.presentedViewController
        }
        return vc
    }
}
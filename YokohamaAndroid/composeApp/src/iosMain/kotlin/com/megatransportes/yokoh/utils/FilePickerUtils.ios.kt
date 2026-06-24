package com.megatransportes.yokoh.utils

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSString
import platform.Foundation.NSDictionary
import platform.Foundation.NSArray

import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerCameraCaptureMode
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.memcpy
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import kotlin.coroutines.resume
import kotlin.system.getTimeMillis

@OptIn(ExperimentalForeignApi::class)
actual class FilePickerUtils : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol,
    UIDocumentPickerDelegateProtocol {

    private var imageContinuation: (kotlin.coroutines.Continuation<FileData?>)? = null
    private var fileContinuation: (kotlin.coroutines.Continuation<FileData?>)? = null
    private var shouldShowGalleryAfterCancellingCamera = false

    actual suspend fun pickImageFile(): FileData? = suspendCancellableCoroutine { cont ->
        dispatch_async(dispatch_get_main_queue()) {
            imageContinuation = cont
            val hasCamera = UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            )
            if (hasCamera) {
                shouldShowGalleryAfterCancellingCamera = true
                val picker = UIImagePickerController().apply {
                    sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                    cameraCaptureMode = UIImagePickerControllerCameraCaptureMode.UIImagePickerControllerCameraCaptureModePhoto
                    delegate = this@FilePickerUtils
                }
                topViewController()?.presentViewController(picker, true, null)
            } else {
                val picker = UIImagePickerController().apply {
                    sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
                    delegate = this@FilePickerUtils
                }
                topViewController()?.presentViewController(picker, true, null)
            }
        }
    }

    actual suspend fun pickFile(vararg extensions: String): FileData? = suspendCancellableCoroutine { cont ->
        fileContinuation = cont
        val types = if (extensions.isNotEmpty()) {
            extensions.map { extensionToUti(it) }.distinct()
        } else {
            listOf("public.data")
        }
        dispatch_async(dispatch_get_main_queue()) {
            val picker = UIDocumentPickerViewController(documentTypes = types, inMode = UIDocumentPickerMode.UIDocumentPickerModeImport)
            picker.delegate = this@FilePickerUtils
            picker.allowsMultipleSelection = false
            topViewController()?.presentViewController(picker, true, null)
        }
    }

    @ObjCAction
    fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: NSDictionary?
    ) {
        shouldShowGalleryAfterCancellingCamera = false
        val image = didFinishPickingMediaWithInfo?.objectForKey(UIImagePickerControllerOriginalImage) as? UIImage

        val data = image?.let { UIImageJPEGRepresentation(it, 0.75) }
        val bytes = data?.let { NSDataToByteArray(it) }
        val name = "imagen_${getTimeMillis()}.jpg"

        val fileData = if (bytes != null) {
            FileData(
                name = name,
                size = bytes.size.toLong(),
                mimeType = "image/jpeg",
                data = bytes
            )
        } else {
            null
        }

        imageContinuation?.resume(fileData)
        imageContinuation = null
        picker.dismissViewControllerAnimated(true, null)
    }

    @ObjCAction
    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
        if (shouldShowGalleryAfterCancellingCamera) {
            shouldShowGalleryAfterCancellingCamera = false
            dispatch_async(dispatch_get_main_queue()) {
                val picker = UIImagePickerController().apply {
                    sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
                    delegate = this@FilePickerUtils
                }
                topViewController()?.presentViewController(picker, true, null)
            }
        } else {
            imageContinuation?.resume(null)
            imageContinuation = null
        }
    }

    @ObjCAction
    fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: NSArray?) {
        val url = if (didPickDocumentsAtURLs != null && didPickDocumentsAtURLs.count > 0u) {
            didPickDocumentsAtURLs.objectAtIndex(0u) as? NSURL
        } else null
        resumeFilePick(url)
    }

    @ObjCAction
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentAtURL: NSURL) {
        resumeFilePick(didPickDocumentAtURL)
    }

    @ObjCAction
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        fileContinuation?.resume(null)
        fileContinuation = null
    }

    private fun resumeFilePick(url: NSURL?) {
        if (url != null) {
            val started = url.startAccessingSecurityScopedResource()
            val path = url.path ?: ""
            val bytes = readFileBytes(path)
            val name = fileNameFromPath(path)
            val ext = fileExtensionFromPath(path)
            val mimeType = mimeTypeForExtension(ext)

            val fileData = if (bytes != null) {
                FileData(
                    name = name,
                    size = bytes.size.toLong(),
                    mimeType = mimeType,
                    data = bytes
                )
            } else {
                null
            }

            fileContinuation?.resume(fileData)
            fileContinuation = null
            if (started) {
                url.stopAccessingSecurityScopedResource()
            }
        } else {
            fileContinuation?.resume(null)
            fileContinuation = null
        }
    }

    private fun topViewController(): platform.UIKit.UIViewController? {
        val keyWindow = UIApplication.sharedApplication.keyWindow
        var vc = keyWindow?.rootViewController
        while (vc?.presentedViewController != null) {
            vc = vc.presentedViewController
        }
        return vc
    }

    private fun fileNameFromPath(path: String): String {
        val normalized = path.trimEnd('/')
        return normalized.substringAfterLast('/', normalized).ifBlank { "archivo" }
    }

    private fun fileExtensionFromPath(path: String): String {
        val name = fileNameFromPath(path)
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex in 1 until name.lastIndex) name.substring(dotIndex + 1) else ""
    }

    private fun readFileBytes(path: String): ByteArray? {
        val handler = fopen(path, "rb") ?: return null
        try {
            if (fseek(handler, 0, SEEK_END) != 0) return null
            val size = ftell(handler)
            if (size < 0) return null
            if (fseek(handler, 0, SEEK_SET) != 0) return null
            val bytes = ByteArray(size.toInt())
            bytes.usePinned { pinned ->
                val readAmount = fread(pinned.addressOf(0), bytes.size.toULong(), 1u, handler)
                if (readAmount != 1uL) return null
            }
            return bytes
        } finally {
            fclose(handler)
        }
    }

    private fun extensionToUti(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "public.jpeg"
            "png" -> "public.png"
            "gif" -> "com.compuserve.gif"
            "pdf" -> "com.adobe.pdf"
            "txt" -> "public.plain-text"
            "doc" -> "com.microsoft.word.doc"
            "docx" -> "org.openxmlformats.wordprocessingml.document"
            else -> "public.data"
        }
    }

    private fun NSDataToByteArray(data: NSData): ByteArray {
        val length = data.length.toInt()
        val bytes = ByteArray(length)
        memScoped {
            bytes.usePinned { pinned ->
                data.bytes?.let { source ->
                    memcpy(pinned.addressOf(0), source, data.length)
                }
            }
        }
        return bytes
    }

    private fun mimeTypeForExtension(extension: String): String? {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }
}

actual fun createFilePickerUtils(): FilePickerUtils {
    return FilePickerUtils()
}

@Composable
actual fun InitializeFilePickerIfNeeded() {
    // No initialization required on iOS.
}
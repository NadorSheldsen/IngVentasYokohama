package com.megatransportes.yokoh.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import com.megatransportes.yokoh.platform.ActivityHolder

actual object OpenFileUtils {
    actual fun openFile(filePath: String, mimeType: String, platformContext: Any?) {
        val context = when (platformContext) {
            is Context -> platformContext
            else -> ActivityHolder.activity ?: throw IllegalArgumentException("On Android you must pass a Context as platformContext to OpenFileUtils.openFile or have ActivityHolder initialized")
        }

        val file = File(filePath)
        val uri = FileProvider.getUriForFile(context, "com.megatransportes.yokoh.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    actual fun shareFile(filePath: String, mimeType: String, platformContext: Any?) {
        val context = when (platformContext) {
            is Context -> platformContext
            else -> ActivityHolder.activity ?: throw IllegalArgumentException("On Android you must pass a Context as platformContext to OpenFileUtils.shareFile or have ActivityHolder initialized")
        }
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(context, "com.megatransportes.yokoh.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Enviar PDF vía")
        context.startActivity(chooser)
    }

    actual fun openUrl(url: String, platformContext: Any?) {
        val context = when (platformContext) {
            is Context -> platformContext
            else -> ActivityHolder.activity ?: throw IllegalArgumentException("On Android you must pass a Context as platformContext to OpenFileUtils.openUrl or have ActivityHolder initialized")
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

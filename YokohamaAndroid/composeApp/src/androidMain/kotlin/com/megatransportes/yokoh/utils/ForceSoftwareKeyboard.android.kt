package com.megatransportes.yokoh.utils

import android.content.Context
import android.view.inputmethod.InputMethodManager
import com.megatransportes.yokoh.platform.ActivityHolder

actual fun forceShowSoftwareKeyboard(platformContext: Any?): Boolean {
    val activity = ActivityHolder.activity ?: return false
    val context = platformContext as? Context ?: activity
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
    val view = activity.currentFocus ?: activity.window?.decorView ?: return false
    return try {
        imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
    } catch (_: Exception) {
        false
    }
}
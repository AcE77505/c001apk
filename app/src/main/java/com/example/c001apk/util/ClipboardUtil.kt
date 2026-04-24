package com.example.c001apk.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

object ClipboardUtil {
    fun copyText(context: Context, text: String, showToast: Boolean = true) {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        ClipData.newPlainText("c001apk text", text)?.let { clipboardManager.setPrimaryClip(it) }
        if (showToast)
            ToastUtil.toast(context, "已复制: $text")
    }

    fun copySensitiveText(
        context: Context,
        label: String,
        text: String,
        toastText: String
    ) {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(label, text)
        val extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipData.description.extras = extras
        clipboardManager.setPrimaryClip(clipData)
        ToastUtil.toast(context, toastText)
    }
}

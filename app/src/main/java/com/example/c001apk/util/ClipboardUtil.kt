package com.example.c001apk.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper

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
        toastText: String,
        autoClearMs: Long = 60_000
    ) {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(label, text)
        val extras = Bundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipData.description.extras = extras
        clipboardManager.setPrimaryClip(clipData)
        ToastUtil.toast(context, toastText)

        Handler(Looper.getMainLooper()).postDelayed({
            val current = clipboardManager.primaryClip
            val currentText = current?.getItemAt(0)?.coerceToText(context)?.toString()
            if (currentText == text) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText(label, ""))
            }
        }, autoClearMs)
    }
}

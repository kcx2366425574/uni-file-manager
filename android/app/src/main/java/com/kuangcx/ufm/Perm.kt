package com.kuangcx.ufm

import android.content.Context
import android.os.Build
import android.os.Environment

object Perm {
    fun ensureAllFiles(context: Context): Boolean =
        Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
}

package com.kuangcx.ufm.core

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager

data class Volume(val label: String, val path: String, val removable: Boolean, val availableBytes: Long)

object StorageVolumes {
    fun list(context: Context): List<Volume> {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val primary = Environment.getExternalStorageDirectory()
        val out = mutableListOf(
            Volume("内部存储", primary.absolutePath, false, primary.usableSpace)
        )
        for (v in sm.storageVolumes) {
            val dir = v.directory ?: continue
            if (dir.absolutePath == primary.absolutePath) continue
            out += Volume(
                v.getDescription(context) ?: dir.name,
                dir.absolutePath,
                v.isRemovable,
                dir.usableSpace
            )
        }
        return out
    }
}

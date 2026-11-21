package com.kuangcx.ufm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "Shell")
class ShellPlugin : Plugin() {

    private fun hasMediaPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }

    @PluginMethod
    fun permissionState(call: PluginCall) {
        val sdk = Build.VERSION.SDK_INT
        val allFiles = if (sdk >= 30) Environment.isExternalStorageManager() else false
        val install = if (sdk >= 26) context.packageManager.canRequestPackageInstalls() else true
        val snap = PermState.resolve(sdk, allFiles, hasMediaPermission(), install)
        call.resolve(JSObject().apply {
            put("allFiles", snap.allFiles)
            put("mediaImages", snap.mediaImages)
            put("installPackages", snap.installPackages)
            put("sdk", snap.sdk)
        })
    }

    @PluginMethod
    fun openAllFilesSettings(call: PluginCall) {
        if (Build.VERSION.SDK_INT >= 30) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
        call.resolve()
    }

    @PluginMethod
    fun openInstallSettings(call: PluginCall) {
        if (Build.VERSION.SDK_INT >= 26) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
        call.resolve()
    }
}

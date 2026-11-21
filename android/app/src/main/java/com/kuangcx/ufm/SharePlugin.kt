package com.kuangcx.ufm

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.File

@CapacitorPlugin(name = "Share")
class SharePlugin : Plugin() {

    private fun uriOf(path: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))

    @PluginMethod
    fun shareFiles(call: PluginCall) {
        val arr = call.getArray("paths")
        val paths = ArrayList<String>()
        if (arr != null) { for (i in 0 until arr.length()) paths.add(arr.getString(i)) }
        if (paths.isEmpty()) { call.reject("no files", "E_NOENT"); return }
        try {
            val uris = ArrayList(paths.map { uriOf(it) })
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "application/octet-stream"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(Intent.createChooser(intent, "分享到"))
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "share failed", "E_IO", e)
        }
    }

    @PluginMethod
    fun openFile(call: PluginCall) {
        val path = call.getString("path") ?: return call.reject("path required", "E_NOENT")
        val mime = call.getString("mime") ?: "*/*"
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriOf(path), mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, "打开方式"))
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "no app can open this file", "E_IO", e)
        }
    }
}

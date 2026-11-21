package com.kuangcx.ufm

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.io.File

/**
 * Receives files shared into this app from another app via ACTION_VIEW / ACTION_SEND.
 * The incoming Uri is streamed into the app cache because the granting app may revoke its
 * read permission at any moment, which would make lazy reads fail mid-render.
 */
@CapacitorPlugin(name = "Open")
class OpenPlugin : Plugin() {

    @Volatile
    private var pendingUri: Uri? = null

    @Volatile
    private var pendingMime: String? = null

    /** Called from MainActivity before the WebView is ready. */
    fun accept(intent: Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            else -> null
        } ?: return
        pendingUri = uri
        pendingMime = intent.type ?: context.contentResolver.getType(uri)
    }

    /** The page asks once on mount; after that the slot is empty so a reload does not re-open. */
    @PluginMethod
    fun takePending(call: PluginCall) {
        val uri = pendingUri
        if (uri == null) {
            call.resolve(JSObject().apply { put("uri", "") })
            return
        }
        bridge.execute {
            try {
                val name = displayName(uri)
                val temp = File(context.cacheDir, "incoming")
                if (!temp.exists()) temp.mkdirs()
                val target = File(temp, sanitize(name))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
                } ?: throw java.io.IOException("cannot open the incoming file")
                val mime = pendingMime ?: ""
                pendingUri = null
                pendingMime = null
                call.resolve(JSObject().apply {
                    put("uri", uri.toString())
                    put("path", target.absolutePath)
                    put("name", target.name)
                    put("size", target.length())
                    put("mime", mime)
                })
            } catch (e: Exception) {
                call.reject(e.message ?: "cannot read the shared file", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun install(call: PluginCall) {
        val path = call.getString("path") ?: return call.reject("path required", "E_NOENT")
        bridge.execute {
            try {
                if (android.os.Build.VERSION.SDK_INT >= 26 &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    call.reject("请先允许本应用安装未知来源应用", "E_PERM")
                    return@execute
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", File(path)
                )
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(view)
                call.resolve()
            } catch (e: Exception) {
                call.reject(e.message ?: "无法拉起安装器", "E_IO", e)
            }
        }
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) return c.getString(0) ?: "shared.bin" }
        return uri.lastPathSegment ?: "shared.bin"
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\/:*?\"<>|]"), "_").ifBlank { "shared.bin" }
}

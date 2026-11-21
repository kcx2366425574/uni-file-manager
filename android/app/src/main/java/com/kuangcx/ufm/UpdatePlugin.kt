package com.kuangcx.ufm

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.compareVersionCode
import com.kuangcx.ufm.core.parseVersionCodeFromTag
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@CapacitorPlugin(name = "Update")
class UpdatePlugin : Plugin() {

    @PluginMethod
    fun currentVersion(call: PluginCall) {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        call.resolve(JSObject().apply {
            put("versionName", pi.versionName)
            put("versionCode", if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong())
        })
    }

    /** Compares against the latest GitHub release; returns available=false when up to date. */
    @PluginMethod
    fun check(call: PluginCall) {
        val owner = call.getString("owner") ?: return call.reject("owner required", "E_BAD_NAME")
        val repo = call.getString("repo") ?: return call.reject("repo required", "E_BAD_NAME")
        bridge.execute {
            try {
                val local = localVersionCode()
                val release = fetchLatestRelease(owner, repo)
                    ?: run { call.reject("未发布过 Release", "E_NOENT"); return@execute }
                val remote = parseVersionCodeFromTag(release.tag)
                    ?: run { call.reject("版本号格式不认识：${release.tag}", "E_BAD_NAME"); return@execute }
                call.resolve(JSObject().apply {
                    put("available", compareVersionCode(local, remote) < 0)
                    put("versionName", release.tag.removePrefix("v"))
                    put("versionCode", remote)
                    put("notes", release.notes)
                    put("apkUrl", release.apkUrl)
                })
            } catch (e: Exception) {
                call.reject(e.message ?: "检查更新失败", "E_IO", e)
            }
        }
    }

    /**
     * Enqueues the APK with DownloadManager and returns immediately. The earlier version polled
     * DownloadManager inside bridge.execute for up to ten minutes, which occupied the bridge's
     * single handler thread and made every later plugin call hang — verified on device: a
     * subsequent Update.download never reached the native layer.
     *
     * Completion is reported back through a plugin event; the page listens for it.
     */
    @PluginMethod
    fun download(call: PluginCall) {
        val apkUrl = call.getString("apkUrl") ?: return call.reject("apkUrl required", "E_NOENT")
        val fileName = call.getString("fileName") ?: "update.apk"
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("文件管理器更新")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setMimeType("application/vnd.android.package-archive")
            }
            val id = dm.enqueue(request)
            val target = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            bridge.saveCall(call)
            pendingCallId = call.callbackId
            receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                    unregisterReceiver()
                    val saved = bridge.getSavedCall(pendingCallId)
                    pendingCallId = null
                    dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                        val ok = c.moveToFirst() &&
                            c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                        if (ok) saved?.resolve(JSObject().apply { put("path", target.absolutePath) })
                        else saved?.reject("下载失败", "E_IO")
                        saved?.let { bridge.releaseCall(it) }
                    }
                }
            }
            context.registerReceiver(
                receiver,
                android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            call.reject(e.message ?: "下载失败", "E_IO", e)
        }
    }

    private var receiver: android.content.BroadcastReceiver? = null
    private var pendingCallId: String? = null

    private fun unregisterReceiver() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    override fun handleOnDestroy() {
        unregisterReceiver()
        super.handleOnDestroy()
    }

    /** The user still has to confirm in the system installer; that step is not skippable. */
    @PluginMethod
    fun install(call: PluginCall) {
        val path = call.getString("path") ?: return call.reject("path required", "E_NOENT")
        try {
            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                call.reject("请先允许本应用安装未知来源应用", "E_PERM")
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject(e.message ?: "拉起安装器失败", "E_IO", e)
        }
    }

    private fun localVersionCode(): Long {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }

    private data class Release(val tag: String, val notes: String, val apkUrl: String)

    /** Reads the latest non-draft release; returns null when the repo has none or the call fails. */
    private fun fetchLatestRelease(owner: String, repo: String): Release? {
        val conn = (URL("https://api.github.com/repos/$owner/$repo/releases/latest").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "uni-file-manager")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            if (tag.isBlank()) return null
            val assets = json.optJSONArray("assets") ?: return Release(tag, json.optString("body", ""), "")
            var apk = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name", "").endsWith(".apk")) {
                    apk = a.optString("browser_download_url", "")
                    break
                }
            }
            return Release(tag, json.optString("body", ""), apk)
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val POLL_MS = 500L
        const val MAX_WAIT_MS = 10 * 60 * 1000L
    }
}

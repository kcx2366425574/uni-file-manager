package com.kuangcx.ufm

import android.content.Intent
import android.os.Bundle
import com.getcapacitor.BridgeActivity
import com.kuangcx.ufm.core.OpenIntent

class MainActivity : BridgeActivity() {

    private var openPlugin: OpenPlugin? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(ShellPlugin::class.java)
        registerPlugin(FsPlugin::class.java)
        registerPlugin(OpenPlugin::class.java)
        registerPlugin(SharePlugin::class.java)
        registerPlugin(GroupPlugin::class.java)
        registerPlugin(MediaPlugin::class.java)
        registerPlugin(FavPlugin::class.java)
        registerPlugin(CleanupPlugin::class.java)
        super.onCreate(savedInstanceState)
        openPlugin = bridge.getPlugin("Open")?.instance as? OpenPlugin
        openPlugin?.accept(intent)
        routeToOpenPage(intent)
    }

    /** A second file opened while the app is alive arrives here, not in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openPlugin?.accept(intent)
        routeToOpenPage(intent)
    }

    private fun routeToOpenPage(intent: Intent?) {
        val result = OpenIntent.parse(intent?.action, intent?.dataString, intent?.type)
        if (!result.isOpen) return
        val route = result.route ?: return
        // uni.reLaunch is not reachable from evaluateJavascript (uni is not a global in the
        // uni-app Vue3 H5 runtime), so drive the hash router directly. Route is already
        // percent-encoded by OpenIntent, so it is a safe JS string literal.
        val script = "location.hash = '#" + route + "'"
        bridge.executeOnMainThread {
            var attempts = 0
            fun attempt() {
                if (bridge.webView == null) return
                bridge.webView.evaluateJavascript(script, null)
                if (attempts++ < 5) bridge.webView.postDelayed({ attempt() }, 700)
            }
            bridge.webView?.postDelayed({ attempt() }, 500)
        }
    }
}

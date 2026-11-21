package com.kuangcx.ufm

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(ShellPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}

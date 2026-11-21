package com.kuangcx.ufm.core

import com.getcapacitor.PluginCall

/**
 * Capacitor's PluginCall.getLong only accepts a Long, and a JSON number from JavaScript
 * arrives as Double or Integer, so getLong silently returns null for every numeric argument
 * (verified against PluginCall.java:191-207 — getFloat handles Double/Integer, getLong does not).
 * This helper reads the raw value and converts whatever numeric shape it is.
 */
object CallNum {
    fun long(call: PluginCall, name: String, fallback: Long = 0L): Long {
        val v = call.data?.opt(name) ?: return fallback
        return when (v) {
            is Long -> v
            is Int -> v.toLong()
            is Double -> v.toLong()
            is Float -> v.toLong()
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: fallback
            else -> fallback
        }
    }

    /** Null distinguishes "not supplied" from an explicit 0, which sinceMs needs. */
    fun longOrNull(call: PluginCall, name: String): Long? {
        if (call.data == null || !call.data.has(name) || call.data.isNull(name)) return null
        return long(call, name, 0L)
    }
}

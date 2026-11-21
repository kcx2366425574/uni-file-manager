package com.kuangcx.ufm

import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.kuangcx.ufm.core.FavStore

@CapacitorPlugin(name = "Fav")
class FavPlugin : Plugin() {

    private val store by lazy { FavStore(context) }

    @PluginMethod
    fun add(call: PluginCall) {
        val path = call.getString("path") ?: return call.reject("path required", "E_NOENT")
        val mediaId = com.kuangcx.ufm.core.CallNum.long(call, "mediaId")
        bridge.execute {
            try {
                store.add(path, mediaId)
                call.resolve()
            } catch (e: Exception) {
                call.reject(e.message ?: "favorite failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun remove(call: PluginCall) {
        val path = call.getString("path") ?: return call.reject("path required", "E_NOENT")
        bridge.execute {
            store.remove(path)
            call.resolve()
        }
    }

    @PluginMethod
    fun list(call: PluginCall) {
        bridge.execute {
            try {
                // Prune first: the page must never render a favourite whose file is gone.
                val removed = store.pruneMissing()
                val arr = JSArray()
                store.list().forEach { f ->
                    arr.put(JSObject().apply {
                        put("path", f.path)
                        put("mediaId", f.mediaId)
                        put("addedAt", f.addedAt)
                    })
                }
                call.resolve(JSObject().apply {
                    put("items", arr)
                    put("pruned", removed)
                })
            } catch (e: Exception) {
                call.reject(e.message ?: "list favourites failed", "E_IO", e)
            }
        }
    }

    @PluginMethod
    fun prune(call: PluginCall) {
        bridge.execute {
            call.resolve(JSObject().apply { put("removed", store.pruneMissing()) })
        }
    }
}

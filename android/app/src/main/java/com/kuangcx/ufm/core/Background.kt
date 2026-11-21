package com.kuangcx.ufm.core

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Long-running scans must not run on Capacitor's bridge thread: it is a single HandlerThread, so a
 * multi-second SHA-256 pass over a photo library blocks every other plugin call — measured on device,
 * a subsequent Update.download simply never arrived while an earlier blocking call held the thread.
 *
 * This pool is for work that only touches files and reports back through call.resolve/reject, which
 * Capacitor allows from any thread.
 */
object Background {
    private val pool: ExecutorService = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "ufm-background").apply { isDaemon = true }
    }

    fun submit(task: () -> Unit): Future<*> = pool.submit(task)
}

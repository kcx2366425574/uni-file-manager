package com.kuangcx.ufm.core

/**
 * Difference hash over a 9x8 grayscale patch: for each row every pixel is compared with its
 * right neighbour, and one bit records which is brighter. 64 bits total. Stable against
 * rescaling and mild recompression, which is what photo dedupe needs.
 *
 * Takes a plain IntArray rather than a Bitmap so it unit-tests on the JVM.
 */
fun dhash(gray: IntArray, width: Int, height: Int): Long {
    require(width == 9 && height == 8) { "dhash expects a 9x8 grayscale array" }
    require(gray.size >= width * height) { "gray array too small" }
    var hash = 0L
    var bit = 0
    for (y in 0 until height) {
        for (x in 0 until width - 1) {
            val left = gray[y * width + x]
            val right = gray[y * width + x + 1]
            if (left > right) hash = hash or (1L shl bit)
            bit++
        }
    }
    return hash
}

fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

object Similarity {
    /** Photos within 6 differing bits of 64 are treated as the same scene. */
    const val SIMILAR_THRESHOLD = 6
}

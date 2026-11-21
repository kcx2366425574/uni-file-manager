package com.kuangcx.ufm

data class PermSnapshot(
    val allFiles: Boolean,
    val mediaImages: Boolean,
    val installPackages: Boolean,
    val sdk: Int
)

object PermState {
    /** Below Android 11 there is no all-files switch, so callers must use the legacy path. */
    fun resolve(sdk: Int, allFilesGranted: Boolean, mediaGranted: Boolean, installGranted: Boolean) =
        PermSnapshot(
            allFiles = sdk >= 30 && allFilesGranted,
            mediaImages = mediaGranted,
            installPackages = installGranted,
            sdk = sdk
        )
}

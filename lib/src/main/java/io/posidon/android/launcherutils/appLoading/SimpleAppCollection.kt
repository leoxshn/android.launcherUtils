package io.posidon.android.launcherutils.appLoading

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.UserHandle
import io.posidon.android.launcherutils.AppLoader

abstract class SimpleAppCollection : AppLoader.AppCollection<Nothing?> {

    override fun finalize(context: Context) {}

    fun modifyIcon(
        icon: Drawable,
        packageName: String,
        name: String,
        profile: UserHandle,
    ): Drawable {
        return icon
    }

    final override fun modifyIcon(
        icon: Drawable,
        packageName: String,
        name: String,
        profile: UserHandle,
        expandableBackground: Drawable?
    ): Pair<Drawable, Nothing?> {
        return modifyIcon(icon, packageName, name, profile) to null
    }
}
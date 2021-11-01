package io.posidon.android.launcherutils

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import kotlin.concurrent.thread

/**
 * Loads the apps, instead of just loading the app list,
 * you can decide how your app data is organized, you can
 * have 2 differently sorted lists, or one list of the
 * apps in alphabetical order and the other of them
 * grouped by their first letter...
 * Also you supply the loader with your own app class, so
 * that you don't have to build your project
 * around the loader
 */
class AppLoader <EXTRA_ICON_DATA, APPCollection : AppLoader.AppCollection<EXTRA_ICON_DATA>> (
    val collectionConstructor: (appCount: Int) -> APPCollection
) {

    inline fun async(
        context: Context,
        iconConfig: IconConfig,
        noinline onEnd: (apps: APPCollection) -> Unit
    ) { thread(name = "AppLoader thread", isDaemon = true) { sync(context, iconConfig, onEnd) } }

    fun sync(
        context: Context,
        iconConfig: IconConfig,
        onEnd: (apps: APPCollection) -> Unit
    ) {

        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true
        }
        val maskp = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        val uniformOptions = BitmapFactory.Options().apply {
            inScaled = false
        }

        val packageManager = context.packageManager

        val iconPacks = iconConfig.packPackages.mapNotNull { iconPackPackage ->
            var iconPackInfo: IconTheming.IconPackInfo? = null
            try {
                val themeRes = packageManager.getResourcesForApplication(iconPackPackage)
                iconPackInfo = IconTheming.getIconPackInfo(themeRes, iconPackPackage, uniformOptions)
            } catch (e: Exception) { e.printStackTrace() }
            iconPackInfo
        }

        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

        var appCount = 0
        val appLists = Array(userManager.userProfiles.size) {
            val profile = userManager.userProfiles[it]
            val appList = (context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).getActivityList(null, profile)
            appCount += appList.size
            appList to profile
        }

        val collection = collectionConstructor(appCount)

        for ((appList, profile) in appLists) {
            for (i in appList.indices) addApp(
                collection,
                appList[i],
                iconPacks,
                iconConfig,
                p,
                maskp,
                context,
                profile
            )
        }
        collection.finalize(context)
        onEnd(collection)
    }

    interface AppCollection <EXTRA_ICON_DATA> {
        fun finalize(context: Context)
        fun addApp(context: Context, packageName: String, name: String, profile: UserHandle, label: String, icon: Drawable, extra: ExtraAppInfo<EXTRA_ICON_DATA>)
        fun modifyIcon(icon: Drawable, expandableBackground: Drawable?): Pair<Drawable, EXTRA_ICON_DATA>
    }

    private fun addApp(
        collection: APPCollection,
        appListItem: LauncherActivityInfo,
        iconPacks: List<IconTheming.IconPackInfo>,
        iconConfig: IconConfig,
        p: Paint,
        maskp: Paint,
        context: Context,
        profile: UserHandle,
    ) {

        val packageName = appListItem.applicationInfo.packageName
        val name = appListItem.name

        val label = appListItem.label.let {
            if (it.isNullOrBlank()) packageName
            else it.toString()
        }

        var background: Drawable? = null

        val icon: Drawable = iconPacks.firstNotNullOfOrNull { iconPackInfo ->
            iconPackInfo.getDrawable(appListItem.applicationInfo.packageName, appListItem.name)?.also {
                background = iconPackInfo.getBackground(appListItem.applicationInfo.packageName, appListItem.name)
            }
        } ?: iconPacks.firstNotNullOfOrNull { iconPackInfo ->
            var icon = appListItem.getIcon(iconConfig.density)
            if (iconPackInfo.areUnthemedIconsChanged) {
                try {
                    var orig = Bitmap.createBitmap(
                        icon.intrinsicWidth,
                        icon.intrinsicHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
                    icon.draw(Canvas(orig))
                    val scaledBitmap =
                        Bitmap.createBitmap(iconConfig.size, iconConfig.size, Bitmap.Config.ARGB_8888)
                    Canvas(scaledBitmap).run {
                        if (iconPackInfo.back != null) {
                            val b = iconPackInfo.back!!
                            drawBitmap(
                                b,
                                Rect(0, 0, b.width, b.height),
                                Rect(0, 0, iconConfig.size, iconConfig.size),
                                p
                            )
                        }
                        val scaledOrig =
                            Bitmap.createBitmap(iconConfig.size, iconConfig.size, Bitmap.Config.ARGB_8888)
                        Canvas(scaledOrig).run {
                            val s = (iconConfig.size * iconPackInfo.scaleFactor).toInt()
                            orig = Bitmap.createScaledBitmap(orig, s, s, true)
                            drawBitmap(
                                orig,
                                scaledOrig.width - orig.width / 2f - scaledOrig.width / 2f,
                                scaledOrig.width - orig.width / 2f - scaledOrig.width / 2f,
                                p
                            )
                            if (iconPackInfo.mask != null) {
                                val b = iconPackInfo.mask!!
                                drawBitmap(
                                    b,
                                    Rect(0, 0, b.width, b.height),
                                    Rect(0, 0, iconConfig.size, iconConfig.size),
                                    maskp
                                )
                            }
                        }
                        drawBitmap(
                            Bitmap.createScaledBitmap(scaledOrig, iconConfig.size, iconConfig.size, true),
                            0f,
                            0f,
                            p
                        )
                        if (iconPackInfo.front != null) {
                            val b = iconPackInfo.front!!
                            drawBitmap(
                                b,
                                Rect(0, 0, b.width, b.height),
                                Rect(0, 0, iconConfig.size, iconConfig.size),
                                p
                            )
                        }
                        scaledOrig.recycle()
                    }
                    icon = BitmapDrawable(context.resources, scaledBitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            icon
        } ?: appListItem.getIcon(iconConfig.density) ?: ColorDrawable()
        val (modifiedIcon, extraIconData) = collection.modifyIcon(icon, background)
        collection.addApp(
            context,
            packageName,
            name,
            profile,
            context.packageManager.getUserBadgedLabel(label, profile).toString(),
            context.packageManager.getUserBadgedIcon(modifiedIcon, profile),
            ExtraAppInfo(
                banner = appListItem.applicationInfo.loadBanner(context.packageManager),
                logo = appListItem.applicationInfo.loadLogo(context.packageManager),
                background = background,
                extraIconData = extraIconData,
            )
        )
    }

    class ExtraAppInfo <EXTRA_ICON_DATA> (
        val banner: Drawable?,
        val logo: Drawable?,
        val background: Drawable?,
        val extraIconData: EXTRA_ICON_DATA,
    )
}
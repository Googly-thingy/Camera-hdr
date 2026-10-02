package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
)

object InstalledAppsHelper {

    suspend fun getInstalledLaunchableApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, 0)
        resolveInfos.mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            // Exclude current app from launch targets
            if (pkg == context.packageName) return@mapNotNull null

            val name = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)
            InstalledAppInfo(
                packageName = pkg,
                appName = name,
                icon = icon
            )
        }.sortedBy { it.appName.lowercase() }
    }
}

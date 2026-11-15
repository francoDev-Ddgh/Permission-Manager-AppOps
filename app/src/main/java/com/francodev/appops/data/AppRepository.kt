package com.francodev.appops.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class UserApp(
    val packageName: String,
    val label: String
)

fun loadUserApps(pm: PackageManager): List<UserApp> =
    pm.getInstalledApplications(0)
        .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
        .map { UserApp(packageName = it.packageName, label = pm.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase() }

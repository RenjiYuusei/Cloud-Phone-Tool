package com.cloudphone.tool

import android.graphics.drawable.Drawable

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long?,
    val icon: Drawable
)

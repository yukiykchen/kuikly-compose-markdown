package com.tencent.news.markdown

import android.app.Application
import android.content.Context
import java.io.IOException

actual fun getAssets(filePath: String): String {
    val assetManager = realApplicationContext.assets
    var res = ""
    try {
        val inputStream = assetManager.open(filePath)
        res = inputStream.readBytes().decodeToString()
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return res
}

private val realApplicationContext: Context
    get() {
        val atc = Class.forName("android.app.ActivityThread")
        val app = atc.getMethod("currentApplication").invoke(null) as Application
        return app.applicationContext
    }
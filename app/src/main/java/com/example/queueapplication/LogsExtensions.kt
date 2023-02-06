package com.example.queueapplication

import android.util.Log

private fun getClassNameForLogTag(anyClass: Any): String {
    val tagLengthLimit = 23
    var name = anyClass::class.java.simpleName
    if (name.isEmpty()) {
        name = "ANONYMOUS"
    }

    if (name.length > tagLengthLimit) {
        name = name.substring(0, tagLengthLimit)
    }
    return name
}

fun Any.logE(log: String) {
    if (BuildConfig.DEBUG) {
        Log.e(getClassNameForLogTag(this), log)
    }
}

fun Any.logW(log: String) {
    if (BuildConfig.DEBUG) {
        Log.w(getClassNameForLogTag(this), log)
    }
}
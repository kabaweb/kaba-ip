package com.ipvio.stream.shared

expect fun getPlatformName(): String

object KmpInfo {
    const val MODULE_NAME = "KorpTV-Shared-KMP"
    fun info(): String = "$MODULE_NAME on ${getPlatformName()}"
}

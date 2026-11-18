package com.ipvio.stream.shared

actual fun getPlatformName(): String = "Desktop Java ${System.getProperty("java.version")}"

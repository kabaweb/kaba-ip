package com.ipvio.stream.shared

actual fun getPlatformName(): String = "Android ${android.os.Build.VERSION.SDK_INT}"

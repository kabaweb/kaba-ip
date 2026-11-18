package com.ipvio.stream.shared.prefs

import android.content.Context
import android.content.SharedPreferences

object AndroidPrefsContext {
    lateinit var appContext: Context
}

class AndroidPreferences(private val sp: SharedPreferences) : AppPreferences {
    override fun getString(key: String, defValue: String?): String? = sp.getString(key, defValue)
    override fun putString(key: String, value: String?) {
        sp.edit().putString(key, value).apply()
    }
    override fun getInt(key: String, defValue: Int): Int = sp.getInt(key, defValue)
    override fun putInt(key: String, value: Int) {
        sp.edit().putInt(key, value).apply()
    }
    override fun getLong(key: String, defValue: Long): Long = sp.getLong(key, defValue)
    override fun putLong(key: String, value: Long) {
        sp.edit().putLong(key, value).apply()
    }
    override fun getBoolean(key: String, defValue: Boolean): Boolean = sp.getBoolean(key, defValue)
    override fun putBoolean(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
    }
    override fun contains(key: String): Boolean = sp.contains(key)
    override fun remove(key: String) {
        sp.edit().remove(key).apply()
    }
    override fun clear() {
        sp.edit().clear().apply()
    }
}

actual fun getPreferences(name: String): AppPreferences {
    val sp = AndroidPrefsContext.appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    return AndroidPreferences(sp)
}

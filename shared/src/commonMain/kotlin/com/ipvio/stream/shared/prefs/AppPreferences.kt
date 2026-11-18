package com.ipvio.stream.shared.prefs

interface AppPreferences {
    fun getString(key: String, defValue: String? = null): String?
    fun putString(key: String, value: String?)
    fun getInt(key: String, defValue: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, defValue: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun getBoolean(key: String, defValue: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun contains(key: String): Boolean
    fun remove(key: String)
    fun clear()
}

expect fun getPreferences(name: String): AppPreferences

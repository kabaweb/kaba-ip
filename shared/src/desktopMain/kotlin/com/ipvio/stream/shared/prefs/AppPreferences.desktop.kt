package com.ipvio.stream.shared.prefs

import java.io.File
import java.util.Properties

class DesktopPreferences(private val name: String) : AppPreferences {
    private val dir = File(System.getProperty("user.home"), ".korptv/prefs").apply { mkdirs() }
    private val file = File(dir, "$name.properties")
    private val props = Properties().apply {
        if (file.exists()) {
            runCatching { file.inputStream().use { load(it) } }
        }
    }

    @Synchronized
    private fun save() {
        runCatching { file.outputStream().use { props.store(it, null) } }
    }

    @Synchronized
    override fun getString(key: String, defValue: String?): String? = props.getProperty(key, defValue)

    @Synchronized
    override fun putString(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        save()
    }

    @Synchronized
    override fun getInt(key: String, defValue: Int): Int =
        props.getProperty(key)?.toIntOrNull() ?: defValue

    @Synchronized
    override fun putInt(key: String, value: Int) {
        props.setProperty(key, value.toString())
        save()
    }

    @Synchronized
    override fun getLong(key: String, defValue: Long): Long =
        props.getProperty(key)?.toLongOrNull() ?: defValue

    @Synchronized
    override fun putLong(key: String, value: Long) {
        props.setProperty(key, value.toString())
        save()
    }

    @Synchronized
    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        props.getProperty(key)?.toBooleanStrictOrNull() ?: defValue

    @Synchronized
    override fun putBoolean(key: String, value: Boolean) {
        props.setProperty(key, value.toString())
        save()
    }

    @Synchronized
    override fun contains(key: String): Boolean = props.containsKey(key)

    @Synchronized
    override fun remove(key: String) {
        props.remove(key)
        save()
    }

    @Synchronized
    override fun clear() {
        props.clear()
        save()
    }
}

actual fun getPreferences(name: String): AppPreferences = DesktopPreferences(name)

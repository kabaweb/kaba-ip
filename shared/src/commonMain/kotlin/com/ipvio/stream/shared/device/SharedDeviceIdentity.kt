package com.ipvio.stream.shared.device

import com.ipvio.stream.shared.prefs.getPreferences
import java.security.MessageDigest
import java.util.UUID

object SharedDeviceIdentity {
    private const val PREFS = "ipvio_device"
    private const val KEY_ID = "device_id"
    private const val KEY_MAC = "device_mac"

    fun getDeviceId(): String {
        val sp = getPreferences(PREFS)
        val existing = sp.getString(KEY_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val id = UUID.randomUUID().toString().replace("-", "")
        sp.putString(KEY_ID, id)
        return id
    }

    fun getVirtualMac(): String {
        val sp = getPreferences(PREFS)
        val existing = sp.getString(KEY_MAC, null)
        if (!existing.isNullOrBlank()) return existing

        val devId = getDeviceId()
        val md = MessageDigest.getInstance("MD5").digest(devId.toByteArray())
        val b1 = String.format("%02X", md[0])
        val b2 = String.format("%02X", md[1])
        val b3 = String.format("%02X", md[2])
        val mac = "00:1A:79:$b1:$b2:$b3"

        sp.putString(KEY_MAC, mac)
        return mac
    }

    fun getPairingCode(): String {
        val sp = getPreferences(PREFS)
        val existing = sp.getString("pairing_code", null)
        if (!existing.isNullOrBlank()) return existing

        val mac = getVirtualMac()
        val clean = mac.replace(":", "")
        val hex = if (clean.length >= 6) clean.takeLast(6).uppercase() else clean.uppercase()
        val code = if (hex.length == 6) "${hex.take(3)}-${hex.takeLast(3)}" else hex
        sp.putString("pairing_code", code)
        return code
    }

    fun getDeviceModel(): String {
        val os = System.getProperty("os.name") ?: "Desktop"
        val arch = System.getProperty("os.arch") ?: ""
        return "PC ($os $arch)".trim()
    }
}

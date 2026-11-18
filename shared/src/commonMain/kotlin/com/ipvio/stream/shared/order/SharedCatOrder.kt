package com.ipvio.stream.shared.order

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.ipvio.stream.shared.prefs.getPreferences

object SharedCatOrder {
    private const val PREFS = "ipvio_catorder"
    private const val KEY_MODE = "cat_mode"
    private const val KEY_FAVS = "cat_favs"

    const val MODE_PROVIDER = 0
    const val MODE_AZ = 1

    var mode by mutableIntStateOf(MODE_PROVIDER)
        private set

    val favoriteCategories = mutableStateListOf<String>()

    init {
        val sp = getPreferences(PREFS)
        mode = sp.getInt(KEY_MODE, MODE_PROVIDER)
        val favsRaw = sp.getString(KEY_FAVS, "") ?: ""
        val favsList = favsRaw.split("\n").filter { it.isNotBlank() }
        favoriteCategories.addAll(favsList)
    }

    fun setOrderMode(newMode: Int) {
        mode = newMode
        getPreferences(PREFS).putInt(KEY_MODE, newMode)
    }

    fun toggleFavorite(category: String) {
        if (favoriteCategories.contains(category)) {
            favoriteCategories.remove(category)
        } else {
            favoriteCategories.add(0, category)
        }
        val encoded = favoriteCategories.joinToString("\n")
        getPreferences(PREFS).putString(KEY_FAVS, encoded)
    }

    fun isFavorite(category: String): Boolean = favoriteCategories.contains(category)

    fun sortCategories(rawCategories: List<String>): List<String> {
        val favs = favoriteCategories.filter { it in rawCategories }
        val rest = rawCategories.filter { it !in favs }

        val sortedRest = when (mode) {
            MODE_AZ -> rest.sortedWith(String.CASE_INSENSITIVE_ORDER)
            else -> rest // Ordem original do provedor
        }

        return favs + sortedRest
    }
}

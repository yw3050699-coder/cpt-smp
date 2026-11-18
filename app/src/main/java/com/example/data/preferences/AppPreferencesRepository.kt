package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "eujian_settings")

enum class ThemeMode(val wireValue: String, val label: String) {
    SYSTEM("system", "Sistem"),
    LIGHT("light", "Terang"),
    DARK("dark", "Gelap");

    companion object {
        fun fromWire(value: String?): ThemeMode =
            entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = false
)

class AppPreferencesRepository(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val preferencesFlow: Flow<AppPreferences> = context.appDataStore.data.map { prefs ->
        AppPreferences(
            themeMode = ThemeMode.fromWire(prefs[Keys.THEME_MODE]),
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.wireValue
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }
}

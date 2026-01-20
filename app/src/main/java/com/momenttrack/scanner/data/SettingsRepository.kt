package com.momenttrack.scanner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val apiEndpoint: String = "https://api.momenttrack.com/v1",
    val deviceId: String = "",
    val debounceSeconds: Int = 30,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val autoStartEnabled: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_ENDPOINT = stringPreferencesKey("api_endpoint")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEBOUNCE_SECONDS = intPreferencesKey("debounce_seconds")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATE_ENABLED = booleanPreferencesKey("vibrate_enabled")
        val AUTO_START_ENABLED = booleanPreferencesKey("auto_start_enabled")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            apiEndpoint = preferences[Keys.API_ENDPOINT] ?: "https://api.momenttrack.com/v1",
            deviceId = preferences[Keys.DEVICE_ID] ?: generateDeviceId(),
            debounceSeconds = preferences[Keys.DEBOUNCE_SECONDS] ?: 30,
            soundEnabled = preferences[Keys.SOUND_ENABLED] ?: true,
            vibrateEnabled = preferences[Keys.VIBRATE_ENABLED] ?: true,
            autoStartEnabled = preferences[Keys.AUTO_START_ENABLED] ?: false
        )
    }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[Keys.API_ENDPOINT] = settings.apiEndpoint
            preferences[Keys.DEVICE_ID] = settings.deviceId
            preferences[Keys.DEBOUNCE_SECONDS] = settings.debounceSeconds
            preferences[Keys.SOUND_ENABLED] = settings.soundEnabled
            preferences[Keys.VIBRATE_ENABLED] = settings.vibrateEnabled
            preferences[Keys.AUTO_START_ENABLED] = settings.autoStartEnabled
        }
    }

    suspend fun updateDebounce(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DEBOUNCE_SECONDS] = seconds
        }
    }

    suspend fun updateApiEndpoint(endpoint: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.API_ENDPOINT] = endpoint
        }
    }

    private fun generateDeviceId(): String {
        return "MT-" + UUID.randomUUID().toString().take(8).uppercase()
    }
}

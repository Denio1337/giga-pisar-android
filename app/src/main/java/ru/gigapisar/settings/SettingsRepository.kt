package ru.gigapisar.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(
    name = "giga_pisar_settings",
)

enum class InsertionMode {
    CLIPBOARD,
    TEXT_FIELD,
}

object SettingsRepository {
    private val insertionModeKey =
        stringPreferencesKey("insertion_mode")

    private val virtualButtonVisibleKey =
        booleanPreferencesKey("virtual_button_visible")

    private val volumeKeyEnabledKey =
        booleanPreferencesKey("volume_key_enabled")

    private val vibrationEnabledKey =
        booleanPreferencesKey("vibration_enabled")

    fun insertionMode(context: Context): Flow<InsertionMode> =
        context.settingsDataStore.data.map { preferences ->
            when (
                preferences[insertionModeKey]
            ) {
                InsertionMode.CLIPBOARD.name -> InsertionMode.CLIPBOARD
                else -> InsertionMode.TEXT_FIELD
            }
        }

    fun virtualButtonVisible(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[virtualButtonVisibleKey] ?: true
        }

    suspend fun setInsertionMode(
        context: Context,
        mode: InsertionMode,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[insertionModeKey] = mode.name
        }
    }

    suspend fun setVirtualButtonVisible(
        context: Context,
        visible: Boolean,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[virtualButtonVisibleKey] = visible
        }
    }

    fun volumeKeyEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[volumeKeyEnabledKey] ?: true
        }

    fun vibrationEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { preferences ->
            preferences[vibrationEnabledKey] ?: true
        }

    suspend fun setVolumeKeyEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[volumeKeyEnabledKey] = enabled
        }
    }

    suspend fun setVibrationEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[vibrationEnabledKey] = enabled
        }
    }
}

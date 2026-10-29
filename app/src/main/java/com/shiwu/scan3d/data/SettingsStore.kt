package com.shiwu.scan3d.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shiwu.scan3d.BuildConfig
import kotlinx.coroutines.flow.first

private val Context.shiwuDataStore: DataStore<Preferences> by preferencesDataStore(name = "shiwu_settings")

class SettingsStore(private val context: Context) {

    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val globalRegion = booleanPreferencesKey("global_region")
        val demoMode = booleanPreferencesKey("demo_mode")
        val autoRotate = booleanPreferencesKey("auto_rotate")
    }

    suspend fun read(): AppSettings {
        val prefs = context.shiwuDataStore.data.first()
        val stored = prefs[Keys.apiKey]
        val key = when {
            stored == null -> BuildConfig.DEFAULT_API_KEY
            else -> stored
        }
        return AppSettings(
            apiKey = key,
            globalRegion = prefs[Keys.globalRegion] ?: false,
            demoMode = prefs[Keys.demoMode] ?: false,
            autoRotate = prefs[Keys.autoRotate] ?: true
        )
    }

    suspend fun save(settings: AppSettings) {
        context.shiwuDataStore.edit { prefs ->
            prefs[Keys.apiKey] = settings.apiKey
            prefs[Keys.globalRegion] = settings.globalRegion
            prefs[Keys.demoMode] = settings.demoMode
            prefs[Keys.autoRotate] = settings.autoRotate
        }
    }
}

package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.apiKeyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "clarion_local_provider_keys"
)

/**
 * Stores user-entered third-party API keys strictly on-device in DataStore Preferences.
 * Keys are never written to Firestore or bundled in source code.
 */
class ApiKeyStore(private val context: Context) {
    private fun keyForPreset(presetId: String) = stringPreferencesKey("provider_key_$presetId")

    suspend fun saveApiKey(presetId: String, apiKey: String) {
        val trimmed = apiKey.trim()
        context.apiKeyDataStore.edit { prefs ->
            if (trimmed.isEmpty()) {
                prefs.remove(keyForPreset(presetId))
            } else {
                prefs[keyForPreset(presetId)] = trimmed
            }
        }
    }

    suspend fun getApiKey(presetId: String): String {
        return context.apiKeyDataStore.data
            .map { it[keyForPreset(presetId)].orEmpty() }
            .first()
    }

    suspend fun deleteApiKey(presetId: String) {
        context.apiKeyDataStore.edit { prefs ->
            prefs.remove(keyForPreset(presetId))
        }
    }
}

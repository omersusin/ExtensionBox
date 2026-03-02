package com.extensionbox.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ebox_settings",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "ebox"))
    }
)

object Prefs {

    // --- Synchronous Getters (Legacy bridge, uses runBlocking) ---
    // Note: In 2026 practice, we should migrate callers to observe flows or use suspended functions.
    // This bridge allows the app to function while we refactor.

    fun isModuleEnabled(c: Context, key: String, def: Boolean): Boolean = runBlocking {
        val prefKey = booleanPreferencesKey("m_${key}_enabled")
        c.dataStore.data.map { it[prefKey] ?: def }.first()
    }

    fun setModuleEnabled(c: Context, key: String, value: Boolean) = runBlocking {
        val prefKey = booleanPreferencesKey("m_${key}_enabled")
        c.dataStore.edit { it[prefKey] = value }
    }

    fun isRunning(c: Context): Boolean = runBlocking {
        val prefKey = booleanPreferencesKey("running")
        c.dataStore.data.map { it[prefKey] ?: false }.first()
    }

    fun setRunning(c: Context, value: Boolean) = runBlocking {
        val prefKey = booleanPreferencesKey("running")
        c.dataStore.edit { it[prefKey] = value }
    }

    fun getDataRetentionDays(c: Context): Int = runBlocking {
        val prefKey = intPreferencesKey("data_retention_days")
        c.dataStore.data.map { it[prefKey] ?: 7 }.first() // Default to 7 days
    }

    fun setDataRetentionDays(c: Context, value: Int) = runBlocking {
        val prefKey = intPreferencesKey("data_retention_days")
        c.dataStore.edit { it[prefKey] = value }
    }

    fun getInt(c: Context, key: String, def: Int): Int = runBlocking {
        val prefKey = intPreferencesKey(key)
        c.dataStore.data.map { it[prefKey] ?: def }.first()
    }

    fun getIntFlow(c: Context, key: String, def: Int) = 
        c.dataStore.data.map { it[intPreferencesKey(key)] ?: def }

    fun setInt(c: Context, key: String, value: Int) = runBlocking {
        val prefKey = intPreferencesKey(key)
        c.dataStore.edit { it[prefKey] = value }
    }

    fun getLong(c: Context, key: String, def: Long): Long = runBlocking {
        val prefKey = longPreferencesKey(key)
        c.dataStore.data.map { it[prefKey] ?: def }.first()
    }

    fun setLong(c: Context, key: String, value: Long) = runBlocking {
        val prefKey = longPreferencesKey(key)
        c.dataStore.edit { it[prefKey] = value }
    }

    fun getBool(c: Context, key: String, def: Boolean): Boolean = runBlocking {
        val prefKey = booleanPreferencesKey(key)
        c.dataStore.data.map { it[prefKey] ?: def }.first()
    }

    fun getBoolFlow(c: Context, key: String, def: Boolean) = 
        c.dataStore.data.map { it[booleanPreferencesKey(key)] ?: def }

    fun setBool(c: Context, key: String, value: Boolean) = runBlocking {
        val prefKey = booleanPreferencesKey(key)
        c.dataStore.edit { it[prefKey] = value }
    }

    fun getString(c: Context, key: String, def: String?): String? = runBlocking {
        val prefKey = stringPreferencesKey(key)
        c.dataStore.data.map { it[prefKey] ?: def }.first()
    }

    fun setString(c: Context, key: String, value: String?) = runBlocking {
        val prefKey = stringPreferencesKey(key)
        c.dataStore.edit { 
            if (value == null) it.remove(prefKey) else it[prefKey] = value
        }
    }

    fun isModuleVisibleInNotif(c: Context, key: String): Boolean = runBlocking {
        val prefKey = booleanPreferencesKey("notif_m_${key}_visible")
        c.dataStore.data.map { it[prefKey] ?: true }.first()
    }

    fun setModuleVisibleInNotif(c: Context, key: String, value: Boolean) = runBlocking {
        val prefKey = booleanPreferencesKey("notif_m_${key}_visible")
        c.dataStore.edit { it[prefKey] = value }
    }

    fun getAll(c: Context): Map<String, *> = runBlocking {
        c.dataStore.data.first().asMap().mapKeys { it.key.name }
    }

    fun clearAll(c: Context) = runBlocking {
        c.dataStore.edit { it.clear() }
    }

    fun resetDailyStats(c: Context) = runBlocking {
        c.dataStore.edit {
            it[intPreferencesKey("ulk_today")] = 0
            it[longPreferencesKey("stp_today")] = 0L
            it[longPreferencesKey("dat_daily_total")] = 0L
            it[longPreferencesKey("dat_daily_wifi")] = 0L
            it[longPreferencesKey("dat_daily_mobile")] = 0L
            it[longPreferencesKey("scr_on_acc")] = 0L
            it[intPreferencesKey("hab_today")] = 0
        }
    }

    fun importJson(c: Context, json: String) = runBlocking {
        try {
            val jsonObject = JSONObject(json)
            c.dataStore.edit { prefs ->
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    when (val value = jsonObject.get(key)) {
                        is Boolean -> prefs[booleanPreferencesKey(key)] = value
                        is Int -> prefs[intPreferencesKey(key)] = value
                        is Long -> prefs[longPreferencesKey(key)] = value
                        is Double -> prefs[doublePreferencesKey(key)] = value
                        is String -> prefs[stringPreferencesKey(key)] = value
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

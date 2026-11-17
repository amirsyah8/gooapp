package com.goorder.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.goorder.data.remote.dto.UserDto
import com.goorder.data.remote.dto.toDomain
import com.goorder.data.remote.dto.toDto
import com.goorder.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sessions")

class SessionManager(private val context: Context) {
    private val USER_KEY = stringPreferencesKey("user_data")
    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    private val OUTLET_ID_KEY = longPreferencesKey("current_outlet_id")
    private val OUTLET_NAME_KEY = stringPreferencesKey("current_outlet_name")
    private val PRINTER_MAC_KEY = stringPreferencesKey("saved_printer_mac")
    private val PRINTER_NAME_KEY = stringPreferencesKey("saved_printer_name")
    private val AUTO_PRINT_KEY = booleanPreferencesKey("auto_print_enabled")
    private val AUTO_CUT_KEY = booleanPreferencesKey("auto_cut_enabled")

    val userData: Flow<User?> = context.dataStore.data.map { preferences ->
        val userJson = preferences[USER_KEY]
        if (userJson != null) {
            try {
                Json.decodeFromString<UserDto>(userJson).toDomain()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val tokenData: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }

    val outletIdData: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[OUTLET_ID_KEY]
    }

    val outletNameData: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[OUTLET_NAME_KEY]
    }

    val printerMacData: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PRINTER_MAC_KEY]
    }

    val printerNameData: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PRINTER_NAME_KEY]
    }

    val autoPrintData: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_PRINT_KEY] ?: false
    }

    val autoCutData: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_CUT_KEY] ?: false
    }

    suspend fun getToken(): String? {
        return tokenData.firstOrNull()
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

    suspend fun saveUser(user: User) {
        val userJson = Json.encodeToString(user.toDto())
        context.dataStore.edit { preferences ->
            preferences[USER_KEY] = userJson
        }
    }

    suspend fun saveCurrentOutlet(outletId: Long, outletName: String) {
        context.dataStore.edit { preferences ->
            preferences[OUTLET_ID_KEY] = outletId
            preferences[OUTLET_NAME_KEY] = outletName
        }
    }

    suspend fun getSavedOutletId(): Long? {
        return outletIdData.firstOrNull()
    }

    suspend fun getSavedOutletName(): String? {
        return outletNameData.firstOrNull()
    }

    suspend fun savePrinter(mac: String, name: String) {
        context.dataStore.edit { preferences ->
            preferences[PRINTER_MAC_KEY] = mac
            preferences[PRINTER_NAME_KEY] = name
        }
    }

    suspend fun getSavedPrinterMac(): String? {
        return printerMacData.firstOrNull()
    }

    suspend fun getSavedPrinterName(): String? {
        return printerNameData.firstOrNull()
    }

    suspend fun saveAutoPrint(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_PRINT_KEY] = enabled
        }
    }

    suspend fun getAutoPrintEnabled(): Boolean {
        return autoPrintData.firstOrNull() ?: false
    }

    suspend fun saveAutoCut(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CUT_KEY] = enabled
        }
    }

    suspend fun getAutoCutEnabled(): Boolean {
        return autoCutData.firstOrNull() ?: false
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_KEY)
            preferences.remove(TOKEN_KEY)
            preferences.remove(OUTLET_ID_KEY)
            preferences.remove(OUTLET_NAME_KEY)
            preferences.remove(PRINTER_MAC_KEY)
            preferences.remove(PRINTER_NAME_KEY)
            preferences.remove(AUTO_PRINT_KEY)
            preferences.remove(AUTO_CUT_KEY)
        }
    }
}

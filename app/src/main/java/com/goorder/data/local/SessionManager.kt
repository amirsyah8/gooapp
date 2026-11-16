package com.goorder.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_KEY)
            preferences.remove(TOKEN_KEY)
            preferences.remove(OUTLET_ID_KEY)
            preferences.remove(OUTLET_NAME_KEY)
        }
    }
}

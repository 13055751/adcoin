package com.adcoin.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 登录会话：token + 用户信息（DataStore Preferences 持久化）。 */
data class Session(
    val token: String,
    val username: String,
    val appUserId: String,
    val linkedPlayerName: String?,
)

private val Context.dataStore by preferencesDataStore(name = "session")

object SessionStore {

    private val KEY_TOKEN = stringPreferencesKey("token")
    private val KEY_USERNAME = stringPreferencesKey("username")
    private val KEY_APP_USER_ID = stringPreferencesKey("app_user_id")
    private val KEY_LINKED_NAME = stringPreferencesKey("linked_name")

    val session: Flow<Session?> = App.context.dataStore.data.map { prefs ->
        val token = prefs[KEY_TOKEN] ?: return@map null
        Session(
            token = token,
            username = prefs[KEY_USERNAME] ?: "",
            appUserId = prefs[KEY_APP_USER_ID] ?: "",
            linkedPlayerName = prefs[KEY_LINKED_NAME],
        )
    }

    suspend fun save(session: Session) {
        App.context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = session.token
            prefs[KEY_USERNAME] = session.username
            prefs[KEY_APP_USER_ID] = session.appUserId
            session.linkedPlayerName?.let { prefs[KEY_LINKED_NAME] = it }
        }
    }

    suspend fun updateLinked(name: String?) {
        App.context.dataStore.edit { prefs ->
            if (name == null) prefs.remove(KEY_LINKED_NAME) else prefs[KEY_LINKED_NAME] = name
        }
    }

    suspend fun current(): Session? = session.first()

    suspend fun clear() {
        App.context.dataStore.edit { it.clear() }
    }
}

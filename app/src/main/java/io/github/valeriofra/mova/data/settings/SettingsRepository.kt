package io.github.valeriofra.mova.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class MovaSettings(
    val wifiOnly: Boolean = false,
    val maxConcurrent: Int = DEFAULT_CONCURRENT,
    /** Pasta escolhida pelo usuário (Storage Access Framework). Nulo = Download/MOVA. */
    val storageTreeUri: String? = null,
    val storageTreeName: String? = null,
    val notificationsAsked: Boolean = false,
) {
    companion object {
        const val DEFAULT_CONCURRENT = 2
        val CONCURRENT_RANGE = 1..3
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val maxConcurrent = intPreferencesKey("max_concurrent")
        val treeUri = stringPreferencesKey("storage_tree_uri")
        val treeName = stringPreferencesKey("storage_tree_name")
        val notificationsAsked = booleanPreferencesKey("notifications_asked")
    }

    val settings: Flow<MovaSettings> = context.dataStore.data
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun current(): MovaSettings = settings.first()

    suspend fun setWifiOnly(value: Boolean) = context.dataStore.edit { it[Keys.wifiOnly] = value }

    suspend fun setMaxConcurrent(value: Int) = context.dataStore.edit {
        it[Keys.maxConcurrent] = value.coerceIn(MovaSettings.CONCURRENT_RANGE)
    }

    suspend fun setStorageTree(uri: String?, name: String?) = context.dataStore.edit {
        if (uri == null) {
            it.remove(Keys.treeUri)
            it.remove(Keys.treeName)
        } else {
            it[Keys.treeUri] = uri
            if (name != null) it[Keys.treeName] = name else it.remove(Keys.treeName)
        }
    }

    suspend fun setNotificationsAsked() = context.dataStore.edit { it[Keys.notificationsAsked] = true }

    private fun Preferences.toSettings() = MovaSettings(
        wifiOnly = this[Keys.wifiOnly] ?: false,
        maxConcurrent = (this[Keys.maxConcurrent] ?: MovaSettings.DEFAULT_CONCURRENT)
            .coerceIn(MovaSettings.CONCURRENT_RANGE),
        storageTreeUri = this[Keys.treeUri],
        storageTreeName = this[Keys.treeName],
        notificationsAsked = this[Keys.notificationsAsked] ?: false,
    )
}

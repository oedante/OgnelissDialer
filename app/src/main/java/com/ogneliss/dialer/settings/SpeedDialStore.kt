package com.ogneliss.dialer.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ogneliss.dialer.data.SpeedDialEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.speedDialStore by preferencesDataStore("speed_dial")

/** Быстрый набор: цифры 2..9 (1 традиционно занята голосовой почтой). */
@Singleton
class SpeedDialStore @Inject constructor(@ApplicationContext private val context: Context) {

    private fun key(d: Int) = stringPreferencesKey("sd_$d")

    val entries: Flow<List<SpeedDialEntry>> = context.speedDialStore.data.map { p ->
        (2..9).mapNotNull { d ->
            p[key(d)]?.split("|")?.let { parts ->
                SpeedDialEntry(d, parts.getOrElse(0) { "" }, parts.getOrNull(1))
            }
        }
    }

    suspend fun assign(digit: Int, number: String, name: String?) {
        context.speedDialStore.edit { it[key(digit)] = "$number|${name.orEmpty()}" }
    }

    suspend fun clear(digit: Int) {
        context.speedDialStore.edit { it.remove(key(digit)) }
    }

    suspend fun get(digit: Int): SpeedDialEntry? =
        entries.first().firstOrNull { it.digit == digit }
}

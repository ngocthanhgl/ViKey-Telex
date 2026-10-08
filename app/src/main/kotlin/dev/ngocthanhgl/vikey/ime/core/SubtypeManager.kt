/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.ngocthanhgl.vikey.ime.core

import android.content.Context
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.appContext
import dev.ngocthanhgl.vikey.ime.keyboard.CurrencySet
import dev.ngocthanhgl.vikey.keyboardManager
import dev.ngocthanhgl.vikey.lib.FlorisLocale
import dev.ngocthanhgl.vikey.lib.devtools.flogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.florisboard.lib.kotlin.collectLatestIn

val SubtypeJsonConfig = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = false
}

/**
 * Class which acts as a high level helper for the raw implementation of subtypes in the prefs. Additionally provides
 * helper methods for the in-keyboard language switch process.
 */
class SubtypeManager(context: Context) {
    private val prefs by FlorisPreferenceStore
    private val keyboardManager by context.keyboardManager()
    private val appContext = context.appContext()
    private val scope = CoroutineScope(Dispatchers.Default)

    val subtypesFlow: StateFlow<List<Subtype>>
        field = MutableStateFlow(listOf())
    inline var subtypes
        get() = subtypesFlow.value
        private set(v) { subtypesFlow.value = v }

    val activeSubtypeFlow: StateFlow<Subtype>
        field = MutableStateFlow(Subtype.DEFAULT)
    inline var activeSubtype
        get() = activeSubtypeFlow.value
        private set(v) { activeSubtypeFlow.value = v }

    init {
        prefs.localization.subtypes.asFlow().collectLatestIn(scope) { listRaw ->
            flogDebug { listRaw }
            val list = if (listRaw.isNotBlank()) {
                SubtypeJsonConfig.decodeFromString<List<Subtype>>(listRaw)
            } else {
                emptyList()
            }
            subtypes = list
            evaluateActiveSubtype(list)
        }
        // The preference store is not necessarily ready when the IME process starts, so the flow
        // above may deliver the default "[]" value before the persisted subtypes actually load.
        // Re-read the value once the store signals that it is ready, otherwise the subtype list can
        // stay empty (and the language switch button would silently do nothing).
        appContext.preferenceStoreLoaded.collectLatestIn(scope) { loaded ->
            if (loaded) {
                flogDebug { "preferenceStoreLoaded, re-evaluating subtype list" }
                val listRaw = prefs.localization.subtypes.get()
                val list = if (listRaw.isNotBlank()) {
                    runCatching { SubtypeJsonConfig.decodeFromString<List<Subtype>>(listRaw) }
                        .getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                if (list != subtypes) {
                    subtypes = list
                }
                evaluateActiveSubtype(list)
            }
        }
    }

    private fun persistNewSubtypeList(list: List<Subtype>) = scope.launch {
        val listRaw = SubtypeJsonConfig.encodeToString(list)
        prefs.localization.subtypes.set(listRaw)
    }

    /**
     * Gets the active subtype and returns it. If the activeSubtypeId points to a non-existent
     * subtype, this method tries to determine a new active subtype.
     *
     * @return The active subtype or null, if the subtype list is empty or no new active subtype
     *  could be determined.
     */
    private fun evaluateActiveSubtype(list: List<Subtype>) {
        val activeSubtypeId = prefs.localization.activeSubtypeId.get()
        val subtype = list.find { it.id == activeSubtypeId } ?: list.firstOrNull() ?: Subtype.DEFAULT
        if (list.isNotEmpty() && subtype.id != activeSubtypeId) {
            flogDebug {
                "evaluateActiveSubtype: stored id $activeSubtypeId not found, " +
                    "falling back to ${subtype.toShortString()} (${subtype.id})"
            }
            prefs.localization.activeSubtypeId.set(subtype.id)
        }
        activeSubtype = subtype
    }

    /**
     * Adds a given [subtype] to the subtype list, if it does not exist.
     *
     * @param subtype The subtype which should be added.
     * @return True if the subtype was added, false otherwise. A return value of false indicates
     *  that the subtype already exists.
     */
    fun addSubtype(subtype: Subtype): Boolean {
        val subtypeToAdd = subtype.copy(id = System.currentTimeMillis())
        val subtypeList = subtypes
        if (subtypeList.find { it.equalsExcludingId(subtype) } != null) {
            return false
        }
        val newSubtypeList = subtypeList + subtypeToAdd
        persistNewSubtypeList(newSubtypeList)
        return true
    }

    /**
     * Gets the currency set from the given subtype and returns it. Falls back to a default one if the subtype does not
     * exist.
     *
     * @return The currency set or a fallback.
     */
    fun getCurrencySet(subtypeToSearch: Subtype): CurrencySet {
        return keyboardManager.resources.currencySets.value[subtypeToSearch.currencySet] ?: CurrencySet.Fallback
    }

    /**
     * Gets a subtype by the given [id].
     *
     * @param id The id of the subtype you want to get.
     * @return The subtype or null, if no matching subtype could be found.
     */
    fun getSubtypeById(id: Long): Subtype? {
        val subtypeList = subtypes
        return subtypeList.find { it.id == id }
    }

    /**
     * Gets the default system subtype for a given [locale].
     *
     * @param locale The locale of the default system subtype to get.
     * @return The default system locale or null, if no matching default system subtype could be
     *  found.
     */
    fun getSubtypePresetForLocale(locale: FlorisLocale): SubtypePreset? {
        val presets = keyboardManager.resources.subtypePresets.value
        return presets.find { it.locale == locale } ?: presets.find { it.locale.language == locale.language }
    }

    /**
     * Modifies an existing subtype with the newly provided details. In order to determine which
     * subtype should be updated, the id must be the same.
     *
     * @param subtypeToModify The subtype with the new details but same id.
     */
    fun modifySubtypeWithSameId(subtypeToModify: Subtype) {
        val subtypeList = subtypes
        val index = subtypeList.indexOfFirst { subtypeToModify.id == it.id }
        if (index >= 0 && index < subtypeList.size) {
            val newSubtypeList = subtypeList.mapIndexed { n, subtype ->
                if (n == index) {
                    subtypeToModify
                } else {
                    subtype
                }
            }
            persistNewSubtypeList(newSubtypeList)
        }
    }

    /**
     * Removes a given [subtypeToRemove]. Nothing happens if the given [subtypeToRemove] does not
     * exist.
     *
     * @param subtypeToRemove The subtype which should be removed.
     */
    fun removeSubtype(subtypeToRemove: Subtype) {
        val subtypeList = subtypes
        val indexToRemove = subtypeList.indexOf(subtypeToRemove)
        if (indexToRemove in subtypeList.indices) {
            val newSubtypeList = subtypeList.mapIndexedNotNull { n, subtype ->
                if (n != indexToRemove) {
                    subtype
                } else {
                    null
                }
            }
            persistNewSubtypeList(newSubtypeList)
            evaluateActiveSubtype(newSubtypeList)
        }
    }

    /**
     * Switch to the previous subtype in the subtype list if possible.
     */
    fun switchToPrevSubtype() = switchToAdjacentSubtype(1)

    /**
     * Switch to the next subtype in the subtype list if possible.
     */
    fun switchToNextSubtype() = switchToAdjacentSubtype(-1)

    /**
     * Switches the active subtype by index instead of by [Subtype] equality.
     *
     * The previous implementation searched the list for the currently active subtype and only
     * picked the following entry on a match. That silently failed whenever the active subtype was
     * not part of the persisted list (e.g. the [Subtype.DEFAULT] fallback, whose id is -1), and
     * it wrote that fallback back to the prefs, so every single tap looked like a no-op. It also
     * ran inside a coroutine, so two quick taps could both read the same stale active subtype and
     * persist the same id twice.
     *
     * @param step -1 to advance to the next subtype, 1 to go back to the previous one.
     *
     * @return True if the active subtype changed, false otherwise.
     */
    private fun switchToAdjacentSubtype(step: Int): Boolean {
        val subtypeList = subtypes
        if (subtypeList.isEmpty()) {
            flogDebug { "switchToAdjacentSubtype: no subtypes configured, ignoring switch request" }
            return false
        }
        val cachedActiveSubtypeId = activeSubtype.id
        val currentIndex = subtypeList.indexOfFirst { it.id == cachedActiveSubtypeId }
        val newIndex = when {
            currentIndex < 0 -> if (step < 0) 0 else subtypeList.lastIndex
            else -> (currentIndex + step).mod(subtypeList.size)
        }
        val newActiveSubtype = subtypeList[newIndex]
        if (newActiveSubtype.id == cachedActiveSubtypeId) {
            flogDebug { "switchToAdjacentSubtype: only one subtype (${newActiveSubtype.toShortString()}), nothing to switch to" }
            return false
        }
        if (currentIndex < 0) {
            flogDebug {
                "switchToAdjacentSubtype: active id $cachedActiveSubtypeId is not in the list, " +
                    "falling back to ${newActiveSubtype.toShortString()}"
            }
        }
        flogDebug {
            "switchToAdjacentSubtype: $cachedActiveSubtypeId -> ${newActiveSubtype.id} " +
                "(${newActiveSubtype.toShortString()})"
        }
        // Update the flow first so the keyboard re-renders immediately, then persist the id.
        activeSubtype = newActiveSubtype
        prefs.localization.activeSubtypeId.set(newActiveSubtype.id)
        return true
    }

    fun switchToSubtypeById(id: Long): Boolean {
        val subtypeToSwitchTo = getSubtypeById(id) ?: run {
            flogDebug { "switchToSubtypeById: no subtype with id $id" }
            return false
        }
        if (subtypeToSwitchTo.id == activeSubtype.id) return true
        flogDebug { "switchToSubtypeById: ${activeSubtype.id} -> $id (${subtypeToSwitchTo.toShortString()})" }
        activeSubtype = subtypeToSwitchTo
        prefs.localization.activeSubtypeId.set(id)
        return true
    }
}

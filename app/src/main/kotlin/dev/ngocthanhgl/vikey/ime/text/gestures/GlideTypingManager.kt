/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.ngocthanhgl.vikey.ime.text.gestures

import android.content.Context
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.editorInstance
import dev.ngocthanhgl.vikey.ime.nlp.WordSuggestionCandidate
import dev.ngocthanhgl.vikey.ime.text.keyboard.TextKey
import dev.ngocthanhgl.vikey.keyboardManager
import dev.ngocthanhgl.vikey.nlpManager
import dev.ngocthanhgl.vikey.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles the [GlideTypingClassifier]. Basically responsible for linking [GlideTypingGesture.Detector]
 * with [GlideTypingClassifier].
 */
class GlideTypingManager(private val context: Context) : GlideTypingGesture.Listener {
    companion object {
        private const val MAX_SUGGESTION_COUNT = 8
    }

    private val prefs by FlorisPreferenceStore
    private val keyboardManager by context.keyboardManager()
    private val nlpManager by context.nlpManager()
    private val subtypeManager by context.subtypeManager()
    private val editorInstance by context.editorInstance()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val statisticalClassifier = StatisticalGlideTypingClassifier(context)
    private var futoClassifier: FutoGlideTypingClassifier? = null
    private var gestureClassifier: GlideTypingClassifier? = null
    private var lastTime = System.currentTimeMillis()

    private val activeClassifier: GlideTypingClassifier
        get() = futoClassifier?.takeIf { it.ready } ?: statisticalClassifier

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        val classifier = gestureClassifier ?: activeClassifier
        gestureClassifier = null
        updateSuggestionsAsync(MAX_SUGGESTION_COUNT, true, classifier) {
            classifier.clear()
        }
    }

    override fun onGlideCancelled() {
        (gestureClassifier ?: activeClassifier).clear()
        gestureClassifier = null
    }

    override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
        // Pin the classifier for the whole gesture. The neural engine may finish
        // loading mid-swipe; switching implementations would lose the points.
        val classifier = gestureClassifier ?: activeClassifier.also { gestureClassifier = it }
        classifier.addGesturePoint(point)

        val time = System.currentTimeMillis()
        if (classifier.supportsPreview &&
            prefs.glide.showPreview.get() &&
            time - lastTime > prefs.glide.previewRefreshDelay.get()
        ) {
            updateSuggestionsAsync(1, false, classifier) {}
            lastTime = time
        }
    }

    /**
     * Change the layout of the internal gesture classifier.
     *
     * FUTO Swipe is English-only. Other languages keep the old statistical
     * classifier, but glide itself is disabled for them in the keyboard UI.
     */
    fun setLayout(keys: List<TextKey>) {
        if (keys.isEmpty()) return
        val subtype = subtypeManager.activeSubtype
        if (subtype.primaryLocale.language == "en") {
            val futo = futoClassifier ?: FutoGlideTypingClassifier(context).also {
                futoClassifier = it
            }
            futo.setLayout(keys, subtype)
        } else {
            futoClassifier?.close()
            futoClassifier = null
        }
        statisticalClassifier.setLayout(keys, subtype)
    }

    /**
     * Asks gesture classifier for suggestions and then passes that on to the smartbar.
     * Also commits the most confident suggestion if [commit] is set. All happens on an async executor.
     * NB: only fetches [MAX_SUGGESTION_COUNT] suggestions.
     *
     * @param callback Called when this function completes. Takes a boolean, which indicates if suggestions
     * were successfully set.
     */
    private fun updateSuggestionsAsync(
        maxSuggestionsToShow: Int,
        commit: Boolean,
        classifier: GlideTypingClassifier = activeClassifier,
        callback: (Boolean) -> Unit,
    ) {
        if (!classifier.ready) {
            callback.invoke(false)
            return
        }

        scope.launch(Dispatchers.Default) {
            val rawSuggestions = classifier.getSuggestions(MAX_SUGGESTION_COUNT, true)
            val glideHistory = dev.ngocthanhgl.vikey.ime.nlp.ngramTokens(
                editorInstance.activeContent.textBeforeSelection
            ).takeLast(2)
            if (rawSuggestions.isEmpty()) {
                // Pool has no match (OOV: names, slang, loanwords). Fall back to
                // the literal trail decode so the glide still produces text.
                // commitGesture() learns the word, growing the future pool.
                val trail = classifier.decodeTrailLetters()
                withContext(Dispatchers.Main) {
                    if (trail != null) {
                        val fixed = keyboardManager.fixCase(trail)
                        nlpManager.suggestDirectly(listOf(WordSuggestionCandidate(fixed, confidence = 1.0)))
                        if (commit) keyboardManager.commitGesture(fixed, glideHistory)
                        callback.invoke(true)
                    } else {
                        callback.invoke(false)
                    }
                }
                return@launch
            }
            // FUTO already applies its own neural LM to the candidates. Running
            // the old bigram rerank on top would double-count context.
            val reranked = if (classifier is FutoGlideTypingClassifier) {
                rawSuggestions
            } else {
                val textBefore = editorInstance.activeContent.textBeforeSelection
                nlpManager.rerankGlideSuggestions(
                    subtypeManager.activeSubtype, textBefore, rawSuggestions
                )
            }

            withContext(Dispatchers.Main) {
                val suggestionList = buildList {
                    val from = if (commit) 1 else 0
                    reranked.subList(
                        from.coerceAtMost(reranked.size),
                        maxSuggestionsToShow.coerceAtMost(reranked.size)
                    ).map { keyboardManager.fixCase(it) }.forEach {
                        add(WordSuggestionCandidate(it, confidence = 1.0))
                    }
                }

                nlpManager.suggestDirectly(suggestionList)
                if (commit && reranked.isNotEmpty()) {
                    keyboardManager.commitGesture(reranked.first(), glideHistory)
                }
                callback.invoke(true)
            }
        }
    }
}

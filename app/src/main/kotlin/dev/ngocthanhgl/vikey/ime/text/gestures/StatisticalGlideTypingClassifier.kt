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
import androidx.collection.LruCache
import androidx.collection.SparseArrayCompat
import androidx.collection.set
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.ime.keyboard.KeyData
import dev.ngocthanhgl.vikey.ime.text.key.KeyCode
import dev.ngocthanhgl.vikey.ime.text.keyboard.TextKey
import dev.ngocthanhgl.vikey.nlpManager
import java.text.Normalizer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private fun TextKey.baseCode(): Int {
    return (data as? KeyData)?.code ?: KeyCode.UNSPECIFIED
}

/**
 * Classifies gestures by comparing them with an "ideal gesture".
 *
 * Check out Étienne Desticourt's excellent write up at https://github.com/AnySoftKeyboard/AnySoftKeyboard/pull/1870
 */
open class StatisticalGlideTypingClassifier(context: Context) : GlideTypingClassifier {
    private val nlpManager by context.nlpManager()

    protected val gesture = Gesture()
    protected var keysByCharacter: SparseArrayCompat<TextKey> = SparseArrayCompat()
    private var words: List<String> = emptyList()
    protected var wordFrequencies: Map<String, Double> = emptyMap()
    protected var keys: ArrayList<TextKey> = arrayListOf()
    protected lateinit var pruner: Pruner
    private var wordDataSubtype: Subtype? = null
    private var layoutSubtype: Subtype? = null
    private var currentSubtype: Subtype? = null
    val ready: Boolean
        get() = currentSubtype == layoutSubtype && wordDataSubtype == layoutSubtype && wordDataSubtype != null
    private val prunerCache = LruCache<Subtype, Pruner>(PRUNER_CACHE_SIZE)

    /**
     * The minimum distance between points to be added to a gesture.
     */
    private var distanceThresholdSquared = 0

    companion object {
        /**
         * Dimensionless length variance factor (scaled by key radius at runtime).
         */
        private const val LENGTH_THRESHOLD_FACTOR = 2.5f

        /**
         * describes the number of points to sample a gesture at, i.e the resolution.
         */
        private const val SAMPLING_POINTS: Int = 200

        /**
         * Standard deviation of the distribution of distances between the shapes of two gestures
         * representing the same word. It's expressed for normalized gestures and is therefore
         * independent of the keyboard or key size.
         */
        private const val SHAPE_STD = 22.08f

        /**
         * Standard deviation of the distribution of distances between the locations of two gestures
         * representing the same word. It's expressed as a factor of key radius as it's applied to
         * un-normalized gestures and is therefore dependent on the size of the keys/keyboard.
         */
        private const val LOCATION_STD = 0.5109f

        /**
         * Spatial probability threshold for key inclusion in pruning.
         */
        const val KEY_PROB_THRESHOLD = 0.05f

        /**
         * This is a very small cache that caches suggestions, so that they aren't recalculated e.g when releasing
         * a pointer when the suggestions were already calculated. Avoids a lot of micro pauses.
         */
        private const val SUGGESTION_CACHE_SIZE = 5

        /**
         * For multiple subtypes, the pruner is cached.
         */
        private const val PRUNER_CACHE_SIZE = 5
    }

    override fun addGesturePoint(position: GlideTypingGesture.Detector.Position) {
        if (!gesture.isEmpty) {
            val dx = gesture.getLastX() - position.x
            val dy = gesture.getLastY() - position.y

            if (dx * dx + dy * dy > distanceThresholdSquared) {
                gesture.addPoint(position.x, position.y)
            }
        } else {
            gesture.addPoint(position.x, position.y)
        }
    }

    override fun setLayout(keyViews: List<TextKey>, subtype: Subtype) {
        setWordData(subtype)
        // stop duplicate calls
        if (layoutSubtype == subtype && keys == keyViews) {
            return
        }

        // if only layout changed but not subtype
        val layoutChanged = layoutSubtype == subtype

        keysByCharacter.clear()
        keys.clear()
        keyViews.forEach {
            keysByCharacter[it.baseCode()] = it
            keys.add(it)
        }
        layoutSubtype = subtype
        distanceThresholdSquared = (keyViews.first().visibleBounds.width / 4).toInt()
        distanceThresholdSquared *= distanceThresholdSquared

        if (
            (wordDataSubtype == layoutSubtype)
            || layoutChanged // should force a re-initialize
        ) {
            initializePruner(layoutChanged)
        }
    }

    override fun setWordData(subtype: Subtype) {
        if (wordDataSubtype == subtype) return

        this.words = nlpManager.getListOfWords(subtype)
        this.wordFrequencies = nlpManager.getFrequenciesForWords(subtype, this.words)

        this.wordDataSubtype = subtype
        if (wordDataSubtype == layoutSubtype) {
            initializePruner(false)
        }
    }

    private fun initializePruner(invalidateCache: Boolean) {
        val currentSubtype = this.layoutSubtype!!
        val cached = when {
            invalidateCache -> null
            else -> prunerCache.get(currentSubtype)
        }
        if (cached == null) {
            val key = keys.firstOrNull()
            val radius = if (key != null) min(key.visibleBounds.height, key.visibleBounds.width) else 1f
            this.pruner = Pruner((LENGTH_THRESHOLD_FACTOR * radius).toDouble(), this.words, keysByCharacter)
            prunerCache.put(currentSubtype, this.pruner)
        } else {
            this.pruner = cached
        }
        this.currentSubtype = currentSubtype
    }

    override fun initGestureFromPointerData(pointerData: GlideTypingGesture.Detector.PointerData) {
        for (position in pointerData.positions) {
            addGesturePoint(position)
        }
    }

    private val lruSuggestionCache = LruCache<Pair<Gesture, Int>, List<Pair<CharSequence, Float>>>(SUGGESTION_CACHE_SIZE)
    override fun getSuggestions(maxSuggestionCount: Int, gestureCompleted: Boolean): List<Pair<CharSequence, Float>> {
        return when (val cached = lruSuggestionCache.get(Pair(this.gesture, maxSuggestionCount))) {
            null -> {
                val suggestions = unCachedGetSuggestions(maxSuggestionCount)
                lruSuggestionCache.put(Pair(this.gesture.clone(), maxSuggestionCount), suggestions)
                suggestions
            }
            else -> cached
        }
    }

    protected open fun unCachedGetSuggestions(maxSuggestionCount: Int): List<Pair<CharSequence, Float>> {
        val candidates = arrayListOf<String>()
        val candidateScores = arrayListOf<Float>()
        val key = keys.firstOrNull() ?: return listOf()
        val radius = min(key.visibleBounds.height, key.visibleBounds.width)

        val smoothedGesture = gesture.smooth()
        var remainingWords = pruner.pruneByExtremities(smoothedGesture, this.keys)
        val userGesture = smoothedGesture.resample(SAMPLING_POINTS)
        val normalizedUserGesture: Gesture = userGesture.normalizeByBoxSide()
        remainingWords = pruner.pruneByLength(smoothedGesture, remainingWords, keysByCharacter, keys)

        val eps = 1e-10f

        for (i in remainingWords.indices) {
            val word = remainingWords[i]
            val idealGestures = Gesture.generateIdealGestures(word, keysByCharacter)

            for (idealGesture in idealGestures) {
                val wordGesture = idealGesture.resample(SAMPLING_POINTS)
                val normalizedGesture: Gesture = wordGesture.normalizeByBoxSide()
                val shapeDistance = calcShapeDistance(normalizedGesture, normalizedUserGesture)
                val locationDistance = calcLocationDistance(wordGesture, userGesture)
                val shapeProb = calcGaussianProbability(shapeDistance, 0.0f, SHAPE_STD)
                val locationProb = calcGaussianProbability(locationDistance, 0.0f, LOCATION_STD * radius)
                val freq = wordFrequencies[word] ?: 0.0

                var logScore = ln(shapeProb + eps) + ln(locationProb + eps) + ln((freq * 255f).toFloat() + eps)

                var candidateDistanceSortedIndex = 0
                var duplicateIndex = Int.MAX_VALUE

                while (candidateDistanceSortedIndex < candidateScores.size
                    && candidateScores[candidateDistanceSortedIndex] >= logScore
                ) {
                    if (candidates[candidateDistanceSortedIndex].contentEquals(word)) duplicateIndex =
                        candidateDistanceSortedIndex
                    candidateDistanceSortedIndex++
                }
                if (candidateDistanceSortedIndex < maxSuggestionCount && candidateDistanceSortedIndex <= duplicateIndex) {
                    if (duplicateIndex < Int.MAX_VALUE) {
                        candidateScores.removeAt(duplicateIndex)
                        candidates.removeAt(duplicateIndex)
                    }
                    candidateScores.add(candidateDistanceSortedIndex, logScore)
                    candidates.add(candidateDistanceSortedIndex, word)
                    if (candidateScores.size > maxSuggestionCount) {
                        candidateScores.removeAt(maxSuggestionCount)
                        candidates.removeAt(maxSuggestionCount)
                    }
                }
            }
        }

        return candidates.zip(candidateScores).map { (word, score) ->
            Pair(word, score)
        }
    }

    override fun clear() {
        gesture.clear()
    }

    private fun calcLocationDistance(gesture1: Gesture, gesture2: Gesture): Float {
        var totalDistance = 0.0f
        for (i in 0 until SAMPLING_POINTS) {
            val x1 = gesture1.getX(i)
            val x2 = gesture2.getX(i)
            val y1 = gesture1.getY(i)
            val y2 = gesture2.getY(i)
            val distance = abs(x1 - x2) + abs(y1 - y2)
            totalDistance += distance
        }
        return totalDistance / SAMPLING_POINTS / 2
    }

    private fun calcGaussianProbability(value: Float, mean: Float, standardDeviation: Float): Float {
        val factor = 1.0 / (standardDeviation * sqrt(2 * PI))
        val exponent = ((value - mean) / standardDeviation).toDouble().pow(2.0)
        val probability = factor * exp(-1.0 / 2 * exponent)
        return probability.toFloat()
    }

    private fun calcShapeDistance(gesture1: Gesture, gesture2: Gesture): Float {
        var distance: Float
        var totalDistance = 0.0f
        for (i in 0 until SAMPLING_POINTS) {
            val x1 = gesture1.getX(i)
            val x2 = gesture2.getX(i)
            val y1 = gesture1.getY(i)
            val y2 = gesture2.getY(i)
            distance = Gesture.distance(x1, y1, x2, y2)
            totalDistance += distance
        }
        return totalDistance
    }

    class Pruner(
        /**
         * The length difference between a user gesture and a word gesture above which a word will
         * be pruned.
         */
        private val lengthThreshold: Double,
        words: List<String>,
        keysByCharacter: SparseArrayCompat<TextKey>,
    ) {

        /** A tree that provides fast access to words based on their first and last letter.  */
        private val wordTree = Collections.synchronizedMap(HashMap<Pair<Int, Int>, ArrayList<String>>())

        /**
         * Finds the words whose start and end letter are closest to the start and end points of the
         * user gesture.
         *
         * @param userGesture The current user gesture.
         * @param keys The keys on the keyboard.
         * @return A list of likely words.
         */
        fun pruneByExtremities(
            userGesture: Gesture,
            keys: Iterable<TextKey>,
        ): ArrayList<String> {
            val remainingWords = ArrayList<String>()
            val startX = userGesture.getFirstX()
            val startY = userGesture.getFirstY()
            val endX = userGesture.getLastX()
            val endY = userGesture.getLastY()
            val startKeys = findProbableKeys(startX, startY, keys)
            val endKeys = findProbableKeys(endX, endY, keys)
            for (startKey in startKeys) {
                for (endKey in endKeys) {
                    val keyPair = Pair(startKey, endKey)
                    val wordsForKeys = synchronized(wordTree) { wordTree[keyPair] }
                    if (wordsForKeys != null) {
                        remainingWords.addAll(wordsForKeys)
                    }
                }
            }
            return remainingWords
        }

        /**
         * Finds the words whose ideal gesture length is within a certain threshold of the user
         * gesture's length.
         *
         * @param userGesture The current user gesture.
         * @param words A list of words to consider.
         * @return A list of words that remained after pruning the input list by length.
         */
        fun pruneByLength(
            userGesture: Gesture,
            words: ArrayList<String>,
            keysByCharacter: SparseArrayCompat<TextKey>,
            keys: List<TextKey>,
        ): ArrayList<String> {
            val remainingWords = ArrayList<String>()

            val key = keys.firstOrNull() ?: return arrayListOf()
            val radius = min(key.visibleBounds.height, key.visibleBounds.width)
            val userLength = userGesture.getLength()
            for (word in words) {
                val idealGestures = Gesture.generateIdealGestures(word, keysByCharacter)
                for (idealGesture in idealGestures) {
                    val wordIdealLength = getCachedIdealLength(word, idealGesture)
                    if (abs(userLength - wordIdealLength) < lengthThreshold * radius) {
                        remainingWords.add(word)
                    }
                }
            }
            return remainingWords
        }

        private val cachedIdealLength = ConcurrentHashMap<String, Float>()
        private fun getCachedIdealLength(word: String, idealGesture: Gesture): Float {
            return cachedIdealLength.getOrPut(word) { idealGesture.getLength() }
        }

        companion object {
            private fun getFirstKeyLastKey(
                word: String,
                keysByCharacter: SparseArrayCompat<TextKey>,
            ): Pair<Int, Int>? {
                val firstLetter = word[0]
                val lastLetter = word[word.length - 1]
                val firstBaseChar = Normalizer.normalize(firstLetter.toString(), Normalizer.Form.NFD)[0]
                val lastBaseChar = Normalizer.normalize(lastLetter.toString(), Normalizer.Form.NFD)[0]
                return when {
                    keysByCharacter.indexOfKey(firstBaseChar.code) < 0 || keysByCharacter.indexOfKey(lastBaseChar.code) < 0 -> {
                        null
                    }
                    else -> {
                        val firstKey = keysByCharacter[firstBaseChar.code]
                        val lastKey = keysByCharacter[lastBaseChar.code]
                        if (firstKey != null && lastKey != null) {
                            firstKey.baseCode() to lastKey.baseCode()
                        } else {
                            null
                        }
                    }
                }
            }

            /**
             * Computes 2D Gaussian spatial probability of a touch point hitting a key center.
             */
            private fun keyProbability(tx: Float, ty: Float, key: TextKey): Float {
                val cx = key.visibleBounds.center.x
                val cy = key.visibleBounds.center.y
                val dx = tx - cx
                val dy = ty - cy
                val sigma = key.visibleBounds.width / 3f
                return exp(-(dx * dx + dy * dy) / (2f * sigma * sigma))
            }

            /**
             * Finds keys whose spatial probability for a given touch point exceeds the threshold.
             */
            private fun findProbableKeys(
                x: Float, y: Float, keys: Iterable<TextKey>
            ): List<Int> {
                val result = mutableListOf<Int>()
                for (key in keys) {
                    val prob = keyProbability(x, y, key)
                    if (prob >= StatisticalGlideTypingClassifier.KEY_PROB_THRESHOLD) {
                        result.add(key.baseCode())
                    }
                }
                return if (result.size <= 2) {
                    keys.map { it.baseCode() }.take(3)
                } else result
            }
        }

        init {
            synchronized(wordTree) {
                for (word in words) {
                    val keyPair = getFirstKeyLastKey(word, keysByCharacter)
                    keyPair?.let {
                        wordTree.getOrPut(keyPair) { arrayListOf() }.add(word)
                    }
                }
            }
        }
    }

    class Gesture(
        private val xs: FloatArray = FloatArray(MAX_SIZE),
        private val ys: FloatArray = FloatArray(MAX_SIZE),
        private var size: Int = 0,
    ) {
        companion object {
            private const val MAX_SIZE = 500

            fun generateIdealGestures(word: String, keysByCharacter: SparseArrayCompat<TextKey>): List<Gesture> {
                val idealGesture = Gesture()
                val idealGestureWithLoops = Gesture()
                var previousLetter = '\u0000'
                var hasLoops = false

                for (c in word) {
                    val lc = Character.toLowerCase(c)
                    var key = keysByCharacter[lc.code]
                    if (key == null) {
                        val baseCharacter: Char = Normalizer.normalize(lc.toString(), Normalizer.Form.NFD)[0]
                        key = keysByCharacter[baseCharacter.code]
                        if (key == null) {
                            continue
                        }
                    }
                    val center = key.visibleBounds.center

                    if (previousLetter == lc) {
                        val rx = key.visibleBounds.width * 0.3f
                        val ry = key.visibleBounds.height * 0.3f
                        for (angle in 0 until 360 step 30) {
                            val rad = Math.toRadians(angle.toDouble())
                            idealGestureWithLoops.addPoint(
                                center.x + rx * cos(rad).toFloat(),
                                center.y + ry * sin(rad).toFloat()
                            )
                        }
                        hasLoops = true
                        idealGesture.addPoint(center.x, center.y)
                    } else {
                        idealGesture.addPoint(center.x, center.y)
                        idealGestureWithLoops.addPoint(center.x, center.y)
                    }
                    previousLetter = lc
                }
                return when (hasLoops) {
                    true -> listOf(idealGesture, idealGestureWithLoops)
                    false -> listOf(idealGesture)
                }
            }

            fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
                return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
            }
        }

        val isEmpty: Boolean
            get() = size == 0

        fun addPoint(x: Float, y: Float) {
            if (size >= MAX_SIZE) {
                return
            }
            xs[size] = x
            ys[size] = y
            size += 1
        }

        /**
         * Resamples the gesture into a new gesture with the chosen number of points by oversampling
         * it.
         *
         * @param numPoints The number of points that the new gesture will have. Must be superior to
         * the number of points in the current gesture.
         * @return An oversampled copy of the gesture.
         */
        fun resample(numPoints: Int): Gesture {
            val interpointDistance = (getLength() / numPoints)
            val resampledGesture = Gesture()
            resampledGesture.addPoint(xs[0], ys[0])
            var lastX = xs[0]
            var lastY = ys[0]
            var newX: Float
            var newY: Float
            var cumulativeError = 0.0f

            // otherwise nothing happens if size is only 1:
            if (this.size == 1) {
                for (i in 0 until SAMPLING_POINTS) {
                    resampledGesture.addPoint(xs[0], ys[0])
                }
            }

            for (i in 0 until size - 1) {
                // We calculate the unit vector from the two points we're between in the actual
                // gesture
                var dx = xs[i + 1] - xs[i]
                var dy = ys[i + 1] - ys[i]
                val norm = sqrt(dx.pow(2.0f) + dy.pow(2.0f))
                dx /= norm
                dy /= norm

                // The number of evenly sampled points that fit between the two actual points
                var numNewPoints = norm / interpointDistance

                // The number of point that'd fit between the two actual points is often not round,
                // which means we'll get an increasingly large error as we resample the gesture
                // and round down that number. To compensate for this we keep track of the error
                // and add additional points when it gets too large.
                cumulativeError += numNewPoints - numNewPoints.toInt()
                if (cumulativeError > 1) {
                    numNewPoints = (numNewPoints.toInt() + cumulativeError.toInt()).toFloat()
                    cumulativeError %= 1
                }
                for (j in 0 until numNewPoints.toInt()) {
                    newX = lastX + dx * interpointDistance
                    newY = lastY + dy * interpointDistance
                    lastX = newX
                    lastY = newY
                    resampledGesture.addPoint(newX, newY)
                }
            }
            return resampledGesture
        }

        fun normalizeByBoxSide(): Gesture {
            val normalizedGesture = Gesture()

            var maxX = -1.0f
            var maxY = -1.0f
            var minX = 10000.0f
            var minY = 10000.0f

            for (i in 0 until size) {
                maxX = max(xs[i], maxX)
                maxY = max(ys[i], maxY)
                minX = min(xs[i], minX)
                minY = min(ys[i], minY)
            }

            val width = maxX - minX
            val height = maxY - minY
            val longestSide = max(max(width, height), 0.00001f)

            val centroidX = (width / 2 + minX) / longestSide
            val centroidY = (height / 2 + minY) / longestSide

            for (i in 0 until size) {
                val x = xs[i] / longestSide - centroidX
                val y = ys[i] / longestSide - centroidY
                normalizedGesture.addPoint(x, y)
            }

            return normalizedGesture
        }

        fun getFirstX(): Float = xs.getOrElse(0) { 0f }
        fun getFirstY(): Float = ys.getOrElse(0) { 0f }
        fun getLastX(): Float = xs.getOrElse(size - 1) { 0f }
        fun getLastY(): Float = ys.getOrElse(size - 1) { 0f }

        fun getLength(): Float {
            var length = 0f
            for (i in 1 until size) {
                val previousX = xs[i - 1]
                val previousY = ys[i - 1]
                val currentX = xs[i]
                val currentY = ys[i]
                length += distance(previousX, previousY, currentX, currentY)
            }

            return length
        }

        fun clear() {
            this.size = 0
        }

        fun getX(i: Int): Float = xs.getOrElse(i) { 0f }
        fun getY(i: Int): Float = ys.getOrElse(i) { 0f }

        fun toPointList(): List<Pair<Float, Float>> {
            val list = ArrayList<Pair<Float, Float>>(size)
            for (i in 0 until size) {
                list.add(xs[i] to ys[i])
            }
            return list
        }

        fun clone(): Gesture {
            return Gesture(xs.clone(), ys.clone(), size)
        }

        fun smooth(): Gesture {
            if (size < 3) return this
            var curXs = xs.copyOf(size)
            var curYs = ys.copyOf(size)
            var curSize = size
            for (pass in 0 until 2) {
                val nextXs = FloatArray(curSize)
                val nextYs = FloatArray(curSize)
                nextXs[0] = curXs[0]
                nextYs[0] = curYs[0]
                for (i in 1 until curSize - 1) {
                    nextXs[i] = (curXs[i - 1] + 2f * curXs[i] + curXs[i + 1]) / 4f
                    nextYs[i] = (curYs[i - 1] + 2f * curYs[i] + curYs[i + 1]) / 4f
                }
                nextXs[curSize - 1] = curXs[curSize - 1]
                nextYs[curSize - 1] = curYs[curSize - 1]
                curXs = nextXs
                curYs = nextYs
            }
            val result = Gesture()
            for (i in 0 until curSize) {
                result.addPoint(curXs[i], curYs[i])
            }
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Gesture

            if (this.size != other.size) return false

            for (i in 0 until size) {
                if (xs[i] != other.xs[i] || ys[i] != other.ys[i]) return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = xs.contentHashCode()
            result = 31 * result + ys.contentHashCode()
            result = 31 * result + size
            return result
        }
    }
}

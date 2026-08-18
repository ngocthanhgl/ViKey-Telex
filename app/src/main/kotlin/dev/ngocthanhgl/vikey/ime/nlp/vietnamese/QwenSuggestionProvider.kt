package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

import android.content.Context
import android.util.LruCache
import dev.ngocthanhgl.vikey.app.FlorisPreferenceStore
import dev.ngocthanhgl.vikey.ime.core.Subtype
import dev.ngocthanhgl.vikey.ime.dictionary.DictionaryManager
import dev.ngocthanhgl.vikey.ime.dictionary.UserDictionaryEntry
import dev.ngocthanhgl.vikey.ime.editor.EditorContent
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionCandidate
import dev.ngocthanhgl.vikey.ime.nlp.SuggestionProvider
import dev.ngocthanhgl.vikey.ime.nlp.WordSuggestionCandidate
import dev.ngocthanhgl.vikey.lib.devtools.flogDebug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

class QwenSuggestionProvider(private val context: Context) : SuggestionProvider {
    private val prefs by FlorisPreferenceStore
    private var autocorrectEngine: AutocorrectEngine? = null
    private var typoDetector: TypoDetector? = null

    init {
        Companion.currentInstance = this
    }

    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.vietnamese.qwen"
        private const val NGRAM_PATH = "qwen_ngrams.json"
        private const val PERSONAL_DICT = "qwen_personal_dict.json"
        private const val DISCOURSE_PATH = "qwen_discourse.json"
        private const val CLEARED_MARKER = ".qwen_cleared"
        private const val BIGRAM_BOOST = 5.0
        private const val TRIGRAM_BOOST = 3.0
        private const val SEED_WORDS = "ime/dict/vi.json"
        private const val EN_WORDS = "ime/dict/en.json"
        private const val PHRASES_PATH = "ime/dict/phrases.json"
        private val whitespace = Regex("\\s+")

        private var currentInstance: QwenSuggestionProvider? = null

        fun recase(word: String, shiftState: dev.ngocthanhgl.vikey.ime.input.InputShiftState?): String {
            return when (shiftState) {
                dev.ngocthanhgl.vikey.ime.input.InputShiftState.CAPS_LOCK -> word.uppercase()
                dev.ngocthanhgl.vikey.ime.input.InputShiftState.SHIFTED_MANUAL,
                dev.ngocthanhgl.vikey.ime.input.InputShiftState.SHIFTED_AUTOMATIC -> word.replaceFirstChar { it.uppercase() }
                null, dev.ngocthanhgl.vikey.ime.input.InputShiftState.UNSHIFTED -> word.lowercase()
                else -> word
            }
        }

        fun getInstance(): QwenSuggestionProvider? = currentInstance
    }

    private val modelLock = Any()
    @Volatile
    private var modelPtr = 0L
    @Volatile
    private var natLoaded = false
    @Volatile
    private var natLoading = false

    private data class PersonalWord(val count: Int, val lastUsedTs: Long)
    private data class DampedWord(val dampCount: Int = 0, val lastDampedTs: Long = 0)

    private val personalDicts = ConcurrentHashMap<String, ConcurrentHashMap<String, PersonalWord>>()
    private fun pd(lang: String): ConcurrentHashMap<String, PersonalWord> =
        personalDicts.computeIfAbsent(lang) { ConcurrentHashMap() }
    private fun langFor(subtype: Subtype?): String =
        if (subtype?.primaryLocale?.language == "en") "en" else "vi"
    private val dampedWords = ConcurrentHashMap<String, DampedWord>()
    private var personalDirty = false
    private var lastTopSuggestion: String? = null
    private var lastCommittedWord = ""
    private var lastTypedWord: String? = null

    private val seedWords = ConcurrentHashMap.newKeySet<String>()
    private val seedWordFrequencies = ConcurrentHashMap<String, Int>()
    private val enWordFrequencies = ConcurrentHashMap<String, Int>()
    private var prefixTrie: Map<String, List<String>> = mapOf()
    private var useTrie = false
    private var bigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    private var trigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    private var ngramDirty = false
    private var lastTextLen = 0
    private var pasteUntil = 0L
    private val discourseBuffer = Collections.synchronizedList(mutableListOf<String>())
    private val phraseMap = ConcurrentHashMap<String, List<String>>()
    private val suggestionCache = LruCache<String, List<Pair<String, Double>>>(200)

    private val bgScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _modelLoading = MutableStateFlow(false)
    val modelLoading = _modelLoading.asStateFlow()

    private val _modelError = MutableStateFlow<String?>(null)
    val modelError = _modelError.asStateFlow()

    override val providerId = ProviderId

    override suspend fun create() {
        withContext(Dispatchers.IO) {
            loadSeedWords()
            loadEnglishWords()
            loadPersonalDict()
            loadNgrams()
            purgeJunk()
            loadDiscourseBuffer()
            try { DictionaryManager.default().loadUserDictionariesIfNecessary() }
            catch (_: Exception) {}
            loadModelBg()
        }
        startPeriodicSave()
    }

    private fun startPeriodicSave() {
        bgScope.launch {
            while (true) {
                delay(30_000)
                if (personalDirty) savePersonalDict()
                if (ngramDirty) saveNgrams()
            }
        }
    }

    private fun loadPersonalDict() {
        try {
            val dir = context.filesDir
            val oldF = File(dir, PERSONAL_DICT)
            if (oldF.exists()) {
                val json = JSONObject(oldF.readText())
                val vi = pd("vi")
                for (key in json.keys()) {
                    val obj = json.getJSONObject(key)
                    vi[key] = PersonalWord(
                        count = obj.optInt("c", 1),
                        lastUsedTs = obj.optLong("t", System.currentTimeMillis()),
                    )
                    val dc = obj.optInt("d", 0)
                    if (dc > 0) {
                        dampedWords[key] = DampedWord(
                            dampCount = dc,
                            lastDampedTs = obj.optLong("dt", System.currentTimeMillis()),
                        )
                    }
                }
                oldF.delete()
            }
            for (lang in arrayOf("en", "vi")) {
                val f = File(dir, "${PERSONAL_DICT}_$lang.json")
                if (!f.exists()) continue
                val json = JSONObject(f.readText())
                val dict = pd(lang)
                for (key in json.keys()) {
                    val obj = json.getJSONObject(key)
                    dict[key] = PersonalWord(
                        count = obj.optInt("c", 1),
                        lastUsedTs = obj.optLong("t", System.currentTimeMillis()),
                    )
                }
            }
            val total = pd("en").size + pd("vi").size
            flogDebug { "Qwen: loaded $total personal words (en=${pd("en").size} vi=${pd("vi").size}), damped=${dampedWords.size}" }
        } catch (e: Exception) {
            flogDebug { "Qwen: personal dict load: ${e.message}" }
        }
    }

    private fun loadSeedWords() {
        try {
            val raw = context.assets.open(SEED_WORDS).bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            for (key in json.keys()) {
                val w = key.lowercase()
                if (w.isNotEmpty() && w.none { it.isWhitespace() } && w.all { it.isLetter() || it == '\'' }) {
                    seedWords.add(w)
                    seedWordFrequencies[w] = json.getInt(key)
                }
            }
            flogDebug { "Qwen: loaded ${seedWords.size} seed words" }
            val trie = mutableMapOf<String, MutableList<String>>()
            for (word in seedWords) {
                for (i in 1..word.length.coerceAtMost(6)) {
                    val p = word.take(i)
                    trie.getOrPut(p) { mutableListOf() }.add(word)
                }
            }
            prefixTrie = trie
            useTrie = true
            autocorrectEngine = AutocorrectEngine(seedWords)
            typoDetector = TypoDetector(seedWords)
            loadPhrases()
        } catch (e: Exception) {
            flogDebug { "Qwen: seed words load: ${e.message}" }
        }
    }

    private fun loadEnglishWords() {
        try {
            val raw = context.assets.open(EN_WORDS).bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            for (key in json.keys()) {
                val w = key.lowercase()
                if (w.isNotEmpty() && w.none { it.isWhitespace() } && w.all { it.isLetter() || it == '\'' }) {
                    enWordFrequencies[w] = json.getInt(key)
                }
            }
            flogDebug { "Qwen: loaded ${enWordFrequencies.size} English words" }
        } catch (e: Exception) {
            flogDebug { "Qwen: English words load: ${e.message}" }
        }
    }

    private fun loadPhrases() {
        try {
            val raw = context.assets.open(PHRASES_PATH).bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            for (key in json.keys()) {
                val arr = json.getJSONArray(key)
                val phrases = mutableListOf<String>()
                for (i in 0 until arr.length()) phrases.add(arr.getString(i))
                phraseMap[key.lowercase()] = phrases
            }
            flogDebug { "Qwen: loaded ${phraseMap.size} phrase entries" }
        } catch (e: Exception) {
            flogDebug { "Qwen: phrases load: ${e.message}" }
        }
    }

    private fun savePersonalDict() {
        checkClearedMarker()
        if (!personalDirty) return
        try {
            val dir = context.filesDir
            for (lang in arrayOf("en", "vi")) {
                val dict = personalDicts[lang] ?: continue
                val json = JSONObject()
                for ((word, pw) in dict) {
                    val obj = JSONObject()
                    obj.put("c", pw.count)
                    obj.put("t", pw.lastUsedTs)
                    val dw = dampedWords[word]
                    if (dw != null && dw.dampCount > 0) {
                        obj.put("d", dw.dampCount)
                        obj.put("dt", dw.lastDampedTs)
                    }
                    json.put(word, obj)
                }
                File(dir, "${PERSONAL_DICT}_$lang.json").writeText(json.toString())
            }
            syncRoomDb()
            personalDirty = false
        } catch (e: Exception) {
            flogDebug { "Qwen: personal dict save: ${e.message}" }
        }
    }

    private fun syncRoomDb() {
        try {
            val dm = DictionaryManager.default()
            dm.loadUserDictionariesIfNecessary()
            val dao = dm.florisUserDictionaryDao() ?: return
            for ((_, dict) in personalDicts) {
                for ((word, pw) in dict) {
                    val existing = dao.queryExact(word)
                    val freq = pw.count.coerceIn(1, 255)
                    if (existing.isNotEmpty()) {
                        dao.update(existing[0].copy(freq = freq))
                    } else {
                        dao.insert(UserDictionaryEntry(0, word, freq, null, null))
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun clearAll() {
        personalDicts.clear()
        personalDirty = false
        try {
            val dir = context.filesDir
            for (lang in arrayOf("en", "vi")) {
                File(dir, "${PERSONAL_DICT}_$lang.json").delete()
            }
            DictionaryManager.default().florisUserDictionaryDao()?.deleteAll()
        } catch (_: Exception) {}
    }

    private fun loadNgrams() {
        try {
            val f = File(context.filesDir, NGRAM_PATH)
            if (!f.exists()) return
            val json = JSONObject(f.readText())
            val bi = json.optJSONObject("bigrams")
            if (bi != null) {
                for (k1 in bi.keys()) {
                    val inner = bi.getJSONObject(k1)
                    val map = ConcurrentHashMap<String, Int>()
                    for (k2 in inner.keys()) map[k2] = inner.getInt(k2)
                    bigrams[k1] = map
                }
            }
            val tri = json.optJSONObject("trigrams")
            if (tri != null) {
                for (k1 in tri.keys()) {
                    val inner = tri.getJSONObject(k1)
                    val map = ConcurrentHashMap<String, Int>()
                    for (k2 in inner.keys()) map[k2] = inner.getInt(k2)
                    trigrams[k1] = map
                }
            }
            flogDebug { "Qwen: loaded ${bigrams.size} bigrams, ${trigrams.size} trigrams" }
        } catch (e: Exception) {
            flogDebug { "Qwen: load ngrams: ${e.message}" }
        }
    }

    private fun saveNgrams() {
        if (!ngramDirty) return
        try {
            val bi = JSONObject()
            for ((k1, inner) in bigrams) {
                val jo = JSONObject()
                for ((k2, v) in inner) jo.put(k2, v)
                bi.put(k1, jo)
            }
            val tri = JSONObject()
            for ((k1, inner) in trigrams) {
                val jo = JSONObject()
                for ((k2, v) in inner) jo.put(k2, v)
                tri.put(k1, jo)
            }
            val root = JSONObject()
            root.put("bigrams", bi)
            root.put("trigrams", tri)
            File(context.filesDir, NGRAM_PATH).writeText(root.toString())
            ngramDirty = false
        } catch (e: Exception) {
            flogDebug { "Qwen: save ngrams: ${e.message}" }
        }
    }

    private fun loadDiscourseBuffer() {
        try {
            val f = File(context.filesDir, DISCOURSE_PATH)
            if (!f.exists()) return
            val json = JSONObject(f.readText())
            val words = json.optJSONArray("words")
            if (words != null) {
                discourseBuffer.clear()
                for (i in 0 until words.length()) {
                    discourseBuffer.add(words.getString(i))
                }
            }
        } catch (e: Exception) {
            flogDebug { "Qwen: discourse load: ${e.message}" }
        }
    }

    private fun saveDiscourseBuffer() {
        try {
            val json = JSONObject()
            val arr = org.json.JSONArray(discourseBuffer)
            json.put("words", arr)
            File(context.filesDir, DISCOURSE_PATH).writeText(json.toString())
        } catch (e: Exception) {
            flogDebug { "Qwen: discourse save: ${e.message}" }
        }
    }

    private fun modelFile(): File? {
        val dir = context.filesDir
        return dir.listFiles { f -> f.extension == "gguf" && f.length() > 0 }
            ?.maxByOrNull { it.length() }
    }

    fun getModelName(): String? = modelFile()?.name

    private object GgufFormat {
        private const val MAGIC = 0x46554747 // "GGUF" little-endian as int
        private const val KEY_ARCH = "general.architecture"
        private const val KEY_NAME = "general.name"
        private const val KEY_FILE_TYPE = "general.file.type"

        private const val TYPE_STRING = 8

        /** Parsed GGUF header metadata. */
        data class Header(
            val architecture: String?,
            val name: String?,
            val fileType: Int?,
        )

        /**
         * Reads the GGUF header from [file] and returns parsed metadata.
         * Returns null if the file is not a valid GGUF or cannot be read.
         */
        fun read(file: File): Header? {
            try {
                val raf = java.io.RandomAccessFile(file, "r")
                val headerBytes = ByteArray(minOf(file.length(), 8192L).toInt())
                raf.readFully(headerBytes)
                raf.close()
                val data = headerBytes
                if (data.size < 24) return null
                var off = 0
                val magic = readI32(data, off); off += 4
                if (magic != MAGIC) return null
                val version = readI32(data, off); off += 4
                if (version < 1 || version > 3) return null
                off += 8 // skip tensor count
                val kvCount = readI64(data, off); off += 8
                var arch: String? = null
                var name: String? = null
                var fileType: Int? = null
                for (i in 0 until kvCount) {
                    val keyLen = readI64(data, off); off += 8
                    if (off + keyLen.toInt() > data.size) break
                    val key = String(data, off, keyLen.toInt(), Charsets.UTF_8); off += keyLen.toInt()
                    if (off + 4 > data.size) break
                    val valueType = readI32(data, off); off += 4
                    when (valueType) {
                        TYPE_STRING -> {
                            val strLen = readI64(data, off); off += 8
                            if (off + strLen.toInt() > data.size) break
                            val strVal = String(data, off, strLen.toInt(), Charsets.UTF_8); off += strLen.toInt()
                            when (key) {
                                KEY_ARCH -> arch = strVal
                                KEY_NAME -> name = strVal
                            }
                        }
                        4, 5, 10, 11 -> { // uint32, int32, uint64, int64
                            val intVal = when (valueType) {
                                4 -> readI32(data, off).toLong() and 0xFFFFFFFFL
                                5 -> readI32(data, off).toLong()
                                10 -> readI64(data, off)
                                11 -> readI64(data, off)
                                else -> 0L
                            }
                            off += if (valueType in listOf(10, 11)) 8 else 4
                            if (key == KEY_FILE_TYPE) fileType = intVal.toInt()
                        }
                        0 -> { off += 1; if (key == KEY_FILE_TYPE) fileType = data[off - 1].toInt() and 0xFF }
                        6 -> off += 4 // float32, skip
                        7 -> off += 1 // bool, skip
                        12 -> off += 8 // float64, skip
                        9 -> { // array
                            val arrType = readI32(data, off); off += 4
                            val arrLen = readI64(data, off); off += 8
                            for (j in 0 until arrLen) {
                                when (arrType) {
                                    8 -> { val sl = readI64(data, off); off += 8 + sl.toInt() }
                                    0, 1, 7 -> off += 1
                                    2, 3 -> off += 2
                                    4, 5, 6 -> off += 4
                                    10, 11, 12 -> off += 8
                                    else -> off += 4
                                }
                                if (off > data.size) break
                            }
                        }
                        else -> { off += 4 } // skip unknown
                    }
                    if (off > data.size) break
                    if (arch != null && name != null && fileType != null) break
                }
                return Header(arch, name, fileType)
            } catch (e: Exception) {
                flogDebug { "Qwen: GGUF header read failed: ${e.message}" }
                return null
            }
        }

        private fun readI32(data: ByteArray, off: Int): Int {
            return (data[off].toInt() and 0xFF) or
                ((data[off + 1].toInt() and 0xFF) shl 8) or
                ((data[off + 2].toInt() and 0xFF) shl 16) or
                ((data[off + 3].toInt() and 0xFF) shl 24)
        }

        private fun readI64(data: ByteArray, off: Int): Long {
            return (data[off].toLong() and 0xFF) or
                ((data[off + 1].toLong() and 0xFF) shl 8) or
                ((data[off + 2].toLong() and 0xFF) shl 16) or
                ((data[off + 3].toLong() and 0xFF) shl 24) or
                ((data[off + 4].toLong() and 0xFF) shl 32) or
                ((data[off + 5].toLong() and 0xFF) shl 40) or
                ((data[off + 6].toLong() and 0xFF) shl 48) or
                ((data[off + 7].toLong() and 0xFF) shl 56)
        }
    }

    /** Whitelist of GGUF architectures known to work with libqwen_jni.so. */
    private val SUPPORTED_ARCHS = setOf(
        "qwen2", "Qwen2ForCausalLM",
        "qwen2.5", "Qwen2.5ForCausalLM",
    )

    /**
     * Validates that [file] is a compatible GGUF model.
     * Returns null on success, or an error message string on failure.
     */
    private fun validateGguf(file: File): String? {
        val header = GgufFormat.read(file) ?: return "Cannot read GGUF header"
        val arch = header.architecture
        if (arch == null) return "Missing model architecture in GGUF header"
        if (arch !in SUPPORTED_ARCHS) {
            return "Unsupported model architecture '$arch'. Supported: ${SUPPORTED_ARCHS.joinToString(", ")}. " +
                "Use Qwen2.5 0.5B base model (not Instruct/Chat)."
        }
        val name = header.name
        if (name != null) {
            val lc = name.lowercase()
            if (lc.contains("instruct") || lc.contains("chat")) {
                return "Instruct/Chat variant detected ('$name'). Use the BASE model (non-instruct) for suggestions."
            }
        }
        flogDebug { "Qwen: GGUF validated arch=$arch name=$name fileType=${header.fileType}" }
        return null
    }

    private fun loadModelBg() {
        if (!QwenNatives.isAvailable) {
            flogDebug { "Qwen: native lib not available" }
            return
        }
        if (natLoading || natLoaded) return
        natLoading = true
        _modelLoading.value = true
        _modelError.value = null
        bgScope.launch {
            try {
                val file = modelFile()
                if (file != null) {
                    val validationError = validateGguf(file)
                    if (validationError != null) {
                        flogDebug { "Qwen: model validation failed: $validationError" }
                        _modelError.value = validationError
                    } else {
                        val t0 = System.currentTimeMillis()
                        synchronized(modelLock) {
                            modelPtr = QwenNatives.open(file.absolutePath)
                            natLoaded = modelPtr != 0L
                        }
                        if (!natLoaded) {
                            _modelError.value = "Native library rejected the model"
                        }
                        flogDebug { "Qwen: load ptr=$modelPtr ${System.currentTimeMillis() - t0}ms" }
                    }
                }
            } catch (e: Exception) {
                flogDebug { "Qwen: model load failed: ${e.message}" }
                _modelError.value = "Load failed: ${e.message}"
            }
            natLoading = false
            _modelLoading.value = false
        }
    }

    override suspend fun preload(subtype: Subtype) {
        create()
    }

    fun unloadModel() {
        synchronized(modelLock) {
            if (modelPtr != 0L) {
                QwenNatives.close(modelPtr)
                modelPtr = 0L
            }
            natLoaded = false
            natLoading = false
        }
        _modelError.value = null
        flogDebug { "Qwen: model unloaded" }
    }

    fun reloadModel() {
        if (!QwenNatives.isAvailable) return
        if (natLoaded) unloadModel()
        loadModelBg()
    }

    fun removeModel() {
        unloadModel()
        modelFile()?.delete()
        flogDebug { "Qwen: model removed" }
    }

    private fun checkClearedMarker() {
        val f = File(context.filesDir, CLEARED_MARKER)
        if (!f.exists()) return
        f.delete()
        personalDicts.clear()
        personalDirty = false
        try {
            val dir = context.filesDir
            for (lang in arrayOf("en", "vi")) {
                File(dir, "${PERSONAL_DICT}_$lang.json").delete()
            }
            DictionaryManager.default().florisUserDictionaryDao()?.deleteAll()
        } catch (_: Exception) {}
        flogDebug { "Qwen: processed .cleared marker" }
    }

    private fun resolveTelexPrefix(raw: String): String {
        var s = raw.lowercase()
        s = s.replace("dd", "đ").replace("aa", "â").replace("aw", "ă")
        s = s.replace("ee", "ê").replace("oo", "ô").replace("ow", "ơ").replace("uw", "ư")
        s = s.replace("uow", "ươ")
        if (s.isNotEmpty()) {
            val last = s.last()
            if (last in setOf('s', 'f', 'r', 'x', 'j') && s.length > 1) {
                val base = s.dropLast(1)
                if (base.any { it in "aeiouyăâêôơ" }) return base
            }
        }
        return s
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        checkClearedMarker()
        if (isPrivateSession) return emptyList()
        val lang = langFor(subtype)
        return withContext(Dispatchers.Default) {
            try {
                val textBefore = content.textBeforeSelection
                if (textBefore.isBlank()) return@withContext emptyList()
                if (textBefore.none { it.isLetter() }) return@withContext emptyList()
                val now = System.currentTimeMillis()
                if (textBefore.length > lastTextLen + 1) pasteUntil = now + 500
                lastTextLen = textBefore.length
                val lastChar = textBefore.last()
                if (lastChar == '\n') {
                    if (now >= pasteUntil) commitLearn(textBefore, lang)
                    return@withContext emptyList()
                }
                if (lastChar == '.' || lastChar == '?' || lastChar == '!') {
                    val words = textBefore.trimEnd().split(whitespace).filter { it.isNotBlank() }
                    discourseBuffer.clear()
                    discourseBuffer.addAll(words.takeLast(5))
                    return@withContext emptyList()
                }

                var autoCommitWord: String? = null

                val pairs = if (lastChar == ' ' || lastChar == '\t') {
                    val words = textBefore.trimEnd().split(whitespace).filter { it.isNotBlank() }
                    val lastWord = if (words.isNotEmpty()) words.last() else ""
                    if (lastWord.isBlank()) return@withContext emptyList()
                    if (now >= pasteUntil) commitLearn(textBefore, lang)
                    suggestNextWord(textBefore, maxCandidateCount, lang)
                } else {
                    val cur = getCurrentWord(content) ?: return@withContext emptyList()
                    if (cur.isBlank()) return@withContext emptyList()
                    val stripped = cur.trimEnd { !it.isLetter() }
                    autoCommitWord = stripped.ifEmpty { null }?.lowercase()
                    lastTypedWord = autoCommitWord
                    lastCommittedWord = ""
                    completeCurrentWord(stripped.ifEmpty { cur }, maxCandidateCount, textBefore, lang)
                }

                pairs.also { result ->
                    lastTopSuggestion = result.firstOrNull()?.first?.lowercase()
                }.mapIndexed { index, (word, _) ->
                    val lcWord = word.lowercase()
                    val shouldAutoCommit = prefs.correction.autoCorrect.get() &&
                        autoCommitWord != null && index == 0 &&
                        lcWord != autoCommitWord &&
                        !lcWord.startsWith(autoCommitWord!!) &&
                        !personalDicts.values.any { it.containsKey(autoCommitWord) } &&
                        autoCommitWord!!.let { !(it.any(Char::isLetter) && it.any(Char::isDigit)) }
                    WordSuggestionCandidate(
                        text = word,
                        confidence = 1.0,
                        isEligibleForAutoCommit = shouldAutoCommit,
                        sourceProvider = this@QwenSuggestionProvider,
                    )
                }
            } catch (e: Exception) {
                flogDebug { "Qwen:suggest failed: ${e.message}" }
                emptyList()
            }
        }
    }

    private fun learnFromText(text: CharSequence) {
        if (System.currentTimeMillis() < pasteUntil) return
        val words = text.trimEnd().split(whitespace)
            .map { it.lowercase().trimEnd(',', '.', '?', '!', ';', ':', '"', '\'', ')', ']', '}', '>') }
            .filter { it.isNotEmpty() && shouldLearn(it) }
        if (words.size < 2) return
        val recent = words.takeLast(8)

        for (i in 0 until recent.size - 1) {
            val w1 = recent[i]
            val w2 = recent[i + 1]
            val bi = bigrams.computeIfAbsent(w1) { ConcurrentHashMap() }
            bi[w2] = (bi[w2] ?: 0).coerceAtMost(254) + 1
            if (i + 2 < recent.size) {
                val w3 = recent[i + 2]
                val tri = trigrams.computeIfAbsent("$w1|$w2") { ConcurrentHashMap() }
                tri[w3] = (tri[w3] ?: 0).coerceAtMost(254) + 1
            }
        }
        ngramDirty = true
    }

    private fun purgeJunk() {
        var removed = false
        for (dict in personalDicts.values) {
            val it = dict.entries.iterator()
            while (it.hasNext()) {
                if (!shouldLearn(it.next().key)) {
                    it.remove()
                    removed = true
                }
            }
        }
        for ((k1, inner) in bigrams) {
            if (!shouldLearn(k1)) {
                bigrams.remove(k1)
                removed = true
                continue
            }
            val it = inner.entries.iterator()
            while (it.hasNext()) {
                if (!shouldLearn(it.next().key)) {
                    it.remove()
                    removed = true
                }
            }
        }
        for ((k1, inner) in trigrams) {
            val parts = k1.split('|')
            if (parts.size != 2 || !shouldLearn(parts[0]) || !shouldLearn(parts[1])) {
                trigrams.remove(k1)
                removed = true
                continue
            }
            val it = inner.entries.iterator()
            while (it.hasNext()) {
                if (!shouldLearn(it.next().key)) {
                    it.remove()
                    removed = true
                }
            }
        }
        if (removed) {
            personalDirty = true
            ngramDirty = true
        }
    }

    private fun isNoise(w: String): Boolean {
        if (w.length < 1 || w.length > 30) return true
        if (w.contains("@")) return true
        if (w.contains("://") || w.startsWith("www")) return true
        if (w.count { it.isDigit() } > w.length / 2) return true
        if (!w.any { it.isLetter() }) return true
        if (w.toSet().size == 1) return true
        if (w.any { c -> w.count { it == c } > w.length * 0.6 }) return true
        return false
    }

    private fun shouldLearn(w: String): Boolean {
        if (isNoise(w)) return false
        if (w.length < 2) return false
        if (w.any { it.isDigit() }) return false
        return w.all { it.isLetter() }
    }

    fun recordWord(raw: String, lang: String = "vi") {
        val lc = raw.lowercase().trimEnd(',', '.', '?', '!', ';', ':', '"', '\'', ')', ']', '}', '>')
        if (!shouldLearn(lc)) return
        val dict = pd(lang)
        val existing = dict[lc]
        val newCount = (existing?.count ?: 0) + 1
        dict[lc] = PersonalWord(count = newCount, lastUsedTs = System.currentTimeMillis())
        personalDirty = true
    }

    fun getBigramFrequency(prev: String, next: String): Double {
        val bi = bigrams[prev.lowercase()] ?: return 0.0
        return (bi[next.lowercase()] ?: 0).toDouble()
    }

    private fun dampWord(word: String) {
        val lc = word.lowercase()
        val existing = dampedWords[lc]
        val newCount = (existing?.dampCount ?: 0).coerceAtMost(5) + 1
        dampedWords[lc] = DampedWord(dampCount = newCount, lastDampedTs = System.currentTimeMillis())
    }

    private fun commitLearn(textBefore: String, lang: String) {
        val words = textBefore.trimEnd().split(whitespace).filter { it.isNotBlank() }
        val last = words.lastOrNull()?.lowercase()
            ?.trimEnd(',', '.', '?', '!', ';', ':', '"', '\'', ')', ']', '}', '>')
            ?: return
        if (!shouldLearn(last) || last == lastCommittedWord) return
        lastCommittedWord = last
        lastTypedWord = null
        bgScope.launch { learnFromText(textBefore) }
        recordWord(last, lang)
        val lastTop = lastTopSuggestion
        if (lastTop != null && last != lastTop) dampWord(lastTop)
    }

    private fun unlearnWord(word: String, lang: String) {
        dampWord(word)
        pd(lang).remove(word)
        bigrams.remove(word)
        for (inner in bigrams.values) inner.remove(word)
        for (key in trigrams.keys.filter { it.split('|').any { part -> part == word } }) trigrams.remove(key)
        for (inner in trigrams.values) inner.remove(word)
        personalDirty = true
        ngramDirty = true
    }

    private fun computeAlpha(decayedCount: Double, qwenScored: Boolean): Double = when {
        qwenScored -> when {
            decayedCount < 1.0 -> 0.15
            decayedCount < 3.0 -> 0.20
            decayedCount < 10.0 -> 0.25
            decayedCount < 30.0 -> 0.30
            else -> 0.35
        }
        else -> when {
            decayedCount < 1.0 -> 0.25
            decayedCount < 3.0 -> 0.35
            decayedCount < 10.0 -> 0.45
            decayedCount < 30.0 -> 0.50
            else -> 0.50
        }
    }

    private fun decayedCount(pw: PersonalWord): Double {
        val daysSince = (System.currentTimeMillis() - pw.lastUsedTs) / 86400000.0
        return pw.count * 0.95.pow(daysSince)
    }

    private fun personalScore(pw: PersonalWord): Double =
        (decayedCount(pw) / 50.0).coerceIn(0.0, 1.0)

    private fun rerankWithPersonal(candidates: List<Pair<String, Double>>, lang: String = "vi", qwenScored: Boolean = false): List<Pair<String, Double>> {
        val dict = personalDicts[lang] ?: return candidates
        if (dict.isEmpty()) return candidates
        val rawScores = candidates.map { it.second }
        val minScore = rawScores.min()
        val maxScore = rawScores.max()
        val range = maxScore - minScore
        return candidates.map { (word, baseScore) ->
            var score = if (range > 0.0) (baseScore - minScore) / range else 0.5
            val pw = dict[word.lowercase()]
            if (pw != null) {
                val dc = decayedCount(pw)
                val alpha = computeAlpha(dc, qwenScored)
                val ps = personalScore(pw)
                score = alpha * ps + (1.0 - alpha) * score
            }
            val dw = dampedWords[word.lowercase()]
            if (dw != null && dw.dampCount > 0) {
                val penalty = (dw.dampCount * 0.02).coerceAtMost(0.10)
                score *= (1.0 - penalty)
            }
            word to score
        }.sortedByDescending { it.second }
    }

    private fun suggestNextWord(textBefore: String, k: Int, lang: String = "vi"): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val words = textBefore.split(whitespace).filter { it.isNotBlank() }
        val w2 = if (words.isNotEmpty()) words.last().lowercase() else ""
        val w1 = if (words.size >= 2) words[words.size - 2].lowercase() else null

        val scored = mutableMapOf<String, Double>()
        var qwenScored = false

        val predictions = synchronized(modelLock) {
            if (natLoaded && modelPtr != 0L) {
                val contextText = if (discourseBuffer.isNotEmpty() && textBefore.split(whitespace).size <= 2) {
                    discourseBuffer.joinToString(" ") + " " + textBefore.trimStart()
                } else {
                    textBefore
                }
                QwenNatives.predictNext(modelPtr, contextText, limit * 3)
            } else null
        }
        if (predictions != null) {
            qwenScored = true
            val firstBatch = predictions.take(limit * 2)
            val startScore = firstBatch.size.toDouble()
            for ((idx, word) in firstBatch.withIndex()) {
                val lc = word.lowercase()
                if (!shouldLearn(lc) || lc == w2 || lc == w1) continue
                var s = (startScore - idx) * 2.0
                if (w1 != null) {
                    trigrams["$w1|$w2"]?.let { tri ->
                        s += (tri[lc] ?: 0) * TRIGRAM_BOOST
                    }
                }
                bigrams[w2]?.let { bi ->
                    s += (bi[lc] ?: 0) * BIGRAM_BOOST
                }
                scored[lc] = s
            }
        }

        bigrams[w2]?.forEach { (next, freq) ->
            if (next !in scored && shouldLearn(next) && next != w2 && next != w1) {
                scored[next] = (scored[next] ?: 0.0) + freq * BIGRAM_BOOST
            }
        }
        if (w1 != null) {
            trigrams["$w1|$w2"]?.forEach { (w3, freq) ->
                if (w3 !in scored && shouldLearn(w3) && w3 != w2 && w3 != w1) {
                    scored[w3] = (scored[w3] ?: 0.0) + freq * TRIGRAM_BOOST
                }
            }
        }
        personalDicts[lang]?.entries
            ?.filter { it.key !in scored && shouldLearn(it.key) && it.key != w2 && it.key != w1 }
            ?.sortedByDescending { decayedCount(it.value) }
            ?.take(10)
            ?.forEach { (word, pw) ->
                scored[word] = personalScore(pw) * 0.8
            }

        if (scored.isEmpty() && seedWordFrequencies.isNotEmpty()) {
            val sorted = seedWordFrequencies.entries
                .sortedByDescending { it.value }
                .take(limit * 3)
            val maxFreq = sorted.first().value.toDouble()
            for ((idx, entry) in sorted.withIndex()) {
                scored[entry.key] = (entry.value.toDouble() / maxFreq) * 100.0 * (1.0 - idx * 0.005)
            }
        }

        val topBase = scored.entries.sortedByDescending { it.value }
            .take(limit * 3).map { it.key to it.value }

        return rerankWithPersonal(topBase, lang, qwenScored).take(limit)
    }

    private fun ngramWords(): Set<String> {
        val words = mutableSetOf<String>()
        words.addAll(bigrams.keys)
        bigrams.values.forEach { words.addAll(it.keys) }
        trigrams.keys.forEach { key -> key.split("|").forEach { words.add(it) } }
        trigrams.values.forEach { words.addAll(it.keys) }
        return words
    }

    private fun completeCurrentWord(prefix: String, k: Int, textBefore: String, lang: String = "vi"): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val lcPrefix = prefix.lowercase()
        val ctxWords = textBefore.dropLast(prefix.length).trimEnd().split(whitespace).filter { it.isNotBlank() }
        val ctxKey = ctxWords.lastOrNull()?.lowercase() ?: ""
        val cacheKey = "$lcPrefix|$ctxKey|$lang"
        suggestionCache.get(cacheKey)?.let { return it }

        val result = doCompleteCurrentWord(prefix, k, textBefore, lang)
        suggestionCache.put(cacheKey, result)
        return result
    }

    private fun doCompleteCurrentWord(prefix: String, k: Int, textBefore: String, lang: String = "vi"): List<Pair<String, Double>> {
        val limit = k.coerceIn(1, 15)
        val lcPrefix = prefix.lowercase()
        val resolvedPrefix = resolveTelexPrefix(lcPrefix)
        val hasMixedAlphaNum = lcPrefix.any { it.isLetter() } && lcPrefix.any { it.isDigit() }

        val context = buildString {
            val ctx = textBefore.dropLast(prefix.length).trimEnd()
            append(ctx)
            if (ctx.isNotEmpty()) append(' ')
        }

        val personalWordPool = personalDicts[lang]?.keys ?: emptySet()
        val basePool = (
            if (useTrie) {
                val trieResults = prefixTrie[lcPrefix].orEmpty()
                val resolvedResults = if (resolvedPrefix != lcPrefix) {
                    prefixTrie[resolvedPrefix].orEmpty()
                } else emptyList()
                (trieResults + resolvedResults).filter { it.length > resolvedPrefix.length }
            } else {
                seedWords.filter {
                    it.startsWith(lcPrefix) || it.startsWith(resolvedPrefix)
                }.filter { it.length > resolvedPrefix.length }
            }
        ).union(ngramWords()).union(personalWordPool)
            .filter { shouldLearn(it) }
            .take(200)
            .toSet()

        val autoCorrectOn = prefs.correction.autoCorrect.get()

        val mergedPool = if (autoCorrectOn && lcPrefix.length >= 2 && !hasMixedAlphaNum) {
            val corrections = autocorrectEngine?.correct(lcPrefix, limit * 2) ?: emptyList()
            val typoCorrections = typoDetector?.detectAndScore(lcPrefix) ?: emptyList()
            (basePool + corrections.map { it.word } + typoCorrections.map { it.first })
                .take(250)
                .toTypedArray()
        } else {
            basePool.toTypedArray()
        }

        val candidates = mutableListOf<Pair<String, Double>>()
        var qwenScored = false

        val scores = synchronized(modelLock) {
            if (!natLoading && natLoaded && modelPtr != 0L) {
                QwenNatives.scoreCandidates(modelPtr, context, mergedPool)
            } else null
        }
        if (scores != null && scores.size == mergedPool.size) {
                qwenScored = true
                for (i in mergedPool.indices) {
                    val base = if (autoCorrectOn && !hasMixedAlphaNum) {
                        autocorrectEngine?.score(lcPrefix, mergedPool[i], scores[i].toDouble()) ?: scores[i].toDouble()
                    } else {
                        scores[i].toDouble()
                    }
                    candidates.add(mergedPool[i] to base)
                }
            }

        if (candidates.isEmpty()) {
            val contextWords = context.trimEnd().split(whitespace)
                .map { it.trimEnd(',', '.', '?', '!', ';', ':', '"', '\'', ')', ']', '}', '>') }
                .filter { it.isNotBlank() }
            val w2 = if (contextWords.isNotEmpty()) contextWords.last().lowercase() else ""
            val w1 = if (contextWords.size >= 2) contextWords[contextWords.size - 2].lowercase() else null
            for (word in mergedPool) {
                var s = 0.5
                if (w1 != null) {
                    trigrams["$w1|$w2"]?.let { tri -> s += (tri[word]?.toDouble() ?: 0.0) * TRIGRAM_BOOST }
                }
                bigrams[w2]?.let { bi -> s += (bi[word]?.toDouble() ?: 0.0) * BIGRAM_BOOST }
                val base = if (autoCorrectOn && !hasMixedAlphaNum) {
                    autocorrectEngine?.score(lcPrefix, word, s) ?: s
                } else {
                    s
                }
                candidates.add(word to base)
            }
        }

        val phraseCandidates = phraseMap[resolvedPrefix].orEmpty()
            .filter { it.length > resolvedPrefix.length }
        if (phraseCandidates.isNotEmpty()) {
            for (phrase in phraseCandidates) {
                if (candidates.none { it.first == phrase }) {
                    candidates.add(phrase to 0.9)
                }
            }
        }

        return rerankWithPersonal(candidates, lang, qwenScored).take(limit)
    }

    private fun getCurrentWord(content: EditorContent): String? {
        content.composingText.let { if (it.isNotBlank()) return it.toString() }
        content.currentWordText.let { if (it.isNotBlank()) return it.toString() }
        return null
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        val word = candidate.text.toString().lowercase().trim()
        if (!shouldLearn(word)) return
        recordWord(word, langFor(subtype))
        val typed = lastTypedWord
        if (typed != null && typed != word && typed !in seedWords) {
            unlearnWord(typed, langFor(subtype))
        }
        bgScope.launch { savePersonalDict() }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun rerankGlideSuggestions(
        subtype: Subtype,
        textBefore: String,
        candidates: List<String>,
    ): List<String> {
        if (candidates.isEmpty() || !QwenNatives.isAvailable) return candidates
        val context = buildString {
            val ctx = textBefore.trimEnd()
            append(ctx)
            if (ctx.isNotEmpty()) append(' ')
        }
        val scores = synchronized(modelLock) {
            if (!natLoading && natLoaded && modelPtr != 0L) {
                QwenNatives.scoreCandidates(modelPtr, context, candidates.toTypedArray())
            } else null
        }
        if (scores == null || scores.size != candidates.size) return candidates
        val min = scores.minOrNull() ?: 0f
        val max = scores.maxOrNull() ?: 0f
        val range = max - min
        return candidates.indices
            .map { i ->
                val clean = when {
                    scores[i].isNaN() || scores[i].isInfinite() -> 0.0
                    else -> scores[i].toDouble()
                }
                val norm = if (range > 0f) (clean - min.toDouble()) / range.toDouble() else 0.5
                candidates[i] to norm
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        val lang = langFor(subtype)
        return if (lang == "en")
            (enWordFrequencies.keys + pd("en").keys).toList()
        else
            seedWords.union(pd("vi").keys).toList()
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val lc = word.lowercase()
        val lang = langFor(subtype)
        val dict = personalDicts[lang] ?: mutableMapOf()
        val pw = dict[lc]
        if (pw != null) return (decayedCount(pw) / 50.0).coerceIn(0.0, 1.0)
        val freq = if (lang == "en") enWordFrequencies[lc] else seedWordFrequencies[lc]
        if (freq != null) return (freq / 50_000_000.0).coerceIn(0.0, 1.0)
        return 0.0
    }

    override suspend fun destroy() {
        bgScope.cancel()
        if (personalDirty) savePersonalDict()
        if (ngramDirty) saveNgrams()
        saveDiscourseBuffer()
        synchronized(modelLock) {
            if (modelPtr != 0L) QwenNatives.close(modelPtr)
            modelPtr = 0L
            natLoaded = false
        }
    }
}

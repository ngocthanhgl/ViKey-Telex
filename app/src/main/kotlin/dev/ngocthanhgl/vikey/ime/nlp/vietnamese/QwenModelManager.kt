package dev.ngocthanhgl.vikey.ime.nlp.vietnamese

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class QwenDownloadState {
    data object Idle : QwenDownloadState()
    data object Downloading : QwenDownloadState()
    data class Progress(val bytesDownloaded: Long, val totalBytes: Long) : QwenDownloadState()
    data object Success : QwenDownloadState()
    data class Error(val message: String) : QwenDownloadState()
}

object QwenModelManager {
    private const val MODEL_FILENAME = "qwen-pruned-50k-q5_0.gguf"
    private const val MODEL_URL = "https://github.com/ngocthanhgl/ViKey-Telex/releases/download/v2.3.4/qwen-pruned-50k-q5_0.gguf"

    private val _downloadState = MutableStateFlow<QwenDownloadState>(QwenDownloadState.Idle)
    val downloadState = _downloadState.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getModelFile(): File? {
        val ctx = appContext ?: return null
        val file = File(ctx.filesDir, MODEL_FILENAME)
        return if (file.exists() && file.length() > 0L) file else null
    }

    fun isModelAvailable(): Boolean = getModelFile() != null

    suspend fun download() = withContext(Dispatchers.IO) {
        val ctx = appContext ?: run {
            _downloadState.value = QwenDownloadState.Error("QwenModelManager not initialized")
            return@withContext
        }
        _downloadState.value = QwenDownloadState.Downloading

        try {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.connect()

            val totalBytes = connection.contentLengthLong
            val inputStream = connection.inputStream
            val outputFile = File(ctx.filesDir, MODEL_FILENAME)
            outputFile.parentFile?.mkdirs()

            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead: Long = 0L
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead.toLong()
                    if (totalBytes > 0L) {
                        _downloadState.value = QwenDownloadState.Progress(totalRead, totalBytes)
                    }
                }
            }

            inputStream.close()
            connection.disconnect()

            if (outputFile.exists() && outputFile.length() > 0L) {
                _downloadState.value = QwenDownloadState.Success
                notifyModelReady()
            } else {
                _downloadState.value = QwenDownloadState.Error("Downloaded file is empty")
            }
        } catch (e: Exception) {
            _downloadState.value = QwenDownloadState.Error(e.message ?: "Download failed")
        }
    }

    private var onModelReadyListener: (() -> Unit)? = null

    fun setOnModelReadyListener(listener: () -> Unit) {
        onModelReadyListener = listener
    }

    fun clearModelReadyListener() {
        onModelReadyListener = null
    }

    fun reset() {
        _downloadState.value = QwenDownloadState.Idle
    }

    internal fun notifyModelReady() {
        onModelReadyListener?.invoke()
    }
}

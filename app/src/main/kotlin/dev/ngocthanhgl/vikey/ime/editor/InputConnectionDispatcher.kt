package dev.ngocthanhgl.vikey.ime.editor

import android.view.inputmethod.InputConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps InputConnection IPC calls to run off the main thread.
 * All [InputConnection] methods are documented to be thread-safe.
 */
object InputConnectionDispatcher {

    /**
     * Call [block] on [Dispatchers.IO] and return the result.
     */
    suspend fun <T> call(block: suspend () -> T): T {
        return withContext(Dispatchers.IO) { block() }
    }

    /**
     * Fire-and-forget an IPC call. No result needed.
     */
    suspend fun fire(block: suspend () -> Unit) {
        withContext(Dispatchers.IO) { block() }
    }
}

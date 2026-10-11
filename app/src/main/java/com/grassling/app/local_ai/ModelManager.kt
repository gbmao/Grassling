package com.grassling.app.local_ai

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/** Owns only model installation. All synchronous file methods must be called off the UI thread. */
class ModelManager(context: Context) {
    private val appContext = context.applicationContext

    fun getModelFile(): File = File(appContext.filesDir, "models/$FILE_NAME")

    @WorkerThread
    fun isModelInstalled(): Boolean = validate(getModelFile())

    @WorkerThread
    fun deleteModel() = synchronized(fileLock) {
        // Call after cancelling/awaiting any download work and releasing the loaded model.
        listOf(getModelFile(), File(getModelFile().path + ".part")).forEach {
            if (it.exists() && !it.delete()) throw IOException("Could not delete the AI model.")
        }
    }

    /** Keeps the partial download across interruption and resumes against a pinned revision. */
    suspend fun downloadModel(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        synchronized(fileLock) {
            val destination = getModelFile()
            if (validate(destination)) {
                onProgress(1f)
                return@synchronized
            }
            val directory = destination.parentFile!!
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Could not create the model directory.")
            }
            val partial = File(directory, "$FILE_NAME.part")
            if (partial.length() > EXPECTED_SIZE) partial.delete()
            if (partial.length() != EXPECTED_SIZE) {
                var downloaded = partial.length()
                val connection = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 30_000
                    connection.readTimeout = 60_000
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    if (downloaded > 0L) connection.setRequestProperty("Range", "bytes=$downloaded-")
                    val status = connection.responseCode
                    if (status == HttpURLConnection.HTTP_PARTIAL) {
                        val range = connection.getHeaderField("Content-Range").orEmpty()
                        if (range != "bytes $downloaded-${EXPECTED_SIZE - 1}/$EXPECTED_SIZE") {
                            throw IOException("Unexpected model download range.")
                        }
                    } else if (status == HttpURLConnection.HTTP_OK) {
                        downloaded = 0L // The server ignored Range: safely restart the temporary file.
                    } else {
                        throw IOException("Model download failed (HTTP $status).")
                    }
                    onProgress(downloaded.toFloat() / EXPECTED_SIZE)
                    connection.inputStream.buffered().use { source ->
                        FileOutputStream(partial, downloaded > 0L).use { target ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = source.read(buffer)
                                if (read < 0) break
                                if (downloaded + read > EXPECTED_SIZE) {
                                    throw IOException("The downloaded model is larger than expected.")
                                }
                                target.write(buffer, 0, read)
                                downloaded += read
                                onProgress((downloaded.toFloat() / EXPECTED_SIZE).coerceAtMost(0.99f))
                            }
                            target.fd.sync()
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
            coroutineContext.ensureActive()
            if (!validate(partial)) {
                // Retain short files for retry; discard complete but corrupt files.
                if (partial.length() >= EXPECTED_SIZE) partial.delete()
                throw IOException("The AI model download is incomplete or failed verification. Please retry.")
            }
            if (destination.exists() && !destination.delete()) {
                throw IOException("Could not replace the invalid model.")
            }
            if (!partial.renameTo(destination)) throw IOException("Could not install the AI model.")
            onProgress(1f)
        }
    }

    /** Reuses persistent work on recreation; a finished/failed request can be retried. */
    suspend fun retryDownload(): UUID = withContext(Dispatchers.IO) {
        val workManager = WorkManager.getInstance(appContext)
        workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
            .firstOrNull { !it.state.isFinished }?.let { return@withContext it.id }
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>().build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request).result.get()
        val work = workManager.getWorkInfosForUniqueWork(WORK_NAME).get()
        work.firstOrNull { it.id == request.id }?.id
            ?: work.first().id
    }

    private fun validate(file: File): Boolean {
        if (!file.isFile || file.length() != EXPECTED_SIZE) return false
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { source ->
                val magic = ByteArray(4)
                if (source.read(magic) != 4 || !magic.contentEquals(byteArrayOf(71, 71, 85, 70))) {
                    return false
                }
                digest.update(magic)
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) } == EXPECTED_SHA256
        } catch (_: IOException) {
            false
        }
    }

    companion object {
        const val FILE_NAME = "gemma-3-270m-it-Q8_0.gguf"
        const val EXPECTED_SIZE = 291_545_600L
        const val EXPECTED_SHA256 = "0ef57d2c838458a1952664260dcba38e5bdda37494f3af732f06e4add24068e3"
        const val DOWNLOAD_URL = "https://huggingface.co/ggml-org/gemma-3-270m-it-GGUF/resolve/" +
            "e7647be17ae1108f2f605ed061ca0608b171afff/$FILE_NAME"
        const val WORK_NAME = "install-gemma-3-270m-it-q8"
        private val fileLock = Any()
    }
}

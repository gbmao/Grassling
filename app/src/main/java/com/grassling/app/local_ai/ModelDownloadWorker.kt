package com.grassling.app.local_ai

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Persistent WorkManager job: survives Activity recreation and resumes .part after process death. */
class ModelDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        var lastPercent = -1
        ModelManager(applicationContext).downloadModel { progress ->
            val percent = (progress * 100).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                setProgressAsync(workDataOf(PROGRESS to percent))
            }
        }
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled // WorkManager reschedules interrupted work; keep its partial file.
    } catch (failure: IOException) {
        if (runAttemptCount < MAX_IO_RETRIES) {
            Log.i("GrasslingModel", "Download interrupted; WorkManager will resume the partial file", failure)
            Result.retry()
        } else {
            Log.w("GrasslingModel", "Model download failed after $runAttemptCount retries", failure)
            Result.failure(workDataOf(ERROR to
                "Could not download the AI model. Check your connection and free storage, then retry."))
        }
    } catch (failure: Exception) {
        Log.w("GrasslingModel", "Model installation failed", failure)
        Result.failure(workDataOf(ERROR to
            "Could not download the AI model. Check your connection and free storage, then retry."))
    }

    companion object {
        const val PROGRESS = "download_percent"
        const val ERROR = "download_error"
        private const val MAX_IO_RETRIES = 5
    }
}

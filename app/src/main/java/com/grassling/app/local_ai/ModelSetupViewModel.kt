package com.grassling.app.local_ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.grassling.app.GrasslingApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ModelSetupState {
    data object Preparing : ModelSetupState
    data class Downloading(val percent: Int) : ModelSetupState
    data class Error(val message: String) : ModelSetupState
    data object Ready : ModelSetupState
}

class ModelSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = ModelManager(application)
    private val mutableState = MutableStateFlow<ModelSetupState>(ModelSetupState.Preparing)
    val state = mutableState.asStateFlow()
    private var preparation: Job? = null

    init { retry() }

    fun retry() {
        if (preparation?.isActive == true) return
        preparation = viewModelScope.launch {
            try {
                mutableState.value = ModelSetupState.Preparing
                if (!withContext(Dispatchers.IO) { manager.isModelInstalled() }) {
                    mutableState.value = ModelSetupState.Downloading(0)
                    val id = manager.retryDownload()
                    WorkManager.getInstance(getApplication())
                        .getWorkInfoByIdFlow(id).filterNotNull().first { work ->
                            mutableState.value = ModelSetupState.Downloading(
                                work.progress.getInt(ModelDownloadWorker.PROGRESS, 0),
                            )
                            when (work.state) {
                                WorkInfo.State.FAILED -> error(work.outputData.getString(ModelDownloadWorker.ERROR)
                                    ?: "Could not download the AI model. Check your connection and retry.")
                                WorkInfo.State.CANCELLED -> error("The model download was interrupted. Please retry.")
                                WorkInfo.State.SUCCEEDED -> true
                                else -> false
                            }
                        }
                }
                mutableState.value = ModelSetupState.Preparing
                withContext(Dispatchers.IO) {
                    getApplication<GrasslingApplication>().localAI.loadModel().getOrThrow()
                }
                mutableState.value = ModelSetupState.Ready
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.value = ModelSetupState.Error(failure.message ?: "Could not prepare Grassling. Please retry.")
            }
        }
    }
}

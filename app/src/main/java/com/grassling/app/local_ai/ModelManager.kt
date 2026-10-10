package com.grassling.app.local_ai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

internal class ModelManager(
    context: Context,
    private val config: AIConfig,
    private val engine: LlamaEngine,
) {
    private val appContext = context.applicationContext

    val modelFile: File
        get() = File(File(appContext.filesDir, "local_ai/models"), config.modelFileName)

    fun modelExists(): Boolean {
        val file = modelFile
        if (!file.isFile || file.length() < GGUF_MAGIC.size) return false
        return runCatching {
            file.inputStream().use { input ->
                val magic = ByteArray(GGUF_MAGIC.size)
                input.read(magic) == magic.size && magic.contentEquals(GGUF_MAGIC)
            }
        }.getOrDefault(false)
    }

    suspend fun installModel(uri: Uri): File = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.use { source ->
            installModelStream(source)
        } ?: throw IllegalArgumentException("Não foi possível abrir o arquivo selecionado.")
    }

    suspend fun loadModel() = withContext(Dispatchers.IO) {
        if (!modelExists()) {
            if (bundledModelExists()) {
                appContext.assets.open(config.modelFileName).use(::installModelStream)
            }
        }
        if (!modelExists()) {
            throw ModelUnavailableException(
                "Modelo Qwen3 não encontrado. Selecione ${config.modelFileName} para importar; " +
                    "o app também procura o modelo empacotado em seus assets.",
            )
        }
        engine.loadModel(modelFile.absolutePath)
    }

    fun close() {
        engine.releaseModel()
    }

    private fun hasGgufHeader(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val magic = ByteArray(GGUF_MAGIC.size)
            input.read(magic) == magic.size && magic.contentEquals(GGUF_MAGIC)
        }
    }.getOrDefault(false)

    private fun bundledModelExists(): Boolean =
        appContext.assets.list("")?.contains(config.modelFileName) == true

    private fun installModelStream(source: InputStream): File {
        val destination = modelFile
        val directory = destination.parentFile
            ?: throw IllegalStateException("Não foi possível encontrar a pasta interna do modelo.")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Não foi possível criar a pasta de modelos: $directory")
        }

        val temporary = File(directory, "${destination.name}.partial")
        val backup = File(directory, "${destination.name}.previous")
        temporary.delete()

        try {
            temporary.outputStream().buffered().use { target ->
                source.copyTo(target, bufferSize = 1024 * 1024)
            }
            if (!hasGgufHeader(temporary)) {
                throw IllegalArgumentException("O arquivo selecionado não tem um cabeçalho GGUF válido.")
            }

            backup.delete()
            val hadPreviousModel = destination.exists()
            if (hadPreviousModel && !destination.renameTo(backup)) {
                throw IllegalStateException("Não foi possível substituir o modelo existente.")
            }
            if (!temporary.renameTo(destination)) {
                if (hadPreviousModel) backup.renameTo(destination)
                throw IllegalStateException("Não foi possível gravar o modelo no armazenamento interno.")
            }
            backup.delete()
            return destination
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
        }
    }

    companion object {
        private val GGUF_MAGIC = byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte())
    }
}

class ModelUnavailableException(message: String) : IllegalStateException(message)

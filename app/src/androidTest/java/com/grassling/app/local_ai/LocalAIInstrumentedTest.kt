package com.grassling.app.local_ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAIInstrumentedTest {
    @Test
    fun tiredUserGetsAnOfflineGrasslingReply() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val localAI = LlamaLocalAI(context)

        try {
            val loadResult = localAI.loadModel()
            assertTrue(
                loadResult.exceptionOrNull()?.message
                    ?: "O modelo Qwen3 precisa ser importado antes do teste.",
                loadResult.isSuccess,
            )

            val response = localAI.respond(
                message = "Estou cansado hoje.",
                gameSnapshot = GameSnapshot(),
            ).getOrThrow()
            Log.i("LocalAIInstrumentedTest", "Grassling: $response")

            assertTrue("O Qwen local deve gerar uma resposta não vazia.", response.isNotBlank())
            assertTrue("A resposta deve ter no máximo três frases: $response", sentenceCount(response) <= 3)
            assertTrue(
                "A resposta deve sugerir uma atividade externa leve: $response",
                listOf("caminh", "passeio", "voltinha", "ar livre", "parque", "andar", "sair", "sol")
                    .any { response.contains(it, ignoreCase = true) },
            )
        } finally {
            localAI.close()
        }
    }

    private fun sentenceCount(text: String): Int {
        if (text.isBlank()) return 0
        return Regex("[.!?]+(?:\\s|$)").findAll(text).count().coerceAtLeast(1)
    }
}

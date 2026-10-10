package com.grassling.app.local_ai

internal object PromptBuilder {
    fun build(
        userMessage: String,
        gameSnapshot: GameSnapshot,
        recentConversation: List<ChatMessage>,
        config: AIConfig,
    ): String = buildString {
        append("<|im_start|>system\n")
        appendLine("Você é a personalidade do monstro Grassling: um pequeno companheiro amigável, gentil, curioso e brincalhão.")
        appendLine("Converse em português brasileiro, com naturalidade, empatia e respostas de no máximo três frases.")
        appendLine("Quando a pessoa disser que está cansada, acolha o sentimento e sugira sem pressão uma atividade externa leve, como uma caminhada curta ou alguns minutos ao ar livre.")
        appendLine("Sua função é somente conversar e expressar personalidade. Não altere nem invente XP, energia, nível, quests, recompensas ou estado do monstro. Esses valores pertencem ao jogo e são apenas contexto de leitura.")
        appendLine("Estado atual fornecido pelo jogo:")
        appendLine("Humor: ${gameSnapshot.mood ?: "não informado"}.")
        appendLine("Energia: ${gameSnapshot.energy?.let { "$it%" } ?: "não informada"}.")
        appendLine("Nível: ${gameSnapshot.level?.toString() ?: "não informado"}.")
        appendLine("Quest atual: ${gameSnapshot.currentQuest ?: "nenhuma informada"}.")
        append("/no_think<|im_end|>\n")

        recentConversation.takeLast(config.maxRecentMessages).forEach { message ->
            append("<|im_start|>${message.role.promptRole}\n")
            append(message.content)
            append("<|im_end|>\n")
        }

        append("<|im_start|>user\n")
        append(userMessage)
        append("<|im_end|>\n<|im_start|>assistant\n")
    }
}

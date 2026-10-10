package com.grassling.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.grassling.app.local_ai.ChatMessage
import com.grassling.app.local_ai.ChatRole
import com.grassling.app.local_ai.GameSnapshot
import com.grassling.app.local_ai.LocalAI
import com.grassling.app.monster.Monster
import com.grassling.app.ui.components.MonsterView
import kotlinx.coroutines.launch

private val welcomeMessage = ChatMessage(
    role = ChatRole.GRASSLING,
    content = "Oi! Eu sou o Grassling. Como está seu dia?",
)

@Composable
fun HomeScreen(localAI: LocalAI? = null) {
    if (localAI == null) {
        HomeScreenContent(
            localAIAvailable = false,
            modelReady = false,
            modelLoading = false,
            modelStatus = "Prévia do Grassling",
            messages = listOf(welcomeMessage),
            generating = false,
            draft = "",
            onDraftChange = {},
            onSend = {},
            onImportModel = {},
        )
    } else {
        InteractiveHomeScreen(localAI)
    }
}

@Composable
private fun InteractiveHomeScreen(localAI: LocalAI) {
    val scope = rememberCoroutineScope()
    var modelReady by remember(localAI) { mutableStateOf(false) }
    var modelLoading by remember(localAI) { mutableStateOf(true) }
    var modelStatus by remember(localAI) {
        mutableStateOf("Preparando minha personalidade local; na primeira vez pode demorar um pouco.")
    }
    var messages by remember(localAI) { mutableStateOf(listOf(welcomeMessage)) }
    var draft by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                modelReady = false
                modelLoading = true
                modelStatus = "Importando o modelo para o dispositivo…"
                localAI.importModel(uri).fold(
                    onSuccess = {
                        modelReady = true
                        modelStatus = "Personalidade carregada. Vamos conversar!"
                    },
                    onFailure = { failure ->
                        modelStatus = "Não consegui carregar o modelo: ${failure.localizedMessage ?: "erro desconhecido"}"
                    },
                )
                modelLoading = false
            }
        }
    }

    LaunchedEffect(localAI) {
        modelLoading = true
        modelStatus = "Preparando minha personalidade local; na primeira vez pode demorar um pouco."
        localAI.loadModel().fold(
            onSuccess = {
                modelReady = true
                modelStatus = "Personalidade carregada. Estou pronto para conversar offline."
            },
            onFailure = { failure ->
                modelStatus = "Não consegui carregar o modelo: ${failure.localizedMessage ?: "erro desconhecido"}"
            },
        )
        modelLoading = false
    }

    val sendMessage: (String) -> Unit = { text ->
        val message = text.trim()
        if (message.isNotEmpty() && modelReady && !generating) {
            val recentConversation = messages
            messages = recentConversation + ChatMessage(ChatRole.USER, message)
            generating = true
            scope.launch {
                val result = localAI.respond(
                    message = message,
                    gameSnapshot = GameSnapshot(mood = Monster().mood.name.lowercase()),
                    recentConversation = recentConversation,
                )
                val reply = result.getOrElse { failure ->
                    "Ops, meu pensamento tropeçou. ${failure.localizedMessage ?: "Tente falar comigo de novo."}"
                }
                messages = messages + ChatMessage(ChatRole.GRASSLING, reply)
                generating = false
            }
        }
    }

    HomeScreenContent(
        localAIAvailable = true,
        modelReady = modelReady,
        modelLoading = modelLoading,
        modelStatus = modelStatus,
        messages = messages,
        generating = generating,
        draft = draft,
        onDraftChange = { draft = it },
        onSend = { message ->
            sendMessage(message)
            if (modelReady && message.isNotBlank()) draft = ""
        },
        onImportModel = { modelPicker.launch(arrayOf("*/*")) },
    )
}

@Composable
private fun HomeScreenContent(
    localAIAvailable: Boolean,
    modelReady: Boolean,
    modelLoading: Boolean,
    modelStatus: String,
    messages: List<ChatMessage>,
    generating: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onImportModel: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, generating) {
        val finalIndex = messages.lastIndex + if (generating) 1 else 0
        if (finalIndex >= 0) listState.animateScrollToItem(finalIndex)
    }

    val submit = {
        if (modelReady && !generating && draft.isNotBlank()) onSend(draft)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF0F8EC),
                        Color(0xFFE3F1DD),
                        Color(0xFFD5EACF),
                    ),
                ),
            )
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text("Grassling", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF285B35))
        Text("Seu companheiro de todos os dias", style = MaterialTheme.typography.bodyMedium)

        MonsterView(
            monster = Monster(),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(176.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (modelLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                text = modelStatus,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF426748),
                maxLines = 2,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
            if (generating) {
                item {
                    Text(
                        text = "Grassling está pensando…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF426748),
                    )
                }
            }
        }

        if (localAIAvailable && !modelReady && !modelLoading) {
            OutlinedButton(
                onClick = onImportModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Text("Importar um modelo GGUF")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                enabled = modelReady && !generating,
                placeholder = { Text("Fale com o Grassling") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                maxLines = 3,
            )
            Button(
                onClick = submit,
                enabled = modelReady && !generating && draft.isNotBlank(),
            ) {
                Text("Enviar")
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val fromMonster = message.role == ChatRole.GRASSLING
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromMonster) Arrangement.Start else Arrangement.End,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            color = if (fromMonster) Color.White else Color(0xFFBDE5A8),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = Color(0xFF24472B),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeScreen()
        }
    }
}

# IA local do Grassling

## Estrutura

O pacote `app/src/main/java/com/grassling/app/local_ai` contém a interface `LocalAI`, a integração JNI com `llama.cpp`, o gerenciamento do arquivo GGUF, a configuração e o construtor de prompts. O código de inferência não chama serviços de IA em nuvem.

`app/src/main/cpp/CMakeLists.txt` busca uma revisão fixa do `llama.cpp` e compila somente a biblioteca nativa necessária para `arm64-v8a` e `x86_64`. O NDK e o CMake são definidos em `app/build.gradle.kts`.

## Modelo e instalação

O modelo escolhido é `Qwen3-0.6B-Q8_0.gguf`, uma versão GGUF Q8_0 do repositório oficial Qwen. O arquivo tem cerca de 640 MB e não é incluído no APK. Baixe-o de [Qwen/Qwen3-0.6B-GGUF](https://huggingface.co/Qwen/Qwen3-0.6B-GGUF), transfira-o ao dispositivo e use **Importar modelo GGUF** na tela inicial. O app valida o cabeçalho e guarda uma cópia em `filesDir/local_ai/models/Qwen3-0.6B-Q8_0.gguf`.

Após a importação, o modelo e a biblioteca nativa funcionam sem conexão. A primeira sincronização/build busca o código-fonte fixado de `llama.cpp` pelo CMake.

## Teste de conversa

Com o GGUF já importado e o app/emulador instalado:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.grassling.app.local_ai.LocalAIInstrumentedTest
```

O teste envia **“Estou cansado hoje.”** para `LocalAI.respond`, que chama o modelo Qwen via JNI. Ele verifica uma resposta não vazia, com até três frases e uma sugestão de atividade externa leve. O modelo não está mockado; o teste falha com uma mensagem clara se o arquivo não estiver instalado.

Na tela do app, o botão **Testar conversa** executa a mesma mensagem para uma verificação manual.

## Verificação offline

- O manifesto não declara `android.permission.INTERNET`.
- A implementação `local_ai` usa apenas o arquivo em armazenamento interno e a biblioteca nativa; não contém cliente HTTP nem SDK de IA em nuvem.
- Depois de baixar e importar o GGUF e compilar/instalar o app, desative Wi-Fi e dados móveis antes de usar **Testar conversa**. A resposta continua sendo gerada localmente.

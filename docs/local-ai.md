# Grassling local AI

Grassling uses [Gemma 3 270M IT Q8_0](https://huggingface.co/ggml-org/gemma-3-270m-it-GGUF) through the existing llama.cpp/JNI bridge. Inference is entirely local. The network is used only to install the weights.

## Installation

On startup `ModelSetupViewModel` checks the model on `Dispatchers.IO`. If it is missing or invalid, `ModelDownloadWorker` downloads it automatically using unique WorkManager work. Activity recreation reconnects to the same job; process interruption preserves the partial file and work can resume when Android schedules it again. A force-stopped app resumes after the next launch.

- Destination: `filesDir/models/gemma-3-270m-it-Q8_0.gguf`
- Temporary file: `gemma-3-270m-it-Q8_0.gguf.part` in the same directory
- Revision: `e7647be17ae1108f2f605ed061ca0608b171afff`
- Expected size: `291545600` bytes
- SHA-256: `0ef57d2c838458a1952664260dcba38e5bdda37494f3af732f06e4add24068e3`

The size, GGUF header and SHA-256 must match before atomic rename and before loading. Range downloads resume a partial file; if the server ignores Range, it restarts that file safely. Network/storage errors show a Retry button. A failed partial file is never passed to JNI. Completed corrupt downloads are discarded. WorkManager interruptions retain partial bytes.

`ModelManager` exposes `isModelInstalled`, `downloadModel`, `getModelFile`, `deleteModel`, and `retryDownload` (enqueue/reconnect to persistent work). Call synchronous filesystem methods on an IO dispatcher. Before deleting an installed model, cancel/await any download and close the AI off the main thread.

No weights belong in Git, assets, resources or the APK. The previous `ai` assets source is removed, `.gguf` and `.gguf.part` are ignored, and `preBuild` rejects weights under `app/src`. Model terms: https://ai.google.dev/gemma/terms.

## Inference

`GrasslingApplication` owns one lazy `LlamaLocalAI` instance per process. Native library initialization, model loading and validation happen off the UI thread. The loaded model/context survive Activity recreation. Explicit close is reserved for teardown/tests.

Defaults: 4 threads, 2048 context tokens, at most 32 generated tokens. Native code is compiled with CMake Release for arm64-v8a and x86_64 (emulator). Android ARM64 uses the portable ARM/NEON implementation from the pinned llama.cpp version. No newer device-specific instruction set is forced.

The JNI engine retains identical prompt tokens and drops all previous output tokens before each independent request. Full SWA cache storage keeps Gemma's prefix available; failed sequence removal falls back to clearing the cache. Errors invalidate the cache. There is no conversation history. The current bridge returns complete short responses; it had no streaming API to retain.

`PromptBuilder` uses Gemma's `user`/`model` format and places character instructions in the first user turn; llama.cpp adds BOS. See https://ai.google.dev/gemma/docs/core/prompt-structure. Fixed prompt tokens are reused through the KV prefix cache rather than replayed as new conversation turns.

`LocalAI.generatePersonality(snapshot)` and `generateQuest(snapshot, recentQuests)` accept read-only energy, mood and owner walking activity. Kotlin owns all game state. These APIs do not modify quests, mood or energy. Empty or non-walking quest responses get up to two short prompt retries. The normal home screen remains as it was; no general chat UI is introduced.

## Benchmark and checks

Build and install the app, let its automatic download finish, then run:

```sh
./gradlew connectedDebugAndroidTest
adb logcat -d -s GrasslingLocalAI:I GemmaBenchmark:I
```

`LocalAIInstrumentedTest` checks rejected partial/corrupt files, Gemma turn formatting, then runs six independent walking quests and three personality prompts through the actual JNI model. Native logs report model load time, prompt/cached/output token counts, TTFT, prompt tokens/s and decode tokens/s. Prompt throughput counts only evaluated tokens; decode throughput includes sampling and excludes prefill. TTFT excludes initial model load and includes tokenization, cache setup, prefill and the first sample.

Benchmark results are documented separately under `docs/benchmarks/`. Emulator timings must not be interpreted as phone/ARM64 performance.

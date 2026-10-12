# Grassling

Grassling is an experimental Android virtual-pet app that turns walking into a small game loop.

Your Grassling slowly builds up energy over time. As its energy rises, its mood becomes increasingly restless. Walking and completing outdoor quests reduce that energy and help calm it down. A small local language model gives Grassling short personality lines and reacts when you accept a quest — entirely on-device after the model has been downloaded.

> **Project status:** early development / prototype. Core walking, quest, persistence, and local-AI flows are implemented, but the game design and generated dialogue are still being refined.

## What it does

- Tracks walking using Android's step-counter sensor.
- Keeps step tracking active through a foreground service.
- Persists total steps and quest history locally.
- Offers short outdoor quests with step-based, time-based, or manual objectives.
- Allows one active quest at a time and keeps a completed-quest history.
- Uses walking and quest completion to reduce Grassling's energy.
- Maps energy to four moods:
  - `SLEEPING` — 0–29
  - `NORMAL` — 30–59
  - `ANXIOUS` — 60–79
  - `EXPLODING_WITH_RAGE` — 80–100
- Runs Gemma locally through `llama.cpp` and JNI.
- Generates short Grassling dialogue based on mood, energy, and quest state.
- Generates a short reaction after the user accepts a quest.
- Downloads and verifies the AI model on first launch instead of bundling model weights in the APK.

## Current gameplay loop

Grassling's energy automatically rises toward `100` over roughly 24 hours.

Movement works in the opposite direction: every 11 new steps removes one energy point, and completing a quest removes an additional 10 energy points. Lower energy corresponds to a calmer Grassling, while high energy makes it anxious or angry.

The current quest pool contains ten predefined outdoor activities, including examples such as:

- Walk 750 steps.
- Take a 10-minute walk.
- Find something blue outside.
- Find three different plants.
- Spend 5 minutes outdoors.
- Take a short 500-step walk without using your phone.

Quest selection is currently **random from this predefined pool**. Gemma does not currently invent the quest itself; the local model is used for Grassling's dialogue and reactions.

## Local AI

Grassling uses **Gemma 3 270M IT Q8_0** through a native `llama.cpp` bridge.

After installation, inference runs locally on the device. Internet access is required to download the model the first time, but normal inference does not require a remote AI API.

Current defaults:

| Setting | Value |
| --- | ---: |
| Model | Gemma 3 270M IT Q8_0 |
| Model size | 291,545,600 bytes (~278 MiB) |
| Context | 2,048 tokens |
| Maximum output | 32 tokens |
| Inference threads | 4 |
| Supported native ABIs | `arm64-v8a`, `x86_64` |

The model is stored in the app's private storage at:

```text
filesDir/models/gemma-3-270m-it-Q8_0.gguf
```

Downloads are handled with WorkManager. Partial downloads are retained when possible, and the completed model is validated using its expected size, GGUF header, and SHA-256 before it is loaded.

Model weights are deliberately excluded from Git, Android resources, and the APK. The build also fails if a `.gguf` or `.gguf.part` file is accidentally added under `app/src`.

See [`docs/local-ai.md`](docs/local-ai.md) for the implementation details.

## On-device benchmark

A debug build was benchmarked on a Samsung Galaxy A54 (`SM-A546E`, Android 16 / API 36) using the real JNI inference path.

| Metric | Median |
| --- | ---: |
| Time to first token | 212 ms |
| Prompt processing | 222 tokens/s |
| Token generation | 27.5 tokens/s |
| Native model load | 759 ms |

These numbers are performance measurements, not a claim that the generated text is consistently good. The current model can still return generic, awkward, or incomplete wording, and dialogue quality remains an active area of experimentation.

Full benchmark notes are available in [`docs/benchmarks/gemma-3-270m-it-q8_0-galaxy-a54.md`](docs/benchmarks/gemma-3-270m-it-q8_0-galaxy-a54.md).

## Tech stack

- Kotlin
- Jetpack Compose
- Android WorkManager
- Android foreground services
- Android step-counter sensor (`TYPE_STEP_COUNTER`)
- Kotlin Coroutines / Flow
- C++17
- JNI
- `llama.cpp`
- Gemma 3 270M IT Q8_0 / GGUF
- Gradle Kotlin DSL
- JUnit + Android instrumentation tests

The project also includes Room dependencies, although the current quest and step persistence paths primarily use private app storage and SharedPreferences/JSON-based storage.

## Requirements

To build the project locally you will need:

- Android Studio with Android SDK tooling.
- JDK 17.
- Android SDK compatible with `compileSdk 37`.
- Android NDK.
- CMake `3.31.6`.
- Git and internet access during the first native build, because CMake fetches the pinned `llama.cpp` source.
- An Android device or emulator using `arm64-v8a` or `x86_64`.

The app supports Android API 23+ (`minSdk 23`). Step tracking requires a device with a compatible step-counter sensor. On Android 10+ the user must grant Activity Recognition permission.

## Getting started

Clone the repository and enter the project directory:

```bash
git clone <repository-url>
cd Grassling
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and run the `app` configuration on a connected device/emulator.

On first launch:

1. Grassling checks whether the local Gemma model is installed.
2. If necessary, WorkManager downloads the model into the app's private storage.
3. The file is verified before loading.
4. Once the model is ready, the main Grassling screen is shown.
5. Grant Activity Recognition permission when Android requests it so walking quests and step tracking can work.

The first launch therefore needs network access and enough free storage for the ~278 MiB model plus normal app/build overhead.

## Tests

Run local JVM tests with:

```bash
./gradlew test
```

Run the Android instrumentation suite on a connected device or emulator with:

```bash
./gradlew connectedDebugAndroidTest
```

For local-AI benchmark logs:

```bash
adb logcat -d -s GrasslingLocalAI:I GemmaBenchmark:I
```

Instrumentation currently covers areas such as model-file validation, Gemma prompt formatting, quest selection, and inference through the JNI bridge.

## Project structure

```text
Grassling/
├── app/
│   ├── src/main/java/com/grassling/app/
│   │   ├── data/          # Quest and step persistence
│   │   ├── local_ai/      # Model download, prompts and llama.cpp integration
│   │   ├── monster/       # Energy and mood rules
│   │   ├── quest/         # Quest domain types
│   │   ├── ui/            # Jetpack Compose screens/components
│   │   └── utils/         # Step counter and foreground service
│   └── src/main/cpp/      # JNI bridge and CMake native build
├── ai/                    # Notes about runtime model weights
├── docs/
│   ├── benchmarks/        # Device benchmark results
│   └── local-ai.md        # Local-AI architecture and behavior
├── build.gradle.kts
└── settings.gradle.kts
```

## AI architecture

The application keeps game state in Kotlin. The language model receives read-only snapshots and returns text; it does not directly mutate energy, steps, quests, or mood.

At a high level:

```text
Android UI / game state
        │
        ├── StepCounterManager ──> EnergyManager
        │
        ├── QuestStorage
        │
        └── LlamaLocalAI
                │
                ├── PromptBuilder / conversation prompts
                │
                └── JNI
                     │
                     └── llama.cpp + Gemma GGUF
```

This separation keeps game rules deterministic even when generated text is inconsistent.

## Current limitations

- Quest selection uses a predefined list rather than model-generated quests.
- Generated dialogue quality is inconsistent with the current 270M model.
- There is no general-purpose chat interface or conversation history.
- AI responses are intentionally short and capped at 32 generated tokens.
- Step-based functionality depends on Android sensor availability and permission.
- The UI and game-balancing rules are still evolving.

## Model and third-party terms

Grassling uses the Gemma model distributed by `ggml-org` in GGUF format and integrates `llama.cpp` for local inference.

Before redistributing the application or model, review the applicable Gemma terms and the licenses/terms of the third-party dependencies used by the project.

Gemma terms: <https://ai.google.dev/gemma/terms>

---

Grassling is currently a learning and experimentation project focused on Android development, physical activity tracking, game-state design, and practical on-device AI.

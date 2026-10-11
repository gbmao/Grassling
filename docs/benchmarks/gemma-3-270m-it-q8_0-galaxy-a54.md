# Gemma 3 270M IT Q8_0 — Galaxy A54

Measured on 2026-10-10 with the Grassling Android debug build.

## Device and setup

- Device: Samsung Galaxy A54 (`SM-A546E`), Android 16 / API 36, `arm64-v8a`
- Model: `gemma-3-270m-it-Q8_0.gguf`, 291,545,600 bytes
- Private app path: `filesDir/models/gemma-3-270m-it-Q8_0.gguf`
- SHA-256 verified on the device: `0ef57d2c838458a1952664260dcba38e5bdda37494f3af732f06e4add24068e3`
- llama.cpp build: CMake Release, ARM64; 2,048-token context; 32-token output cap; 4 inference threads

The benchmark ran six quest states and three personality states through JNI. The successful user-visible results include retries when the first generation was empty or did not mention walking. Native token metrics below cover the 12 inference calls, including those retries and cached-prefix calls.

| Metric | Median |
| --- | ---: |
| Time to first token | 212 ms |
| Prompt processing | 222 tokens/s |
| Token generation | 27.5 tokens/s |
| Model load (native log) | 759 ms |

The instrumentation suite passed all four tests. Quest text quality is still inconsistent: the model sometimes returns generic or incomplete wording despite the short quest prompt. Kotlin retries empty or non-walking output, but does not change game state or compose quest text.

## Thread-count trial

An exploratory 4/6/8-thread sweep used an earlier, longer prompt. Quest turns in that trial ended without text, so these are only performance observations, not accepted generation results:

| Threads | Prompt processing, median | Decode observations | Result |
| ---: | ---: | ---: | --- |
| 4 | 239 tokens/s | 24.8 tokens/s on the three non-empty samples | Fastest usable setting |
| 6 | 171 tokens/s | 7.4 tokens/s on the three personality samples | Quest samples were empty |
| 8 | 8.2 tokens/s | One 8-token response took about 80 seconds | Trial stopped early |

Keep 4 threads as the device default. The 8-thread run was stopped to avoid further sustained load on the phone.

## Model download and packaging

First launch automatically started the WorkManager download and reached 78% before the phone's mobile connection timed out. The `.part` file was retained; transient I/O failures now use WorkManager retries. For the on-device inference benchmark, the already SHA-verified official file was transferred over USB into the same private model path.

The model is absent from the APK, app resources and tracked Git files. `*.gguf` remains ignored.

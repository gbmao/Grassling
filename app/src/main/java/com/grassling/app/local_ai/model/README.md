# Runtime model installation

No model weights are stored here. `ModelManager` automatically installs Gemma 3 270M IT Q8_0 from `ggml-org/gemma-3-270m-it-GGUF` into `filesDir/models/gemma-3-270m-it-Q8_0.gguf`. Downloads use WorkManager, a resumable `.part` file and pinned SHA-256 verification. See `docs/local-ai.md` for details.

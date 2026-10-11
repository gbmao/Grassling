# Model weights are downloaded at runtime

Grassling uses `gemma-3-270m-it-Q8_0.gguf` from [ggml-org/gemma-3-270m-it-GGUF](https://huggingface.co/ggml-org/gemma-3-270m-it-GGUF).

This directory is not an Android assets source. Do not put model weights in the APK or commit them to Git. On first launch, the app downloads and verifies the model in its private `filesDir/models` directory. See [local AI documentation](../docs/local-ai.md).

Model terms: https://ai.google.dev/gemma/terms.

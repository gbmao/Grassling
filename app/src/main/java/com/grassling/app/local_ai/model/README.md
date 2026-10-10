# Personalidade local do Grassling

O arquivo `ai/Qwen3-0.6B.Q4_K_M.gguf` é empacotado como asset do app. Na primeira abertura, o `ModelManager` copia o GGUF para o armazenamento privado do app e o llama.cpp carrega o modelo para responder no chat. A quantização Q4_K_M ocupa cerca de 462 MiB; por isso o pacote Android também fica grande.

Origem: [QuantFactory/Qwen3-0.6B-GGUF](https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF), quantização do [Qwen3-0.6B](https://huggingface.co/Qwen/Qwen3-0.6B), licença Apache-2.0. Se o modelo empacotado não estiver disponível, a tela permite importar outro arquivo GGUF.

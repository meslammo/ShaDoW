# SHADOW Local Brain Model

Self-hosted inference service for SHADOW Brain Engine.

- Runtime: llama.cpp server
- Model: ggml-org/Qwen3-4B-GGUF:Q4_K_M
- API: OpenAI-compatible `/v1/chat/completions`
- Tool calling: enabled with llama.cpp Jinja template support
- Once connected to SHADOW Cloud, inference can run without Grok or DeepSeek as providers.

Qwen3-4B GGUF Q4_K_M is an Apache-2.0 4B model variant.
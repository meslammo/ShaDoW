FROM ghcr.io/ggml-org/llama.cpp:server

ENV MODEL_HF=ggml-org/Qwen3-4B-GGUF:Q4_K_M
ENV PORT=8080

EXPOSE 8080

CMD ["sh","-lc","exec llama-server -hf \"$MODEL_HF\" --jinja --host 0.0.0.0 --port \"${PORT:-8080}\" --ctx-size 8192 --n-predict 2048 --parallel 1"]
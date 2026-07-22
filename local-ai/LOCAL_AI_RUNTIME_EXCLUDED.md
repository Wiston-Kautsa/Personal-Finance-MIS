# Local AI Runtime Excluded

The real `local-ai` runtime and model files are not included in this zip because they can be very large and machine-specific.

Expected deployed layout:

local-ai/
- runtime/llama-server.exe and llama.cpp DLL files
- models/pfmis-model.gguf
- agents/pfmis-finance-agent.json
- logs/

The Java source that manages local AI is included under:

src/main/java/com/wk/pfmis/ai

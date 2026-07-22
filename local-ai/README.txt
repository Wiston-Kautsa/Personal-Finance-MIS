PFMIS Bundled Local AI layout

The full installer should place the local AI runtime and model in this directory:

local-ai/runtime/llama-server.exe
local-ai/models/pfmis-model.gguf
local-ai/agents/pfmis-finance-agent.json
local-ai/licenses/

PFMIS uses these automatic settings for the Bundled Local AI provider:

Provider: Bundled Local AI
Endpoint: http://127.0.0.1:11435/v1
Model: pfmis-local
API key: not required

The application starts llama-server.exe automatically when Bundled Local AI is enabled.
The selected model and local AI runtime must permit redistribution before they are included in an installer.

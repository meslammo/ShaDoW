# SHADOW Asset Map — MOD-75

This file maps runtime functions to the assets they require. The build pipeline fetches binary model assets deterministically; missing required assets fail the build instead of silently disabling the feature.

| Function | Asset | Where packaged | Status |
|---|---|---|---|
| Android UI branding | `res/drawable/shadow_logo.*` | APK resources | Existing |
| Wake — Hey Jarvis | `jarvis_v1.onnx` | `app/src/main/assets/` | CI-fetched |
| Wake — Hey Shadow | `hey_shadow.onnx` | `app/src/main/assets/` | CI-fetched; device phrase test is separate and not a build blocker |
| Wake shared frontend | `melspectrogram.onnx` | `app/src/main/assets/` | CI-fetched |
| Wake shared embedding | `embedding_model.onnx` | `app/src/main/assets/` | CI-fetched |
| Android STT | Android SpeechRecognizer | Platform | No bundled model |
| Android default TTS | Android TextToSpeech | Platform | No bundled model |
| Cloud TTS (optional) | provider TTS model | Backend | Network/provider asset |
| Image generation | provider image model | Backend | Network/provider asset |
| Web Search | provider/built-in web tools | Backend | Network/provider capability |
| GitHub | GitHub API/OAuth | Backend + Android auth boundary | Network/provider capability |
| Vision/camera | Android Camera + future vision model | Platform/backend | Camera asset is live input, model optional |
| Companion / device adapters | Adapter-specific assets/config | Per companion | Added only with a verified adapter |

## Wake-word sources

- `jarvis_v1.onnx`, `melspectrogram.onnx`, `embedding_model.onnx`: pinned from `Bwarhness/jarvis-assistant`.
- `hey_shadow.onnx`: pinned from `jakes1345/ShadowCypher`.
- Every binary is fetched at a pinned commit in CI and its SHA-256 is printed during the build.

## Wake-word test note

`Hey Jarvis` and `Hey Shadow` are both mapped to the same SHADOW master route. Microphone/phrase testing will be performed later on the target phone and is not required to build or wire the route.

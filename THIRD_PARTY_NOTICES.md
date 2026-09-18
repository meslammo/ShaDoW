# Third-party components used by SHADOW MOD-74

## Wake-word runtime
- Source project: Bwarhness/jarvis-assistant
- License: Apache-2.0
- Pinned source commit: 17edf69264ae421d68a103cde1bc2c1dcab2f673
- Model assets fetched during CI:
  - melspectrogram.onnx
  - embedding_model.onnx
  - jarvis_v1.onnx

## Wake-word Android library
- Maven artifact: xyz.rementia:openwakeword:0.1.5
- Package used: com.rementia.openwakeword.lib
- The library license/terms remain those of its upstream distribution.

SHADOW's own source remains under the repository's MIT license.


## Hey Shadow wake classifier
- Source project: jakes1345/ShadowCypher
- Source commit: 827829b09399d6c02ba108607e70aa05bf7485a5
- Asset: hey_shadow.onnx
- Project license: MIT
- Note: the classifier is wired as a wake alias for the same SHADOW master route. Physical microphone validation is a later device test, not a build prerequisite.

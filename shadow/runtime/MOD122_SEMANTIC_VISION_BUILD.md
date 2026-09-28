# MOD-122 Semantic Vision Build Marker

This marker intentionally touches shadow/** so the APK workflow rebuilds the current online-only Shadow client alongside the real semantic vision backend integration.

Runtime path:
Image Input -> Layer 16 -> Semantic Vision Adapter -> Visual Evidence -> Brain 8-23 -> Verification -> Response.

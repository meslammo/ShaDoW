# SHADOW MOD-33 Source Map

This change follows the agreed strategy: **search → inspect → select → integrate**, while keeping SHADOW as the product and codebase.

## Integrated now

- **ClosePaw** — https://github.com/imoonkey/closepaw — Apache-2.0. Used as the design/reference source for the phone-use agent: semantic Accessibility perception, action planning, step traces, long-term memory/skills concepts, and approval/stop behavior. SHADOW keeps its own existing UI/core and Android service rather than replacing the application.
- **SHADOW existing Accessibility bridge** — upgraded in place with semantic UI-tree summaries, editable-field text input, scrolling, active-package detection and richer node metadata.

## Selected for next integration waves

- **Roomsmith** — https://github.com/Rumeasiyan/roomsmith — MIT. Candidate for photo-based room/site understanding, measured drawings, elevations, reports and approval-gated design workflows.
- **dwg-bim_AI / Structify_AI** — https://github.com/newva/dwg-bim_AI — MIT. Candidate for floor-plan segmentation/vectorization and CAD/BIM export.
- **AI-CAD** — https://github.com/ishan-parihar/AI-CAD — MIT. Candidate for deterministic text-to-floor-plan/DXF generation.
- **OpenTakeoff** — https://github.com/HexNinja555/opentakeoff — candidate for plan measurement/takeoff and agent-driven quantity workflows; license must be checked again before shipping code from it.

## Product rules

1. Do not replace SHADOW with another app.
2. Do not copy a dependency whose license is incompatible with the intended distribution.
3. Do not execute uploaded code automatically.
4. Construction/design output is assistance, not an engineering approval; dimensions and structural decisions require human/professional verification.
5. GitHub credentials are never hard-coded into the APK or repository. The final Development Agent will use a user-authorized, narrowly scoped credential and explicit approval gates for writes, workflow dispatch, releases and rollback.

## Target capability stack

🧠 Development Agent → 📁 files/ZIP/code → 📱 phone-use → 👁️ perception/accessibility → 🧠 memory → 🛠️ skills/tools → 🔄 Git/version/rollback → 🧮 deterministic analysis → 🎙️ voice/wake/barge-in → 🌐 multimodal web → 🏠 home/car.

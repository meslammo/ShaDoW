# SHADOW — Super Nice 150-Core

SHADOW extends the existing 12-Core master architecture to an exact **150-Core governed capability layer**.

## Contract
- The first 12 cores are preserved as the original foundation.
- 138 additional cores cover intelligence, memory, personal context, research, software creation, multimodal systems, devices/spatial reasoning, physics, governance, self-evolution and legacy.
- SHADOW has **no in-app points, credits, subscription gate or usage meter**.
- Self-hostable/open backends are routed first where they satisfy a capability.
- External AI providers remain optional adapters and may impose their own access restrictions or charges; SHADOW never bypasses those systems.
- Self-evolution is staged, tested, rollback-aware and requires explicit master approval for permanent activation.

## Runtime
`Observe -> Understand -> Route -> Plan -> Permission -> Execute -> Verify -> Learn`

## Files
- `shadow/supernice/catalog.py` — exact 150-Core registry.
- `shadow/supernice/contracts.py` — contracts and result envelopes.
- `shadow/supernice/providers.py` — free-first provider policy.
- `shadow/supernice/runtime.py` — governed execution facade.
- `shadow/supernice/evolution.py` — bounded self-evolution.
- `shadow/supernice/manifest.json` — machine-readable registry.
- `tests/test_supernice_150_core.py` — automated registry/policy verification.
- `docs/SUPER_NICE_150_CORE.md` — architecture contract and full core list.

## Important release truth
A Core contract is real code and a governed capability boundary. It is **not** a claim that every third-party service/device has a live connection. Real image/video, smart-home, car and external-provider adapters are only live after their actual endpoint/device is connected and verified.

No proprietary model weights, vendor secrets, protected commercial source or hidden reasoning traces are copied into SHADOW.

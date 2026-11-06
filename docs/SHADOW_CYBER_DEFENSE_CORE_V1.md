# SHADOW Cyber Defense Core v1 — implementation notes

This implementation is intentionally defensive and non-executing.

## Wired capabilities
- CORE-085: bounded dependency manifest inventory; optional exact-version OSV.dev lookup.
- CORE-086: bounded static workspace scanner for credential-like assignments, unsafe Python constructs, API configuration patterns, and suspicious command indicators.
- CORE-087: secret-pattern detection with redacted evidence.
- CORE-083 / CORE-084: patch recommendations and diff/review planning only; no files are modified.
- CORE-138: temporary review copy only. This is not a process/network sandbox.
- CORE-141: static threat indicators from source text.

## Safety and limits
No exploit payloads are generated or executed. No target network is scanned. No malware is run.
OSV.dev is contacted only when a caller explicitly sets `context.online_lookup=true`; only exact package names, ecosystems, and versions are sent.
Patch actions require separate human review and are not auto-applied. Regression output is a plan unless a CI runner executes the repository tests.

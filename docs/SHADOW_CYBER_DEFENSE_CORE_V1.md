# SHADOW Cyber Defense Core v1 — implementation notes

This implementation separates static analysis from explicitly approved dynamic execution.

## Wired capabilities
- CORE-085: bounded dependency manifest inventory; optional exact-version OSV.dev lookup.
- CORE-086: bounded static workspace scanner for credential-like assignments, unsafe Python constructs, API configuration patterns, and suspicious command indicators.
- CORE-087: secret-pattern detection with redacted evidence.
- CORE-083 / CORE-084: patch recommendations and diff/review planning only; no files are modified.
- CORE-138: review-copy mode remains non-executing by default. An explicit `operation="execute_python"` request routes one Python script through the Docker sandbox and requires CORE confirmation.
- CORE-141: static threat indicators from source text.

## Docker execution boundary
Before a target script can run, SHADOW requires all of the following:
1. An explicit confirmation (`confirmed=true`).
2. A Linux or macOS host with a reachable Docker Engine.
3. A locally available Docker image selected by immutable image ID in `SHADOW_SANDBOX_IMAGE=sha256:<64 hex>`. SHADOW never pulls an image and never falls back to host execution.
4. A regular, non-symlink Python script inside the configured workspace.

The container uses `--network=none`, a read-only root filesystem, a read-only bind mount containing only the staged script, UID/GID 65534, all Linux capabilities dropped, `no-new-privileges`, Docker's default seccomp profile, PID/memory/CPU limits, a bounded timeout, and bounded captured output. No host environment variables are passed into the container.

Example image preparation on a trusted host:

```sh
docker pull python@sha256:e064671ca452259bab50e8e2c7bd896e39e9a343bb06ca25234adf6fe18d0fc7
export SHADOW_SANDBOX_IMAGE="$(docker image inspect --format '{{.Id}}' python@sha256:e064671ca452259bab50e8e2c7bd896e39e9a343bb06ca25234adf6fe18d0fc7)"
```

The CI workflow pulls the same image by immutable registry digest and exports its local image ID before running the integration test. The test proves the container runs as the non-root UID, cannot write to the mounted workspace, and has no outbound network.

## Safety, trust assumptions, and limits
- Docker containers share the host kernel. The Docker daemon, host kernel, and image supply chain remain trusted; this is not a VM and does not claim immunity to container/kernel escapes.
- For hostile high-value workloads, use a dedicated isolated worker with a VM boundary or a hardened runtime such as gVisor/Kata, on a separately managed host.
- No exploit payloads are generated, no target network is scanned, and no malware is run.
- OSV.dev is contacted only when a caller explicitly sets `context.online_lookup=true`; only exact package names, ecosystems, and versions are sent.
- Patch actions require separate human review and are not auto-applied. Regression output remains a plan unless a CI runner executes the repository tests.

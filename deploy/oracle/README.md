# SHADOW Oracle Always Free Brain

Deploys the SHADOW self-hosted brain on an OCI Ampere A1 Always Free VM.

## Target
- VM.Standard.A1.Flex
- 2 OCPU
- 12 GB RAM
- Ubuntu Arm64
- Docker + llama.cpp server
- Qwen3-4B-GGUF Q4_K_M (~2.5 GB)
- Caddy HTTPS
- sslip.io hostname derived from the VM public IP

## OCI setup
Create an Always Free eligible VM in the tenancy's home region. Oracle's current Always Free A1 allowance is 1,500 OCPU-hours and 9,000 GB-hours/month, equivalent to 2 OCPUs and 12 GB RAM.

Allow inbound TCP 22, 80 and 443 in the VCN security list/NSG and the instance OS firewall as applicable.

Then copy and run `bootstrap.sh` on the VM.

## Hatchable wiring
After the script finishes, read `/opt/shadow-brain/CONNECTION.txt`.

Set:
- `SHADOW_LOCAL_MODEL_URL`
- `SHADOW_LOCAL_MODEL_API_KEY`
- `SHADOW_EXTERNAL_AI_ALLOWED=false`

The SHADOW adapter must send the API key as a Bearer token to the llama.cpp server.

## Notes
- Model weights live on the VM at `/opt/shadow-brain/models` and survive container restarts.
- Inference is self-hosted; external model APIs are not required by the model server.
- CPU-only inference is expected on A1; benchmark latency after deployment.
- Never commit the generated API key to GitHub.

#!/usr/bin/env bash
set -euo pipefail

BASE=/opt/shadow-brain
MODEL_DIR="$BASE/models"
ENV_FILE="$BASE/.env"
COMPOSE_FILE="$BASE/docker-compose.yml"
CADDYFILE=/etc/caddy/Caddyfile
MODEL_URL="https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf?download=true"

mkdir -p "$MODEL_DIR"
chmod 755 "$BASE" "$MODEL_DIR"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl gnupg lsb-release openssl

if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg
  ARCH="$(dpkg --print-architecture)"
  CODENAME="$(. /etc/os-release && echo "$VERSION_CODENAME")"
  echo "deb [arch=$ARCH signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $CODENAME stable" > /etc/apt/sources.list.d/docker.list
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable --now docker
fi

if ! command -v caddy >/dev/null 2>&1; then
  install -m 0755 -d /usr/share/keyrings
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update
  apt-get install -y caddy
fi

if [ ! -f "$ENV_FILE" ]; then
  API_KEY="$(openssl rand -hex 32)"
  umask 077
  printf 'SHADOW_BRAIN_API_KEY=%s\n' "$API_KEY" > "$ENV_FILE"
  chmod 600 "$ENV_FILE"
fi

MODEL="$MODEL_DIR/Qwen3-4B-Q4_K_M.gguf"
if [ ! -s "$MODEL" ]; then
  TMP="$MODEL.part"
  echo "Downloading Qwen3-4B Q4_K_M..."
  curl -L --fail --retry 5 --retry-all-errors --continue-at - "$MODEL_URL" -o "$TMP"
  mv "$TMP" "$MODEL"
fi

cp "$(dirname "$0")/docker-compose.yml" "$COMPOSE_FILE"
cd "$BASE"
docker compose up -d

PUBLIC_IP="$(curl -4 -fsS https://ifconfig.me)"
DOMAIN="${PUBLIC_IP//./-}.sslip.io"

install -m 0755 -d /etc/caddy
sed "s/__DOMAIN__/$DOMAIN/g" "$(dirname "$0")/Caddyfile.template" > "$CADDYFILE"
systemctl enable caddy
systemctl restart caddy

API_KEY="$(cut -d= -f2- "$ENV_FILE")"
cat > "$BASE/CONNECTION.txt" <<EOF
SHADOW Brain endpoint:
https://$DOMAIN

OpenAI-compatible endpoint:
https://$DOMAIN/v1/chat/completions

API key:
$API_KEY

Model:
Qwen3-4B Q4_K_M via llama.cpp

Hatchable variables:
SHADOW_LOCAL_MODEL_URL=https://$DOMAIN
SHADOW_LOCAL_MODEL_API_KEY=$API_KEY
SHADOW_EXTERNAL_AI_ALLOWED=false
EOF

chmod 600 "$BASE/CONNECTION.txt"
echo "READY: https://$DOMAIN"

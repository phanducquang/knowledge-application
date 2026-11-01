#!/usr/bin/env bash
set -euo pipefail

deploy_root="${KNOWLEDGE_DEPLOY_ROOT:-/home/ubuntu/knowledge-application}"
env_file="$deploy_root/shared/web.env"
if [[ ! -r "$env_file" ]]; then
  printf 'Missing readable Web environment file: %s\n' "$env_file" >&2
  exit 1
fi

set -a
source "$env_file"
set +a

release_dir="$(cd "$deploy_root/current" && pwd -P)"
web_dir="$release_dir/apps/web"
if [[ ! -f "$web_dir/.next/BUILD_ID" ]]; then
  printf 'Missing production Next.js build in: %s\n' "$web_dir" >&2
  exit 1
fi

node_bin="${KNOWLEDGE_NODE_BIN:-/usr/bin/node}"
export NODE_ENV=production
cd "$web_dir"
exec "$node_bin" "$web_dir/node_modules/next/dist/bin/next" start \
  --hostname "${KNOWLEDGE_WEB_HOST:-127.0.0.1}" \
  --port "${KNOWLEDGE_WEB_PORT:-3000}"

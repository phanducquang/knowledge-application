#!/usr/bin/env bash
set -euo pipefail

deploy_root="${KNOWLEDGE_DEPLOY_ROOT:-/home/ubuntu/knowledge-application}"
env_file="$deploy_root/shared/api.env"
if [[ ! -r "$env_file" ]]; then
  printf 'Missing readable API environment file: %s\n' "$env_file" >&2
  exit 1
fi

# This trusted deployment file contains shell-compatible assignments, not user input.
set -a
source "$env_file"
set +a

release_dir="$(cd "$deploy_root/current" && pwd -P)"
jar_file="$release_dir/runtime/knowledge-api.jar"
if [[ ! -f "$jar_file" ]]; then
  printf 'Missing release artifact: %s\n' "$jar_file" >&2
  exit 1
fi

java_bin="${KNOWLEDGE_JAVA_BIN:-/usr/bin/java}"
java_args=(-XX:+ExitOnOutOfMemoryError)
if [[ -n "${KNOWLEDGE_JAVA_XMS:-}" ]]; then
  java_args+=("-Xms$KNOWLEDGE_JAVA_XMS")
fi
if [[ -n "${KNOWLEDGE_JAVA_XMX:-}" ]]; then
  java_args+=("-Xmx$KNOWLEDGE_JAVA_XMX")
fi

export SERVER_ADDRESS="${SERVER_ADDRESS:-127.0.0.1}"
export SERVER_PORT="${SERVER_PORT:-8080}"
export SERVER_SHUTDOWN="${SERVER_SHUTDOWN:-graceful}"
export SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE="${SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE:-30s}"

cd "$release_dir/apps/api"
exec "$java_bin" "${java_args[@]}" -jar "$jar_file"

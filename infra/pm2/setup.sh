#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)/deploy-common.sh"
simple_arguments setup.sh "$@"

prepare_root
lock_deployment
for name in api web; do
  target="$deploy_root/shared/$name.env"
  if [[ -e "$target" || -L "$target" ]]; then
    log "Keeping existing $target"
  else
    cp "$script_dir/$name.env.example" "$target"
    chmod 600 "$target"
    log "Created $target"
  fi
done
log 'Edit shared/api.env and shared/web.env, then run deploy-first.sh.'
log 'Prepare PostgreSQL/pgvector, the RustFS bucket and runtime dependencies separately; this script does not install or change them.'

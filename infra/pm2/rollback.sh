#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)/deploy-common.sh"

if [[ $# == 1 && "$1" == --help ]]; then
  printf 'Usage: bash rollback.sh --schema-compatible [release-directory]\nOnly use after checking application compatibility with the current database. No database restore is performed.\n'
  exit 0
fi
if [[ "${1:-}" != --schema-compatible || $# -gt 2 ]]; then
  printf 'Usage: bash rollback.sh --schema-compatible [release-directory]\n' >&2
  printf 'Only use after verifying that the target application is compatible with the current database. This never restores the database.\n' >&2
  exit 1
fi
prepare_root
lock_deployment
install_failure_traps
preflight
check_processes update
previous_release="$(current_release)"
target="${2:-}"
if [[ -z "$target" ]]; then
  [[ -r "$deploy_root/shared/previous-release" ]] || die 'No previous release recorded; provide a target directory.'
  IFS= read -r target < "$deploy_root/shared/previous-release"
fi
release_dir="$(cd "$target" && pwd -P)"
[[ "$release_dir" != "$previous_release" ]] || die 'Target is already current.'
validate_release "$release_dir"
printf '%s\n' "$previous_release" > "$deploy_root/shared/previous-release"
stage="stop-previous"
record_stage
apps_stopped=1
pm2 stop knowledge-web
pm2 stop knowledge-api
switch_current
start_release restart

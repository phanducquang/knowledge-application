#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)/deploy-common.sh"
simple_arguments deploy-update.sh "$@"

prepare_root
lock_deployment
install_failure_traps
preflight
check_processes update
previous_release="$(current_release)"
validate_release "$previous_release"
clone_and_build
report_migrations
printf '%s\n' "$previous_release" > "$deploy_root/shared/previous-release"
stage="stop-previous"
record_stage
apps_stopped=1
pm2 stop knowledge-web
pm2 stop knowledge-api
switch_current
start_release restart

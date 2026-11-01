#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)/deploy-common.sh"
simple_arguments restart-current.sh "$@"

prepare_root
lock_deployment
install_failure_traps
preflight
check_processes resume
release_dir="$(current_release)"
validate_release "$release_dir"
previous_release="$release_dir"
# This is the currently selected release, not an automatic rollback to old code.
cutover_started=1
start_release resume

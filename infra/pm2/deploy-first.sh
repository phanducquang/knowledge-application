#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)/deploy-common.sh"
simple_arguments deploy-first.sh "$@"

prepare_root
lock_deployment
install_failure_traps
[[ ! -e "$deploy_root/current" && ! -L "$deploy_root/current" ]] || die 'current already exists; use deploy-update.sh.'
preflight
check_processes first
clone_and_build
switch_current
start_release start
log 'If PM2 startup is not configured, run pm2 startup as this user and follow its printed command.'

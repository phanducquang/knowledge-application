#!/usr/bin/env bash
# Shared deployment functions; sourced by the entrypoints, not run by PM2.

deploy_root="${KNOWLEDGE_DEPLOY_ROOT:-/home/ubuntu/knowledge-application}"
repository_url="${KNOWLEDGE_REPOSITORY_URL:-https://github.com/phanducquang/knowledge-application.git}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
lock_owned=0
apps_stopped=0
cutover_started=0
release_dir=""
previous_release=""
stage="preflight"

simple_arguments() {
  local entry="$1"
  shift
  if [[ $# == 1 && "$1" == --help ]]; then
    printf 'Usage: bash %s\nRoot: KNOWLEDGE_DEPLOY_ROOT (default /home/ubuntu/knowledge-application)\nRepository: KNOWLEDGE_REPOSITORY_URL (default project origin), branch main\nSee infra/pm2/SCRIPTS.md for setup, deployment and recovery.\n' "$entry"
    exit 0
  fi
  [[ $# == 0 ]] || die "Unexpected arguments. Run bash $entry --help."
}

log() { printf '[deploy] %s\n' "$*"; }
die() {
  printf '[deploy] ERROR: %s\n' "$*" >&2
  if [[ "$apps_stopped" == 1 || "$cutover_started" == 1 ]]; then on_failure 1; fi
  exit 1
}

prepare_root() {
  [[ "$(id -u)" != 0 ]] || die 'Run as the ubuntu/PM2 user, not root.'
  [[ "$deploy_root" == /* && "$deploy_root" != / ]] || die 'Deployment root must be an absolute non-root directory.'
  mkdir -p "$deploy_root/releases" "$deploy_root/shared/logs"
  deploy_root="$(cd "$deploy_root" && pwd -P)"
  chmod 700 "$deploy_root/shared" "$deploy_root/shared/logs"
  export KNOWLEDGE_DEPLOY_ROOT="$deploy_root"
}

unlock_deployment() {
  if [[ "$lock_owned" == 1 && "${BASH_SUBSHELL:-0}" == 0 ]]; then
    unlink "$deploy_root/shared/.deploy-lock/pid" 2>/dev/null || true
    rmdir "$deploy_root/shared/.deploy-lock" 2>/dev/null || true
  fi
}

lock_deployment() {
  if ! mkdir "$deploy_root/shared/.deploy-lock" 2>/dev/null; then
    die "Another deployment or stale lock exists: $deploy_root/shared/.deploy-lock. Inspect its pid before removing it."
  fi
  lock_owned=1
  printf '%s\n' "$$" > "$deploy_root/shared/.deploy-lock/pid"
  trap unlock_deployment EXIT
}

env_value() (
  # Values stay in this subprocess; build tools and PM2 do not inherit app secrets.
  local file="$1" name="$2" fallback="${3:-}"
  source "$file" 2>/dev/null
  printf '%s' "${!name:-$fallback}"
)

validate_env() {
  local file
  for file in "$deploy_root/shared/api.env" "$deploy_root/shared/web.env"; do
    [[ -f "$file" && -r "$file" && -O "$file" ]] || die "Missing readable environment file owned by this user: $file"
    bash -n "$file" 2>/dev/null || die "Invalid Bash syntax in $file (values are not printed)."
    node - "$file" <<'NODE'
const fs = require('node:fs');
if (fs.statSync(process.argv[2]).mode & 0o077) {
  console.error('[deploy] Environment file must be private: chmod 600 ' + process.argv[2]);
  process.exit(1);
}
NODE
  done
  (
    source "$deploy_root/shared/api.env" 2>/dev/null
    local name value
    for name in DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET AUTH_ALLOWED_EMAIL APP_OWNER_ID WEB_BASE_URL OBJECT_STORAGE_ENDPOINT OBJECT_STORAGE_REGION OBJECT_STORAGE_BUCKET OBJECT_STORAGE_ACCESS_KEY OBJECT_STORAGE_SECRET_KEY; do
      value="${!name:-}"
      [[ -n "$value" && "$value" != *CHANGE_ME* ]] || die "Configure $name in shared/api.env."
    done
    [[ "$AUTH_ALLOWED_EMAIL" != owner@example.com && "$WEB_BASE_URL" != *knowledge.example.com* ]] || die 'Replace the example owner email and domain in shared/api.env.'
    [[ "$WEB_BASE_URL" == https://* && "${SESSION_COOKIE_SECURE:-false}" == true ]] || die 'Production requires an HTTPS WEB_BASE_URL and SESSION_COOKIE_SECURE=true.'
    for name in SPRING_FLYWAY_USER SPRING_FLYWAY_PASSWORD; do
      [[ "${!name:-}" != *CHANGE_ME* ]] || die "Configure $name in shared/api.env."
    done
    if [[ "${EMBEDDING_ENABLED:-false}" == true || "${ASK_ENABLED:-false}" == true || "${AI_METADATA_ENABLED:-false}" == true ]]; then
      [[ -n "${GEMINI_API_KEY:-}" && "$GEMINI_API_KEY" != *CHANGE_ME* ]] || die 'Enabled AI requires GEMINI_API_KEY.'
    fi
  )
  (
    source "$deploy_root/shared/web.env" 2>/dev/null
    [[ -n "${KNOWLEDGE_API_BASE_URL:-}" && "${KNOWLEDGE_API_BROWSER_BASE_URL:-}" == https://* && "$KNOWLEDGE_API_BROWSER_BASE_URL" != *knowledge.example.com* ]] || die 'Configure the internal API URL and public HTTPS domain in shared/web.env.'
  )
}

preflight() {
  local command_name
  for command_name in git node npm pm2 curl; do
    command -v "$command_name" >/dev/null || die "Required command not found: $command_name"
  done
  node -e 'if (Number(process.versions.node.split(".")[0]) !== 22) { console.error("Use Node.js 22, matching project CI."); process.exit(1); }'
  validate_env
  java_bin="$(env_value "$deploy_root/shared/api.env" KNOWLEDGE_JAVA_BIN /usr/bin/java)"
  "$java_bin" -version >/dev/null 2>&1 || die 'Configured Java cannot run; install JDK 17 and check KNOWLEDGE_JAVA_BIN.'
  api_host="$(env_value "$deploy_root/shared/api.env" SERVER_ADDRESS 127.0.0.1)"
  api_port="$(env_value "$deploy_root/shared/api.env" SERVER_PORT 8080)"
  web_host="$(env_value "$deploy_root/shared/web.env" KNOWLEDGE_WEB_HOST 127.0.0.1)"
  web_port="$(env_value "$deploy_root/shared/web.env" KNOWLEDGE_WEB_PORT 3000)"
  case "$api_host" in 127.0.0.1|localhost|::1) ;; *) die 'API must bind loopback.' ;; esac
  case "$web_host" in 127.0.0.1|localhost|::1) ;; *) die 'Web must bind loopback.' ;; esac
  case "$api_port:$web_port" in *[!0-9:]*|:*|*:) die 'Invalid API/Web port.' ;; esac
  (( 10#$api_port >= 1 && 10#$api_port <= 65535 && 10#$web_port >= 1 && 10#$web_port <= 65535 )) || die 'Ports must be between 1 and 65535.'
  [[ "$api_port" != "$web_port" ]] || die 'API and Web must use different ports.'
  local api_base browser_base web_base
  api_base="$(env_value "$deploy_root/shared/web.env" KNOWLEDGE_API_BASE_URL)"
  browser_base="$(env_value "$deploy_root/shared/web.env" KNOWLEDGE_API_BROWSER_BASE_URL)"
  web_base="$(env_value "$deploy_root/shared/api.env" WEB_BASE_URL)"
  node - "$api_base" "$browser_base" "$web_base" "$api_port" <<'NODE'
try {
  const internal=new URL(process.argv[2]), browser=new URL(process.argv[3]), web=new URL(process.argv[4]);
  if (internal.protocol!=='http:' || !['127.0.0.1','localhost','[::1]'].includes(internal.hostname)
      || Number(internal.port || 80)!==Number(process.argv[5]) || internal.username || internal.password
      || internal.pathname!=='/' || internal.search || internal.hash) throw Error('Internal API URL must target the configured loopback API port.');
  for (const u of [browser,web]) {
    if (u.protocol!=='https:' || u.username || u.password || u.pathname!=='/' || u.search || u.hash) throw Error('Public Web URLs must be HTTPS root origins.');
  }
  if (browser.origin!==web.origin) throw Error('WEB_BASE_URL and KNOWLEDGE_API_BROWSER_BASE_URL must use the same public origin.');
} catch(e) { console.error('[deploy] Invalid deployment URL configuration; verify API port and matching public origins.'); process.exit(1); }
NODE
  [[ "$api_host" != ::1 ]] || api_host='[::1]'
  [[ "$web_host" != ::1 ]] || web_host='[::1]'
  api_health_url="http://$api_host:$api_port/actuator/health"
  web_health_url="http://$web_host:$web_port/login"
  health_attempts="${KNOWLEDGE_HEALTH_ATTEMPTS:-60}"
  health_interval="${KNOWLEDGE_HEALTH_INTERVAL:-2}"
  [[ "$health_attempts" =~ ^[1-9][0-9]*$ && "$health_interval" =~ ^[0-9]+$ ]] || die 'Invalid health attempt/interval settings.'
  pm2 ping >/dev/null
}

check_processes() {
  # Refuse to stop/restart similarly named processes belonging to another project.
  pm2 jlist | node -e '
let input=""; process.stdin.on("data", c => input+=c); process.stdin.on("end", () => {
  try {
    const apps=JSON.parse(input), root=process.argv[1], mode=process.argv[2];
    for (const name of ["knowledge-api","knowledge-web"]) {
      const matches=apps.filter(a=>a.name===name);
      if (mode==="first" ? matches.length!==0 : mode==="resume" ? matches.length>1 : matches.length!==1) throw Error(mode==="first" ? name+" already exists; use deploy-update.sh or restart-current.sh." : name+" must already exist exactly once.");
      if (matches.length && (matches[0].pm2_env.pm_cwd!==root || !matches[0].pm2_env.pm_exec_path.startsWith(root+"/"))) throw Error(name+" points to another deployment root.");
    }
  } catch(e) { console.error("[deploy] "+e.message); process.exit(1); }
});' "$deploy_root" "$1"
}

process_exists() {
  pm2 jlist | node -e 'let s="";process.stdin.on("data",c=>s+=c);process.stdin.on("end",()=>process.exit(JSON.parse(s).some(a=>a.name===process.argv[1])?0:1));' "$1"
}

current_release() {
  [[ -L "$deploy_root/current" ]] || die 'current must be a symlink to an existing release.'
  (cd "$deploy_root/current" && pwd -P)
}

validate_release() {
  local candidate="$1"
  [[ "$candidate" == "$deploy_root/releases/"* ]] || die 'Release must be inside this deployment root/releases.'
  [[ -f "$candidate/runtime/knowledge-api.jar" && -f "$candidate/apps/web/.next/BUILD_ID" && -f "$candidate/infra/pm2/ecosystem.config.js" && -f "$candidate/infra/pm2/start-api.sh" && -f "$candidate/infra/pm2/start-web.sh" ]] || die "Incomplete release artifacts: $candidate"
}

clone_and_build() {
  stage="build"
  release_dir="$deploy_root/releases/release-$(date +%Y%m%d-%H%M%S)-$$"
  log 'Cloning the latest main into a new release.'
  git clone --branch main --single-branch "$repository_url" "$release_dir"
  git -C "$release_dir" checkout --detach HEAD
  release_commit="$(git -C "$release_dir" rev-parse HEAD)"
  log "Release commit: $release_commit"
  local required_file
  for required_file in ecosystem.config.js start-api.sh start-web.sh deploy-common.sh setup.sh deploy-first.sh deploy-update.sh restart-current.sh rollback.sh; do
    [[ -f "$release_dir/infra/pm2/$required_file" ]] || die 'Push the complete PM2 deployment scripts to main before deploying.'
  done
  (cd "$release_dir/apps/api" && ./gradlew --no-daemon bootJar)
  local jar_file="" candidate jar_count=0
  for candidate in "$release_dir"/apps/api/build/libs/*.jar; do
    [[ -f "$candidate" && "$candidate" != *-plain.jar && "$candidate" != *-sources.jar && "$candidate" != *-javadoc.jar ]] || continue
    jar_file="$candidate"
    jar_count=$((jar_count + 1))
  done
  [[ "$jar_count" == 1 ]] || die 'Expected exactly one executable JAR from bootJar.'
  mkdir -p "$release_dir/runtime"
  cp "$jar_file" "$release_dir/runtime/knowledge-api.jar"
  (cd "$release_dir/apps/web" && npm ci --include=dev && npm test && npm run lint && NODE_ENV=production npm run build)
  validate_release "$release_dir"
}

report_migrations() {
  local old_commit changes
  old_commit="$(git -C "$previous_release" rev-parse HEAD)"
  if git -C "$release_dir" cat-file -e "$old_commit^{commit}" 2>/dev/null; then
    changes="$(git -C "$release_dir" diff --name-status "$old_commit" HEAD -- apps/api/src/main/resources/db/migration)"
    if [[ -n "$changes" ]]; then
      log 'Flyway migration files changed. API startup may modify the database service; switching releases will not undo those changes.'
      printf '%s\n' "$changes"
    fi
  else
    log 'Previous commit is not in the new history; migration differences could not be determined. API may still migrate the database service.'
  fi
}

record_stage() {
  printf 'stage=%s\nrelease=%s\nprevious=%s\n' "$stage" "$release_dir" "$previous_release" > "$deploy_root/shared/deploy-state"
}

switch_current() {
  validate_release "$release_dir"
  if [[ -e "$deploy_root/current" && ! -L "$deploy_root/current" ]]; then
    die 'Refusing to replace a real current directory.'
  fi
  stage="cutover"
  cutover_started=1
  record_stage
  node - "$deploy_root" "$release_dir" <<'NODE'
const fs=require('node:fs'), path=require('node:path');
const root=process.argv[2], release=process.argv[3], current=path.join(root,'current');
try { if (!fs.lstatSync(current).isSymbolicLink()) throw Error('current is not a symlink'); }
catch(e) { if(e.code!=='ENOENT') throw e; }
const temporary=path.join(root,'.current-'+process.pid);
fs.symlinkSync(release,temporary);
try { fs.renameSync(temporary,current); }
catch(e) { fs.unlinkSync(temporary); throw e; }
NODE
}

wait_for_api() {
  local attempt response
  for (( attempt=1; attempt<=health_attempts; attempt++ )); do
    if response="$(curl --fail --silent --show-error --connect-timeout 2 --max-time 5 "$api_health_url" 2>/dev/null)" &&
      printf '%s' "$response" | node -e 'let s="";process.stdin.on("data",c=>s+=c);process.stdin.on("end",()=>{try{process.exit(JSON.parse(s).status==="UP"?0:1)}catch{process.exit(1)}})'; then
      log 'API health UP.'
      return 0
    fi
    sleep "$health_interval"
  done
  printf '[deploy] API health did not become UP. Check pm2 logs knowledge-api.\n' >&2
  return 1
}

wait_for_web() {
  local attempt status
  for (( attempt=1; attempt<=health_attempts; attempt++ )); do
    if status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' --connect-timeout 2 --max-time 5 "$web_health_url" 2>/dev/null)" && [[ "$status" == 200 ]]; then
      log 'Web /login returned HTTP 200.'
      return 0
    fi
    sleep "$health_interval"
  done
  printf '[deploy] Web did not become ready. Check pm2 logs knowledge-web.\n' >&2
  return 1
}

start_release() {
  local operation="$1" config="$deploy_root/current/infra/pm2/ecosystem.config.js"
  stage="api-start"
  record_stage
  if [[ "$operation" == start ]] || { [[ "$operation" == resume ]] && ! process_exists knowledge-api; }; then
    pm2 start "$config" --only knowledge-api
  else
    pm2 restart "$config" --only knowledge-api --update-env
  fi
  wait_for_api
  stage="web-start"
  record_stage
  if [[ "$operation" == start ]] || { [[ "$operation" == resume ]] && ! process_exists knowledge-web; }; then
    pm2 start "$config" --only knowledge-web
  else
    pm2 restart "$config" --only knowledge-web --update-env
  fi
  wait_for_web
  pm2 save
  stage="complete"
  record_stage
  log "Deployment complete: $release_dir"
  log 'Next: check HTTPS login/logout, autosave, search and RustFS images through your domain.'
}

on_failure() {
  local status="$1"
  trap - ERR INT TERM
  set +e
  printf '[deploy] FAILED at stage %s. Release files and logs are preserved.\n' "$stage" >&2
  if [[ "$cutover_started" == 1 ]]; then
    # Migration may already have run. Never silently roll application/schema back.
    pm2 stop knowledge-web >/dev/null 2>&1
    pm2 stop knowledge-api >/dev/null 2>&1
    printf '[deploy] Apps stopped. Inspect logs and schema before retrying or using rollback.sh.\n' >&2
  elif [[ "$apps_stopped" == 1 ]]; then
    printf '[deploy] current was not changed; attempting to resume the previous release.\n' >&2
    pm2 restart knowledge-api >/dev/null 2>&1
    if wait_for_api; then pm2 restart knowledge-web; fi
  fi
  exit "$status"
}

install_failure_traps() {
  trap 'on_failure $?' ERR
  trap 'on_failure 130' INT
  trap 'on_failure 143' TERM
}

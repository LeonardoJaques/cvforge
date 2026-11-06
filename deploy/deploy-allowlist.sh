#!/usr/bin/env bash
set -Eeuo pipefail

P=/home/leo/cvforge
S=/home/leo/cvforge-source-sync
C="$P/docker-compose.server.yml"
ACTIVE="$P/.active-api-slot"
PREVIOUS="$P/.previous-api-slot"
NGINX_DIR="$P/deploy/nginx"
DEPLOY_COMMIT=''

fail() { echo "CVForge deploy: $1" >&2; exit 1; }
compose() { docker compose -f "$C" "$@"; }

install_runtime_files() {
  install -D -m 0644 "$S/deploy/docker-compose.server.yml" "$C" || fail "Could not install the versioned server Compose file."
  install -D -m 0644 "$S/deploy/nginx/default.conf" "$NGINX_DIR/default.conf" || fail "Could not install the API router config."
  install -d -m 0755 "$NGINX_DIR"
  if [[ ! -f "$NGINX_DIR/upstream-active.conf" ]]; then
    install -m 0644 "$S/deploy/nginx/upstream-active.conf" "$NGINX_DIR/upstream-active.conf" || fail "Could not create the initial API upstream."
  fi
}

sync_source() {
  local requested commit
  requested="${1:-origin/main}"
  git -C "$S" fetch --quiet origin +refs/heads/main:refs/remotes/origin/main || fail "Could not fetch Gitea main."
  commit=$(git -C "$S" rev-parse --verify "${requested}^{commit}") || fail "Requested Gitea commit is unavailable."
  [[ "$commit" =~ ^[0-9a-f]{40}$ ]] || fail "Invalid source commit."
  git -C "$S" merge-base --is-ancestor "$commit" refs/remotes/origin/main || fail "Requested commit is not on Gitea main."
  git -C "$S" checkout --quiet --force --detach "$commit" || fail "Could not select the requested source commit."
  install_runtime_files
  echo "Source commit: $commit"
}

validate() {
  sync_source origin/main
  compose config --quiet >/dev/null 2>&1 || fail "Compose validation failed."
  docker exec cvforge-db pg_isready -U cvforge -d cvforge >/dev/null 2>&1 || fail "Database is not healthy."
  echo "Red/green Compose and database are healthy."
}

slot_container() {
  case "$1" in
    red) echo cvforge-api-red ;;
    green) echo cvforge-api-green ;;
    legacy) echo cvforge-api ;;
    *) fail "Invalid API slot." ;;
  esac
}

active_slot() {
  if [[ -f "$ACTIVE" ]]; then
    local value
    value=$(<"$ACTIVE")
    [[ "$value" =~ ^(red|green|legacy)$ ]] || fail "Invalid active slot state."
    echo "$value"
  elif docker inspect cvforge-api >/dev/null 2>&1; then
    echo legacy
  else
    fail "No active API slot is recorded."
  fi
}

health_container() {
  local container="$1" n
  for n in $(seq 1 60); do
    if docker inspect --format '{{.State.Health.Status}}' "$container" 2>/dev/null | grep -q '^healthy$'; then
      return 0
    fi
    sleep 2
  done
  return 1
}

router_health() {
  local n body
  for n in $(seq 1 30); do
    body=$(docker exec cvforge-api-router wget -qO- http://127.0.0.1:8080/api/v1/health 2>/dev/null || true)
    if [[ "$body" == *'"status":"UP"'* && "$body" == *'"database":"connected"'* ]]; then
      return 0
    fi
    sleep 2
  done
  return 1
}

set_upstream() {
  local slot="$1" host tmp
  host=$(slot_container "$slot")
  tmp="$NGINX_DIR/.upstream-active.$$.tmp"
  printf 'upstream cvforge_api_active {\n    server %s:8080;\n    keepalive 32;\n}\n' "$host" > "$tmp"
  mv -f "$tmp" "$NGINX_DIR/upstream-active.conf"
  docker exec cvforge-api-router nginx -t >/dev/null 2>&1 || return 1
  docker exec cvforge-api-router nginx -s reload >/dev/null 2>&1 || return 1
}

health() {
  local slot
  slot=$(active_slot)
  if [[ "$slot" == legacy ]]; then
    docker exec cvforge-api wget -qO- http://127.0.0.1:8080/api/v1/health | grep -q '"status":"UP"' || fail "Legacy API or database is unhealthy."
  else
    router_health || fail "Active API router, database, or upstream is unhealthy."
  fi
  docker exec cvforge-frontend wget -qO- http://127.0.0.1/ | grep -qi '<html' || fail "Frontend is not healthy."
  echo "Active API, database, and frontend are healthy."
}

restore_traffic() {
  local slot="$1"
  if [[ "$slot" == legacy ]]; then
    export CVFORGE_API_ROUTER_PRIORITY=10
    compose up -d --no-deps api-router || return 1
    docker exec cvforge-api wget -qO- http://127.0.0.1:8080/api/v1/health | grep -q '"status":"UP"'
  else
    set_upstream "$slot" && router_health
  fi
}

rollback() {
  [[ -f "$PREVIOUS" ]] || fail "No previous red/green slot is recorded."
  local target
  target=$(<"$PREVIOUS")
  restore_traffic "$target" || fail "Previous slot failed the post-rollback health check."
  local current
  current=$(active_slot)
  printf '%s\n' "$current" > "$PREVIOUS.tmp"
  mv -f "$PREVIOUS.tmp" "$PREVIOUS"
  printf '%s\n' "$target" > "$ACTIVE.tmp"
  mv -f "$ACTIVE.tmp" "$ACTIVE"
  echo "API traffic restored to the previous $target slot."
}

deploy() {
  exec 9>/tmp/cvforge-red-green-deploy.lock
  flock -n 9 || fail "Another deployment is running."
  [[ "$DEPLOY_COMMIT" =~ ^[0-9a-f]{40}$ ]] || fail "Invalid deployment commit."
  sync_source "$DEPLOY_COMMIT"
  compose config --quiet >/dev/null 2>&1 || fail "Compose validation failed."
  docker exec cvforge-db pg_isready -U cvforge -d cvforge >/dev/null 2>&1 || fail "Database is not healthy."

  local old target service container first_deploy
  old=$(active_slot)
  first_deploy=false
  [[ "$old" == legacy ]] && first_deploy=true
  if [[ "$old" == red ]]; then target=green; else target=red; fi
  service="api-$target"
  container=$(slot_container "$target")
  export CVFORGE_IMAGE_TAG="$DEPLOY_COMMIT"
  if [[ "$first_deploy" == true ]]; then
    export CVFORGE_API_ROUTER_PRIORITY=10
  else
    export CVFORGE_API_ROUTER_PRIORITY=200
  fi

  # Bring up the stable router on the currently active API before building a candidate.
  compose up -d --no-deps api-router || fail "Could not start the stable API router."
  router_health || fail "The current API did not pass health through the stable router."

  compose build "$service" || fail "Candidate build or backend tests failed."
  compose up -d --no-deps --force-recreate "$service" || fail "Could not start the $target API candidate."
  health_container "$container" || fail "The $target API candidate failed its readiness check; active traffic is unchanged."

  if ! set_upstream "$target" || ! router_health; then
    restore_traffic "$old" || fail "Previous slot failed after automatic traffic rollback."
    fail "Candidate failed after traffic switch; traffic returned to $old."
  fi

  if [[ "$first_deploy" == true ]]; then
    # Keep the old direct Traefik route live until the candidate is proven healthy.
    export CVFORGE_API_ROUTER_PRIORITY=200
    compose up -d --no-deps api-router || {
      export CVFORGE_API_ROUTER_PRIORITY=10
      compose up -d --no-deps api-router || true
      fail "Could not promote the stable API router; the legacy route remains active."
    }
  fi

  printf '%s\n' "$old" > "$PREVIOUS.tmp"
  mv -f "$PREVIOUS.tmp" "$PREVIOUS"
  printf '%s\n' "$target" > "$ACTIVE.tmp"
  mv -f "$ACTIVE.tmp" "$ACTIVE"
  health || {
    restore_traffic "$old" || fail "Final health failed and traffic rollback could not be verified."
    printf '%s\n' "$target" > "$PREVIOUS.tmp" && mv -f "$PREVIOUS.tmp" "$PREVIOUS"
    printf '%s\n' "$old" > "$ACTIVE.tmp" && mv -f "$ACTIVE.tmp" "$ACTIVE"
    fail "Final health failed; traffic returned to $old."
  }
  echo "Red/green deployment completed: $old -> $target ($DEPLOY_COMMIT)."
}

case "${SSH_ORIGINAL_COMMAND:-}" in
  validate) validate ;;
  deploy) DEPLOY_COMMIT=$(git -C /home/leo/cvforge-source-sync rev-parse origin/main) ; deploy ;;
  deploy:*)
    DEPLOY_COMMIT="${SSH_ORIGINAL_COMMAND#deploy:}"
    [[ "$DEPLOY_COMMIT" =~ ^[0-9a-f]{40}$ ]] || fail "Invalid deployment commit."
    deploy
    ;;
  health) health ;;
  rollback) rollback ;;
  *) fail "Command denied." ;;
esac

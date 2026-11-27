#!/usr/bin/env bash
# Deploys an image tag to the VM and rolls back to the previous tag if the new one is not healthy.
# Usage: deploy/deploy.sh <image-tag>
# Runs on the self-hosted runner; expects /opt/shorty/.env (see deploy/env.example).
set -euo pipefail

NEW_TAG="${1:?usage: deploy.sh <image-tag>}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="${SHORTY_DEPLOY_DIR:-/opt/shorty}"
HEALTH_URL="${SHORTY_HEALTH_URL:-http://localhost:8080/actuator/health}"
HEALTH_TIMEOUT="${SHORTY_HEALTH_TIMEOUT:-180}"

cd "$DEPLOY_DIR"
if [[ ! -f .env ]]; then
  echo "::error::$DEPLOY_DIR/.env is missing (see deploy/env.example)"
  exit 1
fi
cp "$SCRIPT_DIR/compose.yaml" compose.yaml
PREVIOUS_TAG="$(cat current-tag 2>/dev/null || true)"

compose() {
  local tag="$1"
  shift
  SHORTY_IMAGE_TAG="$tag" docker compose --env-file .env -f compose.yaml "$@"
}

wait_healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT))
  while ((SECONDS < deadline)); do
    if curl -fsS --max-time 5 "$HEALTH_URL" | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 5
  done
  return 1
}

echo "Deploying $NEW_TAG (previous: ${PREVIOUS_TAG:-none})"
compose "$NEW_TAG" pull app
compose "$NEW_TAG" up -d --remove-orphans

if wait_healthy; then
  echo "$NEW_TAG" > current-tag
  [[ -n "$PREVIOUS_TAG" ]] && echo "$PREVIOUS_TAG" > previous-tag
  docker image prune -f --filter "label=org.opencontainers.image.source=https://github.com/pgatzka/shorty" > /dev/null || true
  echo "Deployed $NEW_TAG"
  exit 0
fi

echo "::error::$NEW_TAG did not become healthy within ${HEALTH_TIMEOUT}s"
compose "$NEW_TAG" logs --tail 100 app || true
if [[ -n "$PREVIOUS_TAG" ]]; then
  echo "Rolling back to $PREVIOUS_TAG"
  compose "$PREVIOUS_TAG" up -d --remove-orphans
  if wait_healthy; then
    echo "Rolled back to $PREVIOUS_TAG"
  else
    echo "::error::Rollback to $PREVIOUS_TAG is not healthy either"
  fi
else
  echo "::warning::No previous tag to roll back to"
fi
exit 1

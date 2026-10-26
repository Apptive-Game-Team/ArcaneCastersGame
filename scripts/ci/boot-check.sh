#!/usr/bin/env bash
# Boots a built game server image against a freshly migrated Postgres and waits for
# /actuator/health to report UP. Tests run on a full JDK, the image runs on a JRE with fewer
# modules, so code that passes the tests can still fail to start in the image (issue #107).
#
# Usage: scripts/ci/boot-check.sh <image> <migration-dir>
#   <migration-dir> is ArcaneCastersDatabase's migration/ directory.
#
# Environment overrides: BOOT_TIMEOUT_SECONDS (default 180), POSTGRES_IMAGE, FLYWAY_IMAGE.
set -euo pipefail

IMAGE=${1:?usage: boot-check.sh <image> <migration-dir>}
MIGRATION_DIR=$(cd "${2:?usage: boot-check.sh <image> <migration-dir>}" && pwd)
TIMEOUT=${BOOT_TIMEOUT_SECONDS:-180}
POSTGRES_IMAGE=${POSTGRES_IMAGE:-postgres:17-alpine}
FLYWAY_IMAGE=${FLYWAY_IMAGE:-flyway/flyway:11}

RUN_ID="boot-check-$$"
NETWORK="$RUN_ID"
DB_CONTAINER="$RUN_ID-db"
APP_CONTAINER="$RUN_ID-app"
DB_NAME=game
DB_USER=game
DB_PASSWORD=boot-check

cleanup() {
    docker rm -f "$APP_CONTAINER" "$DB_CONTAINER" >/dev/null 2>&1 || true
    docker network rm "$NETWORK" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker network create "$NETWORK" >/dev/null

echo "Starting $POSTGRES_IMAGE"
docker run -d --name "$DB_CONTAINER" --network "$NETWORK" \
    -e POSTGRES_DB="$DB_NAME" -e POSTGRES_USER="$DB_USER" -e POSTGRES_PASSWORD="$DB_PASSWORD" \
    "$POSTGRES_IMAGE" >/dev/null
for _ in $(seq 1 30); do
    docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 && break
    sleep 1
done

echo "Applying migrations from $MIGRATION_DIR"
docker run --rm --network "$NETWORK" \
    -v "$MIGRATION_DIR:/flyway/sql:ro" \
    "$FLYWAY_IMAGE" \
    -url="jdbc:postgresql://$DB_CONTAINER:5432/$DB_NAME" \
    -user="$DB_USER" -password="$DB_PASSWORD" \
    -locations=filesystem:/flyway/sql \
    -mixed=true \
    migrate >/dev/null

# Outside services are pointed at addresses that refuse connections. Startup must not need
# them: the JSON Web Key Set is fetched on the first authenticated request, and a missing
# lobby only skips notifications.
echo "Starting $IMAGE"
docker run -d --name "$APP_CONTAINER" --network "$NETWORK" \
    -e DATABASE_URL="jdbc:postgresql://$DB_CONTAINER:5432/$DB_NAME" \
    -e DATABASE_USER="$DB_USER" \
    -e DATABASE_PW="$DB_PASSWORD" \
    -e PORT=7777 \
    -e EXTERNAL_PORT=7777 \
    -e PROTOCOL=http \
    -e DOMAIN=localhost \
    -e ACCOUNT_SERVER_URL=http://127.0.0.1:9 \
    -e DISCORD_WEBHOOK_URL= \
    -e ALERT_ENABLED=false \
    "$IMAGE" >/dev/null

# The JRE image has no curl or wget, so the health endpoint is polled from a throwaway
# container on the same network.
deadline=$((SECONDS + TIMEOUT))
status=""
while ((SECONDS < deadline)); do
    if [[ "$(docker inspect -f '{{.State.Running}}' "$APP_CONTAINER")" != "true" ]]; then
        echo "::error::$IMAGE exited during startup"
        docker logs "$APP_CONTAINER" 2>&1 | tail -200
        exit 1
    fi
    status=$(docker run --rm --network "$NETWORK" curlimages/curl:8.10.1 \
        -s --max-time 2 "http://$APP_CONTAINER:8081/actuator/health" 2>/dev/null || true)
    if [[ "$status" == *'"status":"UP"'* ]]; then
        echo "$IMAGE is UP after ${SECONDS}s"
        exit 0
    fi
    sleep 3
done

echo "::error::$IMAGE did not report UP within ${TIMEOUT}s (last response: ${status:-none})"
docker logs "$APP_CONTAINER" 2>&1 | tail -200
exit 1

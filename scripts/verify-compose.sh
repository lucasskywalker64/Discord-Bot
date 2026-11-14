#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

: "${APP_IMAGE:?Set APP_IMAGE to the image under test}"
export COMPOSE_PROJECT_NAME="discord-bot-smoke-${RANDOM}-$$"
export POSTGRES_PASSWORD=disposable-smoke-password
export DISCORD_ENABLED=false DISCORD_TOKEN= APP_PORT=0
test_env=$(mktemp)
compose() { docker compose --env-file "$test_env" -f compose.yaml "$@"; }
sql() { compose exec -T postgres psql -U discord_bot -d discord_bot -At -v ON_ERROR_STOP=1 -c "$1"; }
cleanup() {
    result=$?
    if [ "$result" -ne 0 ]; then compose logs --no-color --tail 100; fi
    compose down --volumes --remove-orphans
    rm -f "$test_env"
    exit "$result"
}
trap cleanup EXIT

compose up -d --wait --wait-timeout 180
test "$(docker image inspect "$APP_IMAGE" --format '{{.Os}}/{{.Architecture}}')" = linux/amd64
test "$(docker image inspect "$APP_IMAGE" --format '{{.Config.User}}')" = 10001:10001
endpoint=$(compose port app 8080)
curl --fail --silent "http://$endpoint/actuator/health/readiness" | grep -q '"status":"UP"'
test "$(sql 'SELECT count(*) FROM flyway_schema_history WHERE success')" -gt 0
migrations=$(sql 'SELECT installed_rank, version, checksum, success FROM flyway_schema_history ORDER BY installed_rank')
sql "INSERT INTO guild_configuration (guild_id, member_count_voice_channel_id) VALUES ('100000000000000001', '200000000000000001'), ('100000000000000002', '200000000000000002'), ('18446744073709551615', NULL)" >/dev/null

assert_persisted() {
    test "$(sql "SELECT guild_id || ':' || coalesce(member_count_voice_channel_id, 'unset') FROM guild_configuration ORDER BY guild_id")" = \
"100000000000000001:200000000000000001
100000000000000002:200000000000000002
18446744073709551615:unset"
    test "$(sql 'SELECT installed_rank, version, checksum, success FROM flyway_schema_history ORDER BY installed_rank')" = "$migrations"
}

compose up -d --no-deps --force-recreate --wait --wait-timeout 180 app
assert_persisted
# Recreate PostgreSQL too, proving that the named volume retains the data.
compose down
compose up -d --wait --wait-timeout 180
assert_persisted
echo 'Compose readiness, migrations and persistence passed without provider credentials.'

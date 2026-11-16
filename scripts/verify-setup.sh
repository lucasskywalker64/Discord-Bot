#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${APP_IMAGE:?Set APP_IMAGE to the image under test}"
fixture=$(mktemp -d)
mkdir "$fixture/bundle"
tar -xzf target/discord-bot-setup.tar.gz -C "$fixture/bundle"
export COMPOSE_PROJECT_NAME="discord-bot-setup-${RANDOM}-$$"
export DISCORD_ENABLED=false DISCORD_TOKEN= APP_PORT=0
compose() { docker compose --project-directory "$fixture/discord-bot" --file "$fixture/discord-bot/compose.yaml" --env-file "$fixture/discord-bot/.env" "$@"; }
cleanup() {
    result=$?
    if [ -f "$fixture/discord-bot/compose.yaml" ]; then
        if [ "$result" -ne 0 ]; then compose logs --no-color --tail 60; fi
        compose down --volumes --remove-orphans
        rm -f "$fixture/discord-bot/compose.yaml" "$fixture/discord-bot/.env"
        rmdir "$fixture/discord-bot"
    fi
    rm -f "$fixture/bundle/compose.yaml" "$fixture/bundle/.env.example" "$fixture/bundle/setup.sh" "$fixture/bundle/README.md"
    rmdir "$fixture/bundle"
    rmdir "$fixture/invalid" "$fixture" 2>/dev/null || true
    exit "$result"
}
trap cleanup EXIT

# Reject invalid IDs before creating credentials or starting services.
if printf '%s\n' "$fixture/invalid" "$APP_IMAGE" 'disposable-test-token' '18446744073709551616' | bash "$fixture/bundle/setup.sh"; then
    echo 'Setup accepted an invalid guild ID.' >&2; exit 1
fi
test ! -e "$fixture/invalid/.env"

printf '%s\n' "$fixture/discord-bot" "$APP_IMAGE" 'disposable-test-token' \
    '100000000000000001,100000000000000002' | bash "$fixture/bundle/setup.sh"
test "$(compose exec -T postgres psql -U discord_bot -d discord_bot -Atc 'SELECT count(*) FROM guild_configuration')" = 2
saved_env=$(sha256sum "$fixture/discord-bot/.env")
saved_compose=$(sha256sum "$fixture/discord-bot/compose.yaml")
compose exec -T postgres psql -U discord_bot -d discord_bot -v ON_ERROR_STOP=1 -c \
    "UPDATE guild_configuration SET member_count_voice_channel_id='200000000000000001' WHERE guild_id='100000000000000001'" >/dev/null
compose down
printf '%s\n' "$fixture/discord-bot" | bash "$fixture/bundle/setup.sh"
test "$(sha256sum "$fixture/discord-bot/.env")" = "$saved_env"
test "$(sha256sum "$fixture/discord-bot/compose.yaml")" = "$saved_compose"
test "$(compose exec -T postgres psql -U discord_bot -d discord_bot -Atc "SELECT member_count_voice_channel_id FROM guild_configuration WHERE guild_id='100000000000000001'")" = 200000000000000001
echo 'Guided setup, invalid-ID rejection and configuration-preserving rerun passed.'

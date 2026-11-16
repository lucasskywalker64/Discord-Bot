#!/usr/bin/env bash
set -euo pipefail
umask 077
bundle_dir=$(cd "$(dirname "$0")" && pwd)

docker compose version >/dev/null
case "$(docker info --format '{{.OSType}}/{{.Architecture}}')" in
    linux/x86_64|linux/amd64) ;;
    *) echo 'Setup requires Docker running Linux containers on an amd64 host.' >&2; exit 1 ;;
esac

printf 'Stack directory [/opt/stacks/discord-bot]: '
IFS= read -r stack_dir
stack_dir=${stack_dir:-/opt/stacks/discord-bot}
mkdir -p "$stack_dir"
stack_dir=$(cd "$stack_dir" && pwd)

if [ -e "$stack_dir/.env" ]; then
    test -f "$stack_dir/compose.yaml" || { echo 'Existing .env has no compose.yaml; restore the stack files first.' >&2; exit 1; }
    echo 'Reusing existing configuration and database credentials.'
else
    if [ -e "$stack_dir/compose.yaml" ] && [ "$stack_dir" != "$bundle_dir" ]; then
        echo 'Existing compose.yaml has no .env; restore its configuration before running setup.' >&2
        exit 1
    fi
    default_image=$(awk -F= '$1 == "APP_IMAGE" { print $2; exit }' "$bundle_dir/.env.example")
    printf 'Image [%s] (use :develop, an exact version or commit to select another build): ' "$default_image"
    IFS= read -r image
    image=${image:-$default_image}
    echo 'Create a Discord application at https://discord.com/developers/applications.'
    echo 'In Bot, copy or reset its token. In Installation, enable Guild Install with'
    echo 'the bot and applications.commands scopes, then use the install link to invite it.'
    IFS= read -rs -p 'Discord bot token (hidden): ' token
    printf '\n'
    test -n "$token" || { echo 'A Discord bot token is required.' >&2; exit 1; }
    echo 'In Discord: User Settings > Advanced > Developer Mode, then right-click'
    echo 'each server icon > Copy Server ID.'
    printf 'Guild IDs (comma-separated): '
    IFS= read -r guild_ids
    guild_ids=${guild_ids//[[:space:]]/}
    [[ "$guild_ids" =~ ^[1-9][0-9]{0,19}(,[1-9][0-9]{0,19})*$ ]] || {
        echo 'Enter one or more comma-separated Discord guild IDs.' >&2; exit 1;
    }
    IFS=, read -ra ids <<< "$guild_ids"
    for id in "${ids[@]}"; do
        if [[ ${#id} -eq 20 && "$id" > 18446744073709551615 ]]; then
            echo 'A guild ID exceeds the unsigned 64-bit range.' >&2; exit 1
        fi
    done
    password=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
    temporary_env=$(mktemp "$stack_dir/.env.XXXXXX")
    trap 'rm -f "$temporary_env"' EXIT
    write_env() { printf "%s='%s'\n" "$1" "${2//\'/\\\'}" >> "$temporary_env"; }
    write_env APP_IMAGE "$image"
    write_env POSTGRES_PASSWORD "$password"
    write_env APP_PORT 8080
    write_env DISCORD_ENABLED true
    write_env DISCORD_TOKEN "$token"
    write_env DISCORD_GUILD_IDS "$guild_ids"
    if [ "$stack_dir" != "$bundle_dir" ]; then
        cp "$bundle_dir/compose.yaml" "$stack_dir/compose.yaml"
    fi
    mv "$temporary_env" "$stack_dir/.env"
fi

cd "$stack_dir"
docker compose --file compose.yaml --env-file .env config -q
docker compose --file compose.yaml --env-file .env up -d --wait --wait-timeout 180
echo "Setup complete: $stack_dir"
echo 'In Dockge, scan the stacks folder if needed, then manage this stack there.'
echo 'Check Discord connection logs and use /general ping in each configured guild.'

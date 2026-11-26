-- Prefer the origin already registered for active Discord sign-in so the upgrade preserves its redirect URI.
WITH origins AS (
    SELECT id, coalesce(
        CASE WHEN discord_oauth_enabled THEN
            substring(discord_callback_url FROM '^' || pattern || '/dashboard/oauth/discord/callback$')
        END,
        substring(public_url FROM '^' || pattern || '(?:[/?#]|$)'),
        CASE WHEN twitch_enabled THEN substring(twitch_callback_url FROM '^' || pattern || '(?:[/?#]|$)') END
    ) AS public_origin
    FROM installation_configuration
    CROSS JOIN (VALUES ('(https://[^/?#@]+|http://(?:[Ll][Oo][Cc][Aa][Ll][Hh][Oo][Ss][Tt]|127[.]0[.]0[.]1|\[::1\])(?::[0-9]+)?)')) AS origin(pattern)
)
UPDATE installation_configuration AS configuration
SET public_url = origins.public_origin,
    setup_step = CASE configuration.setup_step
        WHEN 'PROVIDERS' THEN 'SERVER'
        WHEN 'DEPLOYMENT' THEN 'STORAGE'
        ELSE configuration.setup_step
    END
FROM origins
WHERE configuration.id = origins.id;

ALTER TABLE installation_configuration
    ALTER COLUMN setup_step SET DEFAULT 'SERVER',
    DROP COLUMN discord_callback_url,
    DROP COLUMN twitch_callback_url;

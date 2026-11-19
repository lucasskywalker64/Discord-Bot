CREATE TABLE installation_configuration (
    id integer PRIMARY KEY CHECK (id = 1),
    discord_bot_enabled boolean NOT NULL DEFAULT false,
    discord_bot_token text,
    discord_oauth_enabled boolean NOT NULL DEFAULT false,
    discord_client_id text,
    discord_client_secret text,
    discord_callback_url text,
    discord_oauth_revision bigint NOT NULL DEFAULT 0,
    discord_bot_revision bigint NOT NULL DEFAULT 0,
    discord_state varchar(255) NOT NULL DEFAULT 'DISABLED',
    discord_failure text,
    twitch_enabled boolean NOT NULL DEFAULT false,
    twitch_client_id text,
    twitch_client_secret text,
    twitch_callback_url text,
    twitch_oauth_revision bigint NOT NULL DEFAULT 0,
    storage_enabled boolean NOT NULL DEFAULT false,
    storage_endpoint text,
    storage_bucket text,
    storage_region text,
    storage_access_key text,
    storage_secret_key text,
    public_url text,
    setup_step varchar(255) NOT NULL DEFAULT 'PROVIDERS'
);

INSERT INTO installation_configuration (id) VALUES (1);
REVOKE ALL ON installation_configuration FROM PUBLIC;

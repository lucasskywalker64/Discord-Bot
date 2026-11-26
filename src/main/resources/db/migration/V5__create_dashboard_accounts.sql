CREATE TABLE dashboard_account (
    id UUID PRIMARY KEY,
    discord_id VARCHAR(20) NOT NULL UNIQUE,
    discord_username VARCHAR(64) NOT NULL,
    discord_login_version BIGINT NOT NULL DEFAULT 0,
    primary_twitch_id VARCHAR(20) UNIQUE,
    managed_guild_id VARCHAR(20) UNIQUE
);

CREATE TABLE dashboard_session (
    id VARCHAR(64) PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES dashboard_account(id) ON DELETE CASCADE,
    login_version BIGINT NOT NULL,
    authenticated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX dashboard_session_expiration ON dashboard_session (expires_at);
CREATE INDEX dashboard_session_account ON dashboard_session (account_id);

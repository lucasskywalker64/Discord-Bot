ALTER TABLE dashboard_account
    ADD COLUMN recovery_code_hash VARCHAR(64) UNIQUE,
    ADD COLUMN recovery_link_hash VARCHAR(64) UNIQUE,
    ADD COLUMN recovery_link_expires_at TIMESTAMP WITH TIME ZONE,
    ADD CHECK ((recovery_link_hash IS NULL) = (recovery_link_expires_at IS NULL));

CREATE TABLE dashboard_recovery_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    action VARCHAR(24) NOT NULL,
    actor VARCHAR(64),
    reason VARCHAR(300),
    previous_discord_id VARCHAR(20) NOT NULL,
    new_discord_id VARCHAR(20)
);

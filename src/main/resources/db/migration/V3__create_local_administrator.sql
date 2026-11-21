CREATE TABLE local_administrator (
    id integer PRIMARY KEY CHECK (id = 1),
    username varchar(64) NOT NULL,
    password_hash varchar(255) NOT NULL,
    credential_version bigint NOT NULL DEFAULT 1
);

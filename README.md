# Discord-Bot rewrite

The rewrite on `develop` requires Java 25 and PostgreSQL. The legacy application
remains on `main` until the rewrite release is accepted.

## Database configuration

Create an empty PostgreSQL database and supply Spring Boot's environment variables:

| Variable | Value |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/discord_bot` |
| `SPRING_DATASOURCE_USERNAME` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | Database password |

The database user needs permission to create and alter tables in the configured
schema. Flyway applies versioned migrations from `src/main/resources/db/migration`
at startup. Hibernate validates the resulting schema without changing it. Failed
migrations or incompatible schemas prevent startup. Add schema changes as new
migrations; do not edit applied migrations.

`guild_configuration` stores one row per Discord guild ID and an optional
member-count voice-channel ID. Discord IDs use decimal strings to preserve the
full unsigned snowflake range. An unset channel is stored as SQL `NULL`.

## Verification

Run `./mvnw -B -ntp verify`, or `.\mvnw.cmd -B -ntp verify` on Windows, with a
Docker-compatible runtime running. The startup tests create a disposable
PostgreSQL 17 container, apply the real migrations, check HTTP readiness and
independent guild settings across application restarts, and reject broken
migrations and incompatible schemas. They supply their own database connection
and require no production credentials or Discord/Twitch connections. Missing
Docker fails verification instead of skipping these checks.

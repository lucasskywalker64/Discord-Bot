# Discord-Bot rewrite

The rewrite on `develop` requires Java 25 and PostgreSQL. The legacy application
remains on `main` until the rewrite release is accepted.

## Container installation

The container workflow publishes `linux/amd64` images to
`ghcr.io/lucasskywalker64/discord-bot`. A verified develop build is tagged
`sha-<full-commit>` for release rehearsal. Stable `v<major>.<minor>.<patch>`
tags and GitHub Releases are published only for release commits on `main`.
The rewrite has no stable image until that release integration occurs.

Download `compose.yaml` and `.env.example` from the same release commit. Copy
`.env.example` to `.env`, replace `APP_IMAGE` with the digest reference from the
GitHub Release (or candidate workflow summary), and set a long random
`POSTGRES_PASSWORD`. Keep `.env` private and outside source control. Public GHCR
pulls need no login, deployment credentials or access to the operator's network.

```sh
docker compose pull
docker compose up -d --wait --wait-timeout 180
docker compose ps
curl --fail http://localhost:8080/actuator/health/readiness
```

Use Docker Compose v2.20 or newer. In Dockge, put the same Compose content and
environment values in a new stack and deploy it. Docker and Dockge must run on
an amd64 host. The app listens on host loopback port `APP_PORT` (default 8080);
PostgreSQL has no published host port. Docker health checks expose app readiness
and database readiness. Flyway initializes an empty database automatically.
HTTP readiness confirms app and database startup; it does not continuously check
database availability or Discord connectivity.

The named `postgres-data` volume retains settings across container recreation
and `docker compose down`. `docker compose down --volumes` deletes it. Changing
`POSTGRES_PASSWORD` after initialization does not change the stored database
password; rotate it in PostgreSQL too. Keep the stack name and volume when
recreating the installation.

Leave `DISCORD_ENABLED=false` and `DISCORD_TOKEN` empty for an installation
check without provider connections. To connect Discord, provision guilds as
described below, set `DISCORD_ENABLED=true` and the token in `.env`, then recreate
the app. PostgreSQL is accessible through the stack without publishing its port:

```sh
docker compose exec postgres psql -U discord_bot -d discord_bot
docker compose up -d --force-recreate app
```

App and PostgreSQL versions are pinned independently. To update the app, back up
the database, change only `APP_IMAGE` to the chosen release digest, then run
`docker compose pull app` and `docker compose up -d --no-deps --wait app`.
Review migration compatibility before changing releases. Database upgrades and
recovery need their own procedure; an older image cannot undo schema changes.

### Build and publication checks

CI runs Maven Wrapper verification, builds the JAR into the pinned Java 25 JRE
image as a non-root user, and runs `scripts/verify-compose.sh` against the sample
stack. The check uses a new disposable project, random host port, its own database
password and no provider credentials. It checks HTTP readiness, real Flyway
migrations and three representative guild rows after app recreation and after
recreating both containers with the same volume. It removes its test volume on
exit. To repeat locally after `./mvnw -B -ntp verify`:

```sh
docker build --platform linux/amd64 -t discord-bot:local .
APP_IMAGE=discord-bot:local bash scripts/verify-compose.sh
```

The Docker build context includes only the packaged JAR and Dockerfile. Runtime
secrets come from Compose environment values and are not baked into the image.
The publishing job loads the exact tested image artifact and is the only job
with package-write permission. Develop builds publish a commit tag and record
the digest in the job summary. After the maintainer merges the release into
`main`, pushing a tag such as `v6.0.0` at that commit runs the same checks. The
workflow rejects release tags outside main's history, publishes the version tag
and records its commit and immutable image digest in the GitHub Release.

GHCR creates a new package as private, even for a public repository. After the
first candidate push, the package owner must open the `discord-bot` package's
settings and change its visibility to Public, then rerun the failed publishing
job. The workflow tests a pull by digest with an empty Docker credential
configuration. It creates the stable version tag and Release only after that
anonymous pull succeeds. See [GitHub's registry documentation](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).

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

## Discord configuration

Discord is disabled by default. Set `DISCORD_ENABLED=true` and `DISCORD_TOKEN`
to a bot token to start the JDA 6.7.0 gateway with Spring. Keep the token outside
source control. Startup tests explicitly disable Discord; adapter tests replace
the outbound JDA connection and use disposable PostgreSQL.

A configured guild has a row in `guild_configuration`. When Discord reports
that a guild is ready, joined, or newly available, the adapter registers the
guild commands `/general ping` and `/general addmembercount`. Registration replaces
the guild's command set. Ping replies ephemerally with REST and gateway latency
in milliseconds. DMs, missing guilds and unconfigured guilds receive a rejection
without using another guild's settings. No privileged gateway intents are needed.
The command retains the legacy administrator-only default. Guild administrators
can grant command access through Discord's integration settings.

Configured guild IDs load from PostgreSQL before JDA starts. Gateway callbacks
use an immutable in-memory snapshot instead of querying the database. A failed
initial load prevents startup. Operator provisioning still requires a restart.
Code that changes guild membership must call `ConfiguredGuilds.refresh()` after
the database transaction commits, outside gateway callbacks. A failed refresh
clears the snapshot, rejecting guild access until a refresh succeeds. This
snapshot covers provisioning only; feature authorization remains separate.

### Guild settings

On a clean installation, start the app once to apply Flyway migrations, then
provision each intended guild through your database client:

```sql
INSERT INTO guild_configuration (guild_id) VALUES ('YOUR_GUILD_ID');
```

Start or restart the app after provisioning. Commands are registered only for
provisioned guilds. The member-count channel starts unset.

Only the current guild owner can read or change this setting, including when
Discord grants another administrator access to the command. Use
`/general addmembercount` to read it, or
`/general addmembercount channel:<voice channel>` to set or replace it. The channel
must be an existing voice channel in the same guild. Replies are ephemeral.
The setting persists across restarts; storage failures return a command error
instead of reporting an unset channel. The command only stores the channel;
member-count renaming, scheduling and removal belong to rewrite ticket #61.

Settings transactions have a five-second timeout, including database lock waits.
On shutdown, the settings executor waits up to five seconds for active tasks,
then interrupts any remaining tasks.

Registration retries transport errors, timeouts and HTTP 5xx failures after
JDA's own request handling finishes. Each operation makes at most three
application attempts, with 1- and 2-second backoff. Permanent failures are
logged without retrying. Only one operation per guild can be pending; Spring
stop cancels retry timers before draining JDA, and stale callbacks cannot restart
work after a lifecycle restart. A later guild lifecycle event can retry an
operation that previously exhausted its attempts.

Declare each `SubcommandModule` as a Spring bean. Its `CommandDefinition` owns
root and group descriptions, default permissions and the subcommand definition.
`CommandRegistry` snapshots these definitions for registration and routing;
duplicate routes, conflicting root/group metadata, and a name shared by a
direct subcommand and a sibling group prevent startup.
Handlers receive explicit guild and actor IDs in `CommandContext`, alongside
the JDA event for Discord operations. Pass these IDs to feature operations.
Return the complete asynchronous operation as a `CompletionStage` so the registry
can handle thrown exceptions and failed stages consistently. It sends an
ephemeral error before acknowledgement, updates the response after
acknowledgement, or returns empty autocomplete choices. Failed error delivery is
logged without retrying. `GeneralPing` owns ping behavior; JDA lifecycle stays
separate.

Spring starts the gateway asynchronously. HTTP readiness confirms application
and database startup, not Discord connectivity. Check JDA's connection logs and
the live ping before accepting an operator deployment. JDA's JVM shutdown hook
is disabled so Spring can drain requests for up to 10 seconds before cancelling
them. If the drain times out, Spring waits up to 5 more seconds after cancellation.
If the drain is interrupted, Spring cancels pending requests and restores the
thread's interrupt status. A timeout or interruption is logged.

### Opt-in live smoke test

Use a dedicated test bot, two test guilds and a disposable PostgreSQL database.
Invite the bot with the `bot` and `applications.commands` OAuth scopes. Supply
the database variables above, enable Discord and supply the test token through
the environment. Do not run this check with a production bot token or database.

1. Build with `./mvnw -B -ntp verify` and start the packaged application with
   `java -jar target/DiscordBot-6.0.0-SNAPSHOT.jar`. Flyway creates the schema.
2. Add the two test guild IDs through your database client, then restart:

   ```sql
   INSERT INTO guild_configuration (guild_id)
   VALUES ('YOUR_FIRST_TEST_GUILD_ID'), ('YOUR_SECOND_TEST_GUILD_ID');
   ```

   Wait for JDA to report a connected gateway.
3. As an administrator in each guild, invoke `/general ping` and check the ephemeral reply contains
   both measured latencies. Repeat to check subsequent interactions. If Discord
   offers the command in a DM, verify it rejects the request.
4. As the owner of each guild, run `/general addmembercount` and confirm the
   channel is unset. Set a different local voice channel in each guild with
   `/general addmembercount channel:<voice channel>`, then omit `channel` to
   read it back. Confirm each guild returns its own channel in an ephemeral reply.
   As an administrator who is not the owner, try both reading and setting the
   channel. Confirm both requests are rejected and the owner's read stays unchanged.
5. Stop the foreground application with Ctrl+C or send SIGTERM. Confirm the
   process exits without a shutdown timeout and the bot disconnects. Start it
   again and repeat both guild pings to check registration after reconnecting.
   As each guild's owner, read `/general addmembercount` again and confirm its
   previously selected channel persists.

Automated tests cover the missing-guild case even when Discord does not expose
the guild command in DMs. Real gateway delivery requires this opt-in check and
is not exercised by the automated suite.

## Verification

Run `./mvnw -B -ntp verify`, or `.\mvnw.cmd -B -ntp verify` on Windows, with a
Docker-compatible runtime running. The startup tests create a disposable
PostgreSQL 17 container, apply the real migrations, check HTTP readiness and
independent guild settings across application restarts, and reject broken
migrations and incompatible schemas. They supply their own database connection
and require no production credentials or Discord/Twitch connections. Missing
Docker fails verification instead of skipping these checks.

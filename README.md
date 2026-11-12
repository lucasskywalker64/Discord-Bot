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

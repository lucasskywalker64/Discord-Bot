import { useEffect, useState } from "react";
import { Field, Panel, Toggle } from "./dashboard";
import type { FieldErrors } from "./dashboard";
import { adminRequest } from "./api";
import type { Configuration, Status } from "./api";

// This placeholder is masked by the input. PostgreSQL cannot store a NUL in a secret.
const MASK_CHARACTER = "\0";
const SAVED_SECRET_MASK = MASK_CHARACTER.repeat(8);
const STATUS_POLL_INTERVAL_MS = 3000;

function selectSavedSecret({ currentTarget }: { currentTarget: HTMLInputElement }) {
  if (currentTarget.value === SAVED_SECRET_MASK) currentTarget.select();
}

function SecretField({ name, label, configured, error }: {
  name: string;
  label: string;
  configured: boolean;
  error?: string;
}) {
  return <>
    {configured && <input type="hidden" name={`${name}.configured`} value="true" />}
    <Field name={name} label={label} type="password" autoComplete="off" error={error}
      data-provider-secret defaultValue={configured ? SAVED_SECRET_MASK : ""}
      aria-description={configured ? "A value is saved. Type to replace it or delete it to clear it on save." : undefined}
      onFocus={selectSavedSecret} onClick={selectSavedSecret}
      onChange={({ currentTarget }) => {
        if (currentTarget.value.includes(MASK_CHARACTER)) {
          currentTarget.value = currentTarget.value.replaceAll(MASK_CHARACTER, "");
        }
      }} />
  </>;
}

export function DiscordFields({ value, errors, setup }: {
  value: Configuration["discord"]; errors: FieldErrors; setup?: boolean;
}) {
  return <>
    {!setup && <Toggle name="discord.botEnabled" label="Enable the Discord bot" checked={value.botEnabled} />}
    <SecretField name="discord.botToken" label="Bot token" configured={value.botTokenConfigured}
      error={errors["discord.botToken"]} />
    <Toggle name="discord.oauthEnabled" label="Enable public Discord sign-in" checked={value.oauthEnabled} />
    <div className="field-grid">
      <Field name="discord.clientId" label="Application ID" defaultValue={value.clientId ?? ""}
        error={errors["discord.clientId"]} />
      <SecretField name="discord.clientSecret" label="Client secret" configured={value.clientSecretConfigured}
        error={errors["discord.clientSecret"]} />
    </div>
    <Field name="discord.callbackUrl" label="OAuth callback URL" type="url" defaultValue={value.callbackUrl ?? ""}
      error={errors["discord.callbackUrl"]} />
  </>;
}

export function TwitchFields({ value, errors }: { value: Configuration["twitch"]; errors: FieldErrors }) {
  return <>
    <Toggle name="twitch.enabled" label="Enable Twitch" checked={value.enabled} />
    <div className="field-grid">
      <Field name="twitch.clientId" label="Client ID" defaultValue={value.clientId ?? ""} error={errors["twitch.clientId"]} />
      <SecretField name="twitch.clientSecret" label="Client secret" configured={value.clientSecretConfigured}
        error={errors["twitch.clientSecret"]} />
    </div>
    <Field name="twitch.callbackUrl" label="OAuth callback URL" type="url" defaultValue={value.callbackUrl ?? ""}
      error={errors["twitch.callbackUrl"]} />
  </>;
}

export function DeploymentFields({ value, errors }: { value: Configuration; errors: FieldErrors }) {
  return <Field name="publicUrl" label="Public URL" type="url" placeholder="https://bot.example.com"
    defaultValue={value.publicUrl ?? ""} error={errors.publicUrl} />;
}

export function StorageFields({ value, errors }: { value: Configuration["storage"]; errors: FieldErrors }) {
  return <>
    <Toggle name="storage.enabled" label="Enable attachment storage" checked={value.enabled} />
    <Field name="storage.endpoint" label="S3 endpoint URL" type="url" defaultValue={value.endpoint ?? ""}
      error={errors["storage.endpoint"]} />
    <div className="field-grid">
      <Field name="storage.bucket" label="Bucket" defaultValue={value.bucket ?? ""} error={errors["storage.bucket"]} />
      <Field name="storage.region" label="Region" defaultValue={value.region ?? ""} error={errors["storage.region"]} />
      <SecretField name="storage.accessKey" label="Access key" configured={value.accessKeyConfigured}
        error={errors["storage.accessKey"]} />
      <SecretField name="storage.secretKey" label="Secret key" configured={value.secretKeyConfigured}
        error={errors["storage.secretKey"]} />
    </div>
  </>;
}

const labels: Record<Status["state"], string> = {
  DISABLED: "Disabled", CONFIGURED: "Configured", CONNECTING: "Connecting",
  CONNECTED: "Connected", UNAVAILABLE: "Unavailable",
};

const storageCheckTime = new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" });

export function IntegrationStatus({ configuration, busy, retry }: {
  configuration: Configuration;
  busy: boolean;
  retry: () => void;
}) {
  const [poll, setPoll] = useState<{ source: Configuration; latest?: Configuration; failed: boolean }>();
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    let stopped = false;
    let timer = window.setTimeout(refresh, STATUS_POLL_INTERVAL_MS);

    async function refresh() {
      try {
        const latest = await adminRequest<Configuration>("configuration");
        if (!stopped) setPoll({ source: configuration, latest, failed: false });
      } catch {
        if (!stopped) setPoll(previous => ({
          source: configuration,
          latest: previous?.source === configuration ? previous.latest : undefined,
          failed: true,
        }));
      } finally {
        if (!stopped) timer = window.setTimeout(refresh, STATUS_POLL_INTERVAL_MS);
      }
    }

    return () => {
      stopped = true;
      window.clearTimeout(timer);
    };
  }, [configuration]);

  const currentPoll = poll?.source === configuration ? poll : undefined;
  const overview = currentPoll?.latest ?? configuration;
  const integrations: { title: string; status: Status; verifiedAt?: string | null }[] = [
    { title: "Discord bot", status: overview.discord.status },
    { title: "Discord OAuth", status: {
      state: overview.discord.oauthEnabled ? "CONFIGURED" : "DISABLED",
      message: "Application credentials saved.",
      lastFailure: null,
    } },
    { title: "Twitch", status: overview.twitch.status },
    { title: "Attachment storage", status: overview.storage.status, verifiedAt: overview.storage.verifiedAt },
  ];
  return <Panel title="Integration status">
    <button className="secondary status-details-toggle" type="button" aria-expanded={expanded}
      aria-controls="integration-details" onClick={() => setExpanded(!expanded)}>
      {expanded ? "Hide details" : "Show details"}
    </button>
    <dl className="integrations" id="integration-details" data-expanded={expanded}>
      {integrations.map(({ title, status, verifiedAt }) => <div key={title}>
        <dt>{title}<span className={`status ${verifiedAt ? "verified" : status.state.toLowerCase()}`}>
          {verifiedAt ? "Verified" : labels[status.state]}
        </span></dt>
        {status.state !== "DISABLED" && <dd>{verifiedAt ? <>
          Upload, download, and cleanup passed on{" "}
          <time dateTime={verifiedAt}>{storageCheckTime.format(new Date(verifiedAt))}</time>.
        </> : status.message}</dd>}
        {title === "Discord bot" && status.state === "UNAVAILABLE" &&
          <dd><button className="secondary" type="button" onClick={retry} disabled={busy}>Retry connection</button></dd>}
      </div>)}
    </dl>
    {currentPoll?.failed && <p role="status" className="muted">Could not update status. Retrying…</p>}
  </Panel>;
}

function value(form: FormData, name: string) {
  return String(form.get(name) ?? "");
}

function secret(form: FormData, name: string) {
  const input = value(form, name);
  if (input === SAVED_SECRET_MASK) return { action: "KEEP" };
  const entered = input.replaceAll(MASK_CHARACTER, "");
  if (entered) return { action: "REPLACE", value: entered };
  return { action: form.has(`${name}.configured`) ? "CLEAR" : "KEEP" };
}

export function discordUpdate(form: FormData, setup = false) {
  return {
    botEnabled: setup || form.has("discord.botEnabled"), botToken: secret(form, "discord.botToken"),
    oauthEnabled: form.has("discord.oauthEnabled"), clientId: value(form, "discord.clientId"),
    clientSecret: secret(form, "discord.clientSecret"), callbackUrl: value(form, "discord.callbackUrl"),
  };
}

export function twitchUpdate(form: FormData) {
  return {
    enabled: form.has("twitch.enabled"), clientId: value(form, "twitch.clientId"),
    clientSecret: secret(form, "twitch.clientSecret"), callbackUrl: value(form, "twitch.callbackUrl"),
  };
}

export function deploymentUpdate(form: FormData) {
  return { publicUrl: value(form, "publicUrl") };
}

export function storageUpdate(form: FormData) {
  return {
    enabled: form.has("storage.enabled"), endpoint: value(form, "storage.endpoint"),
    bucket: value(form, "storage.bucket"), region: value(form, "storage.region"),
    accessKey: secret(form, "storage.accessKey"), secretKey: secret(form, "storage.secretKey"),
  };
}

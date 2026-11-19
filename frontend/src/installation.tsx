import { useState } from "react";
import { Field, Panel, Toggle } from "./dashboard";
import type { FieldErrors } from "./dashboard";
import type { Configuration, Status } from "./api";

function SecretField({ name, label, configured, error }: {
  name: string;
  label: string;
  configured: boolean;
  error?: string;
}) {
  const [action, setAction] = useState("KEEP");
  return <div className="secret">
    {configured && <label className="field">
      <span>{label}</span>
      <select name={`${name}.action`} value={action} onChange={event => setAction(event.target.value)}
        aria-invalid={error && action !== "REPLACE" ? true : undefined}
        aria-describedby={error && action !== "REPLACE" ? `${name}-error` : undefined}>
        <option value="KEEP">Keep saved value</option>
        <option value="REPLACE">Replace saved value</option>
        <option value="CLEAR">Clear saved value</option>
      </select>
      {error && action !== "REPLACE" && <span className="field-error" id={`${name}-error`}>{error}</span>}
    </label>}
    {(!configured || action === "REPLACE") &&
      <Field name={name} label={configured ? `New ${label.toLowerCase()}` : label}
        type="password" autoComplete="off" error={error} />}
  </div>;
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

export function IntegrationStatus({ configuration, busy, refresh, retry }: {
  configuration: Configuration;
  busy: boolean;
  refresh: () => void;
  retry: () => void;
}) {
  const integrations: { title: string; status: Status }[] = [
    { title: "Discord bot", status: configuration.discord.status },
    { title: "Discord OAuth", status: {
      state: configuration.discord.oauthEnabled ? "CONFIGURED" : "DISABLED",
      message: "Application credentials saved.",
      lastFailure: null,
    } },
    { title: "Twitch", status: configuration.twitch.status },
    { title: "Attachment storage", status: configuration.storage.status },
  ];
  return <Panel title="Integration status">
    <dl className="integrations">
      {integrations.map(({ title, status }) => <div key={title}>
        <dt>{title}<span className={`status ${status.state.toLowerCase()}`}>{labels[status.state]}</span></dt>
        {status.state !== "DISABLED" && <dd>{status.message}</dd>}
        {title === "Discord bot" && status.state === "UNAVAILABLE" &&
          <dd><button className="secondary" type="button" onClick={retry} disabled={busy}>Retry connection</button></dd>}
      </div>)}
    </dl>
    <button className="secondary" type="button" onClick={refresh} disabled={busy}>Refresh status</button>
  </Panel>;
}

function value(form: FormData, name: string) {
  return String(form.get(name) ?? "");
}

function secret(form: FormData, name: string) {
  const entered = value(form, name);
  return { action: value(form, `${name}.action`) || (entered ? "REPLACE" : "KEEP"), value: entered || undefined };
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

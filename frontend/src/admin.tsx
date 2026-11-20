import { useEffect, useRef, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { adminRequest, ApiError } from "./api";
import type { Configuration, Session } from "./api";
import { DashboardShell, Field, Panel } from "./dashboard";
import type { FieldErrors } from "./dashboard";
import {
  DeploymentFields, DiscordFields, IntegrationStatus, StorageFields, TwitchFields,
  deploymentUpdate, discordUpdate, storageUpdate, twitchUpdate,
} from "./installation";
import "./dashboard.css";

const steps = [
  { step: "PROVIDERS", label: "Providers" },
  { step: "DEPLOYMENT", label: "Deployment" },
  { step: "STORAGE", label: "Storage" },
] as const;
const discordGuide = <>Find the bot token in the <a href="https://discord.com/developers/applications"
  target="_blank" rel="noreferrer">Discord Developer Portal</a>. For public sign-in, register the OAuth callback URL
  under Redirects and enter the application ID and client secret.</>;
const twitchGuide = <>Register an application in the <a href="https://dev.twitch.tv/console/apps"
  target="_blank" rel="noreferrer">Twitch Developer Console</a>. Add the OAuth callback URL there and copy its
  client ID and secret here.</>;
const storageGuide = "Enter the endpoint and credentials from your S3-compatible provider. You can enable storage later.";

function feedback(error: unknown): FieldErrors {
  if (error instanceof ApiError) {
    if (Object.keys(error.fields).length) return error.fields;
    if (error.status === 429) return { form: "Too many attempts. Wait a minute and try again." };
    if (error.status === 401) return { form: "The username or password was rejected, or your session expired." };
    if (error.status === 403) return { form: "The form expired. Reload the console and try again." };
    if (error.status === 409) return { form: "The installation changed in another session. Reload to continue." };
  }
  return { form: "The private console could not complete the request. Try again." };
}

function SaveForm({ label, submit, children, showSaved }: {
  label: string;
  submit: (form: FormData) => Promise<boolean | void>;
  children: (errors: FieldErrors) => ReactNode;
  showSaved?: boolean;
}) {
  const [errors, setErrors] = useState<FieldErrors>({});
  const [pending, setPending] = useState(false);
  const [saves, setSaves] = useState(0);
  const [saved, setSaved] = useState("");

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const submitted = new FormData(form);
    form.querySelectorAll<HTMLInputElement>('input[type="password"]:not([data-provider-secret])')
      .forEach(input => { input.value = ""; });
    setPending(true);
    setSaved("");
    setErrors({});
    try {
      const verified = await submit(submitted);
      setSaves(value => value + 1);
      setSaved(verified ? "Settings saved. Provider checks passed." : "Settings saved");
    } catch (error) {
      setErrors(feedback(error));
      requestAnimationFrame(() => {
        (form.querySelector<HTMLElement>('[aria-invalid="true"]') ??
          form.querySelector<HTMLElement>('[role="alert"]'))?.focus();
      });
    } finally {
      setPending(false);
    }
  }

  const hasErrors = Object.keys(errors).length > 0;
  return <form onSubmit={save}>
    <fieldset key={saves} disabled={pending}>
      {children(errors)}
      {hasErrors && <p role="alert" tabIndex={-1} className="form-error">
        {errors.form ?? errors.setupStep ?? errors.providers ?? "Check the marked fields."}
      </p>}
      <div className="form-actions">
        <button className="primary" type="submit">{pending ? "Working…" : label}</button>
        {saved && showSaved && <span role="status" className="saved">{saved}</span>}
      </div>
    </fieldset>
  </form>;
}

function SignIn({ session, submit }: {
  session: Session;
  submit: (form: FormData) => Promise<void>;
}) {
  return <div className="signin">
    <div className="brand signin-brand"><span className="brand-mark" aria-hidden="true">D</span>
      <div><strong>Discord Bot</strong><span>Private console</span></div>
    </div>
    <h1>{session.claimAvailable ? "Create your administrator account" : "Sign in"}</h1>
    <p className="muted">{session.claimAvailable
      ? "Use this account to finish setup and manage installation settings."
      : "Use your local administrator credentials."}</p>
    <SaveForm label={session.claimAvailable ? "Create account" : "Sign in"} submit={submit}>
      {errors => <>
        <Field name="username" label="Username" autoComplete="username" maxLength={64} error={errors.username} required />
        <Field name="password" label="Password" type="password" maxLength={256} required error={errors.password}
          autoComplete={session.claimAvailable ? "new-password" : "current-password"}
          minLength={session.claimAvailable ? 12 : undefined} />
      </>}
    </SaveForm>
  </div>;
}

function Wizard({ configuration, save }: {
  configuration: Configuration;
  save: (route: string, body: object) => Promise<boolean>;
}) {
  const activeStep = steps.findIndex(({ step }) => step === configuration.setupStep);
  return <>
    <ol className="steps" aria-label="Setup progress">
      {steps.map(({ step, label }, index) => <li key={step} data-done={index < activeStep}
        aria-current={index === activeStep ? "step" : undefined}>
        <span className="step-number" aria-hidden="true">{index + 1}</span>{label}
      </li>)}
    </ol>
    {configuration.setupStep === "PROVIDERS" && <SaveForm label="Save and continue"
      submit={form => save("setup/providers", { discord: discordUpdate(form, true), twitch: twitchUpdate(form) })}>
      {errors => <>
        <Panel title="Discord" description={discordGuide}>
          <DiscordFields value={configuration.discord} errors={errors} setup />
        </Panel>
        <Panel title="Twitch (optional)" description={twitchGuide}>
          <TwitchFields value={configuration.twitch} errors={errors} />
        </Panel>
      </>}
    </SaveForm>}
    {configuration.setupStep === "DEPLOYMENT" && <Panel title="Deployment"
      description="Enter the external address of your public dashboard if you have one. You can set it later.">
      <SaveForm label="Save and continue" submit={form => save("setup/deployment", deploymentUpdate(form))}>
        {errors => <DeploymentFields value={configuration} errors={errors} />}
      </SaveForm>
    </Panel>}
    {configuration.setupStep === "STORAGE" && <Panel title="Attachment storage (optional)" description={storageGuide}>
      <SaveForm label="Finish setup" submit={form => save("setup/storage", storageUpdate(form))}>
        {errors => <StorageFields value={configuration.storage} errors={errors} />}
      </SaveForm>
    </Panel>}
  </>;
}

function Settings({ configuration, save }: {
  configuration: Configuration;
  save: (route: string, body: object) => Promise<boolean>;
}) {
  return <>
    <Panel title="Discord" description={discordGuide}>
      <SaveForm label="Save Discord settings" showSaved submit={form => save("configuration/discord", discordUpdate(form))}>
        {errors => <DiscordFields value={configuration.discord} errors={errors} />}
      </SaveForm>
    </Panel>
    <Panel title="Twitch" description={twitchGuide}>
      <SaveForm label="Save Twitch settings" showSaved submit={form => save("configuration/twitch", twitchUpdate(form))}>
        {errors => <TwitchFields value={configuration.twitch} errors={errors} />}
      </SaveForm>
    </Panel>
    <Panel title="Deployment">
      <SaveForm label="Save public URL" showSaved submit={form => save("configuration/deployment", deploymentUpdate(form))}>
        {errors => <DeploymentFields value={configuration} errors={errors} />}
      </SaveForm>
    </Panel>
    <Panel title="Attachment storage" description={storageGuide}>
      <SaveForm label="Save storage settings" showSaved submit={form => save("configuration/storage", storageUpdate(form))}>
        {errors => <StorageFields value={configuration.storage} errors={errors} />}
      </SaveForm>
    </Panel>
  </>;
}

function AdminConsole() {
  const [session, setSession] = useState<Session>();
  const [configuration, setConfiguration] = useState<Configuration>();
  const [active, setActive] = useState<"installation" | "password">("installation");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const operations = useRef(Promise.resolve());

  async function load() {
    const next = await adminRequest<Session>("session");
    const settings = next.authenticated ? await adminRequest<Configuration>("configuration") : undefined;
    setSession(next);
    setConfiguration(settings);
    setActive("installation");
  }

  async function perform(task: () => Promise<unknown>) {
    setBusy(true);
    setError("");
    try {
      await task();
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) {
        await load().catch(() => setSession(undefined));
      }
      setError(feedback(failure).form ?? "The request could not be completed.");
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => { void perform(load); }, []);

  if (!session) {
    return <main className="signin">
      <h1>Private console</h1>
      {error ? <><p role="alert" className="form-error">{error}</p>
        <button className="primary" type="button" onClick={() => void perform(load)}>Try again</button></>
        : <p role="status">Loading…</p>}
    </main>;
  }
  if (!session.authenticated || !configuration) {
    return <main>
      <SignIn key={String(session.claimAvailable)} session={session} submit={async form => {
        const credentials = new URLSearchParams();
        for (const name of ["username", "password"]) credentials.set(name, String(form.get(name) ?? ""));
        if (session.claimAvailable) {
          try {
            await adminRequest("claim", credentials, session.csrfToken);
          } catch (failure) {
            if (!(failure instanceof ApiError) || failure.status !== 409) throw failure;
          }
        } else {
          await adminRequest("login", credentials, session.csrfToken);
        }
        await load();
      }} />
    </main>;
  }

  function serialize<T>(task: () => Promise<T>) {
    const next = operations.current.then(task);
    operations.current = next.then(() => undefined, () => undefined);
    return next;
  }

  function save(route: string, body: object) {
    return serialize(async () => {
      try {
        const updated = await adminRequest<Configuration>(route, JSON.stringify(body), session!.csrfToken);
        setConfiguration(updated);
        return Object.keys(updated.providerChecks ?? {}).length > 0;
      } catch (failure) {
        if (failure instanceof ApiError && failure.status === 401) await load();
        throw failure;
      }
    });
  }

  function endSession(route: string, body = new URLSearchParams()) {
    return serialize(async () => {
      await adminRequest(route, body, session!.csrfToken);
      await load();
    });
  }

  const setup = configuration.setupStep !== "COMPLETE";
  const title = active === "password" ? "Change password" : setup ? "Installation setup" : "Installation settings";
  return <DashboardShell context="Private console"
    navigation={<>
      <button aria-current={active === "installation" ? "page" : undefined}
        onClick={() => setActive("installation")}>{setup ? "Setup" : "Settings"}</button>
      <button aria-current={active === "password" ? "page" : undefined}
        onClick={() => setActive("password")}>Password</button>
    </>}
    account={<><span>{session.username}</span>
      <button onClick={() => void perform(() => endSession("logout"))} disabled={busy}>Sign out</button></>}>
    <header className="heading"><h1>{title}</h1></header>
    {error && <p role="alert" className="form-error">{error}</p>}
    {active === "password" ? <Panel title="Local administrator"
      description="Changing your password signs out all local administrator sessions.">
      <SaveForm label="Change password" submit={form => {
        const body = new URLSearchParams();
        for (const name of ["currentPassword", "newPassword"]) body.set(name, String(form.get(name) ?? ""));
        return endSession("password", body);
      }}>
        {errors => <>
          <Field name="currentPassword" label="Current password" type="password" autoComplete="current-password"
            required maxLength={256} error={errors.currentPassword} />
          <Field name="newPassword" label="New password" type="password" autoComplete="new-password"
            required minLength={12} maxLength={256} error={errors.newPassword} />
        </>}
      </SaveForm>
    </Panel> : <div className="content-grid">
      <div>{setup ? <Wizard configuration={configuration} save={save} />
        : <Settings configuration={configuration} save={save} />}</div>
      <div><IntegrationStatus configuration={configuration} busy={busy}
        retry={() => void perform(() => save("configuration/discord/retry", {}))} /></div>
    </div>}
  </DashboardShell>;
}

createRoot(document.getElementById("root")!).render(<AdminConsole />);

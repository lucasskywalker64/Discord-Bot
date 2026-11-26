import { useEffect, useRef, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { adminRequest, ApiError } from "./api";
import { DashboardAccounts } from "./accounts";
import type { Configuration, Session } from "./api";
import { DashboardShell, Field, Panel } from "./dashboard";
import type { FieldErrors } from "./dashboard";
import {
  DiscordFields, IntegrationStatus, ServerFields, StorageFields, TwitchFields,
  discordUpdate, serverUpdate, storageUpdate, twitchUpdate,
} from "./installation";
import "./dashboard.css";

const steps = [
  { step: "SERVER", label: "Server" },
  { step: "PROVIDERS", label: "Providers" },
  { step: "STORAGE", label: "Storage" },
] as const;
const serverGuide = "Use the address people open to reach your public dashboard, such as https://bot.example.com. "
  + "Required for public Discord sign-in and Twitch. Leave it blank for a bot-only installation.";
const discordGuide = <>Find your token on your application's <strong>Bot</strong> page in the
  {" "}<a href="https://discord.com/developers/applications" target="_blank" rel="noreferrer">Discord Developer Portal</a>.
  For public sign-in, open <strong>OAuth2</strong>, register the exact callback URL below under <strong>Redirects</strong>,
  and enter your application ID and client secret here.</>;
const twitchGuide = <>Open your application in the <a href="https://dev.twitch.tv/console/apps"
  target="_blank" rel="noreferrer">Twitch Developer Console</a>. Add the exact callback URL below under
  {" "}<strong>OAuth Redirect URLs</strong>, then enter its client ID and secret here.</>;
const storageGuide = "Enter the endpoint and credentials from your S3-compatible provider. "
  + "Leave the region blank to use auto. "
  + "Saving enabled storage uploads, reads and deletes a temporary test object. You can enable storage later.";

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

function hasUnsavedChanges(form: HTMLFormElement) {
  const fields = form.querySelectorAll<HTMLInputElement>('input[name]:not([readonly]):not([type="hidden"])');
  return Array.from(fields).some(field => field.type === "checkbox"
    ? field.checked !== field.defaultChecked
    : field.value !== field.defaultValue);
}

function SaveForm({ label, submit, children, showSaved, submitDisabled = false, requireChanges = false }: {
  label: string;
  submit: (form: FormData) => Promise<boolean | void>;
  children: (errors: FieldErrors) => ReactNode;
  showSaved?: boolean;
  submitDisabled?: boolean;
  requireChanges?: boolean;
}) {
  const [errors, setErrors] = useState<FieldErrors>({});
  const [pending, setPending] = useState(false);
  const [saves, setSaves] = useState(0);
  const [saved, setSaved] = useState("");
  const [changed, setChanged] = useState(false);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    if (submitDisabled || (requireChanges && !hasUnsavedChanges(form))) return;
    const submitted = new FormData(form);
    form.querySelectorAll<HTMLInputElement>('input[type="password"]:not([data-provider-secret])')
      .forEach(input => { input.value = ""; });
    setPending(true);
    setSaved("");
    setErrors({});
    try {
      const verified = await submit(submitted);
      setChanged(false);
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
  const disabled = submitDisabled || (requireChanges && !changed);
  return <form onSubmit={save}
    onChange={requireChanges ? event => setChanged(hasUnsavedChanges(event.currentTarget)) : undefined}>
    <fieldset key={saves} disabled={pending}>
      {children(errors)}
      {hasErrors && <p role="alert" tabIndex={-1} className="form-error">
        {errors.form ?? errors.setupStep ?? errors.providers ?? "Check the marked fields."}
      </p>}
      <div className="form-actions">
        <button className="primary" type="submit" disabled={disabled}>{pending ? "Working…" : label}</button>
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
        <Field name="username" label="Username" autoComplete="username" maxLength={64} error={errors.username}
          required showRequiredLabel={session.claimAvailable} />
        <Field name="password" label="Password" type="password" maxLength={256} required error={errors.password}
          autoComplete={session.claimAvailable ? "new-password" : "current-password"}
          minLength={session.claimAvailable ? 12 : undefined} showRequiredLabel={session.claimAvailable} />
      </>}
    </SaveForm>
  </div>;
}

function Wizard({ configuration, save }: {
  configuration: Configuration;
  save: (route: string, body: object) => Promise<boolean>;
}) {
  const [currentStep, setCurrentStep] = useState(configuration.setupStep);
  const nextRequiredStep = steps.findIndex(({ step }) => step === configuration.setupStep);
  const canFinish = configuration.setupStep === "STORAGE";

  async function saveAndContinue(route: string, body: object, nextStep: "PROVIDERS" | "STORAGE") {
    const verified = await save(route, body);
    setCurrentStep(step => step === currentStep ? nextStep : step);
    return verified;
  }

  return <>
    <ol className="steps" aria-label="Setup progress">
      {steps.map(({ step, label }, index) => <li key={step} data-done={index < nextRequiredStep}>
        <button type="button" aria-current={step === currentStep ? "step" : undefined}
          onClick={() => setCurrentStep(step)}>
          <span className="step-number" aria-hidden="true">{index + 1}</span>{label}
        </button>
      </li>)}
    </ol>
    <div hidden={currentStep !== "SERVER"}>
      <Panel title="Server" description={serverGuide}>
        <SaveForm label="Save and continue"
          submit={form => saveAndContinue("setup/deployment", serverUpdate(form), "PROVIDERS")}>
          {errors => <ServerFields value={configuration} errors={errors} />}
        </SaveForm>
      </Panel>
    </div>
    <div hidden={currentStep !== "PROVIDERS"}>
      <SaveForm label="Save and continue"
        submit={form => saveAndContinue("setup/providers",
          { discord: discordUpdate(form, true), twitch: twitchUpdate(form) }, "STORAGE")}>
        {errors => <>
          <Panel title="Discord" description={discordGuide}>
            <DiscordFields value={configuration.discord} errors={errors} setup />
          </Panel>
          <Panel title="Twitch (optional)" description={twitchGuide}>
            <TwitchFields value={configuration.twitch} errors={errors} />
          </Panel>
        </>}
      </SaveForm>
    </div>
    <div hidden={currentStep !== "STORAGE"}>
      <Panel title="Attachment storage (optional)" description={storageGuide}>
        <SaveForm label="Finish setup" submitDisabled={!canFinish}
          submit={form => save("setup/storage", storageUpdate(form))}>
          {errors => <>
            <StorageFields value={configuration.storage} errors={errors} />
            {!canFinish && <p className="muted">Save the required provider settings before finishing setup.</p>}
          </>}
        </SaveForm>
      </Panel>
    </div>
  </>;
}

type AuthorizationTest = {
  status: "IDLE" | "PENDING" | "SUCCEEDED" | "REJECTED" | "EXPIRED";
  launchUrl: string | null;
};

const authorizationMessages = {
  IDLE: "",
  PENDING: "Waiting for Discord authorization.",
  SUCCEEDED: "Discord returned an authorization code and the application exchanged it successfully.",
  REJECTED: "Discord authorization did not complete. Check the saved settings and try again.",
  EXPIRED: "The test expired. Start a new test.",
};
const authorizationTestPath = "discord-authorization-test";

function DiscordAuthorizationTest({ csrf }: { csrf: string }) {
  const [test, setTest] = useState<AuthorizationTest>();
  const [launchUrl, setLaunchUrl] = useState<string>();
  const [error, setError] = useState("");
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    let active = true;
    let timer: number | undefined;
    async function refresh() {
      try {
        const next = await adminRequest<AuthorizationTest>(authorizationTestPath);
        if (!active) return;
        setTest(next);
        if (next.status === "PENDING") timer = window.setTimeout(() => void refresh(), 1500);
        else setLaunchUrl(undefined);
      } catch {
        if (active) setError("The authorization test status could not be loaded.");
      }
    }
    if (!starting) void refresh();
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [starting]);

  async function start() {
    setStarting(true);
    setError("");
    try {
      const next = await adminRequest<AuthorizationTest>(authorizationTestPath, undefined, csrf);
      setTest(next);
      setLaunchUrl(next.launchUrl ?? undefined);
    } catch (failure) {
      setError(failure instanceof ApiError && failure.status === 409
        ? "Save enabled Discord OAuth settings with the dashboard callback URL before testing."
        : "The authorization test could not be started.");
    } finally {
      setStarting(false);
    }
  }

  return <>
    <div className="form-actions">
      <button type="button" disabled={starting} onClick={() => void start()}>Test authorization</button>
      {launchUrl && <a href={launchUrl} target="_blank" rel="noreferrer"
        onClick={() => setLaunchUrl(undefined)}>Continue to Discord</a>}
    </div>
    <p className="muted" role="status">{test && authorizationMessages[test.status]}</p>
    {error && <p className="form-error" role="alert">{error}</p>}
  </>;
}

function Settings({ configuration, save, csrf }: {
  configuration: Configuration;
  save: (route: string, body: object) => Promise<boolean>;
  csrf: string;
}) {
  return <>
    <Panel title="Server" description={serverGuide + " If you change this URL, update the redirects in each enabled provider."}>
      <SaveForm label="Save public base URL" showSaved requireChanges
        submit={form => save("configuration/deployment", serverUpdate(form))}>
        {errors => <ServerFields value={configuration} errors={errors} />}
      </SaveForm>
    </Panel>
    <Panel title="Discord" description={discordGuide}>
      <SaveForm label="Save Discord settings" showSaved requireChanges
        submit={form => save("configuration/discord", discordUpdate(form))}>
        {errors => <DiscordFields value={configuration.discord} errors={errors} />}
      </SaveForm>
      {configuration.discord.oauthEnabled && <DiscordAuthorizationTest csrf={csrf} />}
    </Panel>
    <Panel title="Twitch" description={twitchGuide}>
      <SaveForm label="Save Twitch settings" showSaved requireChanges
        submit={form => save("configuration/twitch", twitchUpdate(form))}>
        {errors => <TwitchFields value={configuration.twitch} errors={errors} />}
      </SaveForm>
    </Panel>
    <Panel title="Attachment storage" description={storageGuide}>
      <SaveForm label="Save storage settings" showSaved requireChanges
        submit={form => save("configuration/storage", storageUpdate(form))}>
        {errors => <StorageFields value={configuration.storage} errors={errors} />}
      </SaveForm>
    </Panel>
  </>;
}

function AdminConsole() {
  const [session, setSession] = useState<Session>();
  const [configuration, setConfiguration] = useState<Configuration>();
  const [active, setActive] = useState<"installation" | "accounts" | "password">("installation");
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
  const installationTitle = setup ? "Installation setup" : "Installation settings";
  const title = active === "accounts" ? "Dashboard accounts"
    : active === "password" ? "Change password" : installationTitle;
  return <DashboardShell context="Private console"
    navigation={<>
      <button aria-current={active === "installation" ? "page" : undefined}
        onClick={() => setActive("installation")}>{setup ? "Setup" : "Settings"}</button>
      <button aria-current={active === "accounts" ? "page" : undefined}
        onClick={() => setActive("accounts")}>Accounts</button>
      <button aria-current={active === "password" ? "page" : undefined}
        onClick={() => setActive("password")}>Password</button>
    </>}
    account={<><span className="account-name">{session.username}</span>
      <button onClick={() => void perform(() => endSession("logout"))} disabled={busy}>Sign out</button></>}>
    <header className="heading"><h1>{title}</h1></header>
    {error && <p role="alert" className="form-error">{error}</p>}
    {active === "accounts" ? <DashboardAccounts csrf={session.csrfToken} /> : active === "password" ? <Panel title="Local administrator"
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
      <div className="status-overview"><IntegrationStatus configuration={configuration} busy={busy}
        retry={() => void perform(() => save("configuration/discord/retry", {}))} /></div>
      <div className="settings-column">{setup ? <Wizard configuration={configuration} save={save} />
        : <Settings configuration={configuration} save={save} csrf={session.csrfToken} />}</div>
    </div>}
  </DashboardShell>;
}

createRoot(document.getElementById("root")!).render(<AdminConsole />);

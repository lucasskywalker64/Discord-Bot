import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { DashboardShell, Field, Panel } from "./dashboard";
import "./dashboard.css";

type Session = { authenticated: boolean; discordAvailable: boolean; recent: boolean; csrfToken: string };
type Account = { id: string; discordId: string; discordUsername: string; hasRecoveryCode: boolean };
type Identity = { id: string; username: string };
type Replacement = { confirmation: string; current: Identity; proposed: Identity };

const outcomes = new Map([
  ["expired", "This Discord authorization attempt expired or was already used. Start again."],
  ["rejected", "Discord authorization did not complete. Try again."],
  ["unavailable", "Discord sign-in is unavailable. Contact the installation administrator."],
  ["reauthenticate", "Verify your current Discord login before replacing it."],
  ["conflict", "That Discord identity is already linked to a dashboard account."],
  ["recovery_invalid", "That recovery code or link is invalid or has expired."],
  ["recovery_signed_in", "Sign out before starting recovery, then reopen the link or enter your recovery code."],
]);

async function dashboardGet<T>(endpoint: string): Promise<T> {
  const response = await fetch(`/dashboard/api/${endpoint}`);
  if (!response.ok) throw new Error("The dashboard could not be loaded.");
  return response.json();
}

function PublicDashboard() {
  const [session, setSession] = useState<Session>();
  const [account, setAccount] = useState<Account>();
  const [replacement, setReplacement] = useState<Replacement>();
  const [recoveryCode, setRecoveryCode] = useState("");
  const [linkCode] = useState(() => new URLSearchParams(location.hash.slice(1)).get("recovery"));
  const [showRecovery, setShowRecovery] = useState(() =>
    linkCode !== null || new URLSearchParams(location.search).get("oauth") === "recovery_invalid");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [result] = useState(() => new URLSearchParams(location.search).get("oauth"));
  const isRecovery = result === "recovery_confirm";
  const outcome = outcomes.get(result ?? "");

  async function load() {
    const next = await dashboardGet<Session>("session");
    setAccount(next.authenticated ? await dashboardGet<Account>("account") : undefined);
    setSession(next);
    if (result === "confirm" || isRecovery) {
      if (result === "confirm" && !next.authenticated) {
        setError("Sign in to start a new replacement.");
        return;
      }
      const response = await fetch(isRecovery ? "/dashboard/api/recovery" : "/dashboard/api/replacement");
      if (response.ok) setReplacement(await response.json());
      else setError(outcomes.get("expired")!);
    }
  }

  useEffect(() => {
    if (linkCode !== null) history.replaceState(null, "", location.pathname + location.search);
    void load().catch(() => setError("The dashboard could not be loaded."));
  }, []);

  async function logout() {
    const response = await fetch("/dashboard/api/logout", {
      method: "POST",
      headers: { "X-CSRF-TOKEN": session!.csrfToken },
    });
    if (!response.ok && response.status !== 401) throw new Error("Sign out failed.");
    location.replace("/dashboard/");
  }

  async function confirm() {
    setSaving(true);
    try {
      const response = await fetch(isRecovery ? "/dashboard/api/confirm-recovery" : "/dashboard/api/confirm-replacement", {
        method: "POST",
        headers: { "X-CSRF-TOKEN": session!.csrfToken },
        body: new URLSearchParams({ confirmation: replacement!.confirmation }),
      });
      switch (response.status) {
        case 204: location.replace(isRecovery ? "/dashboard/?oauth=recovered" : "/dashboard/"); break;
        case 409: location.replace("/dashboard/?oauth=conflict"); break;
        case 410: location.replace("/dashboard/?oauth=expired"); break;
        case 401:
        case 428: location.replace("/dashboard/?oauth=reauthenticate"); break;
        default: setError("The replacement could not be confirmed. Try again.");
      }
    } catch {
      setError("The replacement could not be confirmed. Try again.");
    } finally {
      setSaving(false);
    }
  }

  async function generateRecoveryCode() {
    setSaving(true);
    setError("");
    try {
      const response = await fetch("/dashboard/api/recovery-code", {
        method: "POST",
        headers: { "X-CSRF-TOKEN": session!.csrfToken },
      });
      if (response.status === 401 || response.status === 428) {
        location.replace("/dashboard/?oauth=reauthenticate");
        return;
      }
      if (!response.ok) throw new Error();
      const { code } = await response.json() as { code: string };
      setRecoveryCode(code);
      setAccount(current => current && { ...current, hasRecoveryCode: true });
    } catch {
      setError("The recovery code could not be generated. Try again.");
    } finally {
      setSaving(false);
    }
  }

  return <DashboardShell context="Public dashboard" navigation={null} account={account && <>
    <span className="account-name">{account.discordUsername}</span>
    <button onClick={() => void logout().catch(() => setError("Sign out failed. Try again."))}>Sign out</button>
  </>}>
    <header className="heading"><h1>{account ? "Your account" : "Discord dashboard"}</h1></header>
    {(error || outcome) && <p className="form-error" role="alert">{error || outcome}</p>}
    {result === "recovered" && <p className="muted" role="status">Discord login recovered. Sign in with the new identity.</p>}
    {account && linkCode !== null &&
      <p className="form-error" role="alert">Sign out and reopen the recovery link to continue.</p>}
    {session ? replacement ? <Panel title={isRecovery ? "Recover Discord login" : "Replace Discord login"}>
        <p>Current login <strong>{replacement.current.username}</strong> ({replacement.current.id})</p>
        <p>Proposed login <strong>{replacement.proposed.username}</strong> ({replacement.proposed.id})</p>
        <p className="muted">Confirming signs out your old dashboard sessions.</p>
        <div className="form-actions">
          <button className="primary" disabled={saving} onClick={() => void confirm()}>Confirm Discord login</button>
          <form method="post" action={isRecovery ? "/dashboard/api/discard-recovery" : "/dashboard/api/discard-replacement"}>
            <input type="hidden" name="_csrf" value={session.csrfToken} />
            <input type="hidden" name="confirmation" value={replacement.confirmation} />
            <button type="submit" disabled={saving}>Cancel</button>
          </form>
        </div>
    </Panel> : account ? <>
      <Panel title="Discord login">
        <p className="muted">{account.discordUsername} ({account.discordId})</p>
        {session.discordAvailable && (session.recent
          ? <DiscordAction action="replace-discord" csrf={session.csrfToken} label="Replace Discord login" />
          : <DiscordAction action="reauthenticate" csrf={session.csrfToken} label="Verify current Discord login" />)}
      </Panel>
      <Panel title="Recovery code" description="Save a recovery code somewhere safe so you can recover your dashboard account if you lose access to Discord.">
        {recoveryCode ? <>
          <Field name="new-recovery-code" label="Save this code now" value={recoveryCode} readOnly autoComplete="off" />
          <button type="button" onClick={() => setRecoveryCode("")}>Done</button>
        </> : session.recent
          ? <button type="button" disabled={saving} onClick={() => void generateRecoveryCode()}>
            {account.hasRecoveryCode ? "Replace recovery code" : "Generate recovery code"}
          </button>
          : <p className="muted">Verify your current Discord login to generate a recovery code.</p>}
        {!recoveryCode && account.hasRecoveryCode && session.recent &&
          <p className="muted">Generating a new code invalidates the previous one.</p>}
      </Panel>
    </> : <Panel title={showRecovery ? "Recover Discord login" : "Sign in"}>
      {session.discordAvailable
        ? showRecovery ? <>
          <form method="post" action="/dashboard/api/recover">
            <input type="hidden" name="_csrf" value={session.csrfToken} />
            {linkCode === null
              ? <Field name="credential" label="Recovery code" autoComplete="off" required maxLength={64} />
              : <input type="hidden" name="credential" value={linkCode} />}
            <p className="muted">Continue with the Discord identity you want to use for this account.</p>
            <div className="form-actions">
              <button className="primary" type="submit">Continue with Discord</button>
              <a href="/dashboard/">Back to sign-in</a>
            </div>
          </form>
        </> : <>
          <DiscordAction action="sign-in" csrf={session.csrfToken} label="Sign in with Discord" />
          <div className="form-actions">
            <button type="button" onClick={() => setShowRecovery(true)}>Use a recovery code</button>
          </div>
        </>
        : <p>Discord sign-in is currently unavailable.</p>}
    </Panel> : !error && <p role="status">Loading…</p>}
  </DashboardShell>;
}

function DiscordAction({ action, csrf, label }: { action: string; csrf: string; label: string }) {
  return <form method="post" action={`/dashboard/api/${action}`}>
    <input type="hidden" name="_csrf" value={csrf} />
    <button className="primary" type="submit">{label}</button>
  </form>;
}

createRoot(document.getElementById("root")!).render(<PublicDashboard />);

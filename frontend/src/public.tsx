import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import { DashboardShell, Panel } from "./dashboard";
import "./dashboard.css";

type Session = { authenticated: boolean; discordAvailable: boolean; recent: boolean; csrfToken: string };
type Account = { id: string; discordId: string; discordUsername: string };
type Identity = { id: string; username: string };
type Replacement = { confirmation: string; current: Identity; proposed: Identity };

const outcomes = new Map([
  ["expired", "This Discord authorization attempt expired or was already used. Start again."],
  ["rejected", "Discord authorization did not complete. Try again."],
  ["unavailable", "Discord sign-in is unavailable. Contact the installation administrator."],
  ["reauthenticate", "Verify your current Discord login before replacing it."],
  ["conflict", "That Discord identity is already linked to a dashboard account."],
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
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [result] = useState(() => new URLSearchParams(location.search).get("oauth"));
  const outcome = outcomes.get(result ?? "");

  async function load() {
    const next = await dashboardGet<Session>("session");
    setAccount(next.authenticated ? await dashboardGet<Account>("account") : undefined);
    setSession(next);
    if (result === "confirm") {
      if (!next.authenticated) {
        setError("Sign in to start a new replacement.");
        return;
      }
      const response = await fetch("/dashboard/api/replacement");
      if (response.ok) setReplacement(await response.json());
      else setError(outcomes.get("expired")!);
    }
  }

  useEffect(() => {
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
      const response = await fetch("/dashboard/api/confirm-replacement", {
        method: "POST",
        headers: { "X-CSRF-TOKEN": session!.csrfToken },
        body: new URLSearchParams({ confirmation: replacement!.confirmation }),
      });
      switch (response.status) {
        case 204: location.replace("/dashboard/"); break;
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

  return <DashboardShell context="Public dashboard" navigation={null} account={account && <>
    <span className="account-name">{account.discordUsername}</span>
    <button onClick={() => void logout().catch(() => setError("Sign out failed. Try again."))}>Sign out</button>
  </>}>
    <header className="heading"><h1>{account ? "Your account" : "Discord dashboard"}</h1></header>
    {(error || outcome) && <p className="form-error" role="alert">{error || outcome}</p>}
    {session ? account ? <Panel title="Discord login">
      {replacement ? <>
        <p>Current login <strong>{replacement.current.username}</strong> ({replacement.current.id})</p>
        <p>Proposed login <strong>{replacement.proposed.username}</strong> ({replacement.proposed.id})</p>
        <p className="muted">Changing your Discord identity signs out your other dashboard sessions.</p>
        <div className="form-actions">
          <button className="primary" disabled={saving} onClick={() => void confirm()}>Confirm Discord login</button>
          <form method="post" action="/dashboard/api/discard-replacement">
            <input type="hidden" name="_csrf" value={session.csrfToken} />
            <input type="hidden" name="confirmation" value={replacement.confirmation} />
            <button type="submit" disabled={saving}>Cancel</button>
          </form>
        </div>
      </> : <>
        <p className="muted">{account.discordUsername} ({account.discordId})</p>
        {session.discordAvailable && (session.recent
          ? <DiscordAction action="replace-discord" csrf={session.csrfToken} label="Replace Discord login" />
          : <DiscordAction action="reauthenticate" csrf={session.csrfToken} label="Verify current Discord login" />)}
      </>}
    </Panel> : <Panel title="Sign in">
      {session.discordAvailable
        ? <DiscordAction action="sign-in" csrf={session.csrfToken} label="Sign in with Discord" />
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

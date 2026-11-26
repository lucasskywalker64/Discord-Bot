import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { adminRequest, ApiError } from "./api";
import { Field, Panel } from "./dashboard";

type DashboardAccount = {
  id: string;
  discordId: string;
  discordUsername: string;
  loginVersion: number;
  primaryTwitchId: string | null;
  managedGuildId: string | null;
  recoveryLinkExpiresAt: string | null;
};

type AccountPage = { accounts: DashboardAccount[]; hasMore: boolean };
type RecoveryLink = { url: string; expiresAt: string };

export function DashboardAccounts({ csrf }: { csrf: string }) {
  const [results, setResults] = useState<AccountPage>();
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [refresh, setRefresh] = useState(0);
  const [selected, setSelected] = useState<DashboardAccount>();
  const [link, setLink] = useState<RecoveryLink>();
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    const params = new URLSearchParams({ query, page: String(page) });
    adminRequest<AccountPage>(`accounts?${params}`).then(data => {
      if (active) setResults(data);
    }).catch(() => {
      if (active) setError("Dashboard accounts could not be loaded.");
    });
    return () => { active = false; };
  }, [query, page, refresh]);

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setQuery(String(new FormData(event.currentTarget).get("query") ?? "").trim());
    setRefresh(current => current + 1);
    setError("");
  }

  async function issue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    form.reset();
    setBusy(true);
    setError("");
    try {
      const created = await adminRequest<RecoveryLink>(`accounts/${selected.id}/recovery-link`,
        JSON.stringify({ password: data.get("password"), reason: data.get("reason"),
          loginVersion: selected.loginVersion }), csrf);
      setLink(created);
      setRefresh(current => current + 1);
    } catch (failure) {
      const status = failure instanceof ApiError ? failure.status : 0;
      if (status === 412) {
        setSelected(undefined);
        setRefresh(current => current + 1);
        setError("This account's Discord login changed. Verify ownership again.");
      } else setError(status === 401 ? "Check your local administrator password and try again."
        : status === 429 ? "Too many password checks. Try again in a minute."
          : status === 409 ? "Save the Discord OAuth settings before issuing a recovery link."
            : "The recovery link could not be issued.");
    } finally {
      setBusy(false);
    }
  }

  async function revoke(account: DashboardAccount) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await adminRequest(`accounts/${account.id}/revoke-recovery-link`, undefined, csrf);
      if (selected?.id === account.id) {
        setSelected(undefined);
        setLink(undefined);
      }
      setNotice("Recovery link revoked.");
      setRefresh(current => current + 1);
    } catch {
      setError("The recovery link could not be revoked.");
    } finally {
      setBusy(false);
    }
  }

  return <>
    {error && <p className="form-error" role="alert">{error}</p>}
    {notice && <p className="muted" role="status">{notice}</p>}
    {selected && <Panel title="Issue recovery link"
      description={`${selected.discordUsername} (${selected.discordId})`}>
      {link ? <>
        <Field name="issued-recovery-link" label="Recovery link" value={link.url} readOnly autoComplete="off" />
        <p className="muted">Share privately with the verified account owner. Expires {new Date(link.expiresAt).toLocaleString()}.</p>
        <button type="button" className="secondary" onClick={() => { setLink(undefined); setSelected(undefined); }}>Done</button>
      </> : <form onSubmit={issue}>
        <p className="muted">Whoever receives the link can replace this account's Discord login. Verify ownership before issuing it.</p>
        <Field name="reason" label="How did you verify ownership?" required maxLength={300} />
        <Field name="password" label="Current administrator password" type="password" autoComplete="current-password"
          required maxLength={256} />
        {selected.recoveryLinkExpiresAt && <p className="muted">Issuing a new link revokes the existing link.</p>}
        <div className="form-actions">
          <button type="submit" className="primary" disabled={busy}>Issue recovery link</button>
          <button type="button" className="secondary" disabled={busy} onClick={() => setSelected(undefined)}>Cancel</button>
        </div>
      </form>}
    </Panel>}
    <Panel title="Registered accounts">
      <form onSubmit={search}>
        <Field name="query" label="Discord username or ID" type="search" maxLength={64} />
        <button type="submit" className="secondary">Search accounts</button>
      </form>
      {results ? results.accounts.length > 0 ? <div className="account-table">
        <table>
          <thead><tr><th scope="col">Discord</th><th scope="col">Twitch ID</th><th scope="col">Guild ID</th>
            <th scope="col">Recovery</th></tr></thead>
          <tbody>{results.accounts.map(account => <tr key={account.id}>
            <th scope="row">{account.discordUsername}<span className="muted">{account.discordId}</span></th>
            <td>{account.primaryTwitchId ?? "None"}</td>
            <td>{account.managedGuildId ?? "None"}</td>
            <td>
              <button type="button" className="secondary" disabled={busy} onClick={() => {
                setNotice("");
                setError("");
                setSelected(account);
                setLink(undefined);
              }}>Issue recovery link</button>
              {account.recoveryLinkExpiresAt && <button type="button" className="secondary" disabled={busy}
                onClick={() => void revoke(account)}>Revoke recovery link</button>}
            </td>
          </tr>)}</tbody>
        </table>
      </div> : <p className="muted">No accounts found.</p> : !error && <p role="status">Loading accounts…</p>}
      {(page > 0 || results?.hasMore) && <div className="form-actions">
        <button type="button" className="secondary" disabled={page === 0}
          onClick={() => setPage(current => current - 1)}>Previous page</button>
        <button type="button" className="secondary" disabled={!results?.hasMore}
          onClick={() => setPage(current => current + 1)}>Next page</button>
      </div>}
    </Panel>
  </>;
}

import type { ComponentPropsWithoutRef, ReactNode } from "react";

export function DashboardShell({ context, navigation, account, children }: {
  context: string;
  navigation: ReactNode;
  account: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="dashboard">
      <a className="skip-link" href="#main">Skip to content</a>
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark" aria-hidden="true">D</span>
          <div><strong>Discord Bot</strong><span>{context}</span></div>
        </div>
        <nav aria-label="Dashboard">{navigation}</nav>
        <div className="account">{account}</div>
      </aside>
      <main id="main" className="main" tabIndex={-1}>{children}</main>
    </div>
  );
}

export function Panel({ title, description, children }: {
  title: string;
  description?: ReactNode;
  children: ReactNode;
}) {
  return <section className="panel">
    <h2>{title}</h2>
    {description && <p className="muted">{description}</p>}
    {children}
  </section>;
}

export type FieldErrors = Record<string, string>;

export function Field({ label, error, name, ...input }: ComponentPropsWithoutRef<"input"> & {
  label: string;
  name: string;
  error?: string;
}) {
  return <label className="field" htmlFor={name}>
    <span>{label}</span>
    <input {...input} id={name} name={name} aria-invalid={error ? true : undefined}
      aria-describedby={error ? `${name}-error` : undefined} />
    {error && <span className="field-error" id={`${name}-error`}>{error}</span>}
  </label>;
}

export function Toggle({ name, label, checked }: { name: string; label: string; checked: boolean }) {
  return <label className="toggle"><input type="checkbox" name={name} defaultChecked={checked} />{label}</label>;
}

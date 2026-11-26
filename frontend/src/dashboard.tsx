import type { ComponentPropsWithoutRef, ReactNode } from "react";

const themeKey = "discord-bot-theme";

function storedTheme() {
  try {
    const theme = localStorage.getItem(themeKey);
    if (theme === "light" || theme === "dark") return theme;
  } catch {
    // The system theme remains available when storage is blocked.
  }
  return "system";
}

document.documentElement.dataset.theme = storedTheme();

function ThemeControl() {
  return <label className="theme-control">
    Theme
    <select defaultValue={document.documentElement.dataset.theme} onChange={event => {
      const theme = event.currentTarget.value;
      document.documentElement.dataset.theme = theme;
      try {
        localStorage.setItem(themeKey, theme);
      } catch {
        // Apply the preference to the current page even when it cannot be saved.
      }
    }}>
      <option value="system">System</option>
      <option value="light">Light</option>
      <option value="dark">Dark</option>
    </select>
  </label>;
}

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
        <div className="account">
          <ThemeControl />
          <div className="account-actions">{account}</div>
        </div>
      </aside>
      <main id="main" className="main" tabIndex={-1}><div className="main-content">{children}</div></main>
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

export function Field({ label, error, name, required, ...input }: ComponentPropsWithoutRef<"input"> & {
  label: string;
  name: string;
  error?: string;
}) {
  return <label className="field" htmlFor={name}>
    <span>{label}{required && <span className="field-required" aria-hidden="true"> (required)</span>}</span>
    <input {...input} id={name} name={name} required={required} aria-invalid={error ? true : undefined}
      aria-describedby={error ? `${name}-error` : undefined} />
    {error && <span className="field-error" id={`${name}-error`}>{error}</span>}
  </label>;
}

export function Toggle({ name, label, checked, onChange }: {
  name: string; label: string; checked: boolean; onChange: (checked: boolean) => void;
}) {
  return <label className="toggle">
    <input type="checkbox" name={name} checked={checked} onChange={({ currentTarget }) => onChange(currentTarget.checked)} />
    {label}
  </label>;
}

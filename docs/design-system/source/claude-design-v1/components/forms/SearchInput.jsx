import React from "react";

export function SearchInput({ placeholder = "Search…", value, onChange, shortcut, style, ...rest }) {
  const [focus, setFocus] = React.useState(false);
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: "var(--space-2)",
      background: "rgba(255,255,255,0.02)",
      border: `1px solid ${focus ? "var(--accent)" : "var(--border)"}`,
      borderRadius: "var(--radius-sm)", padding: "5px 8px",
      transition: "border-color var(--motion-fast) var(--ease-standard)", ...style,
    }}>
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--meta)" strokeWidth="2" strokeLinecap="round" style={{ flex: "none" }} aria-hidden="true">
        <circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" />
      </svg>
      <input
        type="search" placeholder={placeholder} value={value} onChange={onChange}
        onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
        style={{
          flex: 1, minWidth: 0, background: "transparent", border: "none", outline: "none",
          color: "var(--fg)", fontFamily: "var(--font-body)", fontSize: "var(--text-sm)",
          fontFeatureSettings: "var(--font-features)", padding: "1px 0",
        }}
        {...rest}
      />
      {shortcut ? (
        <span style={{
          fontFamily: "var(--font-mono)", fontSize: "var(--text-tiny)", color: "var(--meta)",
          border: "1px solid var(--border-soft)", borderRadius: "var(--radius-micro)",
          padding: "1px 4px", flex: "none",
        }}>{shortcut}</span>
      ) : null}
    </div>
  );
}

import React from "react";

export function Input({ label, id, type = "text", placeholder, value, onChange, hint, invalid = false, disabled = false, style, ...rest }) {
  const [focus, setFocus] = React.useState(false);
  const fid = id || `in-${Math.random().toString(36).slice(2, 8)}`;
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)", ...style }}>
      {label ? (
        <label htmlFor={fid} style={{ fontSize: "var(--text-sm)", fontWeight: "var(--weight-medium)", color: "var(--fg-2)", fontFeatureSettings: "var(--font-features)" }}>{label}</label>
      ) : null}
      <input
        id={fid} type={type} placeholder={placeholder} value={value} onChange={onChange} disabled={disabled}
        onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
        style={{
          background: "rgba(255,255,255,0.02)", color: "var(--fg-2)",
          border: `1px solid ${invalid ? "var(--danger)" : focus ? "var(--accent)" : "var(--border)"}`,
          borderRadius: "var(--radius-sm)", padding: "12px 14px",
          fontFamily: "var(--font-body)", fontSize: "var(--text-base)",
          fontFeatureSettings: "var(--font-features)", outline: "none",
          opacity: disabled ? 0.5 : 1,
          transition: "border-color var(--motion-fast) var(--ease-standard)",
        }}
        {...rest}
      />
      {hint ? <span style={{ fontSize: "var(--text-cap)", color: "var(--meta)", letterSpacing: "var(--tracking-caption)" }}>{hint}</span> : null}
    </div>
  );
}

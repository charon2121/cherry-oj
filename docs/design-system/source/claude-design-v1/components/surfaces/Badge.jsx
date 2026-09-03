import React from "react";

const tones = {
  neutral: { background: "rgba(255,255,255,0.05)", color: "var(--fg)", border: "1px solid var(--border-soft)" },
  accent:  { background: "var(--accent-tint)", color: "var(--accent-bright)", border: "1px solid rgba(210,4,45,0.35)" },
  success: { background: "var(--emerald)", color: "var(--fg)", border: "1px solid transparent" },
  warn:    { background: "rgba(234,179,8,0.14)", color: "var(--warn)", border: "1px solid rgba(234,179,8,0.3)" },
};

export function Badge({ tone = "neutral", style, children, ...rest }) {
  return (
    <span
      style={{
        display: "inline-flex", alignItems: "center", gap: "var(--space-1)",
        padding: "1px 8px", borderRadius: "var(--radius-micro)",
        fontFamily: "var(--font-display)", fontFeatureSettings: "var(--font-features)",
        fontSize: "var(--text-tiny)", fontWeight: "var(--weight-medium)",
        letterSpacing: "0.02em", lineHeight: 1.5,
        ...tones[tone], ...style,
      }}
      {...rest}
    >{children}</span>
  );
}

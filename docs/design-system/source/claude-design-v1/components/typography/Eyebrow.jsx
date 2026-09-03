import React from "react";

export function Eyebrow({ tone = "meta", style, children, ...rest }) {
  return (
    <p style={{
      margin: 0, fontFamily: "var(--font-display)", fontFeatureSettings: "var(--font-features)",
      fontSize: "var(--text-xs)", fontWeight: "var(--weight-medium)",
      textTransform: "uppercase", letterSpacing: "var(--tracking-eyebrow)",
      color: tone === "accent" ? "var(--accent-bright)" : "var(--meta)",
      ...style,
    }} {...rest}>{children}</p>
  );
}

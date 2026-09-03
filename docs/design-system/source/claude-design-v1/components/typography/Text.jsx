import React from "react";

const sizes = {
  lg:   { fontSize: "var(--text-lg)", lineHeight: "var(--leading-relaxed)", letterSpacing: "var(--tracking-body)" },
  base: { fontSize: "var(--text-base)", lineHeight: "var(--leading-body)" },
  md:   { fontSize: "var(--text-15)", lineHeight: "var(--leading-relaxed)", letterSpacing: "var(--tracking-body)" },
  sm:   { fontSize: "var(--text-sm)", lineHeight: "var(--leading-body)", letterSpacing: "-0.013em" },
  cap:  { fontSize: "var(--text-cap)", lineHeight: "var(--leading-body)", letterSpacing: "var(--tracking-caption)" },
  xs:   { fontSize: "var(--text-xs)", lineHeight: "var(--leading-label)" },
};
const tones = { default: "var(--fg-2)", strong: "var(--fg)", muted: "var(--muted)", meta: "var(--meta)", accent: "var(--accent-bright)" };
const weights = { light: "var(--weight-light)", regular: "var(--weight-regular)", medium: "var(--weight-medium)", semibold: "var(--weight-semibold)" };

export function Text({ size = "base", tone = "default", weight = "regular", mono = false, as = "p", style, children, ...rest }) {
  const Tag = as;
  return (
    <Tag style={{
      margin: 0, color: tones[tone] || tones.default,
      fontFamily: mono ? "var(--font-mono)" : "var(--font-body)",
      fontFeatureSettings: mono ? "normal" : "var(--font-features)",
      fontWeight: weights[weight], textWrap: "pretty",
      ...(sizes[size] || sizes.base), ...style,
    }} {...rest}>{children}</Tag>
  );
}

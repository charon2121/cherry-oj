import React from "react";

const levels = {
  1: { fontSize: "var(--text-5xl)", lineHeight: "var(--leading-tight)", letterSpacing: "var(--tracking-display-xl)", fontWeight: "var(--weight-medium)" },
  2: { fontSize: "var(--text-3xl)", lineHeight: "var(--leading-tight)", letterSpacing: "var(--tracking-display)", fontWeight: "var(--weight-medium)" },
  3: { fontSize: "var(--text-2xl)", lineHeight: "var(--leading-h1)", letterSpacing: "var(--tracking-h1)", fontWeight: "var(--weight-regular)" },
  4: { fontSize: "var(--text-xl)", lineHeight: "var(--leading-h2)", letterSpacing: "var(--tracking-h2)", fontWeight: "var(--weight-regular)" },
  5: { fontSize: "20px", lineHeight: "var(--leading-h2)", letterSpacing: "var(--tracking-h3)", fontWeight: "var(--weight-semibold)" },
};

export function Heading({ level = 2, as, style, children, ...rest }) {
  const Tag = as || (level <= 5 ? `h${Math.min(level, 6)}` : "div");
  return (
    <Tag style={{
      margin: 0, color: "var(--fg)", fontFamily: "var(--font-display)",
      fontFeatureSettings: "var(--font-features)", textWrap: "pretty",
      ...(levels[level] || levels[2]), ...style,
    }} {...rest}>{children}</Tag>
  );
}

import React from "react";

const gaps = { 1: "var(--space-1)", 2: "var(--space-2)", 3: "var(--space-3)", 4: "var(--space-4)", 6: "var(--space-6)", 8: "var(--space-8)", 12: "var(--space-12)" };

export function Stack({ direction = "column", gap = 4, align, justify, wrap = false, as = "div", style, children, ...rest }) {
  const Tag = as;
  return (
    <Tag style={{
      display: "flex", flexDirection: direction, gap: gaps[gap] || gaps[4],
      alignItems: align, justifyContent: justify,
      flexWrap: wrap ? "wrap" : "nowrap", ...style,
    }} {...rest}>{children}</Tag>
  );
}

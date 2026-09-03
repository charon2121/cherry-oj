import React from "react";

export function Pill({ dot, dotColor = "var(--accent)", selected = false, onClick, style, children, ...rest }) {
  const [hover, setHover] = React.useState(false);
  const interactive = typeof onClick === "function";
  return (
    <span
      onClick={onClick} role={interactive ? "button" : undefined}
      onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{
        display: "inline-flex", alignItems: "center", gap: "var(--space-1x)",
        padding: dot ? "0 10px 0 8px" : "0 10px 0 5px",
        borderRadius: "var(--radius-pill)",
        fontFamily: "var(--font-display)", fontFeatureSettings: "var(--font-features)",
        fontSize: "var(--text-xs)", fontWeight: "var(--weight-medium)", lineHeight: 1.8,
        color: selected ? "var(--fg)" : "var(--fg-2)",
        background: selected ? "rgba(255,255,255,0.05)" : hover && interactive ? "rgba(255,255,255,0.03)" : "transparent",
        border: "1px solid var(--border-solid)",
        cursor: interactive ? "pointer" : "default",
        transition: "background-color var(--motion-fast) var(--ease-standard)",
        ...style,
      }}
      {...rest}
    >
      {dot ? <span style={{ width: 6, height: 6, borderRadius: "50%", background: dotColor, flex: "none" }} /> : null}
      {children}
    </span>
  );
}

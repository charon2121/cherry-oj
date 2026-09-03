import React from "react";

const base = {
  display: "inline-flex", alignItems: "center", justifyContent: "center",
  gap: "var(--space-2)", borderRadius: "var(--radius-sm)",
  fontFamily: "var(--font-display)", fontFeatureSettings: 'var(--font-features)',
  lineHeight: 1, cursor: "pointer", border: "1px solid transparent",
  textDecoration: "none", whiteSpace: "nowrap",
  transition: "background-color var(--motion-fast) var(--ease-standard), color var(--motion-fast) var(--ease-standard)",
};

const variants = {
  primary: {
    rest: { background: "var(--accent)", color: "var(--accent-on)" },
    hover: { background: "var(--accent-hover)" },
    active: { background: "var(--accent-active)" },
  },
  ghost: {
    rest: { background: "rgba(255,255,255,0.02)", color: "var(--fg-ghost)", borderColor: "var(--border-ghost)" },
    hover: { background: "rgba(255,255,255,0.05)" },
    active: { background: "rgba(255,255,255,0.03)" },
  },
  subtle: {
    rest: { background: "rgba(255,255,255,0.04)", color: "var(--fg-2)" },
    hover: { background: "rgba(255,255,255,0.07)" },
    active: { background: "rgba(255,255,255,0.05)" },
  },
  toolbar: {
    rest: {
      background: "rgba(255,255,255,0.05)", color: "var(--meta)",
      borderRadius: "var(--radius-micro)", borderColor: "var(--border-soft)",
      boxShadow: "var(--elev-subtle)",
    },
    hover: { background: "rgba(255,255,255,0.08)", color: "var(--fg-2)" },
    active: { background: "rgba(255,255,255,0.05)" },
  },
};

const sizes = {
  sm: { padding: "0 6px", height: 24, fontSize: "var(--text-xs)", fontWeight: "var(--weight-medium)" },
  md: { padding: "8px 16px", fontSize: "var(--text-sm)", fontWeight: "var(--weight-medium)" },
  lg: { padding: "11px 20px", fontSize: "var(--text-15)", fontWeight: "var(--weight-medium)" },
};

export function Button({
  variant = "primary", size = "md", disabled = false, iconLeft, iconRight,
  href, onClick, type = "button", style, children, ...rest
}) {
  const [hover, setHover] = React.useState(false);
  const [down, setDown] = React.useState(false);
  const v = variants[variant] || variants.primary;
  const s = {
    ...base, ...sizes[size], ...v.rest,
    ...(hover && !disabled ? v.hover : null),
    ...(down && !disabled ? v.active : null),
    ...(disabled ? { opacity: 0.4, cursor: "not-allowed" } : null),
    ...style,
  };
  const Tag = href ? "a" : "button";
  return (
    <Tag
      href={href} type={href ? undefined : type} style={s}
      onClick={disabled ? undefined : onClick}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => { setHover(false); setDown(false); }}
      onMouseDown={() => setDown(true)}
      onMouseUp={() => setDown(false)}
      aria-disabled={disabled || undefined}
      {...rest}
    >
      {iconLeft}
      {children}
      {iconRight}
    </Tag>
  );
}

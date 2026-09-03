import React from "react";

const sizes = { sm: 24, md: 28, lg: 32 };

export function IconButton({ size = "md", shape = "circle", label, active = false, disabled = false, onClick, style, children, ...rest }) {
  const [hover, setHover] = React.useState(false);
  const px = sizes[size] || sizes.md;
  return (
    <button
      type="button" aria-label={label} title={label}
      onClick={disabled ? undefined : onClick}
      onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{
        width: px, height: px, display: "inline-flex", alignItems: "center", justifyContent: "center",
        background: active ? "rgba(255,255,255,0.08)" : hover ? "rgba(255,255,255,0.05)" : "rgba(255,255,255,0.03)",
        color: active ? "var(--fg)" : "var(--fg-2)",
        border: "1px solid var(--border)",
        borderRadius: shape === "circle" ? "var(--radius-circle)" : "var(--radius-sm)",
        cursor: disabled ? "not-allowed" : "pointer", opacity: disabled ? 0.4 : 1, padding: 0,
        transition: "background-color var(--motion-fast) var(--ease-standard), color var(--motion-fast) var(--ease-standard)",
        ...style,
      }}
      {...rest}
    >{children}</button>
  );
}

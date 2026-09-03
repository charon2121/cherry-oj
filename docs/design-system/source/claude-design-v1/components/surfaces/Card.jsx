import React from "react";

const pads = { sm: "var(--space-4)", md: "var(--space-6)", lg: "var(--space-8)" };
const radii = { md: "var(--radius-md)", lg: "var(--radius-lg)", xl: "var(--radius-xl)" };

export function Card({ padding = "md", radius = "md", interactive = false, elevated = false, style, children, ...rest }) {
  const [hover, setHover] = React.useState(false);
  return (
    <div
      onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{
        background: interactive && hover ? "rgba(255,255,255,0.04)" : "rgba(255,255,255,0.02)",
        border: "1px solid var(--border)", borderRadius: radii[radius] || radii.md,
        padding: pads[padding] || pads.md,
        boxShadow: elevated ? "var(--elev-raised)" : "none",
        transition: "background-color var(--motion-base) var(--ease-standard)",
        ...style,
      }}
      {...rest}
    >{children}</div>
  );
}

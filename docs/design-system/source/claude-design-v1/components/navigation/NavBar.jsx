import React from "react";

export function NavBar({ brand = "Cherry OJ", links = [], activeHref, cta, secondary, style, children }) {
  return (
    <header style={{
      position: "sticky", top: 0, zIndex: 20,
      background: "var(--surface-panel)", borderBottom: "1px solid var(--border-soft)",
      height: "var(--header-height)", display: "flex", alignItems: "center",
      padding: "0 var(--container-gutter-desktop)", ...style,
    }}>
      <div style={{
        width: "100%", maxWidth: "var(--container-max)", margin: "0 auto",
        display: "flex", alignItems: "center", gap: "var(--space-6)",
      }}>
        <span style={{
          fontFamily: "var(--font-display)", fontFeatureSettings: "var(--font-features)",
          fontSize: "var(--text-base)", fontWeight: "var(--weight-semibold)",
          letterSpacing: "var(--tracking-h3)", color: "var(--fg)", flex: "none",
        }}>{brand}</span>
        <nav style={{ display: "flex", alignItems: "center", gap: "var(--space-5)", flex: 1 }}>
          {links.map((l) => (
            <NavLink key={l.href || l.label} {...l} active={l.href === activeHref} />
          ))}
        </nav>
        {children}
        <div style={{ display: "flex", alignItems: "center", gap: "var(--space-3)", flex: "none" }}>
          {secondary}{cta}
        </div>
      </div>
    </header>
  );
}

function NavLink({ label, href, active, onClick }) {
  const [hover, setHover] = React.useState(false);
  return (
    <a
      href={href} onClick={onClick}
      onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{
        fontFamily: "var(--font-display)", fontFeatureSettings: "var(--font-features)",
        fontSize: "var(--text-cap)", fontWeight: "var(--weight-medium)",
        color: active || hover ? "var(--fg)" : "var(--fg-2)",
        textDecoration: "none",
        transition: "color var(--motion-fast) var(--ease-standard)",
      }}
    >{label}</a>
  );
}

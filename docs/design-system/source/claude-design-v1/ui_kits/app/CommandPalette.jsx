const { Text, Stack } = window.CherryOJDesignSystem_51433c;

const ITEMS = [
  ["Go to problem…", "P"], ["Create contest", "C"], ["Rejudge submission", "R"],
  ["Open standings", "S"], ["Switch language", "L"],
];

function CommandPalette({ open, onClose }) {
  React.useEffect(() => {
    const h = (e) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [onClose]);
  if (!open) return null;
  return (
    <div onClick={onClose} style={{ position: "fixed", inset: 0, background: "var(--overlay)", display: "flex", alignItems: "flex-start", justifyContent: "center", paddingTop: "14vh", zIndex: 40 }}>
      <div onClick={(e) => e.stopPropagation()} style={{
        width: 520, background: "var(--surface)", border: "1px solid var(--border)",
        borderRadius: "var(--radius-lg)", boxShadow: "var(--elev-dialog)", overflow: "hidden",
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", padding: "12px 14px", borderBottom: "1px solid var(--border-soft)" }}>
          <AppIcon name="search" size={15} color="var(--meta)" />
          <input autoFocus placeholder="Type a command or search…" style={{
            flex: 1, background: "transparent", border: "none", outline: "none", color: "var(--fg)",
            fontFamily: "var(--font-body)", fontSize: "var(--text-base)", fontFeatureSettings: "var(--font-features)",
          }} />
        </div>
        <Stack gap={0} style={{ padding: "6px" }}>
          {ITEMS.map(([label, key], i) => (
            <div key={label} style={{
              display: "flex", alignItems: "center", gap: "var(--space-3)", padding: "8px 10px",
              borderRadius: "var(--radius-xs)", background: i === 0 ? "rgba(255,255,255,0.05)" : "transparent",
            }}>
              <Text size="cap" weight="medium" tone={i === 0 ? "strong" : "default"} style={{ flex: 1 }}>{label}</Text>
              <span style={{ fontFamily: "var(--font-mono)", fontSize: "var(--text-tiny)", color: "var(--meta)", border: "1px solid var(--border-soft)", borderRadius: "var(--radius-micro)", padding: "1px 4px" }}>{key}</span>
            </div>
          ))}
        </Stack>
      </div>
    </div>
  );
}
Object.assign(window, { CommandPalette });

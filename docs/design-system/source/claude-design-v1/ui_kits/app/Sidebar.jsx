const { Text, Stack } = window.CherryOJDesignSystem_51433c;

const NAV = [
  { id: "problems", label: "Problems", icon: "list", count: 1284 },
  { id: "contests", label: "Contests", icon: "trophy", count: 42 },
  { id: "submissions", label: "Submissions", icon: "inbox", count: 9 },
  { id: "teams", label: "Teams", icon: "users" },
];
const SETS = ["Beginner ladder", "Graph theory", "ICPC 2026 prep", "Company sets"];

function SideRow({ active, icon, label, count, onClick }) {
  const [hover, setHover] = React.useState(false);
  return (
    <button onClick={onClick} onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{
        display: "flex", alignItems: "center", gap: "var(--space-2)", width: "100%",
        padding: "5px 8px", borderRadius: "var(--radius-xs)", border: "1px solid transparent",
        background: active ? "rgba(255,255,255,0.05)" : hover ? "rgba(255,255,255,0.03)" : "transparent",
        color: active ? "var(--fg)" : "var(--fg-2)", cursor: "pointer", textAlign: "left",
        fontFamily: "var(--font-display)", fontFeatureSettings: "var(--font-features)",
        fontSize: "var(--text-cap)", fontWeight: "var(--weight-medium)",
        transition: "background-color var(--motion-fast) var(--ease-standard)",
      }}>
      <AppIcon name={icon} size={15} color={active ? "var(--fg)" : "var(--muted)"} />
      <span style={{ flex: 1 }}>{label}</span>
      {count != null ? <span style={{ fontSize: "var(--text-micro)", color: "var(--meta)" }}>{count}</span> : null}
    </button>
  );
}

function Sidebar({ view, onNavigate }) {
  return (
    <aside style={{
      width: "var(--sidebar-width)", flex: "none", background: "var(--surface-panel)",
      borderRight: "1px solid var(--border-soft)", padding: "var(--space-3)",
      display: "flex", flexDirection: "column", gap: "var(--space-5)", height: "100%",
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: "var(--space-2)", padding: "2px 6px" }}>
        <span style={{ width: 18, height: 18, borderRadius: "var(--radius-xs)", background: "var(--accent)", flex: "none" }} />
        <span style={{ fontFamily: "var(--font-display)", fontSize: "var(--text-sm)", fontWeight: "var(--weight-semibold)", letterSpacing: "var(--tracking-h3)", color: "var(--fg)" }}>Cherry OJ</span>
      </div>
      <Stack gap={1}>
        {NAV.map((n) => <SideRow key={n.id} {...n} active={view === n.id} onClick={() => onNavigate(n.id)} />)}
      </Stack>
      <Stack gap={1}>
        <Text size="xs" tone="meta" weight="medium" style={{ padding: "0 8px", textTransform: "uppercase", letterSpacing: "0.07em", fontSize: "var(--text-micro)" }}>Your sets</Text>
        {SETS.map((s) => <SideRow key={s} icon="chevron" label={s} />)}
      </Stack>
      <div style={{ marginTop: "auto" }}>
        <SideRow icon="settings" label="Settings" />
      </div>
    </aside>
  );
}
Object.assign(window, { Sidebar });

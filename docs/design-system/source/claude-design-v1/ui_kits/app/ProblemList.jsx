const { Text, Pill, Badge, Button, SearchInput, IconButton, Stack } = window.CherryOJDesignSystem_51433c;

const ROWS = [
  { id: "CO-1042", title: "Segment tree range sum", tags: ["data structures", "trees"], diff: "Medium", rate: "48%", state: "solved" },
  { id: "CO-1041", title: "Knapsack with duplicates", tags: ["dp"], diff: "Medium", rate: "51%", state: "attempted" },
  { id: "CO-1039", title: "Dijkstra on grids", tags: ["graphs", "shortest path"], diff: "Hard", rate: "23%", state: "solved" },
  { id: "CO-1036", title: "Two sum, sorted input", tags: ["two pointers"], diff: "Easy", rate: "82%", state: "solved" },
  { id: "CO-1030", title: "Minimum spanning cactus", tags: ["graphs", "mst"], diff: "Hard", rate: "11%", state: "none" },
  { id: "CO-1027", title: "Longest palindromic run", tags: ["strings", "dp"], diff: "Medium", rate: "44%", state: "none" },
];
const DIFF = { Easy: "var(--success)", Medium: "var(--warn)", Hard: "var(--accent-bright)" };
const STATE_DOT = { solved: "var(--success)", attempted: "var(--warn)", none: "rgba(255,255,255,0.1)" };

function Row({ r, onOpen }) {
  const [hover, setHover] = React.useState(false);
  return (
    <div onClick={() => onOpen(r)} onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{
        display: "flex", alignItems: "center", gap: "var(--space-3)",
        padding: "10px 16px", borderBottom: "1px solid var(--line-tertiary)",
        background: hover ? "rgba(255,255,255,0.02)" : "transparent", cursor: "pointer",
        transition: "background-color var(--motion-fast) var(--ease-standard)",
      }}>
      <span style={{ width: 8, height: 8, borderRadius: r.state === "none" ? 2 : "50%", background: STATE_DOT[r.state], flex: "none" }} />
      <Text size="cap" tone="meta" mono style={{ width: 68, flex: "none" }}>{r.id}</Text>
      <Text size="sm" tone={hover ? "strong" : "default"} weight="medium" style={{ flex: 1 }}>{r.title}</Text>
      <Stack direction="row" gap={1} style={{ flex: "none" }}>
        {r.tags.map((t) => <Badge key={t}>{t}</Badge>)}
      </Stack>
      <Text size="cap" weight="medium" style={{ width: 64, flex: "none", color: DIFF[r.diff] }}>{r.diff}</Text>
      <Text size="cap" tone="meta" mono style={{ width: 40, flex: "none", textAlign: "right" }}>{r.rate}</Text>
    </div>
  );
}

function ProblemList({ onOpen, onPalette }) {
  const [filter, setFilter] = React.useState("All");
  const filters = ["All", "Unsolved", "Solved", "Contest only"];
  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", minWidth: 0 }}>
      <header style={{ height: "var(--header-height)", flex: "none", display: "flex", alignItems: "center", gap: "var(--space-3)", padding: "0 var(--space-4)", borderBottom: "1px solid var(--border-soft)" }}>
        <Text size="sm" weight="medium" tone="strong">Problems</Text>
        <Text size="cap" tone="meta">1,284</Text>
        <div style={{ flex: 1 }} />
        <SearchInput placeholder="Search problems…" shortcut="⌘K" style={{ width: 240 }} onFocus={onPalette} />
        <IconButton label="New problem" shape="square"><AppIcon name="plus" size={14} /></IconButton>
      </header>
      <div style={{ flex: "none", display: "flex", alignItems: "center", gap: "var(--space-2)", padding: "10px var(--space-4)", borderBottom: "1px solid var(--border-soft)" }}>
        {filters.map((f) => <Pill key={f} selected={filter === f} onClick={() => setFilter(f)}>{f}</Pill>)}
        <div style={{ flex: 1 }} />
        <Button variant="toolbar">Difficulty</Button>
        <Button variant="toolbar">Acceptance</Button>
      </div>
      <div style={{ overflow: "auto", flex: 1 }}>
        {ROWS.filter((r) => filter === "Solved" ? r.state === "solved" : filter === "Unsolved" ? r.state !== "solved" : true)
          .map((r) => <Row key={r.id} r={r} onOpen={onOpen} />)}
      </div>
    </div>
  );
}
Object.assign(window, { ProblemList, ROWS });

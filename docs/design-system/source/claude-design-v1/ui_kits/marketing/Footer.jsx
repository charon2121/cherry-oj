const { Text, Eyebrow, Stack, Container } = window.CherryOJDesignSystem_51433c;

const COLS = [
  ["Product", ["Problems", "Contests", "Standings", "Changelog"]],
  ["Developers", ["API", "Judge images", "Status", "Import guide"]],
  ["Company", ["About", "Blog", "Careers", "Contact"]],
];

function Footer() {
  return (
    <Container as="footer" style={{ paddingBlock: "var(--space-12)", borderTop: "1px solid var(--border)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "1.4fr repeat(3, 1fr)", gap: "var(--space-8)" }}>
        <Stack gap={3}>
          <span style={{ fontFamily: "var(--font-display)", fontSize: "var(--text-base)", fontWeight: "var(--weight-semibold)", letterSpacing: "var(--tracking-h3)", color: "var(--fg)" }}>Cherry OJ</span>
          <Text size="cap" tone="meta">An online judge for teams that grade code all day.</Text>
        </Stack>
        {COLS.map(([title, items]) => (
          <Stack key={title} gap={2}>
            <Eyebrow>{title}</Eyebrow>
            {items.map((i) => <a key={i} href="#" style={{ fontSize: "var(--text-cap)", color: "var(--fg-2)", letterSpacing: "var(--tracking-caption)" }}>{i}</a>)}
          </Stack>
        ))}
      </div>
      <Text size="cap" tone="meta" style={{ marginBlockStart: "var(--space-8)" }}>© 2026 Cherry OJ</Text>
    </Container>
  );
}
Object.assign(window, { Footer });

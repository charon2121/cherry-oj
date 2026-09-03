const { Button, Heading, Text, Eyebrow, Stack, Card, Container, Pill } = window.CherryOJDesignSystem_51433c;

function Hero({ onSignup }) {
  return (
    <Container as="section" style={{ paddingBlock: "var(--section-y-desktop)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "1.2fr 1fr", gap: "var(--space-12)", alignItems: "center" }}>
        <Stack gap={4}>
          <Eyebrow>Cherry OJ · release 2.4</Eyebrow>
          <Heading level={2} style={{ maxWidth: "22ch" }}>Built for people who ship solutions.</Heading>
          <Text size="lg" tone="muted" style={{ maxWidth: "50ch" }}>
            Problems, contests, submissions and standings in one place. Judged in
            milliseconds, reported in plain language, dark by default.
          </Text>
          <Stack direction="row" gap={3} style={{ marginBlockStart: "var(--space-2)" }}>
            <Button onClick={onSignup}>Start solving</Button>
            <Button variant="ghost" iconRight={<Icon name="arrowRight" />}>Read the docs</Button>
          </Stack>
          <Stack direction="row" gap={2} wrap style={{ marginBlockStart: "var(--space-4)" }}>
            <Pill dot dotColor="var(--success)">1,284 problems</Pill>
            <Pill dot dotColor="var(--accent)">42 live contests</Pill>
            <Pill>17 languages</Pill>
          </Stack>
        </Stack>

        <Card radius="lg" padding="md" elevated>
          <Eyebrow style={{ marginBottom: "var(--space-3)" }}>Recent submissions</Eyebrow>
          <Stack gap={2}>
            {[
              ["Two Sum · C++17", "Accepted", "var(--success)", "42 ms"],
              ["Segment tree range sum", "Running", "var(--accent)", "—"],
              ["Knapsack variants", "Wrong answer", "var(--meta)", "test 14"],
              ["Dijkstra on grids", "Accepted", "var(--success)", "108 ms"],
            ].map(([name, state, dot, time]) => (
              <div key={name} style={{ display: "flex", alignItems: "center", gap: "var(--space-3)", padding: "6px 0", borderBottom: "1px solid var(--border-soft)" }}>
                <span style={{ width: 8, height: 8, borderRadius: "50%", background: dot, flex: "none" }} />
                <Text size="sm" style={{ flex: 1 }}>{name}</Text>
                <Text size="cap" tone="meta">{state}</Text>
                <Text size="cap" tone="meta" mono style={{ width: 56, textAlign: "right" }}>{time}</Text>
              </div>
            ))}
          </Stack>
        </Card>
      </div>
    </Container>
  );
}
Object.assign(window, { Hero });

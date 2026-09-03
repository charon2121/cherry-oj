const { Heading, Text, Eyebrow, Stack, Card, Container, Badge } = window.CherryOJDesignSystem_51433c;

const FEATURES = [
  { icon: "zap", title: "Judged in milliseconds", body: "Sandboxed runners return a verdict before you switch tabs. Time and memory reported per test case." },
  { icon: "layers", title: "Problem sets that scale", body: "Group problems into ladders, courses and contests. Reuse test data across every set." },
  { icon: "terminal", title: "Seventeen languages", body: "C++, Rust, Python, Go and more, each pinned to a versioned toolchain image." },
  { icon: "gauge", title: "Live standings", body: "Penalty-aware scoreboards update as submissions land — no refresh, no polling gap." },
  { icon: "check", title: "Plain-language verdicts", body: "Every failure names the test, the limit it crossed, and what to look at next." },
  { icon: "github", title: "Import from anywhere", body: "Pull statements and test data straight from a repository, or push them via the API." },
];

function Features() {
  return (
    <Container as="section" style={{ paddingBlock: "var(--section-y-desktop)", borderTop: "1px solid var(--border)" }}>
      <Stack gap={3}>
        <Eyebrow>What the platform does</Eyebrow>
        <Heading level={3} style={{ maxWidth: "28ch" }}>Darkness as the native medium.</Heading>
      </Stack>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: "var(--space-4)", marginBlockStart: "var(--space-6)" }}>
        {FEATURES.map((f) => (
          <Card key={f.title} interactive>
            <Stack gap={3}>
              <Icon name={f.icon} size={18} color="var(--accent-bright)" />
              <Heading level={5}>{f.title}</Heading>
              <Text size="md" tone="muted">{f.body}</Text>
            </Stack>
          </Card>
        ))}
      </div>
      <Stack direction="row" gap={2} wrap style={{ marginBlockStart: "var(--space-6)", alignItems: "center" }}>
        <Badge tone="accent">New</Badge>
        <Text size="sm" tone="muted">Rejudge queues now report progress per test case.</Text>
      </Stack>
    </Container>
  );
}
Object.assign(window, { Features });

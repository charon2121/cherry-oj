const { Heading, Text, Badge, Pill, Button, Card, Stack, Textarea, IconButton } = window.CherryOJDesignSystem_51433c;

function ProblemView({ problem, onBack }) {
  const [verdict, setVerdict] = React.useState(null);
  const [running, setRunning] = React.useState(false);
  const submit = () => {
    setRunning(true); setVerdict(null);
    setTimeout(() => { setRunning(false); setVerdict({ state: "Accepted", ms: 42, mem: "3.1 MB" }); }, 900);
  };
  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", minWidth: 0 }}>
      <header style={{ height: "var(--header-height)", flex: "none", display: "flex", alignItems: "center", gap: "var(--space-3)", padding: "0 var(--space-4)", borderBottom: "1px solid var(--border-soft)" }}>
        <IconButton label="Back" shape="square" onClick={onBack}><AppIcon name="x" size={14} /></IconButton>
        <Text size="cap" tone="meta" mono>{problem.id}</Text>
        <Text size="sm" weight="medium" tone="strong">{problem.title}</Text>
        <div style={{ flex: 1 }} />
        <Pill dot dotColor="var(--success)">{problem.rate} accepted</Pill>
        <Button size="sm" onClick={submit} iconLeft={<AppIcon name="play" size={13} />}>{running ? "Running…" : "Submit"}</Button>
      </header>
      <div style={{ display: "grid", gridTemplateColumns: "1.1fr 1fr", gap: 0, flex: 1, minHeight: 0 }}>
        <section style={{ padding: "var(--space-6)", overflow: "auto", borderRight: "1px solid var(--border-soft)" }}>
          <Stack gap={4}>
            <Stack direction="row" gap={2}>{problem.tags.map((t) => <Badge key={t}>{t}</Badge>)}<Badge tone="accent">{problem.diff}</Badge></Stack>
            <Heading level={4}>{problem.title}</Heading>
            <Text size="md" tone="muted">
              Given an array of n integers and q queries, report the sum of each query
              range. Updates arrive interleaved with the queries, so a prefix-sum table
              will not hold.
            </Text>
            <Stack gap={2}>
              <Text size="cap" tone="meta" weight="medium">Constraints</Text>
              <Text size="sm" mono>1 ≤ n, q ≤ 2·10^5</Text>
              <Text size="sm" mono>|a_i| ≤ 10^9</Text>
            </Stack>
            <Card padding="sm" radius="md">
              <Stack gap={2}>
                <Text size="cap" tone="meta" weight="medium">Sample input</Text>
                <Text size="sm" mono style={{ whiteSpace: "pre" }}>{"5 3\n1 2 3 4 5\nQ 1 3\nU 2 10\nQ 1 3"}</Text>
                <Text size="cap" tone="meta" weight="medium" style={{ marginTop: 6 }}>Sample output</Text>
                <Text size="sm" mono style={{ whiteSpace: "pre" }}>{"6\n14"}</Text>
              </Stack>
            </Card>
          </Stack>
        </section>
        <section style={{ padding: "var(--space-6)", display: "flex", flexDirection: "column", gap: "var(--space-4)", minHeight: 0 }}>
          <Stack direction="row" gap={2} align="center">
            <Text size="cap" tone="meta" weight="medium">Solution · C++17</Text>
            <div style={{ flex: 1 }} />
            <Button variant="toolbar">Language</Button>
            <Button variant="toolbar">Reset</Button>
          </Stack>
          <Textarea rows={12} defaultValue={"#include <bits/stdc++.h>\nusing namespace std;\n\nint main() {\n  int n, q; cin >> n >> q;\n  // segment tree here\n}"} style={{ flex: 1 }} />
          {running ? (
            <Card padding="sm"><Stack direction="row" gap={2} align="center"><AppIcon name="clock" size={14} color="var(--warn)" /><Text size="sm">Running 14 test cases…</Text></Stack></Card>
          ) : verdict ? (
            <Card padding="sm" style={{ borderColor: "rgba(39,166,68,0.4)" }}>
              <Stack direction="row" gap={3} align="center">
                <AppIcon name="check" size={14} color="var(--success)" />
                <Text size="sm" weight="medium" tone="strong">{verdict.state}</Text>
                <Text size="cap" tone="meta" mono>{verdict.ms} ms · {verdict.mem}</Text>
                <div style={{ flex: 1 }} />
                <Text size="cap" tone="meta">14 / 14 tests</Text>
              </Stack>
            </Card>
          ) : (
            <Text size="cap" tone="meta">Submit to run against all 14 test cases.</Text>
          )}
        </section>
      </div>
    </div>
  );
}
Object.assign(window, { ProblemView });

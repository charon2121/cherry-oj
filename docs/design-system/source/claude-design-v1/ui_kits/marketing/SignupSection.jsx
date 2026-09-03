const { Button, Heading, Text, Eyebrow, Stack, Input, Container } = window.CherryOJDesignSystem_51433c;

function SignupSection({ onSignup }) {
  const [email, setEmail] = React.useState("");
  const [sent, setSent] = React.useState(false);
  return (
    <Container as="section" style={{ paddingBlock: "var(--section-y-desktop)", borderTop: "1px solid var(--border)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "1.2fr 1fr", gap: "var(--space-12)", alignItems: "start" }}>
        <Stack gap={4}>
          <Eyebrow>Get an account</Eyebrow>
          <Heading level={3}>Inputs on dark surfaces.</Heading>
          <Text size="lg" tone="muted" style={{ maxWidth: "44ch" }}>
            A 0.02 white fill on a hairline border. Focus shifts the border to cherry —
            no halo, no blue ring.
          </Text>
        </Stack>
        <form style={{ maxWidth: 400, width: "100%" }} onSubmit={(e) => { e.preventDefault(); setSent(true); onSignup && onSignup(); }}>
          <Stack gap={4}>
            <Input label="Work email" type="email" placeholder="you@company.com" value={email} onChange={(e) => setEmail(e.target.value)} />
            <Stack direction="row" gap={3}>
              <Button type="submit">{sent ? "Check your inbox" : "Sign up"}</Button>
              <Button variant="ghost" type="button">Learn more</Button>
            </Stack>
            {sent ? <Text size="cap" tone="meta">We sent a sign-in link to {email || "your inbox"}.</Text> : null}
          </Stack>
        </form>
      </div>
    </Container>
  );
}
Object.assign(window, { SignupSection });

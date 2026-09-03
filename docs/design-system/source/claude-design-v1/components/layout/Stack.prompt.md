# Stack
Vertical and horizontal grouping. Always prefer it over per-element margins.

```jsx
<Stack gap={3}><Heading level={5}>Weight 510</Heading><Text size="sm" tone="muted">…</Text></Stack>
<Stack direction="row" gap={2} wrap>{tags}</Stack>
```

Gap steps map to `--space-*`: 1=4 · 2=8 · 3=12 · 4=16 · 6=24 · 8=32 · 12=48px.

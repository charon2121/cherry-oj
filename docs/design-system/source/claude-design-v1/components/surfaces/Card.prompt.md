# Card
Every panel, feature tile and dropdown surface in the system.

```jsx
<Card interactive>
  <Heading level={3}>Dark-first</Heading>
  <Text size="sm" tone="muted">Luminance stepping, not colour.</Text>
</Card>
```

- Fill rgba(255,255,255,0.02) → 0.04 on hover when `interactive`. Never solid.
- `radius`: 8px cards · 12px panels · 22px large panels. `padding`: 16 / 24 / 32px.
- Use `elevated` only for floating surfaces (dropdowns, palettes).

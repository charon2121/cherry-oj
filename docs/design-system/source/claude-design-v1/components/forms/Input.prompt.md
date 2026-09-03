# Input
Labelled text field for forms and settings panels.

```jsx
<Input label="Work email" type="email" placeholder="you@company.com" hint="We only email about releases." />
```

- rgba(255,255,255,0.02) fill, `--border` hairline, 6px radius, 12px 14px padding, 16px text.
- Focus = border becomes `--accent` (cherry). `invalid` swaps it to `--danger`.
- Label 14px/510 `--fg-2`, 8px above the field.

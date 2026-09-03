# Button
The standard action control — cherry fill for the one primary action per view, ghost for secondary, subtle/toolbar for dense chrome.

```jsx
<Button onClick={submit}>Start building</Button>
<Button variant="ghost" href="/spec">Read the spec</Button>
<Button variant="toolbar" size="sm">Sort</Button>
```

- `variant`: `primary` (bg `--accent`, white label) · `ghost` (rgba(255,255,255,0.02) on a solid `#24282c` border) · `subtle` (rgba(255,255,255,0.04)) · `toolbar` (2px radius, 12px/510, muted label)
- `size`: `sm` 0 6px · `md` 8px 16px · `lg` 11px 20px. Radius is 6px for all but `toolbar`.
- Hover lightens; press darkens. Never use more than one primary button in a view.

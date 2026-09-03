# IconButton
Icon-only action for chrome — close, menu, overflow, panel toggles.

```jsx
<IconButton label="Close"><i data-lucide="x"></i></IconButton>
<IconButton label="Filter" shape="square" active><i data-lucide="filter"></i></IconButton>
```

- `shape="circle"` (50% radius) is the default; `square` uses 6px radius for toolbar rows.
- Background steps rgba(255,255,255,0.03) → 0.05 hover → 0.08 active. Border is always `--border`.
- Sizes 24 / 28 / 32px. Pair with 14–16px Lucide glyphs.

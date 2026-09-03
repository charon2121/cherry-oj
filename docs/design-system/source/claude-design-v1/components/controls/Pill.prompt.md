# Pill
Filter chips, status tags and category labels — the only rounded-full element in the system.

```jsx
<Pill selected>All issues</Pill>
<Pill dot dotColor="var(--success)">Active</Pill>
<Pill onClick={() => setFilter('done')}>Done</Pill>
```

- Transparent by default; selected fills rgba(255,255,255,0.05) and lifts the label to `--fg`.
- 12px / weight 510, asymmetric padding (0 10px 0 5px) as in the source recipe.

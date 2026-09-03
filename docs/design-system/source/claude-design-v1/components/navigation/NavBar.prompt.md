# NavBar
Sticky top navigation for marketing pages and app shells.

```jsx
<NavBar
  brand="Cherry OJ"
  links={[{label:'Problems',href:'/p'},{label:'Contests',href:'/c'}]}
  activeHref="/p"
  secondary={<Button variant="ghost" size="sm">Log in</Button>}
  cta={<Button size="sm">Sign up</Button>}
/>
```

- `--surface-panel` (#0f1011) bar, 1px `--border-soft` bottom rule, 56px tall.
- Links 13px/510 `--fg-2`, lifting to `--fg` on hover/active — no underline, no accent colour.
- The brand slot is type only; this system ships no logo mark.

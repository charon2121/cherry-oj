# Judge app UI kit

App-shell recreation. The source package ships no application screen code — only
the chrome recipes in `linear-app/DESIGN.md` (§4 Navigation, Cards, Inputs,
Badges; §6 Depth) and the issue-tracker panel in `components.html`. Those recipes
are followed literally here: `--surface-panel` sidebar, hairline rules instead of
dividers, translucent rows, 2px-radius toolbar buttons, multi-layer dialog shadow
on the command palette.

| File | Surface |
|---|---|
| `index.html` | Interactive shell — navigate, open a problem, submit, ⌘K palette |
| `Sidebar.jsx` | Panel-dark nav with counts and a user-sets section |
| `ProblemList.jsx` | Filter chips, toolbar buttons, dense problem rows |
| `ProblemView.jsx` | Statement + editor split, submit → verdict card |
| `CommandPalette.jsx` | ⌘K overlay on `--overlay` with `--elev-dialog` |
| `AppIcons.jsx` | Lucide glyph paths inlined at 1.75 stroke (substitution) |

Content is illustrative online-judge copy, not source content.

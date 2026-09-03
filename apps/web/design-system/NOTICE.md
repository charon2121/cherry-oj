<!--
Modified for Cherry OJ on 2026-09-03.
This notice records the frozen visual source, its upstream fixture, and the
production changes made in the Web-owned package.
-->

# Attribution and modification notice

Cherry OJ Web Design System 2.0 productionizes the user-approved Claude Design export named
`Cherry OJ Design System`. The complete 99-file source is retained only in the repository documentation tree; its
deterministic root SHA-256 is
`68d93dd52ee2c7e9da3b058156ead5e2a789f82f56a2ead28beb9a3f676f9e7d`. This Web package records that digest but
does not load the snapshot at build or runtime.

The export states that its neutral visual foundation was derived from the `design-systems/linear-app` fixture in the
OpenDesign repository. That curated fixture is not official Linear source code, and Cherry OJ is not affiliated with or
endorsed by Linear. Its Apache License 2.0 text is distributed as [`LICENSE.open-design`](./LICENSE.open-design).

## Upstream fixture snapshot retained for the license chain

| Source file | SHA-256 |
|---|---|
| `design-systems/linear-app/tokens.css` | `9f99cf1b4b799f1871b742542a56fc9dd8c9a179fc452c1e56e7b6e2cdfd022e` |
| `design-systems/linear-app/DESIGN.md` | `4c7264d8bc0e26de761c550e9f0445b0e7d92078c1a288f3fdb604b4f6df8fb7` |
| repository `LICENSE` | `9d95806a26532623360eb84bb17d298f394b55ef73fb4c0796d99b4319b2b0da` |

## Cherry OJ production changes

- Replaced the previous Web visual contract with the Claude Design spacing, typography, radius, layout, surface and
  Cherry colour recipes while retaining the `--ds-*` production namespace.
- Preserved `cherry-black` as the exact-source default and rebuilt `pure-white` as a complete light counterpart sharing
  component structure and every non-colour token.
- Mapped solid Cherry hover/pressed states to darker colours from the source family so white action text stays readable;
  retained the source bright Cherry values for dark-theme links, focus and accents.
- Replaced prototype-only inline styles, random ids, mouse state, SVG paths and remote fonts with semantic React/Base UI,
  `useId`, Lucide React, local Inter Variable and local JetBrains Mono Variable.
- Retained CodeMirror for structured long content and the existing route, auth, permission, API and business contracts.
- Kept deterministic theme generation, contract/contrast checks, source scanning and reduced-motion behavior.
- Moved the executable frontend assets into this Web-owned package; repository documentation is not a build dependency.

No Linear logo, trademark, product copy, screenshot, Berkeley Mono file or other proprietary asset is distributed.
“Linear” is used only to identify the upstream design inspiration and fixture; Apache-2.0 does not grant trademark rights.
Inter Variable and JetBrains Mono Variable copyright notices and OFL-1.1 terms are distributed as
[`LICENSE.fonts`](./LICENSE.fonts).

<!--
Modified for Cherry OJ on 2026-09-03.
This notice records the frozen Claude Design source, its upstream fixture, and
the production interpretation maintained by Cherry OJ.
-->

# Attribution and modification notice

## Two derivation chains, not one

Cherry OJ's current design system derives from **two** sources, and conflating them is what produced four rounds of rework.
State them separately:

1. **The frozen Claude Design export** (below) is where the colour palette, the optical spacing steps and the component
   inventory come from. It is retained here as licence and provenance evidence.
2. **Measured values from an external Figma reference** are where the type scale, weights, letter-spacing, radius,
   overlay material and the composition principles come from. That file is not in this repository; the numbers taken
   from it are recorded in [`measurements.md`](./measurements.md), which is their only record here.

The frozen export is **no longer the composition authority**. It is a second-hand recreation, and its own readme asserted
two rules that measurement later disproved — that blur is never used, and that focus is a 2px outline. Both were written
into this repository's specification and both have been withdrawn. Treat the export as evidence of what we derived from,
not as instruction on how to build.

## The frozen Claude Design export

The user-provided Claude Design export `Cherry OJ Design System` is stored byte-for-byte under
[`source/claude-design-v1/`](./source/claude-design-v1/) and locked by
[`source-lock.json`](./source-lock.json): 99 files, 239831 bytes, root SHA-256
`68d93dd52ee2c7e9da3b058156ead5e2a789f82f56a2ead28beb9a3f676f9e7d`. The lock includes every relative path,
byte length and SHA-256 and rejects symlinks or path escape.

The export states that its neutral foundation was derived from the `design-systems/linear-app` fixture in the OpenDesign
repository. That fixture is curated bundled material, not official Linear source code, and Cherry OJ is not affiliated
with or endorsed by Linear. The fixture is licensed under Apache License 2.0; a verbatim copy is included in
[`LICENSE.open-design`](./LICENSE.open-design).

## Upstream fixture snapshot retained for the license chain

| Source file | SHA-256 |
|---|---|
| `design-systems/linear-app/tokens.css` | `9f99cf1b4b799f1871b742542a56fc9dd8c9a179fc452c1e56e7b6e2cdfd022e` |
| `design-systems/linear-app/DESIGN.md` | `4c7264d8bc0e26de761c550e9f0445b0e7d92078c1a288f3fdb604b4f6df8fb7` |
| repository `LICENSE` | `9d95806a26532623360eb84bb17d298f394b55ef73fb4c0796d99b4319b2b0da` |

## Production interpretation

- `cherry-black` carries the source dark surfaces, text ladder, Cherry scale, optical spacing, typography, radius,
  density and 220px/56px application chrome into the production semantic contract.
- `pure-white` is a Cherry OJ light counterpart, not a claim that the dark-only source supplied a light design. It shares
  the exact component anatomy and non-colour Foundation and recalibrates only theme-dependent semantics.
- Solid Cherry hover/pressed states use darker source-family values so white text passes the full state contrast matrix;
  prototype opacity-disabled behavior is replaced by dedicated readable semantic tokens.
- Downloaded JSX/HTML/bundle files remain immutable evidence. They are not imported, executed or copied as production
  implementation because they contain remote-font, inline-style, mouse-state, random-id and inline-SVG demo patterns.
- Production uses TypeScript, React 19, Base UI, Tailwind, Lucide React, local Inter/JetBrains Mono and CodeMirror while
  preserving existing routes, auth, permissions, APIs and business state.

The source contains no production logo or Berkeley Mono binary. Lucide reference use is covered by
[`LICENSE.lucide`](./LICENSE.lucide). No Linear logo, trademark, product copy or screenshot is distributed. “Linear” is
used only to identify the design inspiration and upstream fixture; Apache-2.0 does not grant trademark rights.

<!--
Modified for Cherry OJ on 2026-08-27.
This notice identifies the OpenDesign fixture snapshot and the Cherry OJ changes.
-->

# Attribution and modification notice

Parts of this package are derived from the `design-systems/linear-app` fixture in the OpenDesign repository. That
repository describes the fixture as curated bundled material. It is not official Linear source code, and this Cherry OJ
package is not affiliated with or endorsed by Linear.

The source material is licensed under Apache License 2.0. A verbatim license copy is provided in
[`LICENSE.open-design`](./LICENSE.open-design). The license permits modification and redistribution subject to its
terms; in particular, modified files in this package carry their own prominent Cherry OJ modification notice.

## Fixed source snapshot

The implementation was prepared on 2026-08-27 from these local snapshot files:

| Source file | SHA-256 |
|---|---|
| `design-systems/linear-app/tokens.css` | `9f99cf1b4b799f1871b742542a56fc9dd8c9a179fc452c1e56e7b6e2cdfd022e` |
| `design-systems/linear-app/DESIGN.md` | `4c7264d8bc0e26de761c550e9f0445b0e7d92078c1a288f3fdb604b4f6df8fb7` |
| repository `LICENSE` | `9d95806a26532623360eb84bb17d298f394b55ef73fb4c0796d99b4319b2b0da` |

The package does not load files from that local snapshot at build or runtime.

## Cherry OJ modifications

- Renamed public tokens into the `--ds-*` namespace and separated shared metrics from theme-dependent semantics.
- Preserved the fixture's dark structure as the default `cherry-black` theme while replacing active purple with the
  Cherry brand scale.
- Added accessibility corrections for metadata, focus, necessary borders, Cherry interactions, and OJ statuses.
- Designed a complete `pure-white` theme; it is a Cherry OJ extension, not an upstream Linear theme.
- Added a versioned theme contract, manifest-driven generation, a theme-neutral Tailwind/shadcn adapter, component
  contracts, dual-theme references, and deterministic verification.
- Added Chinese system-font fallbacks. No Inter, Berkeley Mono, logo, trademark, or other font/brand asset is bundled.

“Linear” is used only to identify the design inspiration and source fixture. Apache-2.0 does not grant trademark rights.

## Lucide icons

The reference HTML embeds path data for selected Lucide icons from `lucide-react` 1.33.0. Lucide is distributed under
the ISC License; icons derived from Feather retain their MIT License. A verbatim copy of both notices is provided in
[`LICENSE.lucide`](./LICENSE.lucide). Lucide is not affiliated with or responsible for Cherry OJ.

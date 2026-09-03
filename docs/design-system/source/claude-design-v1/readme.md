# Cherry OJ Design System

A dark-mode-native product system for **Cherry OJ**, an online judge. It is a
direct derivative of the design package mounted at `linear-app/` — that
package's greyscale surfaces, Inter Variable typography, spacing, radii and
elevation model are carried over **unchanged**. The single deliberate change is
the accent family: the source's indigo-violet (`#5e6ad2` / `#7170ff` / `#828fff`)
is replaced by a **cherry** family (`#d2042d` / `#ff4d67` / `#ff7088`).

## Sources

- **Mounted codebase:** `linear-app/` (read-only, attached via the Import menu).
  Files read: `DESIGN.md`, `USAGE.md`, `tokens.css`, `components.html`,
  `components.manifest.json`, `design-tokens.json`,
  `system/kit.html`, `system/kit.dark.html`, `system/artifacts/*.html`,
  `preview/{colors,spacing,typography}.html`.
  The package describes itself as "Design System Inspired by Linear" — a curated
  token/CSS fixture, not Linear's own source. It contains **no** logo files, font
  binaries, icons, or product screenshots.
- **Brand brief (from the user):** `cherry-oj-design-system` — "use linear-app's
  design system as the base, swap its accent/theme colour for the colour cherry
  should have, leave everything else alone."
- No Figma file, GitHub repository, or deck was provided.

## Products represented

| Surface | Where | Basis |
|---|---|---|
| Marketing site | `ui_kits/marketing/` | Recreates the source fixture's hero → features → form sections, plus the nav/footer recipes in `DESIGN.md` §4 |
| Judge app | `ui_kits/app/` | App shell composed strictly from `DESIGN.md`'s documented chrome recipes (sidebar, toolbar, palette, cards) — the source ships no screen code |

---

## CONTENT FUNDAMENTALS

The voice is that of a tool that assumes competence. Short declaratives, concrete
numbers, no cheerleading.

- **Person.** Second person for actions and state ("Your submission ran in 42 ms").
  First-person plural only for things the product itself did ("We sent a sign-in
  link"). Never "I".
- **Casing.** Sentence case everywhere — headlines, buttons, nav, table headers.
  The only uppercase is the 12px eyebrow/overline at 0.08em tracking.
  `Start solving`, not `Start Solving`. `Read the docs`, not `Read The Docs`.
- **Length.** Headlines run 4–9 words ("Built for people who ship solutions").
  Leads are one or two sentences, max ~50 characters of measure. Card copy is a
  single sentence, occasionally two.
- **Verbs first in actions.** `Submit`, `Rejudge`, `Import from repository`,
  `Open standings`. No "Click here", no "Get started now".
- **Numbers stay exact.** "1,284 problems", "42 ms", "14 / 14 tests", "23%
  acceptance". Never "blazing fast", never "thousands of".
- **Errors name the cause and the next look.** "Wrong answer on test 14" beats
  "Something went wrong". "That handle is already in use."
- **Technical terms are used plainly** — segment tree, penalty-aware scoreboard,
  toolchain image. The reader is a programmer; the copy does not translate for them.
- **No emoji. No exclamation marks. No em-dash drama in UI strings** (em dashes are
  fine in long-form prose like this readme). No ALL-CAPS emphasis, no bold inside
  running UI copy.
- **Metadata is terse and lowercase-friendly:** "updated 4 minutes ago",
  "17 languages", "test 14".

Vibe: an instrument panel, not a storefront. Confident, unhurried, quiet.

---

## VISUAL FOUNDATIONS

### Colour
Achromatic dark system with exactly one chromatic family. Surfaces step by
luminance: `#010102` deepest canvas → `#08090a` page → `#0f1011` panels/sidebar →
`#191a1b` elevated → `#28282c` hover. Text steps in four tones:
`#f7f8f8` primary (never pure white) → `#d0d6e0` body → `#8a8f98` muted →
`#62666d` meta. Cherry (`#d2042d` fill, `#ff4d67` interactive, `#ff7088` hover,
`#a80324` pressed) appears **only** on the primary button, focus states, active
accents, link text and the occasional accent eyebrow — never as decoration, never
as a large field of colour outside the primary button. Status greens
(`#27a644`, `#10b981`) and amber (`#eab308`) are carried over unchanged.

### Type
Inter Variable with `font-feature-settings: "cv01", "ss03"` on every text node —
without those features it is generic Inter, not this system. Three working
weights: 400 reading, **510 the signature emphasis weight**, 590 maximum
(700 never). Display sizes 72 / 64 / 48px at 510 with -0.022em tracking; 32px at
400/1.13; 24px at 400/1.33; 20px at 590. Body 18 lead / 16 reading / 15 secondary
/ 14 UI, tracking relaxing to normal below 16px. Mono for code and any measured
value.

### Spacing & layout
8px base with optical micro-steps at 7, 11, 19 and 22px — use them when optical
alignment needs it, not as general spacing. Sections are 80px desktop / 48px
tablet / 32px phone, separated by a 1px hairline and nothing else. Content column
1200px max, gutters 24 / 16 / 12px. The app shell uses a fixed 220px sidebar and
a 56px sticky header; both are the only fixed-position chrome.

### Backgrounds
Flat near-black. **No** gradients, no photography, no illustration, no texture,
no noise, no pattern. Whitespace is darkness — space is created by absence, not
by lighter panels. If imagery is ever added it should be a product screenshot on
the dark canvas with a `rgba(255,255,255,0.08)` hairline, `12px 12px 0 0` radius
when it meets a panel edge, and a `rgba(0,0,0,0.4) 0 2px 4px` shadow beneath.
Photographic imagery has no place in the system as it stands; if it must appear,
cool and low-saturation only, never warm, never grainy filters.

### Cards & borders
Cards are translucent, never solid: `rgba(255,255,255,0.02)` fill, 1px
`rgba(255,255,255,0.08)` border, 8px radius, 24px padding. Panels take 12px,
large panels 22px. Borders are always semi-transparent white
(0.05 subtle / 0.08 standard); solid `#23252a`–`#3e3e44` borders appear only on
pills and ghost buttons, where the source uses them.

### Elevation & shadow
Depth is luminance, not shadow. The ladder: flat → `0 1.2px 0` micro shadow on
toolbar buttons → translucent surface + hairline → inset
`rgba(0,0,0,0.2) 0 0 12px inset` for recessed panels → border-as-shadow ring →
`rgba(0,0,0,0.4) 0 2px 4px` for floating elements → a five-layer stack for
dialogs and the command palette. No large soft drop shadows anywhere.

### Interaction states
- **Hover:** background lightens by ~0.02 white (`0.02 → 0.04`, `0.03 → 0.05`);
  text lifts a tone (`--fg-2 → --fg`). Links go `#ff4d67 → #ff7088`. No underline.
- **Press:** darkens rather than shrinks — primary button drops to `#a80324`,
  translucent surfaces fall back one step. No scale transforms, no bounce.
- **Focus:** cherry ring — `0 0 0 2px` cherry at 70% opacity plus a soft
  `0 4px 12px` black. Text fields instead swap their border to `--accent`; no halo.
- **Disabled:** opacity 0.4, `not-allowed` cursor, colours untouched.
- **Selected:** `rgba(255,255,255,0.05)` fill and primary-tone text.

### Motion
150ms for colour and background, 200ms for surface changes, all on
`cubic-bezier(0.2, 0, 0, 1)`. Only opacity, colour and background animate — no
transforms, no springs, no entrance animations, no parallax. Overlays appear
immediately.

### Transparency & blur
Transparency is structural (surface fills, borders). Blur is **not** used
anywhere: the modal backdrop is a flat `rgba(0,0,0,0.85)`, not a frosted pane.

### Corner radii
2px inline badges and toolbar buttons · 4px list rows · 6px buttons and inputs ·
8px cards · 12px panels and dialogs · 22px large panels · pill for chips · 50%
for icon buttons, avatars and status dots.

---

## ICONOGRAPHY

The source package contains **no icon assets** — no sprite, no icon font, no SVG
files, and no glyph inventory in `DESIGN.md` beyond "Linear logomark left-aligned
(SVG icon)" and a 16×16 `.icon` slot in `components.html`.

- **Substitution (flagged):** [Lucide](https://lucide.dev) is used as the icon
  set, at **1.75px stroke**, 24-box, round caps and joins, sized 13–18px. It is
  the closest available match to the source's thin-stroke, geometric 16px slot.
  The UI kits inline the specific Lucide paths they need
  (`ui_kits/marketing/Icons.jsx`, `ui_kits/app/AppIcons.jsx`) rather than loading
  a runtime; swap in the real set when it is supplied.
- Icons are monochrome, inheriting `currentColor` — `--muted` at rest, `--fg` when
  active, `--accent-bright` only when an icon is the accent of a card.
- **Status is conveyed by dots, not icons:** an 8px circle (`--success`,
  `--accent`, `--warn`) or a 2px rounded square at `rgba(255,255,255,0.1)` for
  "not started". This is the source's own pattern, taken from its issue rows.
- **No emoji, ever**, in UI or marketing copy. Unicode is used only for keyboard
  glyphs (`⌘K`) and mathematical/complexity notation (`≤`, `·`, `O(n log n)`) set
  in the mono face.
- **No logo exists.** The source ships no mark, so none was drawn. The brand is
  set as type — "Cherry OJ" in Inter 590 at -0.022em — wherever a mark would go;
  the app sidebar uses a plain cherry square as a placeholder tile. Supply a real
  mark and it drops into `assets/`.

---

## Index

**Root**
- `styles.css` — the single entry point consumers link (`@import` list only)
- `tokens/` — `fonts.css`, `colors.css`, `typography.css`, `spacing.css`,
  `radius.css`, `elevation.css`, `motion.css`, `layout.css`
- `thumbnail.html` — homepage tile
- `SKILL.md` — Agent-Skills-compatible entry point
- `assets/` — **empty**: the source provided no logos, fonts, icons or imagery

**Components** (`components/<group>/`, one `.jsx` + `.d.ts` + `.prompt.md` each)
- `controls/` — **Button**, **IconButton**, **Pill**
- `forms/` — **Input**, **Textarea**, **SearchInput**
- `surfaces/` — **Card**, **Badge**
- `navigation/` — **NavBar**
- `typography/` — **Heading**, **Text**, **Eyebrow**
- `layout/` — **Container**, **Stack**

Every family is drawn from the source's own inventory
(`components.manifest.json` groups: buttons, inputs, cards, badges, links, icons,
typography, layout). **Intentional additions:** `IconButton` (the source
documents an icon-button recipe but gives it no group), `SearchInput` (documented
under Inputs as "Search input"), and `Eyebrow` (a `.eyebrow` selector in the
fixture, promoted to a component). No component was invented beyond these.

**Guidelines** (`guidelines/`) — 20 specimen cards: Colors (6), Type (7),
Spacing (5, incl. radius, elevation, focus/motion), Brand (2).

**UI kits**
- `ui_kits/marketing/` — landing page: `Hero`, `Features`, `SignupSection`,
  `Footer`, `Icons`
- `ui_kits/app/` — judge workspace: `Sidebar`, `ProblemList`, `ProblemView`,
  `CommandPalette`, `AppIcons`

## Known substitutions

1. **Inter Variable** — no font binaries in the source; loaded from the Google
   Fonts variable build (same typeface design). Upload the licensed
   `InterVariable.woff2` to replace it.
2. **Berkeley Mono** — commercial licence, not present. **JetBrains Mono** stands
   in as the nearest match. Please supply the real files.
3. **Lucide icons** — see ICONOGRAPHY above.
4. **No logo mark** — type-only wordmark until one is provided.
5. `--danger` (`#dc2626`) sits close to the cherry accent. It was left unchanged
   per the brief; always pair destructive states with an icon or explicit label so
   they cannot be read as accent.

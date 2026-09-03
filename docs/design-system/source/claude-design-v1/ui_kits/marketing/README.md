# Marketing site UI kit

Recreation of the source package's marketing fixture (`linear-app/components.html`
hero → features → form sections, plus the nav and footer described in
`DESIGN.md` §4 Navigation), rebranded to the cherry accent.

| File | Surface |
|---|---|
| `index.html` | Full page, click-through nav and working signup form |
| `Hero.jsx` | Two-column hero: eyebrow, 48px headline, lead, CTA pair, submissions panel |
| `Features.jsx` | Three-column translucent card grid |
| `SignupSection.jsx` | Split copy + form section, focus-tinted input |
| `Footer.jsx` | Wordmark + three link columns |
| `Icons.jsx` | Lucide glyph paths inlined at 1.75 stroke (substitution — see root readme) |

All chrome comes from the design system components (`NavBar`, `Button`, `Card`,
`Input`, `Pill`, `Badge`, `Heading`, `Text`, `Eyebrow`, `Stack`, `Container`);
nothing is re-implemented locally.

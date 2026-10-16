# Design system

The brief: refined contemporary luxury; dark charcoal surfaces, restrained burgundy accents, warm cream light mode; editorial serif headings, readable sans-serif controls; elegant bottle photography; subtle animation; information density and usability over decoration; accessible.

## Concept: the cellar book

Serious collectors and merchants keep a *cellar book*: a ledger with one line per wine, the vintage written large in the margin, quantities pencilled in and crossed out. House of Vino borrows that, and nothing else, for its one memorable gesture:

**The vintage column.** In every list, the vintage is set in large old-style serif figures in a fixed left column, so a collection reads down the page by year the way a cellar book does. Producer and cuvée sit beside it; quantities, sizes and locations are quiet sans-serif detail on the right. Everything else in the interface stays calm so this rhythm carries the identity.

```
┌──────────────────────────────────────────────┐
│ Cellar                         ⌕   ＋        │  serif title, actions
│ 1,214 bottles · 386 wines                    │  (this line is the only meta string)
├──────────────────────────────────────────────┤
│ 2010  Château Léoville Barton                │  vintage 28sp serif old-style figures
│       Saint-Julien                 6 × 750ml │  region + count/size, sans
│ ──────────────────────────────────────────── │  hairline: a row boundary
│ 2015  Domaine Ponsot                         │
│       Clos de la Roche  Grand Cru  2 × 1.5L  │
│ ──────────────────────────────────────────── │
│   NV  Krug  Grande Cuvée 171ème    3 × 750ml │
│       Champagne                   Ready ●    │  status as word + small mark
└──────────────────────────────────────────────┘
```

Rows are not cards. Hairlines separate rows because they *are* rows in a ledger; cards are reserved for things that are genuinely separate objects (a photo, a lot, a dialog).

## Colour

| Token | Dark (default) | Light ("cream") | Use |
|---|---|---|---|
| `canvas` | `#1B1918` | `#F5EFE4` | App background |
| `surface` | `#242120` | `#FBF7F0` | Sheets, rows on press, inputs |
| `surfaceRaised` | `#2E2A28` | `#FFFFFF` | Dialogs, menus, photo frames |
| `hairline` | `#3A3533` | `#E2D9CB` | Row separators, input borders |
| `ink` | `#EEE7DC` | `#241E1B` | Primary text |
| `inkMuted` | `#B0A699` | `#6A5F57` | Secondary text |
| `inkFaint` | `#7F766C` | `#958A80` | Tertiary text, disabled |
| `burgundy` | `#8E2C3B` | `#7A2333` | Primary buttons, selection, focus ring base |
| `burgundyBright` | `#D27A86` | `#7A2333` | Accent text/icons on dark (meets 4.5:1) |
| `onBurgundy` | `#F8EFEF` | `#FFF8F5` | Text on burgundy |

Burgundy appears only where something is actionable or selected: the primary button on a screen, the selected tab, the active filter count, focus rings. Never as decoration, never as a background wash.

Drinking-window status uses muted, distinct hues, always paired with the word:

| Status | Dark | Light |
|---|---|---|
| Hold | `#8D9BB0` slate | `#4D5E78` |
| Ready | `#9DB38A` sage | `#4C6B3A` |
| Drink soon | `#D2A55C` ochre | `#8A6420` |
| Past window | `#B98A80` faded rose-brown | `#8A4E42` |
| No window | `inkFaint` | `inkFaint` |

All text/background pairs are checked for WCAG AA (4.5:1 body, 3:1 large text and UI marks) in a unit test that computes contrast from these tokens.

## Typography

Two families, clearly distinct, both open-licensed (SIL OFL) and bundled:

- **Libre Caslon** — headings, wine names, vintages. Caslon is the face of English wine merchants' lists and ledgers; its old-style figures make the vintage column. Regular, Italic, Bold.
- **Atkinson Hyperlegible Next** — every control, label, number in a form, table and chart. Designed by the Braille Institute for legibility; its distinct letterforms (I/l/1, 0/O) help with dense figures and small sizes. Regular, Medium, SemiBold, with tabular figures for quantities and prices.

Scale (sp; line height in brackets), set for phones and scaled by the user's font size setting:

| Style | Face | Size | Use |
|---|---|---|---|
| `display` | Caslon Regular | 34 (40) | Wine name on bottle detail |
| `title` | Caslon Regular | 26 (32) | Screen titles |
| `vintage` | Caslon Regular, old-style figures | 28 (30) | The vintage column |
| `heading` | Caslon Bold | 19 (24) | Section headings, producer in rows |
| `cuvee` | Caslon Italic | 17 (22) | Cuvée names |
| `body` | Atkinson Regular | 16 (24) | Notes, paragraphs |
| `label` | Atkinson Medium | 14 (20) | Buttons, field labels, chips |
| `detail` | Atkinson Regular | 14 (20) | Secondary row text |
| `figure` | Atkinson Medium, tabular | 15 (20) | Counts, prices, sizes |
| `caption` | Atkinson Regular | 12 (16) | Timestamps, provenance ("Source: Wine-Searcher, 3 Sep 2026") |

Rules: sentence case everywhere; no all-caps labels; no letter-spaced eyebrows; one italic use (cuvée names) so it always means the same thing.

## Layout and density

- 4 dp base grid; screen gutters 20 dp; row vertical padding 12 dp; minimum touch target 48 × 48 dp.
- Lists are left-aligned on the vintage column (64 dp wide). Numbers right-aligned in tabular figures.
- Phones: single pane. Tablets/landscape ≥ 840 dp: list-detail with the list at 400 dp.
- Bottle photography sits in a fixed 3:4 frame on `surfaceRaised` with a 2 dp radius — photos are presented like a label shot, not a rounded avatar. No photo: a quiet bottle silhouette in the style colour, never a stock image.
- Corner radii: 2 dp (photos, inputs), 12 dp (sheets, dialogs), full (chips, status marks). Different radii signal different kinds of object.
- Elevation by tone (`surface`, `surfaceRaised`), not shadows, in dark mode; a single soft shadow for sheets in light mode.

## Motion

- Motion answers actions: sheets slide up (250 ms, emphasized decelerate), list/detail shares the vintage figure between screens (shared element), a recorded consumption collapses the quantity figure (150 ms).
- No ambient or entrance animations on scroll. Everything respects the OS "reduce motion" setting (durations go to 0, shared elements become cross-fades).

## States

Each screen defines all of these; components exist for each.

| State | Treatment |
|---|---|
| Loading | Skeleton rows that keep the vintage-column rhythm; no spinners for lists under 300 ms (delay before showing) |
| Empty | One sentence saying what this place is for and the action that fills it ("No wines yet. Add a bottle or import a CSV.") with that action as the primary button |
| Error | What failed and what to do, in the interface's voice ("Couldn't read row 14: vintage '20l5' isn't a year. Fix it or skip the row.") — no apologies |
| Offline | A quiet persistent line under the title: "Offline — changes are saved on this phone and will sync." Never blocks local work |
| Permission denied | Explain what needs the permission, offer the manual path, and link to system settings ("Camera access is off. Enter the wine by hand, or allow camera access in Settings.") |
| Success | Inline confirmation where the change happened plus an Undo snackbar for reversible actions ("Opened 1 × Ponsot 2015. Undo") |
| Conflict | A marker on the record and a resolution sheet showing both versions side by side |

## Accessibility

- Dynamic type: all text in `sp`; layouts tested at 200 % font scale; the vintage column wraps above the name when space runs out.
- Screen readers: rows read as one sentence ("Domaine Ponsot, Clos de la Roche Grand Cru, 2015, 2 magnums, ready to drink"); decorative silhouettes are hidden; every icon button has a label; custom actions on rows ("Open a bottle", "Move").
- Colour is never the only signal (status always has a word).
- Focus order follows reading order; visible focus ring in `burgundyBright` 2 dp.
- Contrast tokens unit-tested.

## Writing

Plain verbs, sentence case, the user's vocabulary: *bottles, wines, vintages, cellar, rack, opened, moved, sold, gave away*. An action keeps its name through the flow: the button "Open a bottle" produces "Opened 1 bottle". Ledger vocabulary ("movement", "lot") stays out of the main UI; lots appear as "Purchases".

## Review against the brief

- Dark charcoal + burgundy + cream light mode + serif headings are the brief's own direction, so they are used as asked. The charcoal is warm (`#1B1918`), matching cork and oak rather than a blue-black screen default.
- The generic version of this brief would be a card grid of bottle photos with a serif header. Rejected: it fails "information density" for a 2,000-bottle collection. The cellar-book list with the vintage column is denser and specific to wine.
- Generic "luxury" display serifs (Playfair, Didone fashion faces) were considered and rejected: fragile at list sizes and unrelated to wine's own typographic history. Caslon fits merchants' lists; Atkinson Hyperlegible answers the accessibility requirement directly rather than defaulting to a neutral grotesque.

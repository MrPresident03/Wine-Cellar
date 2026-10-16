# Implementation checklist

A feature is ticked only when it has real behaviour, persists real data, has tests, and builds on both platforms in CI. UI-only placeholders, sample data and buttons without behaviour stay unticked.

Legend: ☐ not started · ◐ in progress · ☑ done (with the evidence in brackets)

## Milestone 0 — Plan and scaffold

- ☑ Inspect the existing repository and record what's kept ([00-repo-survey.md](00-repo-survey.md))
- ☑ Architecture decision record with verified versions ([adr/0001-architecture.md](adr/0001-architecture.md))
- ☑ Database schema ([schema.md](schema.md), `migrations/1.sqm`)
- ☑ Navigation map ([navigation.md](navigation.md))
- ☑ Design system ([design-system.md](design-system.md))
- ☑ KMP project: core, data, ui, androidApp, iosApp (XcodeGen)
- ☑ CI on the branch: JVM + desktop tests, Android APK, Kotlin/Native simulator tests, iOS framework + Xcode build, simulator launch screenshot

## Milestone 1 — Domain core and local database

- ◐ Exact money and decimals; currencies; exchange rates with provenance (property-tested against `java.math`)
- ☐ Repositories: wines, vintages, purchases (lots), places, tags — create/edit/soft-delete, all in transactions that also queue sync work
- ☐ Ledger: record purchase/receive/open/sell/give/loss/move/correct; reversals; balance checks; bottle positions
- ☐ Invariant test: balances recomputed from the ledger equal stored balances after random operation sequences
- ☐ Duplicate detection (same identity key, same vintage) with explicit merge — never across vintages without confirmation
- ☐ Search: FTS index maintenance, scopes, filters, sorts, paging, typo widening, saved and recent searches
- ☐ Search performance test on a 20,000-vintage synthetic collection
- ☐ Migration test harness (v1 database with data → current)

## Milestone 2 — Design system and core screens

- ☐ Theme (dark/light tokens, Caslon + Atkinson Hyperlegible Next), contrast test
- ☐ Components: vintage-column row, buttons, fields, steppers, chips, status mark, sheets, empty/error/offline/permission states
- ☐ Cellar list (wines / places), wine detail, add wine (fast entry), edit wine, record movement, history, place detail with grid
- ☐ Search screen with scopes, filter sheet, sort, grid/list, saved and recent
- ☐ UI tests for: add wine, open a bottle, move bottles, search with filters
- ☐ Screenshots from desktop renderer, Android emulator and iOS simulator in CI

## Milestone 3 — Journal, wishlist, insights, import/export, settings

- ☐ Tasting notes: structured + free text, rating scales, photos, occasions, pairings; linked to openings
- ☐ Wishlist: targets, priorities, quantities, merchant links, status; conversion to a purchase
- ☐ Insights: bottle/wine totals, purchase cost per currency and converted (rates shown), estimated values with source/date, drinking windows, consumption over time, breakdowns
- ☐ CSV import: delimiter/encoding detection, column mapping with guesses (incl. CellarTracker-style headers), validation per row, duplicate detection, partial import with a report
- ☐ Export: inventory, tasting history, financial records (purchases, sales, values) as CSV
- ☐ Settings: currency and rates, preferences, privacy, notifications, local backup/restore

## Milestone 4 — Accounts and sync

- ☐ Supabase migrations mirroring the device schema; global revision sequence; tombstones
- ☐ RLS for every table and role; household invites; pgTAP tests (member, non-member, viewer, removed member)
- ☐ `apply_inventory_movement` (idempotent, locked, non-negative)
- ☐ Auth (email + Sign in with Apple + Google), local-only → account upload
- ☐ Sync engine: outbox, pull by revision, three-way merge, conflicts UI, retries with backoff
- ☐ Integration tests against local Supabase: offline edits, duplicate pushes, concurrent consumption, multi-device edits, failed uploads, permission failures
- ☐ Photos: compression, private storage, upload queue with retry
- ☐ Background sync (WorkManager / BGTaskScheduler)
- ☐ Account deletion (Edge Function) with export offer; tested

## Milestone 5 — Scanning and integrations

- ☐ Barcode scanning (ML Kit / AVFoundation), GTIN validation, candidate matching from own history
- ☐ Label OCR (ML Kit / Vision) → ported label parser → per-field confidence, editable
- ☐ Service interfaces for catalogue, prices, FX; manual paths everywhere; providers off by default
- ☐ Permission-denied and no-camera paths

## Milestone 6 — Hardening and release

- ☐ Accessibility pass (TalkBack, VoiceOver, 200 % text)
- ☐ Performance on real devices with 5,000+ bottles and photos
- ☐ Drinking-window reminders
- ☐ Release signing (Android upload key, Apple distribution), store metadata, privacy labels
- ☐ Owner's device test pass on iPhone and Android (checklist in README)

## Acceptance criteria tracking

| Criterion | Milestone |
|---|---|
| Add and edit wines manually | 1, 2 |
| Quantities, purchases, batches, locations | 1, 2 |
| Consumption and transfers without corruption | 1 (ledger + invariant tests), 4 (server) |
| Search and filter large collections | 1, 2 |
| Wishlist and tasting history | 3 |
| Import and export | 3 |
| Offline and reliable sync | 4 |
| Private collections, household permissions | 4 |
| Runs on iOS and Android | every milestone (CI); 6 (devices) |

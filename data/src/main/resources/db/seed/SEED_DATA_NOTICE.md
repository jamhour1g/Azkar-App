# Seed data provenance and editorial changes

`library.json` is the single maintained dataset for remembrances, tags, localized collection
wording and explicit ordered memberships. Edit it directly. The initial content was imported
once from the pinned Muslim Data database and curated as described below.

## Source

- Repository: https://github.com/my-prayers/muslim-data-android
- Commit: `f7968b8765bfa23706751edadc3fed2fa17a9864`
- Artifact: `muslim-data/src/main/assets/database/muslim_db_v2.5.1.db`
- SHA-256: `527913aaf9813d922f30f33a19cc59cea59d638206e57b2d8e28fb13d519deaa`
- The pinned LICENSE contains Apache-2.0; its text is retained as `LICENSE-muslim-data.txt`.
  The pinned README instead claims MIT. We retain the LICENSE attribution and record this
  discrepancy rather than resolving it by assumption. Translator identities and a specific
  Hisnul Muslim edition are not established by these files.

## Editorial decisions

- Preserve all 287 source items, including repeated Arabic wording used in different contexts.
  Stable `muslim-data:<item ID>` keys identify seed records, independently of database IDs.
- Preserve supplied Arabic and English wording and diacritics, except for item-level corrections
  recorded below. Normalize display-text line endings to LF; keep meaningful internal line breaks.
- Put the supplied English bibliographic reference in the model's `source`, with Arabic fallback
  only if English is absent. Preserve both reference languages in per-item provenance.
- Use each source chapter's localized title as occasion context in `explanation`, rather than
  misrepresenting bibliographic references as commentary. No new religious commentary is authored.
- Keep grades `UNSPECIFIED`: the source does not contain structured grades. No virtues, repetition
  counts, translations or authenticity claims are inferred.
- Assign tags through reviewed chapter IDs, never translated-name keyword matching. The Morning
  collection uses chapter 27; Evening uses chapter 28; After Prayer uses chapter 25 only.
  A separate Prayer & Worship collection preserves access to
  ablution, mosque and prayer-internal chapters without calling them post-prayer content.
- The combined Morning & Evening shelf uses the 13 explicitly listed morning records whose Arabic
  wording is also present in the evening chapter. Both contextual records remain in the library;
  this shelf does not collapse their metadata or assert identical virtues/counts.
- Publisher attribution lives in provenance, not in a subject tag attached to every remembrance.
- Collection order, member order, wording and tag translations are explicit in the resource.
  User favorites, pins and other per-install state are not part of the dataset.
- Future wording changes, exclusions or grades require item-level evidence and an editorial
  explanation recorded in this notice. Never silently rewrite religious content or infer a grade
  from a book title.

## Editing and verification

Edit `library.json` directly and review its diff. Keep seed keys stable, reference tags by key,
and list each collection's member keys in the intended reading order. Keep provenance alongside
content, and preserve the source references when changing display wording. There is no generator
or separate curation dataset to synchronize.

Run from the repository root:

```sh
./gradlew :data:test :data:detekt :data:spotlessCheck
```

The runtime validates keys, languages, enum values and links before inserting any seed rows.
Bootstrap inserts remembrances and collections in one transaction. Existing populated libraries
are preserved; this rebuild does not replace user content or migrate older seeded libraries.
See `docs/seed-data-research.md` for evidence and limits: source-faithful curation is not independent
scholarly authentication of every entry.

### Reviewed corrections

The 2026-10-25 pass corrected clear English typos and duplicated words, fixed evening
references to night in items 100 and 112, and corrected corresponding Arabic spelling in
those items plus the typo in item 97. Item 101 now translates *al-maṣīr* as “our return.”
Item 121 retains the supplied translation of Qur'an 2:285–286 once; the duplicate rendering
of verse 286 was removed and the recitation note moved to the explanation. Item 122's
bedding instruction is now a separate explanation rather than a broken fragment appended to
the supplication. Item 250 now translates *qurbatan ilayka* as nearness to Allah, not a
sacrificial offering. Item 276's English grammar was repaired, item 279's quotation was
made readable, and item 284 now preserves the distinction between the best supplication and
the best remembrance. These meaning-level corrections were checked against the linked primary
reports: [Hisn 77](https://sunnah.com/hisn:77), [Hisn 78](https://sunnah.com/hisn:78),
[Bukhari 6361](https://sunnah.com/bukhari:6361), and [Tirmidhi 3383](https://sunnah.com/tirmidhi:3383).
Original imported references remain intact in per-item provenance. The pass also corrected obvious English spelling/grammar issues, clarified the morning count note
in item 93, separated the leftward action note in item 158, and corrected item 92’s occasion title.
These edits do not establish new hadith grades or independently authenticate the remaining translations.

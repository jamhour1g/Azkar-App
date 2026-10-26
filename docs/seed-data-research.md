# Seed data provenance and curation research

Historical research checked 2026-10-25, before the seed rebuild. Scope: the former importer
and notice plus direct, read-only SQLite queries. The research itself changed no runtime code
or bundled data. The current library is maintained directly in `library.json`; see the
[editing guide and notice](../data/src/main/resources/db/seed/SEED_DATA_NOTICE.md).
All upstream links below pin commit
`f7968b8765bfa23706751edadc3fed2fa17a9864`.

## Provenance and licensing

- The former importer downloaded [muslim_db_v2.5.1.db](https://github.com/my-prayers/muslim-data-android/blob/f7968b8765bfa23706751edadc3fed2fa17a9864/muslim-data/src/main/assets/database/muslim_db_v2.5.1.db).
  Downloaded SHA-256: `527913aaf9813d922f30f33a19cc59cea59d638206e57b2d8e28fb13d519deaa`.
  Keep both commit/path and checksum in rebuild provenance; the artifact filename is not a complete source identity.
- The pinned [LICENSE](https://github.com/my-prayers/muslim-data-android/blob/f7968b8765bfa23706751edadc3fed2fa17a9864/LICENSE)
  contains Apache License 2.0, supporting the bundled notice's choice. However, the same commit's
  [README](https://github.com/my-prayers/muslim-data-android/blob/f7968b8765bfa23706751edadc3fed2fa17a9864/README.md)
  claims MIT and describes the azkar as Hisnul Muslim. Record this conflict; do not silently relabel
  the data MIT. These files do not establish translator identities, a particular book edition, or
  separate rights clearance for every underlying translation. Seek upstream clarification for those claims.
- For Apache redistribution, retain the license, applicable notices, and a clear statement of changes;
  a short `License: Apache-2.0` label alone is not the license text. See
  [Apache 2.0 section 4](https://www.apache.org/licenses/LICENSE-2.0#redistribution).

## What the pinned database actually represents

The following are direct queries of the linked SQLite artifact, not assumptions from current upstream documentation:

- **11 categories → 133 chapters → 287 items.** `azkar_item._id` identifies an item;
  `chapter_id` links it to `azkar_chapter._id`; chapters link to categories. IDs are dataset keys,
  not demonstrated universal Hisnul Muslim numbering. Preserve item, chapter, and category IDs
  separately rather than relying on translated names or the rendered `Muslim Data Azkars #…` label.
- `azkar_item.item` stores base Arabic; `azkar_item_translation` stores language-specific text.
  Every item has one row for each of `ar`, `en`, `ckb`, `ckb_BADINI`, `fa`, `ru`.
  Arabic translation differs from base Arabic for three items: preserve the current explicit Arabic
  selection rule rather than treating these columns as guaranteed duplicates.
- Every item has exactly one `azkar_reference` row and one reference translation in each of those
  six languages. Arabic/English item text and references are all nonblank; the shortest trimmed
  English item is eight characters. Missing-reference placeholders and minimum English-length
  filtering do not repair any missing content in this pin.
- References are **free text**, including bibliographic locations, Qur'an citations, and occasional
  attributed authenticity statements. For example, item/reference 140 says Al-Albani graded it
  authentic; 183 mentions an authentic chain. There is **no structured grade field**, narrator
  field, or repetition-count field in these azkar tables. Keep `UNSPECIFIED` unless a separately
  reviewed, cited grading source supports an explicit mapping; collection names or book titles
  containing “Sahih” do not establish an individual grade.

## Corrections and rebuild constraints

Findings below describe the pre-rebuild Kotlin/TSV importer and the pinned artifact. The
[current notice](../data/src/main/resources/db/seed/SEED_DATA_NOTICE.md) documents the implemented
corrections and direct-edit workflow:

1. **Use chapter membership for timing.** Chapter 27 is morning (items 75–97), chapter 28 evening
   (98–118). Category 1 is named `Morning & Evening` and also contains chapters 1 and 29–32
   (waking/sleep/dream contexts). Searching category/chapter strings makes every category-1 item
   both morning and evening. Explicit curated chapter-ID mappings avoid presenting sleep or waking
   content as a prescribed morning/evening remembrance merely because of its category.
2. **Do not discard context when deduplicating.** Current cleaned-Arabic deduplication reduces
   287 source items to 270 distinct texts (17 duplicate pairs). Thirteen pairs cross morning and
   evening chapters, e.g. 75/98; other pairs include 9/12 (undressing/ablution) and 141/157
   (anxiety/debt). Preserve all source memberships, translations, and references, even if a shared
   text is stored once. Arabic equality alone does not establish interchangeable use or metadata.
3. **Preserve sourced text; distinguish references from explanations.** The generator puts reference
   text into explanation columns. Label it as source/reference material, not newly authored commentary.
   Keep the supplied Arabic/English text; any correction needs item IDs, before/after evidence, and
   a primary citation. Do not fill gaps with invented translations, virtues, counts, or grades.
4. **Fix serialization before regeneration.** `Regex("(?U)\\\\s+")` in the Kotlin source matches
   a literal backslash followed by `s`, rather than whitespace. The pin contains actual newlines
   in 8 Arabic item texts, 1 English item text, and 14 Arabic references (no tabs in those ar/en
   fields). Consequently current cleaning can break the claimed one-record-per-TSV-line format.
   Normalize/escape record delimiters deliberately and validate six fields per record; retain raw
   upstream text separately for audit. These are field-row counts, not distinct-item totals.
5. **Update the notice alongside a later rebuild.** Its dependency claim includes Ktor, while the
   inspected script uses the JDK HTTP client and only declares `sqlite-jdbc`. Describe actual
   normalization, deduplication/context handling, provenance, and license ambiguity accurately.

Verification limits: queried the pinned artifact and inspected local source; did not regenerate the
TSV, run the Kotlin generator, validate runtime migration, or independently authenticate hadith or
translate religious text. Upstream packaging/license statements establish repository provenance,
not scholarly verification of each entry.

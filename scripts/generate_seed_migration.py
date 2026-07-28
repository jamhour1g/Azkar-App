from __future__ import annotations

import sqlite3
import tempfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path

DATASET_DB_URL = (
    "https://raw.githubusercontent.com/"
    "my-prayers/muslim-data-android/"
    "f7968b8765bfa23706751edadc3fed2fa17a9864/"
    "muslim-data/src/main/assets/database/muslim_db_v2.5.1.db"
)

OUTPUT_SQL = Path("data/src/main/resources/db/migration/V2__seed_remembrances_muslim_data.sql")

SOURCE_NAME = "Muslim Data Azkars"
SOURCE_REPO = "https://github.com/my-prayers/muslim-data-android"
SOURCE_COMMIT = "f7968b8765bfa23706751edadc3fed2fa17a9864"
SOURCE_LICENSE = "Apache-2.0"

GRADE = "UNSPECIFIED"

TAG_DATASET = "Muslim Data"
TAG_MORNING = "Morning"
TAG_EVENING = "Evening"


@dataclass(frozen=True)
class Row:
    item_id: int
    category_name: str
    chapter_name: str
    arabic: str
    english: str
    reference_ar: str
    reference_en: str


def normalize_whitespace(value: str) -> str:
    return " ".join((value or "").replace("\r", " ").replace("\n", " ").replace("\t", " ").split())


def sanitize_text(value: str) -> str:
    value = normalize_whitespace(value)
    return value.replace("\u200f", "").replace("\u200e", "")


def contains_arabic(text: str) -> bool:
    for char in text:
        code = ord(char)
        if 0x0600 <= code <= 0x06FF:
            return True
    return False


def looks_morning(*values: str) -> bool:
    lower = " ".join(values).lower()
    return "morning" in lower or "waking" in lower or "sunrise" in lower


def looks_evening(*values: str) -> bool:
    lower = " ".join(values).lower()
    return "evening" in lower or "night" in lower or "sleep" in lower


def sql_quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def fetch_rows() -> list[Row]:
    db_path = Path(tempfile.gettempdir()) / "muslim_db_v2.5.1.db"
    urllib.request.urlretrieve(DATASET_DB_URL, db_path)

    sql = """
    SELECT
      i._id AS item_id,
      COALESCE(ct.category_name, 'General') AS category_name,
      COALESCE(ch_t.chapter_name, 'General') AS chapter_name,
      COALESCE(ar_t.item_translation, i.item) AS arabic_text,
      en_t.item_translation AS english_text,
      COALESCE(ref_ar.reference_ar, '') AS reference_ar,
      COALESCE(ref_en.reference_en, '') AS reference_en
    FROM azkar_item i
    JOIN azkar_chapter ch
      ON ch._id = i.chapter_id
    LEFT JOIN azkar_chapter_translation ch_t
      ON ch_t.chapter_id = ch._id AND ch_t.language = 'en'
    JOIN azkar_category c
      ON c._id = ch.category_id
    LEFT JOIN azkar_category_translation ct
      ON ct.category_id = c._id AND ct.language = 'en'
    LEFT JOIN azkar_item_translation ar_t
      ON ar_t.item_id = i._id AND ar_t.language = 'ar'
    LEFT JOIN azkar_item_translation en_t
      ON en_t.item_id = i._id AND en_t.language = 'en'
    LEFT JOIN (
      SELECT r.item_id, GROUP_CONCAT(rt.reference, ' | ') AS reference_ar
      FROM azkar_reference r
      JOIN azkar_reference_translation rt
        ON rt.reference_id = r._id AND rt.language = 'ar'
      GROUP BY r.item_id
    ) ref_ar
      ON ref_ar.item_id = i._id
    LEFT JOIN (
      SELECT r.item_id, GROUP_CONCAT(rt.reference, ' | ') AS reference_en
      FROM azkar_reference r
      JOIN azkar_reference_translation rt
        ON rt.reference_id = r._id AND rt.language = 'en'
      GROUP BY r.item_id
    ) ref_en
      ON ref_en.item_id = i._id
    WHERE en_t.item_translation IS NOT NULL
    ORDER BY i._id
    """

    rows: list[Row] = []
    seen_arabic: set[str] = set()

    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        for record in conn.execute(sql):
            arabic = sanitize_text(record["arabic_text"])
            english = sanitize_text(record["english_text"])
            category_name = sanitize_text(record["category_name"])
            chapter_name = sanitize_text(record["chapter_name"])
            reference_ar = sanitize_text(record["reference_ar"])
            reference_en = sanitize_text(record["reference_en"])

            if not arabic or not english:
                continue
            if not contains_arabic(arabic):
                continue
            if len(english) < 3:
                continue
            if arabic in seen_arabic:
                continue
            seen_arabic.add(arabic)

            rows.append(
                Row(
                    item_id=int(record["item_id"]),
                    category_name=category_name,
                    chapter_name=chapter_name,
                    arabic=arabic,
                    english=english,
                    reference_ar=reference_ar,
                    reference_en=reference_en,
                )
            )

    return rows


def tag_insert_sql(tag_name: str) -> str:
    return (
        "INSERT INTO tag (name) "
        f"SELECT {sql_quote(tag_name)} "
        "WHERE NOT EXISTS ("
        f"SELECT 1 FROM tag t WHERE lower(t.name) = lower({sql_quote(tag_name)})"
        ");"
    )


def attach_tag_sql(source_value: str, tag_name: str) -> str:
    return (
        "INSERT INTO remembrance_tag (remembrance_id, tag_id) "
        "SELECT r.id, t.id "
        "FROM remembrance r "
        "JOIN tag t ON lower(t.name) = lower(" + sql_quote(tag_name) + ") "
        "WHERE r.source = " + sql_quote(source_value) + " "
        "AND NOT EXISTS ("
        "SELECT 1 FROM remembrance_tag rt WHERE rt.remembrance_id = r.id AND rt.tag_id = t.id"
        ");"
    )


def render_sql(rows: list[Row]) -> str:
    lines: list[str] = []

    lines.append("-- Seed data sourced from public web dataset.")
    lines.append(f"-- Source: {SOURCE_NAME}")
    lines.append(f"-- Repository: {SOURCE_REPO}")
    lines.append(f"-- Commit: {SOURCE_COMMIT}")
    lines.append(f"-- License: {SOURCE_LICENSE}")
    lines.append("-- Languages imported: ar, en")
    lines.append("--")
    lines.append("-- This migration is deterministic and safe to re-run on clean databases.")
    lines.append("")

    lines.append(tag_insert_sql(TAG_DATASET))
    lines.append(tag_insert_sql(TAG_MORNING))
    lines.append(tag_insert_sql(TAG_EVENING))

    category_tags = sorted({row.category_name for row in rows if row.category_name})
    for tag in category_tags:
        lines.append(tag_insert_sql(tag))

    lines.append("")

    for index, row in enumerate(rows, start=1):
        source_value = f"{SOURCE_NAME} #{row.item_id} - {row.chapter_name}"
        explanation_en = row.reference_en or "Reference unavailable in source dataset."
        explanation_ar = row.reference_ar or "المرجع غير متاح في بيانات المصدر."

        lines.append("-- -----------------------------------------------------------------------------")
        lines.append(f"-- {index}: item {row.item_id}")
        lines.append("INSERT INTO remembrance (source, grade)")
        lines.append(
            f"SELECT {sql_quote(source_value)}, {sql_quote(GRADE)} "
            "WHERE NOT EXISTS ("
            "SELECT 1 FROM remembrance r "
            "JOIN remembrance_translation rt ON rt.remembrance_id = r.id "
            f"WHERE r.source = {sql_quote(source_value)} "
            "AND rt.locale_code = 'ar' "
            f"AND rt.text = {sql_quote(row.arabic)}"
            ");"
        )

        lines.append("INSERT INTO remembrance_translation (remembrance_id, locale_code, text)")
        lines.append(
            "SELECT r.id, 'ar', "
            f"{sql_quote(row.arabic)} "
            "FROM remembrance r "
            f"WHERE r.source = {sql_quote(source_value)} "
            "AND NOT EXISTS ("
            "SELECT 1 FROM remembrance_translation rt "
            f"WHERE rt.remembrance_id = r.id AND rt.locale_code = 'ar' AND rt.text = {sql_quote(row.arabic)}"
            ");"
        )

        lines.append("INSERT INTO remembrance_translation (remembrance_id, locale_code, text)")
        lines.append(
            "SELECT r.id, 'en', "
            f"{sql_quote(row.english)} "
            "FROM remembrance r "
            f"WHERE r.source = {sql_quote(source_value)} "
            "AND NOT EXISTS ("
            "SELECT 1 FROM remembrance_translation rt "
            "WHERE rt.remembrance_id = r.id AND rt.locale_code = 'en'"
            ");"
        )

        lines.append("INSERT INTO explanation_translation (remembrance_id, locale_code, text)")
        lines.append(
            "SELECT r.id, 'en', "
            f"{sql_quote(explanation_en)} "
            "FROM remembrance r "
            f"WHERE r.source = {sql_quote(source_value)} "
            "AND NOT EXISTS ("
            "SELECT 1 FROM explanation_translation et "
            "WHERE et.remembrance_id = r.id AND et.locale_code = 'en'"
            ");"
        )

        lines.append("INSERT INTO explanation_translation (remembrance_id, locale_code, text)")
        lines.append(
            "SELECT r.id, 'ar', "
            f"{sql_quote(explanation_ar)} "
            "FROM remembrance r "
            f"WHERE r.source = {sql_quote(source_value)} "
            "AND NOT EXISTS ("
            "SELECT 1 FROM explanation_translation et "
            "WHERE et.remembrance_id = r.id AND et.locale_code = 'ar'"
            ");"
        )

        lines.append(attach_tag_sql(source_value, TAG_DATASET))

        if row.category_name:
            lines.append(attach_tag_sql(source_value, row.category_name))

        if looks_morning(row.category_name, row.chapter_name):
            lines.append(attach_tag_sql(source_value, TAG_MORNING))

        if looks_evening(row.category_name, row.chapter_name):
            lines.append(attach_tag_sql(source_value, TAG_EVENING))

        lines.append("")

    return "\n".join(lines).strip() + "\n"


def main() -> None:
    rows = fetch_rows()
    if not rows:
        raise RuntimeError("No valid rows fetched from source dataset")

    sql = render_sql(rows)
    OUTPUT_SQL.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_SQL.write_text(sql, encoding="utf-8")

    print(f"Generated {OUTPUT_SQL} with {len(rows)} remembrances")


if __name__ == "__main__":
    main()

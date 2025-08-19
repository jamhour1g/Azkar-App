PRAGMA foreign_keys = ON;

-- ============================================================================
-- Table: remembrance
-- Each row represents one “remembrance” (اذكار): the core zikr record itself.
--
-- Columns:
--   id         : Unique auto-incrementing identifier for this zikr.
--   source     : Optional free-text field for the hadith source (e.g. 'صحيح أبي داود').
--   grade      : Reliability grade of the hadith; nullable. If provided, must be
--                one of ('Sahih','Hasan','Daif'). Enforced via CHECK.
--   created_at : unixepoch() when the row was inserted.
--   updated_at : unixepoch() auto-updated via trigger on any UPDATE.
--
-- Notes:
--   - `grade` is optional; set it only when known. The CHECK constraint ensures
--     consistency without preventing NULLs.
--   - created_at / updated_at support feeds, ordering, sync, and diffs.
-- ============================================================================
CREATE TABLE IF NOT EXISTS remembrance
(
    id         INTEGER PRIMARY KEY AUTOINCREMENT, -- zikr identifier
    source     TEXT,                              -- nullable hadith source description
    grade      TEXT                               -- reliability grade (nullable)
        CHECK (grade IN ('SAHIH', 'HASAN', 'DAIF', 'UNSPECIFIED') OR grade IS NULL),
    created_at INTEGER NOT NULL DEFAULT (unixepoch()),
    updated_at INTEGER NOT NULL DEFAULT (unixepoch())
);

-- ============================================================================
-- Trigger: trg_remembrance__set_updated_at
-- Purpose:
--   Keep `remembrance.updated_at` in sync on any UPDATE.
-- Details:
--   - Runs AFTER UPDATE to avoid SQLite's limitation of writing to NEW in BEFORE triggers.
--   - Guard (WHEN NEW.updated_at = OLD.updated_at) prevents infinite recursion.
-- ============================================================================
CREATE TRIGGER IF NOT EXISTS trg_remembrance__set_updated_at
    AFTER UPDATE
    ON remembrance
    FOR EACH ROW
    WHEN NEW.updated_at = OLD.updated_at
BEGIN
    UPDATE remembrance
    SET updated_at = unixepoch()
    WHERE id = OLD.id;
END;


-- ============================================================================
-- Table: remembrance_translation
-- Each row holds a translation of the core zikr text for one locale.
-- One-to-many: a single remembrance can have translations in multiple languages.
--
-- Columns:
--   id             : Unique auto-incrementing identifier for this translation.
--   remembrance_id : FK back to remembrance(id). ON DELETE CASCADE ensures
--                    deleting a zikr removes its translations.
--   locale_code    : Language code (e.g. 'ar', 'en-US'). Keep short and normalized.
--   text           : The translated zikr text (non-empty).
--   created_at     : unixepoch() when inserted.
--   updated_at     : unixepoch() auto-updated via trigger.
--
-- Constraints:
--   - UNIQUE(remembrance_id, locale_code) prevents duplicate locale rows.
--   - CHECK(length(text) > 0) guards empty strings.
-- ============================================================================
CREATE TABLE IF NOT EXISTS remembrance_translation
(
    id             INTEGER PRIMARY KEY AUTOINCREMENT,         -- translation record ID
    remembrance_id INTEGER NOT NULL                           -- reference to parent zikr
        REFERENCES remembrance (id)
            ON DELETE CASCADE,
    locale_code    TEXT    NOT NULL,                          -- locale of this translation
    text           TEXT    NOT NULL CHECK (length(text) > 0), -- translated zikr text
    created_at     INTEGER NOT NULL DEFAULT (unixepoch()),
    updated_at     INTEGER NOT NULL DEFAULT (unixepoch()),
    UNIQUE (remembrance_id, locale_code)
);

-- ============================================================================
-- Trigger: trg_rt__set_updated_at
-- Purpose: Keep `remembrance_translation.updated_at` current on UPDATE.
-- Guard prevents recursion by only firing the internal UPDATE once.
-- ============================================================================
CREATE TRIGGER IF NOT EXISTS trg_rt__set_updated_at
    AFTER UPDATE
    ON remembrance_translation
    FOR EACH ROW
    WHEN NEW.updated_at = OLD.updated_at
BEGIN
    UPDATE remembrance_translation
    SET updated_at = unixepoch()
    WHERE id = OLD.id;
END;

-- ============================================================================
-- Index: idx_rt__by_remembrance
-- Purpose: Speed lookups of translations for a given remembrance (detail screens).
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_rt__by_remembrance
    ON remembrance_translation (remembrance_id);

-- ============================================================================
-- Index: idx_rt__by_locale
-- Purpose: Speed filtering by current UI language / preload by locale.
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_rt__by_locale
    ON remembrance_translation (locale_code);


-- ============================================================================
-- Table: explanation_translation
-- Each row holds a translation of the explanation/commentary for a zikr.
-- Similar structure to remembrance_translation.
--
-- Columns:
--   id             : Unique auto-incrementing identifier.
--   remembrance_id : FK back to remembrance(id). Cascades on delete.
--   locale_code    : Language code for this explanation.
--   text           : The explanatory text (non-empty).
--   created_at     : unixepoch() when inserted.
--   updated_at     : unixepoch() auto-updated via trigger.
--
-- Constraints:
--   - UNIQUE(remembrance_id, locale_code) prevents duplicates per locale.
--   - CHECK(length(text) > 0) guards empty strings.
-- ============================================================================
CREATE TABLE IF NOT EXISTS explanation_translation
(
    id             INTEGER PRIMARY KEY AUTOINCREMENT,         -- explanation record ID
    remembrance_id INTEGER NOT NULL                           -- reference to parent zikr
        REFERENCES remembrance (id)
            ON DELETE CASCADE,
    locale_code    TEXT    NOT NULL,                          -- locale of explanation
    text           TEXT    NOT NULL CHECK (length(text) > 0), -- explanatory text
    created_at     INTEGER NOT NULL DEFAULT (unixepoch()),
    updated_at     INTEGER NOT NULL DEFAULT (unixepoch()),
    UNIQUE (remembrance_id, locale_code)
);

-- ============================================================================
-- Trigger: trg_et__set_updated_at
-- Purpose: Keep `explanation_translation.updated_at` current on UPDATE.
-- ============================================================================
CREATE TRIGGER IF NOT EXISTS trg_et__set_updated_at
    AFTER UPDATE
    ON explanation_translation
    FOR EACH ROW
    WHEN NEW.updated_at = OLD.updated_at
BEGIN
    UPDATE explanation_translation
    SET updated_at = unixepoch()
    WHERE id = OLD.id;
END;

-- ============================================================================
-- Index: idx_et__by_remembrance
-- Purpose: Speed explanation fetches by zikr.
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_et__by_remembrance
    ON explanation_translation (remembrance_id);

-- ============================================================================
-- Index: idx_et__by_locale
-- Purpose: Speed explanation filtering by locale.
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_et__by_locale
    ON explanation_translation (locale_code);


-- ============================================================================
-- Table: tag
-- Each row defines one tag that can be applied to remembrances.
--
-- Columns:
--   id         : Unique auto-incrementing identifier for the tag.
--   name       : The tag label (e.g. 'Morning', 'Evening', 'Juma', or custom).
--   created_at : unixepoch() when inserted.
--
-- Constraints:
--   - Case-insensitive uniqueness so 'Morning' and 'morning' don't both exist.
-- ============================================================================
CREATE TABLE IF NOT EXISTS tag
(
    id         INTEGER PRIMARY KEY AUTOINCREMENT, -- tag identifier
    name       TEXT    NOT NULL,                  -- tag name
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
);

-- ============================================================================
-- Unique Index: uq_tag__name_nocase
-- Purpose:
--   Enforce case-insensitive uniqueness on tag names (prevents 'Morning' vs 'morning').
-- Note:
--   Kept separate from table definition for cross-platform clarity.
-- ============================================================================
CREATE UNIQUE INDEX IF NOT EXISTS uq_tag__name_nocase
    ON tag (name COLLATE NOCASE);


-- ============================================================================
-- Table: remembrance_tag
-- Join table implementing many-to-many between remembrances and tags.
-- Each row links one remembrance to one tag.
--
-- Columns:
--   remembrance_id : FK to remembrance(id).
--   tag_id         : FK to tag(id).
--
-- Constraints:
--   - Composite PK ensures each pair only appears once.
--   - Deletions cascade so removing a zikr or tag cleans up links, without
--     affecting the other table's base rows beyond the link.
-- ============================================================================
CREATE TABLE IF NOT EXISTS remembrance_tag
(
    remembrance_id INTEGER NOT NULL -- linked zikr
        REFERENCES remembrance (id)
            ON DELETE CASCADE,
    tag_id         INTEGER NOT NULL -- linked tag
        REFERENCES tag (id)
            ON DELETE CASCADE,
    PRIMARY KEY (remembrance_id, tag_id)
);

-- ============================================================================
-- Index: idx_remembrance_tag__by_remembrance
-- Purpose: Fast “list tags for zikr” queries and cascades.
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_remembrance_tag__by_remembrance
    ON remembrance_tag (remembrance_id);

-- ============================================================================
-- Index: idx_remembrance_tag__by_tag
-- Purpose: Fast “list zikr for a given tag” queries.
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_remembrance_tag__by_tag
    ON remembrance_tag (tag_id);


-- ============================================================================
-- Table: favorite
-- Purpose:
--   Stores remembrances (اذكار) marked as favorites by the single user.
--   Allows quick access to frequently used or personally significant zikr.
--
-- Columns:
--   remembrance_id : Foreign key to remembrance(id). Also the PK.
--   created_at     : unixepoch() when the favorite was added.
--
-- Constraints & Behavior:
--   - PRIMARY KEY(remembrance_id) enforces uniqueness (boolean “is favorite”).
--   - ON DELETE CASCADE removes the favorite if its zikr is deleted.
-- =========================================================================
CREATE TABLE IF NOT EXISTS favorite
(
    remembrance_id INTEGER PRIMARY KEY
        REFERENCES remembrance (id)
            ON DELETE CASCADE,
    created_at     INTEGER NOT NULL DEFAULT (unixepoch())
);

-- =========================================================================
-- View: remembrance_with_favorite
-- Purpose:
--   Show all remembrances with a computed `is_favorite` flag (0 or 1).
--   The flag is 1 if there is a matching row in `favorite`, otherwise 0.
--
-- Why the NULL check works:
--   In the `favorite` table, `remembrance_id` is never NULL because it’s a PK.
--   But after a LEFT JOIN, if there’s no match in `favorite`, all columns from
--   `f` will be NULL in the result set. That’s what we test.
--
-- Usage examples:
--   -- All favorites:
--   SELECT * FROM remembrance_with_favorite WHERE is_favorite = 1;
--   -- All non-favorites:
--   SELECT * FROM remembrance_with_favorite WHERE is_favorite = 0;
-- =========================================================================
CREATE VIEW IF NOT EXISTS remembrance_with_favorite AS
SELECT r.*,
       CASE WHEN f.remembrance_id IS NOT NULL THEN 1 ELSE 0 END AS is_favorite
FROM remembrance r
         LEFT JOIN favorite f ON f.remembrance_id = r.id;


-- ============================================================================
-- OPTIONAL MODULE: Full‑Text Search (FTS5)
-- Enable if you need fast text search over translations.
-- Strategy:
--   - Contentless FTS table kept in sync via insert/update/delete triggers.
--   - Extend or duplicate to include explanation_translation if desired.
-- ============================================================================
CREATE VIRTUAL TABLE IF NOT EXISTS remembrance_fts
    USING fts5
(
    remembrance_id UNINDEXED,
    locale_code,
    text,
    content=''
);

-- ============================================================================
-- Trigger: trg_fts__rt_after_insert
-- Purpose: On new translation, index it in FTS.
-- ============================================================================
CREATE TRIGGER IF NOT EXISTS trg_fts__rt_after_insert
    AFTER INSERT
    ON remembrance_translation
    FOR EACH ROW
BEGIN
    INSERT INTO remembrance_fts(remembrance_id, locale_code, text)
    VALUES (NEW.remembrance_id, NEW.locale_code, NEW.text);
END;

-- ============================================================================
-- Trigger: trg_fts__rt_after_update
-- Purpose: On translation update, refresh its FTS row.
-- Strategy:
--   Update-in-place by matching (remembrance_id, locale_code).
-- ============================================================================
CREATE TRIGGER IF NOT EXISTS trg_fts__rt_after_update
    AFTER UPDATE
    ON remembrance_translation
    FOR EACH ROW
BEGIN
    UPDATE remembrance_fts
    SET locale_code = NEW.locale_code,
        text        = NEW.text
    WHERE remembrance_id = OLD.remembrance_id
      AND locale_code = OLD.locale_code;
END;

-- ============================================================================
-- Trigger: trg_fts__rt_after_delete
-- Purpose: Remove translation from the FTS index when deleted.
-- ============================================================================
CREATE TRIGGER IF NOT EXISTS trg_fts__rt_after_delete
    AFTER DELETE
    ON remembrance_translation
    FOR EACH ROW
BEGIN
    DELETE
    FROM remembrance_fts
    WHERE remembrance_id = OLD.remembrance_id
      AND locale_code = OLD.locale_code;
END;

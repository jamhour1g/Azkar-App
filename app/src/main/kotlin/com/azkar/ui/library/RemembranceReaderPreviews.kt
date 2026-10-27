package com.azkar.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.azkar.data.storage.CoverStorage
import com.azkar.domain.model.Remembrance
import com.azkar.domain.model.impl.RemembranceImpl
import com.azkar.domain.model.impl.TagImpl
import com.azkar.i18n.ARABIC
import com.azkar.i18n.ENGLISH
import com.azkar.ui.preview.PreviewCollectionsGateway
import com.azkar.ui.preview.previewFixtureInstant
import com.azkar.ui.theme.AzkarArabicPreview
import com.azkar.ui.theme.AzkarPreview
import java.nio.file.Files
import java.util.Locale

@AzkarPreview
@Composable
internal fun RemembranceReaderTaggedFavoritePreview() {
    ReaderPreview(remembrance = readerPreviewRemembrance(), uiLocale = ENGLISH)
}

@AzkarArabicPreview
@Composable
internal fun RemembranceReaderArabicPreview() {
    ReaderPreview(remembrance = readerPreviewRemembrance(), uiLocale = ARABIC)
}

@AzkarPreview
@Composable
internal fun RemembranceReaderMissingPreview() {
    val catalogue =
        com.azkar.ui.preview
            .rememberPreviewCatalogue(emptyLibrary = true)
    RemembranceReaderScreen(
        detail = rememberRemembranceReaderState(catalogue, remembranceId = -1L),
        onBack = {},
        showToast = { _, _, _ -> },
    )
}

@Composable
private fun ReaderPreview(
    remembrance: Remembrance,
    uiLocale: Locale,
) {
    val catalogue = rememberReaderPreviewCatalogue(remembrance, uiLocale)
    RemembranceReaderScreen(
        detail = rememberRemembranceReaderState(catalogue, remembranceId = requireNotNull(remembrance.id)),
        onBack = {},
        showToast = { _, _, _ -> },
    )
}

@Composable
private fun rememberReaderPreviewCatalogue(
    remembrance: Remembrance,
    uiLocale: Locale,
): CollectionsCatalogue {
    val scope = rememberCoroutineScope()
    val gateway =
        remember(remembrance) {
            PreviewCollectionsGateway(collections = emptyList(), remembrances = listOf(remembrance))
        }
    val coverDirectory = remember { Files.createTempDirectory("azkar-reader-preview-covers-") }
    val covers = remember { CollectionCoverCache(CoverStorage(coverDirectory)) }
    DisposableEffect(coverDirectory) {
        onDispose { Files.deleteIfExists(coverDirectory) }
    }
    return remember(scope, gateway, covers, uiLocale) { CollectionsCatalogue(scope, uiLocale, gateway, covers) }
}

private fun readerPreviewRemembrance(): Remembrance {
    val now = previewFixtureInstant()
    return RemembranceImpl(
        id = 701L,
        isFavorite = true,
        source = "Sahih al-Bukhari, Book of Invocations, chapter on remembrance after prayer, hadith 6306",
        createdAt = now,
        updatedAt = now,
        translations =
            mapOf(
                ENGLISH to
                    Remembrance.Translations(
                        explanationPair = Remembrance.Translations.Pair(id = 701L, text = ""),
                        translationPair =
                            Remembrance.Translations.Pair(
                                id = 701L,
                                text =
                                    "O Allah, You are peace and from You comes peace. Blessed are You, " +
                                        "O Possessor of majesty and honor. This remembrance is said after " +
                                        "completing the prayer, while reflecting on the peace and goodness " +
                                        "that come from Allah.",
                            ),
                    ),
                ARABIC to
                    Remembrance.Translations(
                        explanationPair = Remembrance.Translations.Pair(id = 701L, text = ""),
                        translationPair =
                            Remembrance.Translations.Pair(
                                id = 701L,
                                text =
                                    "اللَّهُمَّ أَنْتَ السَّلَامُ وَمِنْكَ السَّلَامُ، " +
                                        "تَبَارَكْتَ يَا ذَا الْجَلَالِ وَالْإِكْرَامِ",
                            ),
                    ),
            ),
        tags =
            setOf(
                TagImpl(
                    name = "after prayer",
                    translations =
                        mapOf(
                            ARABIC to "بعد الصلاة",
                        ),
                    nameLocale = Locale.ENGLISH,
                ),
                TagImpl(
                    name = "remembrance",
                    translations = mapOf(ARABIC to "ذكر"),
                    nameLocale = Locale.ENGLISH,
                ),
            ),
    )
}

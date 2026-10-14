package com.azkar.ui.library.model

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * One language's version of a passage, as the "new remembrance" form holds it mid-edit.
 *
 * The form's own type rather than the domain's [com.azkar.domain.model.Remembrance.Translations] because
 * of one difference that only matters while typing: a pair of texts is complete and valid the moment it
 * exists, whereas an entry here is born empty. Someone who clicks "add translation" and then backs out
 * has left a blank entry behind, and that is not something the domain should ever be asked to
 * represent — it is dropped on the way out instead.
 *
 * @property explanation what the passage *means*, which is a different job from saying it in another
 * language and is therefore a separate box rather than a second line of the same one.
 */
@Immutable
data class RemembranceTranslationEntry(
    val locale: Locale,
    val text: String = "",
    val explanation: String? = null,
) {
    /** Whether this version says anything yet. The translation is the minimum; most have no explanation. */
    val isBlank: Boolean get() = text.isBlank()
}

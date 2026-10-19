package com.azkar.ui.i18n

import androidx.compose.runtime.Composable
import com.azkar.domain.model.HadithGrade
import com.azkar.generated.resources.Res
import com.azkar.generated.resources.remembranceCreateGradeDaif
import com.azkar.generated.resources.remembranceCreateGradeHasan
import com.azkar.generated.resources.remembranceCreateGradeSahih
import com.azkar.generated.resources.remembranceCreateGradeUnspecified
import org.jetbrains.compose.resources.stringResource

/**
 * How the tradition names a grade, in the language the reader is in.
 *
 * Shared rather than written per surface because the word is not the surface's to choose: a grade is
 * a term of art, and translating it differently in the editor than on a reminder would teach the
 * reader two vocabularies for one thing. The four keys keep their `remembranceCreate` names because
 * that is where the wording was first written; renaming them would churn every call site to make a
 * name prettier, which is the same mistake in the other direction.
 *
 * [HadithGrade.UNSPECIFIED] is a real label rather than an empty one, because "no grade recorded" is
 * itself something a reader is entitled to see — it is the state most azkar in the shipped data set
 * are in, and rendering it as a blank would read as a mistake in the card.
 */
@Composable
internal fun hadithGradeLabel(grade: HadithGrade): String =
    stringResource(
        when (grade) {
            HadithGrade.SAHIH -> Res.string.remembranceCreateGradeSahih
            HadithGrade.HASAN -> Res.string.remembranceCreateGradeHasan
            HadithGrade.DAIF -> Res.string.remembranceCreateGradeDaif
            HadithGrade.UNSPECIFIED -> Res.string.remembranceCreateGradeUnspecified
        },
    )

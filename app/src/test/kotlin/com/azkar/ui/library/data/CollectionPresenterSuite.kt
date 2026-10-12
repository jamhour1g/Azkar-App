package com.azkar.ui.library.data

import de.infix.testBalloon.framework.core.testSuite
import org.assertj.core.api.Assertions.assertThat

/**
 * The icon that stands for a collection's labels, without a presenter instance.
 *
 * Ordering is for stability, not quality: nothing knows which of a user's labels is most
 * characteristic, only that the leader must not move underneath them as labels are added.
 */
val CollectionPresenterSuite by testSuite("The mark stands for the first label") {

    test("a collection with no labels falls back to the default mark") {
        assertThat(CollectionPresenter.iconKeyForTagNames(emptyList()))
            .isEqualTo(CollectionPresenter.DEFAULT_ICON_KEY)
    }

    test("a label the taxonomy does not know is drawn with the fallback mark") {
        assertThat(CollectionPresenter.iconKeyForTagNames(listOf("not a known category")))
            .isEqualTo(CollectionPresenter.CATEGORY_ICON_FALLBACK)
    }

    test("the first label alphabetically leads, however it is capitalised") {
        assertThat(CollectionPresenter.iconKeyForTagNames(listOf("prayer", "Morning")))
            .isEqualTo("sunrise")
    }

    test("the label names are matched case-insensitively against the taxonomy") {
        assertThat(CollectionPresenter.iconKeyForTagNames(listOf("MUSLIM DATA")))
            .isEqualTo("book")
    }
}

package com.azkar.ui.shell

/**
 * What the header does when it is clicked, as one value.
 *
 * All three are [AzkarNavigator] methods and only [AzkarApp] knows them, so passing the three
 * separately to [MainScreenHeader] would be the same three arguments at every call site forever.
 *
 * There is no theme callback any more. It used to take the target preference rather than being a bare
 * `onClick` because the bar resolved the target from the palette it was rendering in, which is only
 * possible for a bar that offers two states; the settings screen lists all three and so states the
 * target itself.
 */
class HeaderCallbacks(
    val onHome: () -> Unit,
    val onLibrary: () -> Unit,
    val onSettings: () -> Unit,
)

package net.mullvad.gotatunandroid.ui.navigation

import android.os.Parcelable

interface Destination : Parcelable

/** Handles navigation events (forward and back) by updating the navigation state. */
class Navigator(
    val state: NavigationState,
) {

    /** A view of the previous back stack as it was before the last navigation/pop event. */
    var previousBackStack: List<Destination> = state.backStack.toList()
        private set

    val backStack: List<Destination> by state::backStack

    /**
     * Navigate to a navigation key.
     *
     * @param keys the navigation keys to navigate to.
     * @param clearBackStack if true clears the back stack before pushing the new key
     */
    fun navigate(vararg keys: Destination, clearBackStack: Boolean = false) {
        previousBackStack = state.backStack.toList()

        state.backStack.apply {
            if (clearBackStack) {
                clear()
            }

            keys.forEach { key ->
                if (key != state.backStack.lastOrNull()) {
                    add(key)
                }
            }
        }
    }

    /** Go back to the previous navigation key. If there is no previous key, do nothing. */
    fun goBack() {
        val backStackBeforePop = state.backStack.toList()
        if (tryPop()) previousBackStack = backStackBeforePop
    }

    private fun tryPop(): Boolean =
        if (state.backStack.size > 1) {
            state.backStack.removeLastOrNull()
            true
        } else {
            false
        }
}

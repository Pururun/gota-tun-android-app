package net.mullvad.gotatunandroid.ui.navigation

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import kotlinx.coroutines.flow.Flow

/** Create a navigation state that persists config changes and process death. */
@Composable
fun rememberNavigationState(startKey: Destination): NavigationState {
    val backStack = rememberParcelableNavBackStack(startKey)

    return remember(backStack) { NavigationState(backStack = backStack) }
}

/**
 * State holder for navigation state.
 *
 * @param backStack - the navigation back stack.
 */
class NavigationState(val backStack: SnapshotStateList<Destination>) {

    val backStackFlow: Flow<List<Destination>> = snapshotFlow { backStack.toList() }
}

@Composable
fun <T : Parcelable> rememberParcelableNavBackStack(vararg initial: T): SnapshotStateList<T> {
    return rememberSaveable(
        saver =
            listSaver(
                save = { backStack -> backStack.toList() },
                restore = { savedList -> savedList.toMutableStateList() },
            )
    ) {
        initial.toList().toMutableStateList()
    }
}
package net.mullvad.gotatunandroid.ui.splittunneling

import kotlinx.parcelize.Parcelize
import net.mullvad.gotatunandroid.ui.navigation.Destination

@Parcelize data class SplitTunneling(val configId: String) : Destination

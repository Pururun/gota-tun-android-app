package net.mullvad.gotatunandroid.ui.manual

import kotlinx.parcelize.Parcelize
import net.mullvad.gotatunandroid.ui.navigation.Destination

@Parcelize
data class ManualEntry(val editConfigId: String? = null) : Destination

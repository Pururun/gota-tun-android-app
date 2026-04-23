package net.mullvad.gotatunandroid.vpn

import kotlinx.coroutines.flow.StateFlow
import net.mullvad.gotatunandroid.domain.model.VpnConfig

interface VpnController {
    val state: StateFlow<VpnState>
    fun connect(config: VpnConfig)
    fun disconnect()
}

sealed class VpnState {
    object Idle : VpnState()
    object Connecting : VpnState()
    object Connected : VpnState()
    object Disconnecting : VpnState()
    data class Error(val message: String) : VpnState()
}

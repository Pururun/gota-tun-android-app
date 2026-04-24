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
    /** Carries the fully-resolved config used for this session (ports are always concrete). */
    data class Connected(val resolvedConfig: VpnConfig) : VpnState()
    object Disconnecting : VpnState()
    data class Error(val message: String) : VpnState()
}

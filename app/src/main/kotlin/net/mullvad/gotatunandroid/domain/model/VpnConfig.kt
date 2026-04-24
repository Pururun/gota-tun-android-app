package net.mullvad.gotatunandroid.domain.model

import java.util.UUID

enum class SplitTunnelingMode { DISABLED, EXCLUDE, INCLUDE_ONLY }

data class SplitTunnelingConfig(
    val mode: SplitTunnelingMode = SplitTunnelingMode.DISABLED,
    val packageNames: List<String> = emptyList()
)

data class VpnConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val interfaceConfig: InterfaceConfig,
    val peers: List<PeerConfig>,
    val splitTunneling: SplitTunnelingConfig = SplitTunnelingConfig()
)

data class InterfaceConfig(
    val privateKey: String,
    val addresses: List<String>,
    val dns: List<String> = emptyList(),
    val mtu: Int? = null
)

data class PeerConfig(
    val publicKey: String,
    val allowedIps: List<String>,
    val endpointHost: String,
    val endpointPort: Int? = null  // null → random port chosen at connect time (0–65535)
)

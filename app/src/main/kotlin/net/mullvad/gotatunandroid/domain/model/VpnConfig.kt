package net.mullvad.gotatunandroid.domain.model

data class VpnConfig(
    val name: String,
    val interfaceConfig: InterfaceConfig,
    val peers: List<PeerConfig>
)

data class InterfaceConfig(
    val privateKey: String,
    val addresses: List<String>,
    val dns: List<String> = emptyList()
)

data class PeerConfig(
    val publicKey: String,
    val allowedIps: List<String>,
    val endpoint: String
)

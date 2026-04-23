package net.mullvad.gotatunandroid.domain

import net.mullvad.gotatunandroid.domain.model.InterfaceConfig
import net.mullvad.gotatunandroid.domain.model.PeerConfig
import net.mullvad.gotatunandroid.domain.model.VpnConfig

object WireGuardConfigParser {

    fun parse(content: String, name: String = "Imported"): VpnConfig {
        val lines = content.lines()

        var privateKey = ""
        val addresses = mutableListOf<String>()
        val dns = mutableListOf<String>()

        val peers = mutableListOf<PeerConfig>()
        var currentSection = ""

        // Per-peer accumulators
        var peerPublicKey = ""
        val peerAllowedIps = mutableListOf<String>()
        var peerEndpoint = ""

        fun flushPeer() {
            if (peerPublicKey.isNotEmpty()) {
                peers.add(
                    PeerConfig(
                        publicKey = peerPublicKey,
                        allowedIps = peerAllowedIps.toList(),
                        endpoint = peerEndpoint
                    )
                )
                peerPublicKey = ""
                peerAllowedIps.clear()
                peerEndpoint = ""
            }
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.startsWith("#") || line.isEmpty() -> continue
                line.equals("[Interface]", ignoreCase = true) -> {
                    flushPeer()
                    currentSection = "interface"
                }
                line.equals("[Peer]", ignoreCase = true) -> {
                    flushPeer()
                    currentSection = "peer"
                }
                else -> {
                    val (key, value) = line.splitKeyValue() ?: continue
                    when (currentSection) {
                        "interface" -> when (key.lowercase()) {
                            "privatekey" -> privateKey = value
                            "address" -> addresses.addAll(value.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                            "dns" -> dns.addAll(value.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                        }
                        "peer" -> when (key.lowercase()) {
                            "publickey" -> peerPublicKey = value
                            "allowedips" -> peerAllowedIps.addAll(value.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                            "endpoint" -> peerEndpoint = value
                        }
                    }
                }
            }
        }
        flushPeer()

        return VpnConfig(
            name = name,
            interfaceConfig = InterfaceConfig(
                privateKey = privateKey,
                addresses = addresses,
                dns = dns
            ),
            peers = peers
        )
    }

    fun serialize(config: VpnConfig): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = ${config.interfaceConfig.privateKey}")
        if (config.interfaceConfig.addresses.isNotEmpty()) {
            appendLine("Address = ${config.interfaceConfig.addresses.joinToString(", ")}")
        }
        if (config.interfaceConfig.dns.isNotEmpty()) {
            appendLine("DNS = ${config.interfaceConfig.dns.joinToString(", ")}")
        }
        config.peers.forEach { peer ->
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${peer.publicKey}")
            if (peer.allowedIps.isNotEmpty()) {
                appendLine("AllowedIPs = ${peer.allowedIps.joinToString(", ")}")
            }
            if (peer.endpoint.isNotEmpty()) {
                appendLine("Endpoint = ${peer.endpoint}")
            }
        }
    }

    private fun String.splitKeyValue(): Pair<String, String>? {
        val idx = indexOf('=')
        if (idx < 0) return null
        return substring(0, idx).trim() to substring(idx + 1).trim()
    }
}



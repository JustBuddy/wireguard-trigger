package wtf.buddyworks.wireguardtrigger.data

/** What the user set up. */
data class Config(
    val enabled: Boolean = false,
    val tunnel: String = "",
    val trusted: Set<String> = emptySet(),
    /** Turn the tunnel off on trusted Wi-Fi. When false, the app only ever turns it on. */
    val offOnTrusted: Boolean = true,
)

enum class LinkType { Wifi, Ethernet, Cellular, Other }

/** One non-VPN network the phone is connected to. [ssid] is null when Android hides the name. */
data class Link(val type: LinkType, val ssid: String? = null)

/** The phone's connections at one moment. */
data class NetSnapshot(
    val links: List<Link>,
    val vpnActive: Boolean,
    /** False until the first network callbacks have arrived. */
    val ready: Boolean = true,
) {
    /** The link shown to the user: Wi-Fi first, as that is what the rules are about. */
    val primary: Link?
        get() = links.minByOrNull { it.type.ordinal }

    companion object {
        val Unknown = NetSnapshot(emptyList(), vpnActive = false, ready = false)
    }
}

enum class Place {
    /** Connected to a trusted Wi-Fi network. */
    Trusted,

    /** Connected, but not to a trusted Wi-Fi network (or the Wi-Fi name can't be read). */
    Untrusted,

    /** No connection at all: nothing to protect, so nothing changes. */
    Offline,
}

enum class TunnelAction { Up, Down }

/** What the rules say for one snapshot. [link] is the network that decided it. */
data class Decision(val place: Place, val action: TunnelAction?, val link: Link?)

/** One line in the activity list. */
data class ActivityEntry(
    val time: Long,
    val kind: Kind,
    val link: Link?,
    val tunnel: String,
) {
    enum class Kind {
        TurnedOn,
        TurnedOff,

        /** The command was sent, but the VPN did not change within the timeout. */
        NoResponse,
        WireGuardMissing,
        PermissionMissing,
    }
}

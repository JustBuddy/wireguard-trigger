package wtf.buddyworks.wireguardtrigger.data

/**
 * The switching rules, kept free of Android types so they can be unit tested.
 *
 * - On a trusted Wi-Fi network: tunnel off (or left alone when [Config.offOnTrusted] is false).
 * - Anywhere else, including Wi-Fi whose name Android hides: tunnel on. When in doubt the
 *   tunnel goes up, since a needless tunnel costs less than an unprotected network.
 * - No connection at all: leave the tunnel as it is.
 */
object Rules {
    fun decide(snapshot: NetSnapshot, config: Config): Decision? {
        if (!snapshot.ready) return null
        val links = snapshot.links
        if (links.isEmpty()) return Decision(Place.Offline, null, null)
        val trustedLink = links.firstOrNull { it.type == LinkType.Wifi && it.ssid != null && it.ssid in config.trusted }
        return if (trustedLink != null) {
            Decision(Place.Trusted, if (config.offOnTrusted) TunnelAction.Down else null, trustedLink)
        } else {
            Decision(Place.Untrusted, TunnelAction.Up, snapshot.primary)
        }
    }

    /**
     * Turns a raw SSID from WifiInfo into a display name: UTF-8 names arrive in quotes, and a
     * hidden name arrives as "<unknown ssid>".
     */
    fun cleanSsid(raw: String?): String? {
        if (raw.isNullOrEmpty() || raw == UNKNOWN_SSID) return null
        return if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) raw.substring(1, raw.length - 1) else raw
    }

    /** Same value as WifiManager.UNKNOWN_SSID. */
    const val UNKNOWN_SSID = "<unknown ssid>"
}

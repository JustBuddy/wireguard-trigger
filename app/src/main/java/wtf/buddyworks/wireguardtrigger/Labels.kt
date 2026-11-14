package wtf.buddyworks.wireguardtrigger

import android.content.Context
import android.text.format.DateUtils
import wtf.buddyworks.wireguardtrigger.data.ActivityEntry
import wtf.buddyworks.wireguardtrigger.data.Decision
import wtf.buddyworks.wireguardtrigger.data.Link
import wtf.buddyworks.wireguardtrigger.data.LinkType
import wtf.buddyworks.wireguardtrigger.data.Place

/** User-facing names for networks and events, shared by the screen and the notification. */
object Labels {
    fun link(context: Context, link: Link?): String = when (link?.type) {
        null -> context.getString(R.string.net_none)
        LinkType.Wifi -> link.ssid?.let { context.getString(R.string.net_wifi_named, it) } ?: context.getString(R.string.net_wifi_hidden)
        LinkType.Ethernet -> context.getString(R.string.net_ethernet)
        LinkType.Cellular -> context.getString(R.string.net_mobile)
        LinkType.Other -> context.getString(R.string.net_other)
    }

    /** The network that decided, or a note while the first snapshot is pending. */
    fun network(context: Context, decision: Decision?): String =
        if (decision == null) context.getString(R.string.place_checking) else link(context, decision.link)

    /** What the rules make of that network. Empty while checking. */
    fun trust(context: Context, decision: Decision?): String = when (decision?.place) {
        null -> ""
        Place.Trusted -> context.getString(R.string.place_trusted)
        Place.Untrusted -> context.getString(R.string.place_untrusted)
        Place.Offline -> context.getString(R.string.place_offline)
    }

    fun activity(context: Context, kind: ActivityEntry.Kind): String = context.getString(
        when (kind) {
            ActivityEntry.Kind.TurnedOn -> R.string.activity_on
            ActivityEntry.Kind.TurnedOff -> R.string.activity_off
            ActivityEntry.Kind.NoResponse -> R.string.activity_no_response
            ActivityEntry.Kind.WireGuardMissing -> R.string.activity_wireguard_missing
            ActivityEntry.Kind.PermissionMissing -> R.string.activity_permission_missing
        },
    )

    fun time(context: Context, time: Long): String {
        val flags = if (DateUtils.isToday(time)) {
            DateUtils.FORMAT_SHOW_TIME
        } else {
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME
        }
        return DateUtils.formatDateTime(context, time, flags)
    }
}

package wtf.buddyworks.wireguardtrigger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RulesTest {
    private val home = Config(enabled = true, tunnel = "home", trusted = setOf("HomeNet", "Office"))

    private fun snapshot(vararg links: Link, vpn: Boolean = false) = NetSnapshot(links.toList(), vpnActive = vpn)

    @Test
    fun `trusted wifi turns the tunnel off`() {
        val d = Rules.decide(snapshot(Link(LinkType.Wifi, "HomeNet")), home)!!
        assertEquals(Place.Trusted, d.place)
        assertEquals(TunnelAction.Down, d.action)
        assertEquals("HomeNet", d.link?.ssid)
    }

    @Test
    fun `trusted wifi leaves the tunnel alone when turning off is disabled`() {
        val d = Rules.decide(snapshot(Link(LinkType.Wifi, "HomeNet")), home.copy(offOnTrusted = false))!!
        assertEquals(Place.Trusted, d.place)
        assertNull(d.action)
    }

    @Test
    fun `untrusted wifi turns the tunnel on`() {
        val d = Rules.decide(snapshot(Link(LinkType.Wifi, "Cafe")), home)!!
        assertEquals(Place.Untrusted, d.place)
        assertEquals(TunnelAction.Up, d.action)
    }

    @Test
    fun `mobile data turns the tunnel on`() {
        val d = Rules.decide(snapshot(Link(LinkType.Cellular)), home)!!
        assertEquals(TunnelAction.Up, d.action)
        assertEquals(LinkType.Cellular, d.link?.type)
    }

    @Test
    fun `hidden wifi name counts as untrusted`() {
        val d = Rules.decide(snapshot(Link(LinkType.Wifi, null)), home)!!
        assertEquals(Place.Untrusted, d.place)
        assertEquals(TunnelAction.Up, d.action)
    }

    @Test
    fun `trusted wifi wins over mobile data alongside it`() {
        val d = Rules.decide(snapshot(Link(LinkType.Cellular), Link(LinkType.Wifi, "Office")), home)!!
        assertEquals(Place.Trusted, d.place)
        assertEquals("Office", d.link?.ssid)
    }

    @Test
    fun `untrusted shows wifi before mobile data`() {
        val d = Rules.decide(snapshot(Link(LinkType.Cellular), Link(LinkType.Wifi, "Cafe")), home)!!
        assertEquals(LinkType.Wifi, d.link?.type)
    }

    @Test
    fun `ssids match exactly`() {
        val d = Rules.decide(snapshot(Link(LinkType.Wifi, "homenet")), home)!!
        assertEquals(Place.Untrusted, d.place)
    }

    @Test
    fun `no connection changes nothing`() {
        val d = Rules.decide(snapshot(), home)!!
        assertEquals(Place.Offline, d.place)
        assertNull(d.action)
    }

    @Test
    fun `no decision before the first snapshot`() {
        assertNull(Rules.decide(NetSnapshot.Unknown, home))
    }

    @Test
    fun `vpn state does not change the decision`() {
        assertEquals(
            Rules.decide(snapshot(Link(LinkType.Wifi, "Cafe"), vpn = false), home),
            Rules.decide(snapshot(Link(LinkType.Wifi, "Cafe"), vpn = true), home),
        )
    }

    @Test
    fun `ssid cleaning`() {
        assertEquals("HomeNet", Rules.cleanSsid("\"HomeNet\""))
        assertEquals("a\"b", Rules.cleanSsid("\"a\"b\""))
        assertEquals("0a1b2c", Rules.cleanSsid("0a1b2c"))
        assertNull(Rules.cleanSsid(Rules.UNKNOWN_SSID))
        assertNull(Rules.cleanSsid(""))
        assertNull(Rules.cleanSsid(null))
    }
}

package svoboda.runet.abonent0007.awg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import svoboda.runet.abonent0007.warp.WarpIdentity

class AwgGeneratorTest {

    private val identity = WarpIdentity(
        privateKey = "H+k/FuEJPhBPZg4izRfkYa/LpSaswon3dxMsXji0mLY=",
        publicKey = "",
        ipv4 = "172.16.0.2",
        ipv6 = "2606:4700:110:885c:e6a7:f8a6:d4f3:664f",
        peerPublicKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
        reserved = "",
        deviceId = "",
        token = "",
    )

    @Test
    fun buildsValidStructure() {
        val cfg = AwgGenerator.buildConfig(identity, AwgGenerator.COUNTRIES.first(), AwgPreset.V1, "1.1.1.1", 1440, true, 25)
        assertTrue(cfg.contains("[Interface]"))
        assertTrue(cfg.contains("PrivateKey = ${identity.privateKey}"))
        assertTrue(cfg.contains("Address = 172.16.0.2/32, 2606:4700:110:885c:e6a7:f8a6:d4f3:664f/128"))
        assertTrue(cfg.contains("DNS = 1.1.1.1"))
        assertTrue(cfg.contains("MTU = 1440"))
        assertTrue(cfg.contains("Jc = 4"))
        assertTrue(cfg.contains("Jmin = 40"))
        assertTrue(cfg.contains("Jmax = 70"))
        assertTrue(cfg.contains("[Peer]"))
        assertTrue(cfg.contains("PublicKey = ${identity.peerPublicKey}"))
        assertTrue(cfg.contains("AllowedIPs = 0.0.0.0/0, ::/0"))
        assertTrue(cfg.contains("PersistentKeepalive = 25"))
        assertTrue(cfg.contains("Endpoint = "))
    }

    @Test
    fun presetsProduceMasks() {
        val v1 = AwgGenerator.buildConfig(identity, AwgGenerator.COUNTRIES.first(), AwgPreset.V1, "", 1440, false, null)
        val v3 = AwgGenerator.buildConfig(identity, AwgGenerator.COUNTRIES.first(), AwgPreset.V3, "", 1440, false, null)
        assertTrue(v1.contains("I1 = <b 0x"))
        assertTrue(v3.contains("I1 = <b 0x"))
        assertTrue(v3.contains("I2 = <b 0x"))
    }

    @Test
    fun ipv6OffRemovesV6() {
        val cfg = AwgGenerator.buildConfig(identity, AwgGenerator.COUNTRIES.first(), AwgPreset.V1, "1.1.1.1", 1440, false, null)
        assertTrue(!cfg.contains("2606:4700"))
        assertTrue(cfg.contains("AllowedIPs = 0.0.0.0/0"))
    }

    @Test
    fun endpointForCountry() {
        val std = AwgGenerator.randomEndpoint(AwgGenerator.COUNTRIES.first())
        assertTrue(std.first.isNotBlank())
        val fi = AwgGenerator.COUNTRIES.first { it.id == "FL" }
        assertEquals("fi.tribukvy.ltd" to 500, AwgGenerator.randomEndpoint(fi))
    }
}

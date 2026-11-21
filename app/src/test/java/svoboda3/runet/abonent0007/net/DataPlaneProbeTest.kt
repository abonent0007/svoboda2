package svoboda3.runet.abonent0007.net

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import svoboda3.runet.abonent0007.vpn.EndpointProbe
import svoboda3.runet.abonent0007.vpn.FailureKind

/**
 * P0: реалистичные пробы через MockWebServer — короткое тело Cloudflare trace и грамматика URL.
 */
class DataPlaneProbeTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private val shortTrace = """fl=29
ip=203.0.113.10
ts=1700000000
loc=US
colo=LAX
""".trimIndent() + "\n"

    @Test
    fun `short cloudflare trace body is accepted`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(shortTrace))
        val result = DataPlane.probeEndpointForTest(server.url("/cdn-cgi/trace").toString(), DataPlane.Expect.CF)
        assertTrue("short trace must pass, got $result", result.ok)
        assertEquals(200, result.httpCode)
    }

    @Test
    fun `ws trace with ip deep in body passes within 16KB cap`() = runBlocking {
        val bigBody = buildString {
            repeat(400) { append("x".repeat(28)).append('\n') }  // ~12 КБ наполнение перед ip-строкой
            append("ip=203.0.113.10\n")
            repeat(400) { append("y".repeat(28)).append('\n') }
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody(bigBody))
        val result = DataPlane.probeEndpointForTest(server.url("/cdn-cgi/trace").toString(), DataPlane.Expect.CF)
        assertTrue("trace with ip before cap must pass", result.ok)
    }

    @Test
    fun `trace without ip line fails and keeps http code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("fl=29\nloc=US\n"))
        val result = DataPlane.probeEndpointForTest(server.url("/cdn-cgi/trace").toString(), DataPlane.Expect.CF)
        assertFalse(result.ok)
        assertEquals(200, result.httpCode)
    }

    @Test
    fun `generate_204 accepted on 204 only`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val ok = DataPlane.probeEndpointForTest(server.url("/").toString(), DataPlane.Expect.G204)
        assertTrue(ok.ok)
        assertEquals(204, ok.httpCode)

        server.enqueue(MockResponse().setResponseCode(200).setBody("not-empty"))
        val bad = DataPlane.probeEndpointForTest(server.url("/").toString(), DataPlane.Expect.G204)
        assertFalse(bad.ok)
        assertEquals(200, bad.httpCode)
    }

    @Test
    fun `production probe urls are well-formed`() {
        assertEquals("/cdn-cgi/trace", DataPlane.CF_TRACE_URL.toHttpUrlOrNull()!!.encodedPath)
        assertEquals("https", DataPlane.CF_TRACE_URL.toHttpUrlOrNull()!!.scheme)
        assertEquals("/generate_204", DataPlane.G204_URL.toHttpUrlOrNull()!!.encodedPath)
        assertEquals("https", DataPlane.G204_URL.toHttpUrlOrNull()!!.scheme)
    }

    @Test
    fun `classify and verdict reflect partial and total failure`() {
        val twoPass = DataPlane.Outcome(
            cf = EndpointProbe.pass(200), g = EndpointProbe.fail(FailureKind.HTTPS, "http=500"),
        )
        assertTrue(DataPlane.isUsable(twoPass))   // частичный успех = usable
        val none = DataPlane.Outcome(
            cf = EndpointProbe.fail(FailureKind.DNS, "UnknownHostException"),
            g = EndpointProbe.fail(FailureKind.ENDPOINT_UNREACHABLE, "timed out"),
        )
        assertFalse(DataPlane.isUsable(none))
    }
}

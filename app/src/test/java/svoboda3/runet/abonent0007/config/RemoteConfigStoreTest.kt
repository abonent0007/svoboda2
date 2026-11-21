package svoboda3.runet.abonent0007.config

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * RemoteConfigStore: remote API → disk cache → embedded fallback.
 * Сеть мокается MockWebServer; часы инжектируются — TTL-поведение детерминировано.
 */
class RemoteConfigStoreTest {

    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private lateinit var cacheFile: File
    private var now = 1_000_000L

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dir = Files.createTempDirectory("rcfg-test").toFile()
        cacheFile = File(dir, "remote_config.json")
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    private fun store(ttlMs: Long = 60_000) = RemoteConfigStore(
        cacheFile = cacheFile,
        endpoint = server.url("/remote.json").toString(),
        clock = { now },
        ttlMs = ttlMs,
    )

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun `valid remote json wins and is cached`() = runBlocking {
        enqueueJson("""{"cf_client_version":"a-9.99-1000"}""")
        val s = store()
        assertEquals("a-9.99-1000", s.cfClientVersion())
        assertEquals("disk cache must be written", true, cacheFile.exists())
        assertEquals(1, server.requestCount) // секундой запрос не уходит: память + кэш
        assertEquals("a-9.99-1000", s.cfClientVersion())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `remote telegram_dcs overrides embedded and is cached on disk`() = runBlocking {
        enqueueJson("""{"cf_client_version":"a-1","telegram_dcs":["1.2.3.4","5.6.7.8"]}""")
        val s = store()
        assertEquals(listOf("1.2.3.4", "5.6.7.8"), s.telegramDcs())
        assertEquals(1, server.requestCount)
        assertEquals(listOf("1.2.3.4", "5.6.7.8"), s.telegramDcs())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `invalid telegram_dcs entries are filtered out`() = runBlocking {
        enqueueJson("""{"cf_client_version":"a-1","telegram_dcs":["not-an-ip","","149.154.167.50"]}""")
        assertEquals(listOf("149.154.167.50"), store().telegramDcs())
    }

    @Test
    fun `all-garbage telegram_dcs falls back to embedded`() = runBlocking {
        enqueueJson("""{"cf_client_version":"a-1","telegram_dcs":["junk",""]}""")
        assertEquals(RemoteBundle.EMBEDDED_TELEGRAM_DCS, store().telegramDcs())
    }

    @Test
    fun `stale cache keeps telegram_dcs override`() = runBlocking {
        cacheFile.writeText(
            """{"cf_client_version":"a-stale","telegram_dcs":["9.9.9.9"],"fetched_at":${now - 120_000}}""",
            Charsets.UTF_8,
        )
        enqueueJson("oops", 500)
        assertEquals(listOf("9.9.9.9"), store(ttlMs = 60_000).telegramDcs())
    }

    @Test
    fun `parse tolerates legacy body without telegram_dcs`() {
        val s = store()
        val b = s.parse("""{"cf_client_version":"a-legacy"}""")!!
        assertEquals("a-legacy", b.cfClientVersion)
        assertEquals(RemoteBundle.EMBEDDED_TELEGRAM_DCS, b.telegramDcs)
    }

    @Test
    fun `fresh disk cache is served without network`() = runBlocking {
        cacheFile.writeText("""{"cf_client_version":"a-cached-1","fetched_at":$now}""", Charsets.UTF_8)
        val s = store(ttlMs = 60_000)
        assertEquals("a-cached-1", s.cfClientVersion())
        assertEquals(0, server.requestCount) // сеть не трогаем
    }

    @Test
    fun `http 404 falls back to embedded`() = runBlocking {
        enqueueJson("not here", 404)
        assertEquals(RemoteBundle.EMBEDDED_CF_CLIENT_VERSION, store().cfClientVersion())
    }

    @Test
    fun `malformed json falls back to embedded`() = runBlocking {
        enqueueJson("{ not json at all")
        assertEquals(RemoteBundle.EMBEDDED_CF_CLIENT_VERSION, store().cfClientVersion())
    }

    @Test
    fun `embedded-shaped json with empty version falls back to embedded`() = runBlocking {
        enqueueJson("""{"cf_client_version":""}""")
        assertEquals(RemoteBundle.EMBEDDED_CF_CLIENT_VERSION, store().cfClientVersion())
    }

    @Test
    fun `unknown keys are ignored`() = runBlocking {
        enqueueJson("""{"cf_client_version":"a-ok","future_field":123,"another":{"nested":true}}""")
        assertEquals("a-ok", store().cfClientVersion())
    }

    @Test
    fun `fetch failure with stale cache serves stale cache`() = runBlocking {
        // кэш старее TTL, сеть отвечает 500 → отдаём диск, не embedded
        cacheFile.writeText("""{"cf_client_version":"a-stale-x","fetched_at":${now - 120_000}}""", Charsets.UTF_8)
        enqueueJson("oops", 500)
        assertEquals("a-stale-x", store(ttlMs = 60_000).cfClientVersion())
    }

    @Test
    fun `no network and no cache serves embedded`() = runBlocking {
        // ничего не ставим в очередь → MockWebServer ответит отклонённым соединением/EOF? нет: он ответит 200 с пустым телом по умолчанию.
        // Поэтому шьём заведомо невалидное пустое тело:
        enqueueJson("", 200)
        assertEquals(RemoteBundle.EMBEDDED_CF_CLIENT_VERSION, store().cfClientVersion())
    }

    @Test
    fun `parse rejects blank and keeps trim`() = runBlocking {
        val s = store()
        assertEquals("a-b", s.parse("""{"cf_client_version":"  a-b  "}""")?.cfClientVersion)
        org.junit.Assert.assertNull(s.parse("""{"cf_client_version":""}"""))
        org.junit.Assert.assertNull(s.parse(""))
    }
}

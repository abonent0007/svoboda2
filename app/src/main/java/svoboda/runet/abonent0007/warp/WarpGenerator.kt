package svoboda.runet.abonent0007.warp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.amnezia.awg.crypto.KeyPair
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * WARP-генератор: регистрация устройства в Cloudflare и сборка
 * WARP-safe AmneziaWG-конфига.
 *
 * `/v0a2158/reg` — неофициальный контракт: при 429/5xx — откат к
 * сохранённым профилям, без обещаний в UI.
 */
object WarpGenerator {

    const val REG_URL = "https://api.cloudflareclient.com/v0a2158/reg"
    const val CF_CLIENT_VERSION = "a-6.30-3596"
    const val MTU = 1240

    private val REG_IPS: List<InetAddress> = listOf(
        InetAddress.getByName("104.16.24.84"),
        InetAddress.getByName("104.16.192.82"),
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        // DNS для api.cloudflareclient.com в РФ подменяется (8.6.112.0),
        // поэтому ходим на известные anycast-IP Cloudflare с настоящим SNI.
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                if (hostname.equals("api.cloudflareclient.com", ignoreCase = true)) {
                    REG_IPS
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
        })
        .build()

    /** Регистрирует новую WARP-идентичность. Возвращает null при любой ошибке. */    suspend fun register(installId: String = ""): WarpIdentity? = withContext(Dispatchers.IO) {
        runCatching {
            val keyPair = KeyPair()
            val publicKey = keyPair.publicKey.toBase64()
            val body = JSONObject()
                .put("key", publicKey)
                .put("install_id", installId)
                .put("fcm_token", "")
                .put("tos", java.time.Instant.now().toString())
                .put("type", "Android")
                .put("model", "PC")
                .put("locale", "en_US")
                .toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(REG_URL)
                .header("Content-Type", "application/json")
                .header("User-Agent", "okhttp/3.12.1")
                .header("CF-Client-Version", CF_CLIENT_VERSION)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("SvobodaWarp", "reg HTTP ${response.code}: ${response.body?.string()?.take(200)}")
                    return@runCatching null
                }
                val raw = response.body?.string().orEmpty()
                val json = JSONObject(raw)
                val config = json.optJSONObject("config") ?: run {
                    android.util.Log.w("SvobodaWarp", "reg no config: ${raw.take(200)}")
                    return@runCatching null
                }
                val iface = config.optJSONObject("interface") ?: return@runCatching null
                val addresses = iface.optJSONObject("addresses") ?: return@runCatching null
                val peer = config.optJSONArray("peers")?.optJSONObject(0)
                    ?: return@runCatching null
                val endpoint = peer.optJSONObject("endpoint")
                val endpointV4 = endpoint?.optString("v4").orEmpty().substringBefore(":")
                WarpIdentity(
                    privateKey = keyPair.privateKey.toBase64(),
                    publicKey = publicKey,
                    ipv4 = addresses.optString("v4"),
                    ipv6 = addresses.optString("v6"),
                    peerPublicKey = peer.optString("public_key"),
                    reserved = config.optString("client_id"),
                    deviceId = json.optString("id"),
                    token = json.optString("token"),
                    endpointV4 = endpointV4,
                    endpointHost = endpoint?.optString("host").orEmpty().substringBefore(":"),
                    endpointPort = endpoint?.optJSONArray("ports")?.optInt(0, 2408) ?: 2408,
                )
            }
        }.onFailure {
            android.util.Log.w("SvobodaWarp", "reg failed: ${it.javaClass.simpleName}: ${it.message}")
        }.getOrNull()?.takeIf {
            it.privateKey.isNotBlank() && it.peerPublicKey.isNotBlank() && it.ipv4.isNotBlank()
        }
    }

    /** Собирает WARP-safe AmneziaWG-конфиг (S1..S4=0, H1..H4=1..4). */
    fun buildConfig(
        identity: WarpIdentity,
        endpointHost: String,
        port: Int,
        jc: Int = 7,
        jmin: Int = 54,
        jmax: Int = 76,
        i1: String = "",
    ): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = ${identity.privateKey}")
        val addresses = buildList {
            identity.ipv4.takeIf { it.isNotBlank() }?.let { add("$it/32") }
            identity.ipv6.takeIf { it.isNotBlank() }?.let { add("$it/128") }
        }
        appendLine("Address = ${addresses.joinToString(", ")}")
        appendLine("DNS = 1.1.1.1, 1.0.0.1, 2606:4700:4700::1111, 2606:4700:4700::1001")
        appendLine("MTU = $MTU")
        appendLine("S1 = 0")
        appendLine("S2 = 0")
        appendLine("S3 = 0")
        appendLine("S4 = 0")
        appendLine("Jc = $jc")
        appendLine("Jmin = $jmin")
        appendLine("Jmax = $jmax")
        appendLine("H1 = 1")
        appendLine("H2 = 2")
        appendLine("H3 = 3")
        appendLine("H4 = 4")
        if (i1.isNotBlank()) appendLine("I1 = $i1")
        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = ${identity.peerPublicKey}")
        appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
        appendLine("Endpoint = $endpointHost:$port")
    }
}

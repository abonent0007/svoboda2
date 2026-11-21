package svoboda.runet.abonent0007.warp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Фабрика WARP-профилей «Свободы 2.0».
 *
 * Идея (функциональность, реализация своя): одна регистрация identity в Cloudflare,
 * затем N разных конфигов подключения — разные endpoint'ы anycast и рандомизированные
 * junk-параметры AmneziaWG (Jc/Jmin/Jmax). Каждый профиль можно отдельно подключить и проверить.
 *
 * Рандомизация junk нужна, чтобы у нескольких профилей не было одинакового отпечатка
 * на стороне DPI: при одинаковых параметрах все соединения выглядят как одно.
 */
object WarpProfileFactory {

    private const val TAG = "SvobodaWarpFactory"

    data class Endpoint(val host: String, val port: Int)

    /**
     * Публичные anycast-endpoint'ы WARP (UDP/2408 — основной порт WireGuard-совместимого WARP;
     * 500/1701/4500 — стандартные WG-порты, принимаются частью сетей).
     */
    val KNOWN_ENDPOINTS: List<Endpoint> = buildList {
        // Публичные anycast-подсети WARP (Cloudflare): 162.159.192/193/195.0/24 и 188.114.96-99.0/24.
        val subnets = listOf(
            "162.159.192", "162.159.193", "162.159.195",
            "188.114.96", "188.114.97", "188.114.98", "188.114.99",
        )
        val hosts = listOf(1, 10, 100, 225)
        subnets.forEach { net ->
            hosts.forEach { h -> add(Endpoint("$net.$h", 2408)) }
        }
        // Стандартные WG-порты (принимаются не всеми сетями — как вариант).
        add(Endpoint("162.159.192.1", 500))
        add(Endpoint("162.159.193.10", 1701))
        add(Endpoint("162.159.195.1", 4500))
    }

    data class Result(
        val profiles: List<GeneratedProfile>,
        val identityEndpoint: String,
        val note: String,
    )

    /**
     * Регистрирует identity (если ещё нет) и собирает [count] профилей.
     *
     * @param identity готовая identity или null — тогда будет выполнена регистрация
     * @param seedRawConfig сырой конфиг seed'а (нужен I1 — параметр AmneziaWG)
     */
    suspend fun generate(
        count: Int,
        identity: WarpIdentity?,
        seedRawConfig: String,
        dns: String = WarpGenerator.DNS_CLOUDFLARE,
        allowedIpsOverride: String? = null,
        random: Random = Random.Default,
    ): Result = withContext(Dispatchers.IO) {
        val dnsValue = dns
        val wanted = count.coerceIn(1, 50)
        val id = identity ?: WarpGenerator.register()
        if (id == null) {
            return@withContext Result(emptyList(), "", "Cloudflare не выдал identity (сеть/лимит). Попробуйте позже.")
        }

        val i1 = Regex("(?m)^\\s*I1\\s*=\\s*(.+)$").find(seedRawConfig)?.groupValues?.get(1).orEmpty()

        // Разные endpoint'ы: сначала endpoint из ответа Cloudflare, затем известные anycast.
        val endpoints = buildList {
            if (id.endpointV4.isNotBlank()) add(Endpoint(id.endpointV4, id.endpointPort.takeIf { it > 0 } ?: 2408))
            addAll(KNOWN_ENDPOINTS)
        }.distinct()

        val profiles = (0 until wanted).map { index ->
            val endpoint = endpoints[index % endpoints.size]
            // Рандомизируем junk в разумных пределах, чтобы профили различались,
            // но оставались рабочими (Jc 4..9, Jmin 30..70, Jmax > Jmin).
            val jc = random.nextInt(4, 10)
            val jmin = random.nextInt(30, 71)
            val jmax = jmin + random.nextInt(8, 36)
            val config = WarpGenerator.buildConfig(
                identity = id,
                endpointHost = endpoint.host,
                port = endpoint.port,
                jc = jc,
                jmin = jmin,
                jmax = jmax,
                i1 = i1,
                dns = dnsValue,
                allowedIps = allowedIpsOverride,
            )
            GeneratedProfile(
                createdAt = System.currentTimeMillis() + index,
                host = endpoint.host,
                port = endpoint.port,
                config = config,
            )
        }
        Log.d(TAG, "generated ${profiles.size} profiles, identity endpoint ${id.endpointV4}")
        Result(
            profiles = profiles,
            identityEndpoint = "${id.endpointV4}:${id.endpointPort}",
            note = "Создано профилей: ${profiles.size}. Каждый подключается отдельно — проверяйте и оставляйте рабочие.",
        )
    }
}

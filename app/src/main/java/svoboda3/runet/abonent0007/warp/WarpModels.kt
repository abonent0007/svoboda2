package svoboda3.runet.abonent0007.warp

import kotlinx.serialization.Serializable

/** Запись bundled seed-каталога (assets/seeds/warp_verified_seeds.json). */
@Serializable
data class BundledSeed(
    val engine: String = "wireguard",
    val mode: String = "warp-awg-exact",
    val endpoint_source: String = "bundled-seed",
    val success_count: Int = 1,
    val scope: String = "default",
    val seed_order: Int = 0,
    val preferred_sni: String = "",
    val source_file: String = "",
    val host: String = "",
    val port: Int = 0,
    val raw_config: String = "",
    val last_verified_at: Long = 0L,
) {
    /** Стабильный идентификатор профиля: mode|host|port. */
    val id: String get() = "$mode|$host|$port"

    /** Порядковый номер для UI (1-based). */
    val number: Int get() = seed_order + 1
}

/** Один сгенерированный вручную WARP-профиль. */
@Serializable
data class GeneratedProfile(
    val createdAt: Long = 0L,
    val host: String = "",
    val port: Int = 0,
    val config: String = "",
    val configEncrypted: String = "",
    /** Фактически измеренная страна выхода (ISO-код), напр. "NL". Пусто — не измерено. */
    val observedCountry: String = "",
    val countryCheckedAt: Long = 0L,
)

/** Результат регистрации WARP-устройства в Cloudflare. */
data class WarpIdentity(
    val privateKey: String,
    val publicKey: String,
    val ipv4: String,
    val ipv6: String,
    val peerPublicKey: String,
    val reserved: String,
    val deviceId: String,
    val token: String,
    val endpointV4: String = "",
    val endpointHost: String = "",
    val endpointPort: Int = 2408,
)

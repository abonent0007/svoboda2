package svoboda3.runet.abonent0007.dpi

/**
 * Единый каталог стратегий ByeDPI (ciadpi) для «Свободы 2.0».
 *
 * Все строки проверены по исходникам ciadpi 17.3 (parse_offset / опции).
 * Стратегия одна на весь TCP-трафик через обход.
 *
 * Не добавлены осознанно:
 *  - DPI-X02 / DPI-X04: содержали `-n {sni}`; {sni} — не placeholder ByeDPI,
 *    ciadpi получил бы literal "{sni}" как fake-SNI (бессмыслица);
 *  - DPI-X07: содержит `-S` (TCP MD5SIG) — на Linux/Android требуется
 *    CAP_NET_ADMIN; стратегия мертва в нашей сборке без md5sig.
 */
data class DpiStrategy(val id: String, val name: String, val args: String, val tier: String)

private val BDP = listOf(
    DpiStrategy("BDP-00", "Контрольная (оригинал)", "-Ku -a3 -An -Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An", "Контрольные"),
    DpiStrategy("BDP-01", "Разреженная", "-Ku -a3 -An -Kt,h -d1 -s0+s -d5+s -s11+s -d18+s -s27+s -d37+s -An", "Контрольные"),
    DpiStrategy("BDP-02", "Плотная начальная", "-Ku -a3 -An -Kt,h -d1 -s0+s -d2+s -s4+s -d6+s -s9+s -d13+s -s18+s -d24+s -s31+s -An", "Контрольные"),
    DpiStrategy("BDP-03", "Split first", "-Ku -a3 -An -Kt,h -s0+s -d2+s -s5+s -d8+s -s12+s -d17+s -s23+s -d30+s -An", "Контрольные"),
    DpiStrategy("BDP-04", "Disorder dominant", "-Ku -a3 -An -Kt,h -d1 -s0+s -d3+s -d5+s -d8+s -d12+s -d17+s -s23+s -d30+s -An", "Контрольные"),
    DpiStrategy("BDP-05", "Split dominant", "-Ku -a3 -An -Kt,h -d1 -s0+s -s3+s -d6+s -s9+s -s13+s -d18+s -s24+s -s31+s -An", "Контрольные"),
    DpiStrategy("BDP-06", "Плотный split", "-Ku -a3 -An -Kt,h -s0+s -s2+s -s5+s -s9+s -s14+s -s20+s -s27+s -s35+s -An", "Контрольные"),
    DpiStrategy("BDP-07", "Плотный disorder", "-Ku -a3 -An -Kt,h -d1 -d3+s -d6+s -d10+s -d15+s -d21+s -d28+s -d36+s -An", "Контрольные"),
    DpiStrategy("BDP-08", "Ближний SNI", "-Ku -a3 -An -Kt,h -d1 -s0+s -d6+s -s13+s -d21+s -s30+s -d40+s -An", "Контрольные"),
    DpiStrategy("BDP-09", "Ровный шаг", "-Ku -a3 -An -Kt,h -d1 -s1+s -d4+s -s8+s -d13+s -s19+s -d26+s -s34+s -An", "Контрольные"),
    DpiStrategy("BDP-10", "UDP low", "-Ku -a1 -An -Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An", "Контрольные"),
    DpiStrategy("BDP-11", "UDP high", "-Ku -a4 -An -Kt,h -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An", "Контрольные"),
    DpiStrategy("BDP-12", "TLS и HTTP раздельно", "-Ku -a3 -An -Kt -d1 -s0+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -An -Kh -d1 -s0+h -d3+h -s6+h -d9+h -s12+h -d15+h -s20+h -d25+h -s30+h -d35+h -An", "Контрольные"),
)

private val SIMPLE = listOf(
    DpiStrategy("dpi_01", "Split Start", "-s1", "Простые · низкая агрессивность"),
    DpiStrategy("dpi_02", "Disorder Start", "-d1", "Простые · низкий риск"),
    DpiStrategy("dpi_03", "SNI Split", "-s1+s", "Простые · низкий риск"),
    DpiStrategy("dpi_04", "SNI Disorder", "-d1+s", "Простые · низкий риск"),
    DpiStrategy("dpi_05", "TLS Record SNI", "-r1+s", "Простые · низкий риск"),
    DpiStrategy("dpi_06", "TLS Record Mid", "-r3+s", "Простые · низкий риск"),
)

private val FAKE = listOf(
    DpiStrategy("dpi_07", "Fake TTL 6", "-f-1 -t6", "Fake-пакеты · TTL 6"),
    DpiStrategy("dpi_08", "Fake TTL 8", "-f-1 -t8", "Fake-пакеты · базовый"),
    DpiStrategy("dpi_09", "Fake TTL 10", "-f-1 -t10", "Fake-пакеты · TTL 10"),
    DpiStrategy("dpi_10", "Fake + Disorder", "-d1 -f-1 -t8", "Fake-пакеты · + disorder"),
    DpiStrategy("dpi_11", "Fake SNI Mid", "-f-1+sm -t7", "Fake-пакеты · середина SNI"),
    DpiStrategy("dpi_12", "Fake Offset", "-f-5 -t9", "Fake-пакеты · offset -5"),
)

private val COMBINED = listOf(
    DpiStrategy("dpi_13", "Split + TLS Record", "-s1 -r1+s", "Комбинированные"),
    DpiStrategy("dpi_14", "Disorder + TLS Record", "-d1 -r2+s", "Комбинированные"),
    DpiStrategy("dpi_15", "Multi Split", "-s1 -s5+s", "Комбинированные"),
    DpiStrategy("dpi_16", "Multi Disorder", "-d1 -d5+s", "Комбинированные"),
    DpiStrategy("dpi_17", "Split + Disorder", "-s1+s -d3+s", "Комбинированные"),
    DpiStrategy("dpi_18", "OOB SNI", "-o1+s", "Комбинированные · OOB"),
    DpiStrategy("dpi_19", "DisOOB SNI", "-q1+s", "Комбинированные · DisOOB"),
)

private val HTTP = listOf(
    DpiStrategy("dpi_20", "HTTP Header Mix", "-Mh", "HTTP · только HTTP (не HTTPS)"),
    DpiStrategy("dpi_21", "HTTP Domain Mix", "-Md", "HTTP · только HTTP (не HTTPS)"),
    DpiStrategy("dpi_22", "HTTP Request Mix", "-Mr", "HTTP · только HTTP (не HTTPS)"),
    DpiStrategy("dpi_23", "HTTP + Split", "-Mh -s1", "HTTP · только HTTP (не HTTPS)"),
    DpiStrategy("dpi_24", "HTTP + Fake", "-Mh -f-1 -t8", "HTTP · только HTTP (не HTTPS)"),
)

private val EXTENDED = listOf(
    DpiStrategy("dpi_x01", "Multi Split / Disorder", "-d1 -d3+s -s7+s -d10+s -s13+s -d17+s -s22+s -d28+s -s34+s -r2+s -a1", "Расширенные · экспериментальные"),
    DpiStrategy("dpi_x03", "Multi-stage TLS", "-d2 -s2+s -d4+s -s7+s -d10+s -s14+s -d18+s -s23+s -d29+s -s36+s -r2+s -a1 -As -d2 -s2+s -d4+s -s7+s -d10+s -s14+s -d18+s -s23+s -d29+s -s36+s -a1", "Расширенные · экспериментальные"),
    DpiStrategy("dpi_x05", "Deep Disorder", "-d2 -d4+s -s7+s -d7+s -s9+s -d11+s -s14+s -d17+s -s21+s -d26+s -s32+s -r4+s -a1", "Расширенные · экспериментальные"),
    DpiStrategy("dpi_x06", "Multi Fake / TLS Record", "-f-2 -Qr -s2+sm -d4+s -s7+sm -o3 -a1 -As -r2+s -d9+s -a1 -As -s5:9+sm -f-87 -t9 -r6 -a1", "Расширенные · экспериментальные"),
    DpiStrategy("dpi_x08", "Deep Split Chain", "-d2 -s2+s -d4+s -s7+s -d10+s -s14+s -d18+s -s23+s -d29+s -s36+s -s44+s -s53+s -r2+s -a1", "Расширенные · экспериментальные"),
)

/** Секции каталога: (заголовок, список). Порядок отображения и есть приоритет тестирования. */
val DPI_CATALOG_SECTIONS: List<Pair<String, List<DpiStrategy>>> = listOf(
    "Контрольные (BDP)" to BDP,
    "Простые · P1" to SIMPLE,
    "Fake-пакеты · P1" to FAKE,
    "Комбинированные · P2" to COMBINED,
    "Расширенные · экспериментальные" to EXTENDED,
    "HTTP (не HTTPS/QUIC)" to HTTP,
)

val DPI_CATALOG: List<DpiStrategy> = DPI_CATALOG_SECTIONS.flatMap { it.second }

fun dpiStrategyById(id: String): DpiStrategy? = DPI_CATALOG.firstOrNull { it.id == id }

const val DPI_DEFAULT_STRATEGY_ID = "BDP-00"

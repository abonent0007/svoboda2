import re

root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007'

# 1) WarpGenerator.buildConfig: optional allowedIps override
p = root + r'\warp\WarpGenerator.kt'
s = open(p, encoding='utf-8').read()
old = """        i1: String = "",
        dns: String = DNS_CLOUDFLARE,
    ): String = buildString {"""
new = """        i1: String = "",
        dns: String = DNS_CLOUDFLARE,
        allowedIps: String? = null,
    ): String = buildString {"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """        appendLine("AllowedIPs = 0.0.0.0/0, ::/0")"""
new = """        appendLine("AllowedIPs = ${allowedIps ?: "0.0.0.0/0, ::/0"}")"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('WarpGenerator: allowedIps override')

# 2) WarpProfileFactory: service scope
p = root + r'\warp\WarpProfileFactory.kt'
s = open(p, encoding='utf-8').read()
old = """        dns: String = WarpGenerator.DNS_CLOUDFLARE,
        random: Random = Random.Default,
    ): Result = withContext(Dispatchers.IO) {"""
new = """        dns: String = WarpGenerator.DNS_CLOUDFLARE,
        serviceScope: Set<String> = emptySet(),
        allowedIpsOverride: String? = null,
        random: Random = Random.Default,
    ): Result = withContext(Dispatchers.IO) {"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """                dns = dnsValue,
            )"""
new = """                dns = dnsValue,
                allowedIps = allowedIpsOverride,
            )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('WarpProfileFactory: service scope')

# 3) DpiConfig: маршруты только по выбранным сервисам
p = root + r'\dpi\DpiConfig.kt'
s = open(p, encoding='utf-8').read()
old = """    fun build(prefs: Prefs, split: SplitTunnelConfig): String {"""
new = """    fun build(
        prefs: Prefs,
        split: SplitTunnelConfig,
        serviceScope: Set<String> = emptySet(),
        allowedIpsOverride: String? = null,
    ): String {"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """            appendLine("[Peer]")
            appendLine("PublicKey = $DUMMY_KEY")
            appendLine("AllowedIPs = 0.0.0.0/0")"""
new = """            appendLine("[Peer]")
            appendLine("PublicKey = $DUMMY_KEY")
            appendLine("AllowedIPs = ${allowedIpsOverride ?: "0.0.0.0/0"}")"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('DpiConfig: service scope')

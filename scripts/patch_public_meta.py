p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\own\PublicServerCatalog.kt'
s = open(p, encoding='utf-8').read()

old = """    private val meta = listOf(
        PublicServer("nl_1", "Нидерланды · 1010", "NL", "nl.tribukvy.ltd", 1010, "public_servers/nl_1.conf"),
        PublicServer("nl_2", "Нидерланды · 878", "NL", "nl.tribukvy.ltd", 878, "public_servers/nl_2.conf"),
        PublicServer("nl_3", "Нидерланды · 891", "NL", "nl.tribukvy.ltd", 891, "public_servers/nl_3.conf"),
        PublicServer("nl_4", "Нидерланды · 3476 (nl2)", "NL", "nl2.tribukvy.ltd", 3476, "public_servers/nl_4.conf"),
        PublicServer("nl_5", "Нидерланды · 880 (nl2)", "NL", "nl2.tribukvy.ltd", 880, "public_servers/nl_5.conf"),
        PublicServer("nl_6", "Нидерланды · 955 (nl2)", "NL", "nl2.tribukvy.ltd", 955, "public_servers/nl_6.conf"),
        PublicServer("nl_7", "Нидерланды · 3138 (nl3)", "NL", "nl3.tribukvy.ltd", 3138, "public_servers/nl_7.conf"),
        PublicServer("nl_8", "Нидерланды · 928 (nl3)", "NL", "nl3.tribukvy.ltd", 928, "public_servers/nl_8.conf"),
        PublicServer("nl_9", "Нидерланды · 942 (nl3)", "NL", "nl3.tribukvy.ltd", 942, "public_servers/nl_9.conf"),
        PublicServer("fi_1", "Финляндия · 1843", "FI", "fi.tribukvy.ltd", 1843, "public_servers/fi_1.conf"),
        PublicServer("fi_2", "Финляндия · 864", "FI", "fi.tribukvy.ltd", 864, "public_servers/fi_2.conf"),
        PublicServer("fi_3", "Финляндия · 934", "FI", "fi.tribukvy.ltd", 934, "public_servers/fi_3.conf"),
        PublicServer("any_1", "Anycast · страна после измерения · 3138", "US", "engage.cloudflareclient.com", 3138, "public_servers/any_1.conf"),
        PublicServer("any_2", "Anycast · страна после измерения · 7156", "US", "engage.cloudflareclient.com", 7156, "public_servers/any_2.conf"),
        PublicServer("any_3", "Anycast · страна после измерения · 903", "US", "engage.cloudflareclient.com", 903, "public_servers/any_3.conf"),
    )"""
new = """    // IP вписаны на дату 2026-11-17 (публичные хосты nl2/nl3 — мёртвые, NXDOMAIN;
    // nl.* жив, fi.* жив; engage.cloudflareclient.com — официальный endpoint WARP).
    private val meta = listOf(
        PublicServer("nl_1", "Нидерланды · 1010", "NL", "31.76.110.142", 1010, "public_servers/nl_1.conf"),
        PublicServer("nl_2", "Нидерланды · 878", "NL", "31.76.110.142", 878, "public_servers/nl_2.conf"),
        PublicServer("nl_3", "Нидерланды · 891", "NL", "31.76.110.142", 891, "public_servers/nl_3.conf"),
        PublicServer("fi_1", "Финляндия · 1843", "FI", "144.31.28.185", 1843, "public_servers/fi_1.conf"),
        PublicServer("fi_2", "Финляндия · 864", "FI", "144.31.28.185", 864, "public_servers/fi_2.conf"),
        PublicServer("fi_3", "Финляндия · 934", "FI", "144.31.28.185", 934, "public_servers/fi_3.conf"),
        PublicServer("any_1", "Anycast · страна после измерения · 3138", "US", "engage.cloudflareclient.com", 3138, "public_servers/any_1.conf"),
        PublicServer("any_2", "Anycast · страна после измерения · 7156", "US", "engage.cloudflareclient.com", 7156, "public_servers/any_2.conf"),
        PublicServer("any_3", "Anycast · страна после измерения · 903", "US", "engage.cloudflareclient.com", 903, "public_servers/any_3.conf"),
    )"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# комментарий/атрибуция — про IP
old = "// IP вписаны на дату 2026-11-17"
print('meta patched')
open(p, 'w', encoding='utf-8').write(s)

p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\method\DnsResolverPicker.kt'
s = open(p, encoding='utf-8').read()

old = "            val isChecking = checking.getOrNull() == def.id"
new = "            val isChecking = checking.value == def.id"
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """                TextButton(onClick = {
                    val ips = DnsResolvers.runCatchingAddresses(def)
                    onApply(ips)
                }) { Text("ВЫБРАТЬ", fontSize = 11.sp, color = AccentGreen) }"""
new = """                TextButton(onClick = {
                    scope.launch {
                        val ips = DnsResolvers.runCatchingAddresses(def)
                        if (ips.isNotBlank()) onApply(ips)
                    }
                }) { Text("ВЫБРАТЬ", fontSize = 11.sp, color = AccentGreen) }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """            TextButton(onClick = {
                val ips = DnsResolvers.runCatchingAddressesCustom(custom)
                if (ips.isNotEmpty()) onApply(ips)
            }) { Text("ПРИМЕНИТЬ", fontSize = 11.sp, color = AccentGreen) }"""
new = """            TextButton(onClick = {
                scope.launch {
                    val ips = DnsResolvers.runCatchingAddressesCustom(custom)
                    if (ips.isNotEmpty()) onApply(ips)
                }
            }) { Text("ПРИМЕНИТЬ", fontSize = 11.sp, color = AccentGreen) }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

open(p, 'w', encoding='utf-8').write(s)
print('DnsResolverPicker fixed')

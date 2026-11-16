import re

root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007'

# 1) Manager: Dpi_CATALOG -> DPI_CATALOG
p = root + r'\dpi\benchmark\DpiBenchmarkManager.kt'
s = open(p, encoding='utf-8').read()
s = s.replace('Dpi_CATALOG', 'DPI_CATALOG')
open(p, 'w', encoding='utf-8').write(s)
print('manager: catalog name fixed')

# 2) Tester: substringBefore on StringBuilder
p = root + r'\dpi\benchmark\DpiDomainTester.kt'
s = open(p, encoding='utf-8').read()
old = '        val line = buffer.substringBefore("\\r\\n")'
new = '        val line = buffer.toString().substringBefore("\\r\\n")'
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('tester: substringBefore fixed')

# 3) Socks5Client: explicit Int writes
p = root + r'\dpi\benchmark\Socks5Client.kt'
s = open(p, encoding='utf-8').read()
old = """        socket.outputStream.write(byteArrayOf(0x05, 0x01, 0x00))
        socket.outputStream.flush()"""
new = """        val out = socket.outputStream
        out.write(0x05)
        out.write(0x01)
        out.write(0x00)
        out.flush()"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """        val out = socket.outputStream
        out.write(byteArrayOf(0x05, 0x01, 0x00, atype))
        if (hostName != null) {
            out.write(hostName.toByteArray(Charsets.UTF_8).size.toByte())
            out.write(hostName.toByteArray(Charsets.UTF_8))
        } else {
            out.write(address)
        }
        out.write(byteArrayOf((targetPort shr 8).toByte(), (targetPort and 0xFF).toByte()))
        out.flush()"""
new = """        val out = socket.outputStream
        out.write(0x05)
        out.write(0x01)
        out.write(0x00)
        out.write(atype.toInt())
        if (hostName != null) {
            val hb = hostName.toByteArray(Charsets.UTF_8)
            out.write(hb.size)
            out.write(hb)
        } else {
            out.write(address)
        }
        out.write((targetPort shr 8) and 0xFF)
        out.write(targetPort and 0xFF)
        out.flush()"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('socks5: writes fixed')

# 4) Benchmark dialog: LocalContext captured in composable scope
p = root + r'\ui\benchmark\DpiBenchmarkDialog.kt'
s = open(p, encoding='utf-8').read()
old = """    val state by AppGraph.dpiBenchmark.state.collectAsState()
    val prefs = AppGraph.prefs"""
new = """    val state by AppGraph.dpiBenchmark.state.collectAsState()
    val prefs = AppGraph.prefs
    val context = LocalContext.current"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = "onExport = { exportNote = exportResults(LocalContext.current, current) },"
new = "onExport = { exportNote = exportResults(context, current) },"
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('dialog: LocalContext fixed')

p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()

# удалить внешние (неправильные) val-ы
old = "    var showBenchmark by remember { mutableStateOf(false) }\n    val publicServers = svoboda.runet.abonent0007.own.PublicServerCatalog.servers\n    val publicCountries = remember { AppGraph.prefs.publicServerCountries }"
new = "    var showBenchmark by remember { mutableStateOf(false) }"
assert s.count(old) == 1
s = s.replace(old, new, 1)

# добавить val-ы в WarpSettings (там есть scope и profileStatus)
old = """private fun WarpSettings(mtu: Int, onMtu: (Int) -> Unit) {
  val scope = rememberCoroutineScope()"""
new = """private fun WarpSettings(mtu: Int, onMtu: (Int) -> Unit) {
  val scope = rememberCoroutineScope()
  val publicServers = svoboda.runet.abonent0007.own.PublicServerCatalog.servers
  val publicCountries = remember { AppGraph.prefs.publicServerCountries }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('vars moved into WarpSettings')

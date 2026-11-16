p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\AppGraph.kt'
s = open(p, encoding='utf-8').read()
old = "    val appScope: CoroutineScope = CoroutineScope(SupervisorJob())\n"
new = old + "    lateinit var appContext: android.content.Context\n"
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = "        AppGraph.prefs = Prefs(this)\n"
new = "        AppGraph.appContext = this\n        AppGraph.prefs = Prefs(this)\n"
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('AppGraph: appContext added')

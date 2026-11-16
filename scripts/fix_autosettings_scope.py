p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()
old = """private fun AutoSettings() {
    val prefs = AppGraph.prefs"""
new = """private fun AutoSettings() {
    val prefs = AppGraph.prefs
    val scope = rememberCoroutineScope()"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('AutoSettings: scope added')

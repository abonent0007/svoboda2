p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()

# 1) убрать дублирующий импорт WarpGenerator (уже был)
old = "import svoboda.runet.abonent0007.warp.WarpGenerator\nimport svoboda.runet.abonent0007.split.ServiceCatalog\n"
new = "import svoboda.runet.abonent0007.split.ServiceCatalog\n"
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 2) prefs -> AppGraph.prefs
old = "    var directServices by remember { mutableStateOf(prefs.serviceDirectSelection) }"
new = "    var directServices by remember { mutableStateOf(AppGraph.prefs.serviceDirectSelection) }"
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = "                    prefs.serviceDirectSelection = directServices"
new = "                    AppGraph.prefs.serviceDirectSelection = directServices"
assert s.count(old) == 1
s = s.replace(old, new, 1)

open(p, 'w', encoding='utf-8').write(s)
print('fixed imports and prefs refs')

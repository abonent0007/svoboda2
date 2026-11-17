p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\home\HomeScreen.kt'
s = open(p, encoding='utf-8').read()

# 1) import BackHandler
old = "import androidx.compose.material3.rememberDrawerState\n"
new = "import androidx.activity.compose.BackHandler\n" + old
assert s.count(old) == 1
s = s.replace(old, new, 1)

# 2) after drawerState/scope declarations, add BackHandler that closes drawer
old = """    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
"""
new = """    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // BACK/навигация-назад в режиме меню: закрыть drawer (не выходить из приложения).
    // Выход — только с главной страницы (стандартное поведение Activity).
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

open(p, 'w', encoding='utf-8').write(s)
print('BackHandler added')

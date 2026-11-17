p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()

old1 = 'profileStatus = "Подключение ${server.host}:${server.port} …"'
if s.count(old1) == 1:
    s = s.replace(old1, 'status = "Подключение ${server.host}:${server.port} …"')

old2 = 'profileStatus = if (ok) {'
if s.count(old2) == 1:
    s = s.replace(old2, 'status = if (ok) {')

old3 = '''    }

    SectionTitle("DNS В КОНФИГЕ")'''
new3 = '''    }
    status?.let {
        TileRow(title = "Статус", value = it, opaque = true)
    }

    SectionTitle("DNS В КОНФИГЕ")'''
assert s.count(old3) == 1, s.count(old3)
s = s.replace(old3, new3, 1)

open(p, 'w', encoding='utf-8').write(s)
print('fixed')

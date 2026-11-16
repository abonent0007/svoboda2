import io, re

cat = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\split\RussianDirectCatalog.kt'
src = open(cat, encoding='utf-8').read()

packages = sorted(set(re.findall(r'"([a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+)"', src)))
real = [p for p in packages if not p.startswith(('id',))]
# filter out non-package words accidentally matched
skip = {'banks', 'web', 'gov', 'packages', 'title', 'id'}
pkgs = [p for p in packages if p not in skip and '.' in p]
print(f'found {len(pkgs)} packages')

man = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\AndroidManifest.xml'
m = open(man, encoding='utf-8').read()

new_queries = '    <queries>\n        <intent>\n            <action android:name="android.intent.action.MAIN" />\n            <category android:name="android.intent.category.LAUNCHER" />\n        </intent>\n'
for p in pkgs:
    new_queries += f'        <package android:name="{p}" />\n'
new_queries += '    </queries>'

m = re.sub(r'    <queries>.*?</queries>', new_queries, m, flags=re.S)
open(man, 'w', encoding='utf-8').write(m)
print('manifest updated')

p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\ui\method\MethodSettings.kt'
s = open(p, encoding='utf-8').read()
pairs = [
    ('"(?m)^Jc\\s*=\\s*(\\d+)"', '"(?m)^Jc\\\\s*=\\\\s*(\\\\d+)"'),
    ('"(?m)^Jmin\\s*=\\s*(\\d+)"', '"(?m)^Jmin\\\\s*=\\\\s*(\\\\d+)"'),
    ('"(?m)^Jmax\\s*=\\s*(\\d+)"', '"(?m)^Jmax\\\\s*=\\\\s*(\\\\d+)"'),
]
for old, new in pairs:
    n = s.count(old)
    print('anchor', old[:14], n)
    s = s.replace(old, new)
open(p, 'w', encoding='utf-8').write(s)
print('escapes fixed')

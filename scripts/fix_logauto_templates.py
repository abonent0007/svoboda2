import re

p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()

pairs = [
    ('"anti-bounce: hold ${\'$\'}{prefs.autoHoldMinutes}m"', '"anti-bounce: hold ${prefs.autoHoldMinutes}m"'),
    ('"AUTO start; order=${\'$\'}{prefs.autoTransportOrder}; countries=${\'$\'}{prefs.autoCountryPriority}"', '"AUTO start; order=${prefs.autoTransportOrder}; countries=${prefs.autoCountryPriority}"'),
    ('"try candidate #${\'$\'}{i + 1}"', '"try candidate #${i + 1}"'),
    ('"candidate #${\'$\'}{i + 1} selected: ${\'$\'}{controller.currentProfileId}"', '"candidate #${i + 1} selected: ${controller.currentProfileId}"'),
    ('"done: profile=${controller.currentProfileId}"', '"done: profile=${controller.currentProfileId}"'),
]
fixed = 0
for old, new in pairs:
    n = s.count(old)
    if n:
        s = s.replace(old, new)
        fixed += 1
        print(f'fixed {fixed}: {old[:40]}')

open(p, 'w', encoding='utf-8').write(s)
print('total fixed:', fixed)

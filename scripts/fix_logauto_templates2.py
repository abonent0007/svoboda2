p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()

pairs = [
    ("${'$'}{prefs.autoHoldMinutes}", "${prefs.autoHoldMinutes}"),
    ("${'$'}{prefs.autoTransportOrder}", "${prefs.autoTransportOrder}"),
    ("${'$'}{prefs.autoCountryPriority}", "${prefs.autoCountryPriority}"),
    ("${'$'}{i + 1}", "${i + 1}"),
    ("${'$'}{controller.currentProfileId}", "${controller.currentProfileId}"),
]
fixed = 0
for old, new in pairs:
    n = s.count(old)
    s = s.replace(old, new)
    fixed += n

open(p, 'w', encoding='utf-8').write(s)
print('total fixed:', fixed)

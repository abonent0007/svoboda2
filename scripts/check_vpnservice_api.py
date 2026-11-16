import re
import sys
import zipfile

jar = r'C:\Users\Abonen0007\Android\Sdk\platforms\android-35\android.jar'
z = zipfile.ZipFile(jar)
names = [n for n in z.namelist() if n.startswith('android/net/VpnService')]
print('classes:', names)

for cls in ['android/net/VpnService$Profile.class', 'android/net/VpnService$Builder.class']:
    try:
        data = z.read(cls)
    except KeyError:
        print(cls, '-> NOT FOUND')
        continue
    strings = re.findall(rb'[ -~]{4,}', data)
    interesting = [s.decode('ascii') for s in strings if any(k in s for k in
                    (b'Profile', b'setProfile', b'excludeDisallowed', b'allowedApplications',
                     b'setMtu', b'setBlocking', b'getProfile', b'addDisallowed', b'addAllowed'))]
    print(f'--- {cls} ({len(data)} bytes) ---')
    for s in sorted(set(interesting)):
        print('   ', s)

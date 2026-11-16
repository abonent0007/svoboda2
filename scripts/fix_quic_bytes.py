p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\warp\QuicInitial.kt'
s = open(p, encoding='utf-8').read()

pairs = [
    ('writeByte(dcid.size.toByte())', 'writeByte(dcid.size)'),
    ('writeByte(scid.size.toByte())', 'writeByte(scid.size)'),
    ('writeByte((body.size shr 16).toByte())', 'writeByte((body.size shr 16) and 0xFF)'),
    ('writeByte((body.size shr 8).toByte())', 'writeByte((body.size shr 8) and 0xFF)'),
    ('writeByte(body.size.toByte())', 'writeByte(body.size and 0xFF)'),
]
for old, new in pairs:
    assert s.count(old) == 1, old
    s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('QuicInitial: byte args fixed')

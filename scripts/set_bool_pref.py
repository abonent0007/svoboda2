import re
import sys
import xml.etree.ElementTree as ET

p, key, value, out = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
s = open(p, encoding='utf-8').read()
pattern = r'<boolean name="' + re.escape(key) + r'" value="(true|false)" ?/>'
if re.search(pattern, s):
    s2 = re.sub(pattern, f'<boolean name="{key}" value="{value}" />', s)
else:
    s2 = s.replace('</map>', f'    <boolean name="{key}" value="{value}" />\n</map>')
open(out, 'w', encoding='utf-8').write(s2)
ET.parse(out)
print('ok, contains key:', key in s2, 'value:', value)

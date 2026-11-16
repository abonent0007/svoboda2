import re
import sys
import xml.etree.ElementTree as ET

# set_string_pref.py <in.xml> <key> <value> <out.xml>
p, key, value, out = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
s = open(p, encoding='utf-8').read()
pattern = r'<string name="' + re.escape(key) + r'"[^>]*>.*?</string>'
if re.search(pattern, s):
    s = re.sub(pattern, f'<string name="{key}">{value}</string>', s)
else:
    s = s.replace('</map>', f'    <string name="{key}">{value}</string>\n</map>')
open(out, 'w', encoding='utf-8').write(s)
ET.parse(out)
print('ok:', key, '=', value)

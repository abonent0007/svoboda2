import re
import sys
import xml.etree.ElementTree as ET

p, strategy, out = sys.argv[1], sys.argv[2], sys.argv[3]
s = open(p, encoding='utf-8').read()
s2 = re.sub(r'<string name="dpi_strategy_id">.*?</string>',
            '<string name="dpi_strategy_id">' + strategy + '</string>', s)
if s2 == s:
    s2 = s.replace('</map>', '    <string name="dpi_strategy_id">' + strategy + '</string>\n</map>')
open(out, 'w', encoding='utf-8').write(s2)
ET.parse(out)
print('edited ok, strategy in file:', strategy in s2)

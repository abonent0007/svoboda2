import re
import sys
import xml.etree.ElementTree as ET

p, out = sys.argv[1], sys.argv[2]
s = open(p, encoding='utf-8').read()

def set_str(text, key, value):
    pat = r'<string name="' + re.escape(key) + r'"[^>]*>.*?</string>'
    new = '<string name="' + key + '">' + value + '</string>'
    if re.search(pat, text):
        return re.sub(pat, new, text)
    return text.replace('</map>', '    ' + new + '\n</map>')

def set_bool(text, key, value):
    pat = r'<boolean name="' + re.escape(key) + r'" value="(true|false)" ?/>'
    new = '<boolean name="' + key + '" value="' + value + '" />'
    if re.search(pat, text):
        return re.sub(pat, new, text)
    return text.replace('</map>', '    ' + new + '\n</map>')

s = set_str(s, 'connection_method', 'AUTO')
s = set_bool(s, 'auto_include_public_servers', 'true')
open(out, 'w', encoding='utf-8').write(s)
ET.parse(out)
print('prefs edited ok')

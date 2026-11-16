import json
import os
import sys

path = sys.argv[1]
d = json.load(open(path, encoding='utf-8'))
for x in d.get('tree', []):
    if x['type'] == 'blob':
        print(f"{x['path']}  ({x.get('size', 0)})")

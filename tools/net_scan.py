import os, re, sys

ROOT = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main'
NET_PATTERNS = [
    (re.compile(r'\.newCall\s*\('), 'okhttp execute call'),
    (re.compile(r'\.execute\s*\(\s*\)'), 'okhttp execute()'),
    (re.compile(r'URL\s*\(.+\)\s*\.(openConnection|openStream|readText|readBytes)'), 'URL open/read'),
    (re.compile(r'HttpURLConnection'), 'HttpURLConnection'),
    (re.compile(r'\.connect\s*\(\s*InetSocketAddress'), 'raw socket connect'),
    (re.compile(r'Socks5Client\s*\('), 'socks5 benchmark client'),
    (re.compile(r'DoH|dns\.google|1\.1\.1\.1'), 'doh/dns literal'),
]
HANDLER_RX = re.compile(r'(try|catch|runCatching|onFailure|getOrNull|getOrDefault|isSuccessful|Socket\(\).use|\.use\s*\{)')

def scan_file(path):
    hits = []
    with open(path, 'r', encoding='utf-8', errors='replace') as f:
        lines = f.readlines()
    for i, line in enumerate(lines):
        if line.lstrip().startswith('//') or line.lstrip().startswith('*'):
            continue
        for rx, label in NET_PATTERNS:
            if rx.search(line):
                lo = max(0, i - 12)
                hi = min(len(lines), i + 24)
                window = ''.join(lines[lo:hi])
                handled = bool(HANDLER_RX.search(window))
                hits.append((i + 1, label, line.rstrip()[:120], handled))
    return hits

total = 0
unhandled = []
for dirpath, _, files in os.walk(ROOT):
    for name in files:
        if not name.endswith('.kt'):
            continue
        path = os.path.join(dirpath, name)
        hits = scan_file(path)
        for ln, label, src, handled in hits:
            total += 1
            rel = os.path.relpath(path, ROOT)
            mark = 'OK ' if handled else '!! '
            print(f'{mark}{rel}:{ln}: [{label}] {src.strip()}')
            if not handled:
                unhandled.append((rel, ln, label, src.strip()))
print()
print(f'TOTAL network-ish call sites: {total}; without nearby error handling: {len(unhandled)}')
sys.exit(0)

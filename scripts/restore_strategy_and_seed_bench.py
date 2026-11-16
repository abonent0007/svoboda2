import re
import sys

p, out = sys.argv[1], sys.argv[2]
ORIGINAL = "-d1 -s1+s -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -a1"
BENCH_DOMAINS = [
    "google.com", "ya.ru", "cloudflare.com",
    "youtube.com", "googlevideo.com", "ytimg.com",
    "web.telegram.org", "t.me",
    "chatgpt.com", "gemini.google.com", "grok.com", "api.groq.com",
    "github.com", "raw.githubusercontent.com",
    "wikipedia.org",
]

s = open(p, encoding='utf-8').read()

def set_str(text, key, value):
    pattern = r'<string name="' + re.escape(key) + r'"[^>]*>.*?</string>'
    if re.search(pattern, text):
        return re.sub(pattern, f'<string name="{key}">{value}</string>', text)
    return text.replace('</map>', f'    <string name="{key}">{value}</string>\n</map>')

def set_bool(text, key, value):
    pattern = r'<boolean name="' + re.escape(key) + r'" value="(true|false)" ?/>'
    if re.search(pattern, text):
        return re.sub(pattern, f'<boolean name="{key}" value="{value}" />', text)
    return text.replace('</map>', f'    <boolean name="{key}" value="{value}" />\n</map>')

def set_set(text, key, values):
    block = '\n'.join(f'    <string name="{key}">{v}</string>' for v in values)
    pattern = r'(    <string name="' + re.escape(key) + r'">.*?</string>\n)+'
    if re.search(pattern, text):
        return re.sub(pattern, block + '\n', text)
    return text.replace('</map>', block + '\n</map>')

# 1) текущую стратегию — в prev (откат)
m = re.search(r'<string name="dpi_custom_strategy">([^<]*)</string>', s)
current = m.group(1) if m else ''
s = set_str(s, 'dpi_custom_strategy_prev', current)

# 2) восстанавливаем исходную рабочую стратегию
s = set_str(s, 'dpi_custom_strategy', ORIGINAL)

# 3) режим DPI + чистые замеры (без mapdns)
s = set_str(s, 'connection_method', 'DPI_DIRECT')
s = set_bool(s, 'dpi_map_dns', 'false')

# 4) набор доменов бенчмарка
s = set_set(s, 'dpi_benchmark_domains', BENCH_DOMAINS)

open(out, 'w', encoding='utf-8').write(s)
print('prev strategy:', current[:40])
print('restored    :', ORIGINAL)
print('bench domains:', len(BENCH_DOMAINS))

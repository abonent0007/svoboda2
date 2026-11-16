import sqlite3
import sys
from collections import defaultdict

db = sys.argv[1] if len(sys.argv) > 1 else r'C:\Users\Abonen0007\Documents\svoboda2\bench_run.db'
con = sqlite3.connect(db)
cur = con.cursor()

print('=== sessions ===')
for row in cur.execute(
    "SELECT session_id, network_type, campaign_seed, app_version, device_model, carrier, mtu, "
    "total_strategies, completed_strategies, status, best_strategy_id FROM test_session"
):
    print(' ', row)

print('\n=== DIRECT baseline (route_type=DIRECT) ===')
for row in cur.execute(
    "SELECT strategy_id, successful_domains, failed_domains FROM strategy_test WHERE route_type='DIRECT'"
):
    print(' ', row)
direct = {}
for row in cur.execute(
    "SELECT domain, dns_success, tcp_success, tls_success, http_success, http_status, outcome "
    "FROM domain_test WHERE test_id IN (SELECT test_id FROM strategy_test WHERE route_type='DIRECT')"
):
    direct[row[0]] = row

print('\n=== рейтинг стратегий ===')
rows = list(cur.execute(
    "SELECT st.strategy_id, st.strategy_name, st.successful_domains, st.failed_domains, st.median_http_ms, "
    "st.error_count, st.test_id FROM strategy_test st WHERE st.route_type='SOCKS' "
    "ORDER BY st.successful_domains DESC, st.median_http_ms ASC"
))

def domains_of(test_id):
    return list(cur.execute(
        "SELECT domain, outcome, http_status FROM domain_test WHERE test_id=?", (test_id,)
    ))

print(f'{"стратегия":<12} {"сеть":<6} {"gain":<5} {"регресс":<7} {"медиана":<9} категории')
ranking = []
for sid, sname, ok, fail, med, errc, tid in rows:
    doms = domains_of(tid)
    gain = sum(1 for d, outcome, _ in doms if d in direct and direct[d][1] == 0 and outcome in ('FULL_SUCCESS', 'TRANSPORT_ONLY', 'SERVER_LIMITED'))
    reg = sum(1 for d, outcome, _ in doms if d in direct and direct[d][1] == 1 and outcome == 'NETWORK_FAILURE')
    cats = defaultdict(lambda: [0, 0])
    for d, outcome, _ in doms:
        cats[d][1] += 1
        if outcome != 'NETWORK_FAILURE':
            cats[d][0] += 1
    macro = sum(a / b for a, b in cats.values()) / max(len(cats), 1)
    ranking.append((macro, sid, sname, ok, fail, gain, reg, med, dict(cats)))
    catstr = ' '.join(f'{k}:{v[0]}/{v[1]}' for k, v in sorted(cats.items()))
    print(f'{sid:<12} {ok}/{ok+fail:<4} {gain:<5} {reg:<7} {str(med)+"мс":<9} {catstr}')

ranking.sort(reverse=True)
print('\n=== ТОП по макро-оценке ===')
for macro, sid, sname, ok, fail, gain, reg, med, cats in ranking[:10]:
    print(f'  {sid:<12} макро={macro*100:.0f}%  сеть {ok}/{ok+fail}  gain={gain}  регресс={reg}  медиана={med}мс')

print('\n=== лучшие по категориям ===')
cats_all = defaultdict(list)
for macro, sid, sname, ok, fail, gain, reg, med, cats in ranking:
    for c, (a, b) in cats.items():
        cats_all[c].append((a / b, a, b, sid))
for c, lst in sorted(cats_all.items()):
    lst.sort(reverse=True)
    print(f'  {c}: ' + ', '.join(f'{sid}({a}/{b})' for _, a, b, sid in lst[:4]))

con.close()

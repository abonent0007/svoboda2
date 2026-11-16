import sqlite3
import sys

db = sys.argv[1] if len(sys.argv) > 1 else r'C:\Users\Abonen0007\Documents\svoboda2\bench.db'
con = sqlite3.connect(db)
cur = con.cursor()

print('=== sessions ===')
for row in cur.execute("SELECT session_id, network_type, mapdns_enabled, total_strategies, completed_strategies, status, best_strategy_id FROM test_session"):
    print(' ', row)

print('=== strategy_test (top by successful_domains) ===')
for row in cur.execute("SELECT strategy_id, strategy_name, connected, successful_domains, failed_domains, ROUND(success_rate,2), median_http_ms, error_count FROM strategy_test ORDER BY successful_domains DESC, median_http_ms ASC LIMIT 12"):
    print(' ', row)

print('=== domain_test sample ===')
for row in cur.execute("SELECT domain, dns_success, resolved_ip, tcp_success, tcp_latency_ms, tls_success, http_status, http_latency_ms, error_type FROM domain_test LIMIT 8"):
    print(' ', row)

print('=== attempts sample ===')
for row in cur.execute("SELECT attempt_no, dns_ok, resolved_ip, tcp_ok, tcp_ms, tls_ok, tls_ms, http_status, http_ok, http_ms, error_type FROM attempt LIMIT 8"):
    print(' ', row)

print('=== counts ===')
for t in ('test_session', 'strategy_test', 'domain_test', 'attempt'):
    print(' ', t, cur.execute(f'SELECT COUNT(*) FROM {t}').fetchone()[0])

con.close()

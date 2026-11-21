# Passive DPI monitoring - 2026-11-19

Authorized by user: keep DPI connected, passive observation 1-2 h.
Forbidden (and NOT performed by this script): changing connection_method,
starting AUTO/WARP, new WARP registration, touching DNS/keys/profiles/quarantine.

Method: curl from the device shell (uid 2000 -> ip rule 13000 -> table 1334 = VPN/tun94).
This is real HTTPS through the TUN route. Zero UI interaction.
example.com: baseline LOADED. Any 4xx/5xx from CF endpoints == server reachable.
000 == transport/TLS blocked (matches known WARP-reg outage).
DNS over DPI is slow/flaky; budgets raised to 20s connect / 60s total.
Intermittent FAIL rows are kept as-is (real signal, not hidden).

2026-11-19 12:43:57 | === monitor started (Hours=2, IntervalMinutes=15, Once=True) ===
2026-11-19 12:43:57 | ----- iteration 1 -----
2026-11-19 12:43:58 | example.com (HTTPS via TUN)   | http=200 time=0.654742s ip=8.47.69.0 | LOADED (2xx/3xx)
2026-11-19 12:44:18 | api.cloudflareclient.com/reg | http=0 time=20.000016s ip=8.47.69.0 curl_exit=28 err='SSL connection timeout' | FAIL (000: transport/TLS blocked)
2026-11-19 12:44:38 | engage.cloudflareclient.com  | http=0 time=20.002023s ip=162.159.192.1 curl_exit=28 err='SSL connection timeout' | FAIL (000: transport/TLS blocked)
2026-11-19 12:44:38 | applog | 11-19 12:44:13.211 12681 12809 I SvobodaVpn: markDegraded degraded=false oldPhase=CONNECTED
2026-11-19 12:44:38 | === monitor finished ===
2026-11-19 12:44:52 | === monitor started (Hours=2, IntervalMinutes=15, Once=False) ===
2026-11-19 12:44:52 | ----- iteration 1 -----
2026-11-19 12:44:54 | example.com (HTTPS via TUN)   | http=200 time=1.620869s ip=8.47.69.0 | LOADED (2xx/3xx)
2026-11-19 12:45:14 | api.cloudflareclient.com/reg | http=0 time=20.001913s ip=8.47.69.0 curl_exit=28 err='SSL connection timeout' | FAIL (000: transport/TLS blocked)
2026-11-19 12:45:34 | engage.cloudflareclient.com  | http=0 time=20.001996s ip=162.159.192.1 curl_exit=28 err='SSL connection timeout' | FAIL (000: transport/TLS blocked)
2026-11-19 12:45:35 | applog | 11-19 12:44:46.852 12681 12807 I SvobodaVpn: markDegraded degraded=false oldPhase=CONNECTED
2026-11-19 12:45:35 | applog | 11-19 12:45:17.564 12681 12758 I SvobodaVpn: markDegraded degraded=false oldPhase=CONNECTED
2026-11-19 13:00:35 | ----- iteration 2 -----
2026-11-19 13:08:57 | example.com (HTTPS via TUN)   | http=0 time=502.007063s ip= curl_exit=28 err='Resolving timed out after 20000 milliseconds' | FAIL (000: transport/TLS blocked)
2026-11-19 13:09:17 | api.cloudflareclient.com/reg | http=0 time=20.001196s ip=8.6.112.0 curl_exit=28 err='SSL connection timeout' | FAIL (000: transport/TLS blocked)
2026-11-19 13:09:37 | engage.cloudflareclient.com  | http=0 time=20.001167s ip=162.159.192.1 curl_exit=28 err='SSL connection timeout' | FAIL (000: transport/TLS blocked)
2026-11-19 13:09:37 | applog | 11-19 13:09:29.384 18379 22865 I SvobodaVpn: markDegraded degraded=false oldPhase=CONNECTED

2026-11-19 13:15:33 | === monitor STOPPED by user order (not expired naturally) ===

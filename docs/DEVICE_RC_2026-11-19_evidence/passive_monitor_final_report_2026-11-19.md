# Пассивный мониторинг DPI — итоговый отчёт (2026-11-19)

Окно наблюдения (по файлу passive_monitor_2026-11-19.md):
- 12:43:57 — контрольный прогон -Once (проверка инструмента), всё записано.
- 12:44:52 — старт 2-часового мониторинга; 13:15:09 — остановлен по решению пользователя (не отработал естественно).
- Разовая проверка 12:42:44: example.com http=200 (0.65 с), CF reg/engage: SSL connection timeout, SvobodaVpn oldPhase=CONNECTED.

## Выводы
| Проверка | Результат |
|---|---|
| example.com (HTTPS через TUN) | LOADED 2 из 3 в окне 12:43–13:09 (12:43:58 → 200/0.65s; 12:44:54 → 200/1.62s); FAIL 13:08:57 — DNS resolving timed out (20 c), окно ~502 c |
| api.cloudflareclient.com/v0a2158/reg | SSL connection timeout 100 % (3/3) |
| engage.cloudflareclient.com | SSL connection timeout 100 % (3/3) |
| SvobodaVpn health tick | CONNECTED всё окно — в т. ч. когда TUN не резолвил DNS (health-check НЕ различает) |

## Заметки для аудита DPI
- DNS через DPI нестабилен: минимум 2 провала сегодня (12:42 — транзиентно; 13:00–13:08 — жёстко).
- App pid изменился (12681 → 18379) между 12:45 и 13:09 — процесс приложения рестартовал; причина не установлена (не вызвана мониторингом — тот только curl/logcat).
- Health tick приложения НЕ доказывает data-plane для приложений: в 13:08 TUN не резолвил DNS, а SvobodaVpn показывал CONNECTED. Кандидат в аудит B: «здоровье» считать только по HTTPS-proof, как при выборе кандидата в AUTO.

## Контрольные суммы (SHA-256)
- passive_monitor_2026-11-19.md: A89109D2F633D8F835AF015E590AB9D06DB344262FDB7D89408CA91E611B316F
- dpi_crash.log:                6989D671B792A537CB8CA9A174A1EFFA536E3E808DDACA48D3F4C2F98294C11E
- DPI_AUDIT_PLAN.md:            809025ED4960E38808C5F3FE1E9FF7CDF665F2FBF95E31802D2E22E069642A16
- DPI_FALLBACK_DECISION.md:     F18645BC3740A0EEBDD6721D56D3E5BB9E5E71F197551EDB9FEE787893962BD8

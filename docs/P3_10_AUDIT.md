# P3.10 — GoBackend Lifecycle Audit (Stage A)

## 1. Реализация GoBackend
- Источник: vendored `third_party/amneziawg-android` (amnezia-vpn/amneziawg-android, tag v3.1.20260814), модуль `:tunnel`.
- Класс сервиса: `org.amnezia.awg.backend.GoBackend$VpnService` (inner class).
- Зарегистрирован в `tunnel/src/main/AndroidManifest.xml`:
  - `android:permission="android.permission.BIND_VPN_SERVICE"`
  - `android:exported="false"`
  - intent-filter `android.net.VpnService`
- В app-манифесте сервис не переопределяется; метаданных `SUPPORTS_ALWAYS_ON` нет.

## 2. Lifecycle (фактическая реализация)
- `onCreate()`: `vpnService.complete(this)`; `super.onCreate()`.
- `onDestroy()`: если owner != null и есть tunnel — `awgTurnOff`, очистка currentTunnel/currentConfig, `tunnel.onStateChange(State.DOWN)`; `vpnService = newIncompleteFuture()`; `super.onDestroy()`.
- `onStartCommand(intent, flags, startId)`: `vpnService.complete(this)`; если intent == null или компонент не из нашего пакета → лог "Service started by Always-on VPN feature" и вызов `alwaysOnCallback.alwaysOnTriggered()` (если зарегистрирован). Возвращает `super.onStartCommand`.
- `onRevoke()`: НЕ переопределён → используется стандартная реализация Android (`stopSelf()`).
- `startForeground()`: НЕ вызывается нигде в tunnel-модуле.

## 3. Always-on
- `setAlwaysOnCallback(AlwaysOnCallback)` — статический метод, хранит callback в статическом поле.
- В нашем приложении НЕ зарегистрирован (поиск по коду не нашёл вызовов).
- `SUPPORTS_ALWAYS_ON` metadata в манифесте отсутствует.

## 4. Foreground Service
- Нет вызовов `startForeground()` / `stopForeground()` в tunnel-модуле.
- В app-манифесте нет `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` разрешений и `foregroundServiceType`.
- Приложение работает без явного foreground-перехода (Android допускает для VpnService короткое время).

## 5. Process death / восстановление
- `onDestroy` очищает ресурсы, но при принудительном убийстве процесса не вызывается.
- Always-on callback не зарегистрирован → системный перезапуск не восстанавливает VPN.
- `VpnController` хранит `activeConfigText` в памяти (теряется при убийстве процесса); `lastGoodConfig` в Prefs (зашифрован) сохраняется.

## 6. Риски
- onRevoke → stopSelf → onDestroy очищает TUN/engine, но наше приложение не получает уведомления → UI может показывать ложный CONNECTED.
- ReconnectCoordinator не блокируется при onRevoke → возможен автопереподключение после системного отзыва.
- Нет foreground-уведомления → на Android 14+ возможны особенности при длительной работе.
- Always-on не настроен → при перезагрузке устройства VPN не восстанавливается.

## 7. Минимальный план изменений (Stage B)
1. Зарегистрировать `GoBackend.setAlwaysOnCallback()` из Application (AppGraph) — один раз, без Activity.
2. Добавить callback, который вызывает `connectionState.setVpn(REVOKED)` и блокирует ReconnectCoordinator (set shouldBeConnected=false).
3. Добавить `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` разрешения и `foregroundServiceType="systemExempted"` в app-манифесте только после проверки условий Android.
4. Добавить `SUPPORTS_ALWAYS_ON` metadata если включаем Always-on.
5. Не создавать второй VpnService.
6. Не переписывать GoBackend.

## 8. Что НЕ менять
- GoBackend внутренний VpnService.
- Механизм establish/TUN.
- SecureConfigStorage, ConnectionManager, ReconnectCoordinator логику (только добавить hook onRevoke).
- Формат конфигов.

## 9. Следующий шаг
Подтвердить план и реализовать Stage B минимальными изменениями:
- Регистрация AlwaysOnCallback в SvobodaApp.
- Hook onRevoke через callback (если GoBackend предоставляет; иначе — отдельный механизм).
- Foreground Service metadata/permissions.
- Тесты на реальном устройстве.

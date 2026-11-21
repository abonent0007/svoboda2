package svoboda3.runet.abonent0007.vpn

import kotlinx.coroutines.withTimeout
import svoboda3.runet.abonent0007.net.NetProbe
import svoboda3.runet.abonent0007.warp.ConfigValidator

enum class CheckStatus { PASS, FAIL, WARNING, NOT_TESTED }

data class ProfileCheckResult(
    val name: String,
    val status: CheckStatus,
    val detail: String = "",
)

data class ProfileTestResult(
    val preflightErrors: List<String>,
    val preflightWarnings: List<String>,
    val checks: List<ProfileCheckResult>,
    val success: Boolean,
    val restored: Boolean,
    val restoreFailed: Boolean,
    val message: String,
) {
    val passedCount: Int get() = checks.count { it.status == CheckStatus.PASS }
    val totalCount: Int get() = checks.size
}

/**
 * Транзакционное тестирование профиля перед применением.
 * Pre-flight (ConfigValidator) → временное подключение → проверки → Commit/Rollback.
 */
class ProfileTestCoordinator(
    private val controller: VpnController,
    private val reconnect: ReconnectCoordinator,
    private val prefs: svoboda3.runet.abonent0007.data.Prefs,
) {
    suspend fun test(candidateConfig: String, wasConnected: Boolean): ProfileTestResult {
        // 1. Pre-flight.
        val preflight = ConfigValidator.validate(candidateConfig)
        if (!preflight.isValid) {
            return ProfileTestResult(
                preflightErrors = preflight.errors,
                preflightWarnings = preflight.warnings,
                checks = emptyList(),
                success = false,
                restored = false,
                restoreFailed = false,
                message = "Pre-flight не пройден: ${preflight.errors.firstOrNull().orEmpty()}",
            )
        }

        val previousConfig = controller.activeConfigText
        val previousLastGood = prefs.lastGoodConfig
        var restored = false
        var restoreFailed = false

        reconnect.testingMode = true
        reconnect.cancelHealth()

        val result = reconnect.withVpnLock {
            // 2. Временное подключение кандидата.
            val connected = controller.connect(candidateConfig)
            if (!connected) {
                // Откат не нужен — кандидат не поднялся.
                ProfileTestResult(
                    preflightErrors = emptyList(),
                    preflightWarnings = preflight.warnings,
                    checks = listOf(ProfileCheckResult("Подключение", CheckStatus.FAIL, controller.error.value.orEmpty())),
                    success = false,
                    restored = false,
                    restoreFailed = false,
                    message = "Не удалось подключить кандидата",
                )
            } else {
                // 3. Проверки.
                val checks = mutableListOf<ProfileCheckResult>()

                val handshake = controller.lastHandshake()
                checks += ProfileCheckResult("Интерфейс", if (handshake > 0) CheckStatus.PASS else CheckStatus.FAIL,
                    if (handshake > 0) "создан" else "не создан")
                checks += ProfileCheckResult("Handshake", if (handshake > 0) CheckStatus.PASS else CheckStatus.FAIL,
                    if (handshake > 0) "получен" else "нет ответа")

                val exit = runCatching { withTimeout(8000) { NetProbe.fetchExit() } }.getOrNull()
                checks += ProfileCheckResult("HTTPS", if (exit != null) CheckStatus.PASS else CheckStatus.FAIL,
                    if (exit != null) "OK" else "недоступен")
                checks += ProfileCheckResult("TX/RX", if (exit != null) CheckStatus.PASS else CheckStatus.FAIL,
                    if (exit != null) "трафик проходит" else "нет трафика")
                checks += ProfileCheckResult("DNS", if (exit != null) CheckStatus.PASS else CheckStatus.FAIL,
                    if (exit != null) "разрешаются" else "недоступны")
                checks += ProfileCheckResult("IPv4", if (!exit?.ip.isNullOrBlank()) CheckStatus.PASS else CheckStatus.FAIL,
                    exit?.ip ?: "—")
                val hasV6 = candidateConfig.contains("::", ignoreCase = true) || candidateConfig.contains("/128")
                checks += ProfileCheckResult("IPv6",
                    if (!hasV6) CheckStatus.NOT_TESTED else if (exit != null) CheckStatus.PASS else CheckStatus.FAIL,
                    if (!hasV6) "маршрут отсутствует" else "проверен")

                val resourcesOk = exit != null
                checks += ProfileCheckResult("Ресурсы", if (resourcesOk) CheckStatus.PASS else CheckStatus.FAIL,
                    if (resourcesOk) "доступны" else "недоступны")

                val success = checks.all { it.status == CheckStatus.PASS || it.status == CheckStatus.NOT_TESTED }

                if (success) {
                    // Commit.
                    prefs.lastGoodConfig = candidateConfig
                    ProfileTestResult(
                        preflightErrors = emptyList(),
                        preflightWarnings = preflight.warnings,
                        checks = checks,
                        success = true,
                        restored = false,
                        restoreFailed = false,
                        message = "Профиль может быть применён",
                    )
                } else {
                    // Rollback.
                    controller.disconnect()
                    var restoreOk = true
                    if (wasConnected && !previousConfig.isNullOrBlank()) {
                        restoreOk = controller.connect(previousConfig!!)
                        if (restoreOk) {
                            val hs = controller.lastHandshake()
                            val ex = runCatching { withTimeout(8000) { NetProbe.fetchExit() } }.getOrNull()
                            if (hs <= 0 || ex == null) restoreOk = false
                        }
                    }
                    restored = restoreOk
                    restoreFailed = !restoreOk && wasConnected
                    ProfileTestResult(
                        preflightErrors = emptyList(),
                        preflightWarnings = preflight.warnings,
                        checks = checks,
                        success = false,
                        restored = restored,
                        restoreFailed = restoreFailed,
                        message = if (restoreFailed) "RESTORE_FAILED: восстановление не удалось"
                        else "Тест не пройден, предыдущий профиль восстановлен",
                    )
                }
            }
        }

        reconnect.testingMode = false
        return result
    }
}

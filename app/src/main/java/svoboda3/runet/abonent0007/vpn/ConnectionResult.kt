package svoboda3.runet.abonent0007.vpn

/**
 * Контракт результата попытки подключения (P0, 2026-11-18).
 *
 * Deferred никогда не превращается в Connected; «подключено» возможно только
 * после подтверждения data-plane (две независимые HTTPS-проверки).
 */
sealed interface ConnectionResult {
    /** Туннель поднят И data-plane подтверждён (обе проверки или одна из них). */
    data object ConnectedHealthy : ConnectionResult

    /** Туннель поднят, data-plane ещё валидируется (переходное состояние). */
    data object TransportUpValidating : ConnectionResult

    /** Попытка отложена антидребезгом (только между рабочими кандидатами). */
    data object ReconnectDeferred : ConnectionResult

    /** Кандидат провалил data-plane — исключён из текущего цикла. */
    data class CandidateDegraded(
        val candidateId: String,
        val kind: FailureKind,
        val detail: String,
    ) : ConnectionResult

    /** Идёт переход к следующему кандидату. */
    data object FailoverInProgress : ConnectionResult

    /** Кандидаты исчерпаны: честный конечный результат, НЕ «подключено».
     * @param quarantine true, если ничего не перебиралось, т.к. все кандидаты в quarantine. */
    data class NoWorkingRoute(val reasons: List<String>, val quarantine: Boolean = false) : ConnectionResult

    /** Отключено пользователем — автоматического подключения больше нет до нового действия. */
    data object UserDisconnected : ConnectionResult
}

/** Классификация причин отказа (для журнала и UI). */
enum class FailureKind {
    HANDSHAKE,
    DNS,
    HTTPS,
    ENDPOINT_UNREACHABLE,
    UNKNOWN,
}

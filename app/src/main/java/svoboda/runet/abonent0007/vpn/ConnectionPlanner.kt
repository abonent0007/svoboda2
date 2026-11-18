package svoboda.runet.abonent0007.vpn

/**
 * Чистая (тестируемая) логика цикла перебора кандидатов и вердикта data-plane.
 * Никакого Android / сети / SharedPreferences — только детерминированные правила.
 */

enum class Verdict {
    /** Обе проверки прошли. */
    Healthy,
    /** Одна из двух прошла — принимаем как рабочее подключение. */
    HealthyPartial,
    /** Обе провалились, но это первая попытка — нужен один контрольный цикл. */
    NeedsRetry,
    /** Обе провалились и на контрольном цикле — кандидат REJECT. */
    Degraded,
}

object DataPlaneVerdict {
    /** @param isRetry true, если это уже контрольный цикл после первого провала. */
    fun evaluate(cfPass: Boolean, gPass: Boolean, isRetry: Boolean): Verdict {
        if (cfPass && gPass) return Verdict.Healthy
        if (cfPass || gPass) return Verdict.HealthyPartial
        return if (isRetry) Verdict.Degraded else Verdict.NeedsRetry
    }
}

/**
 * Учёт кандидатов внутри одного цикла перебора и временный quarantine между циклами:
 * провалившийся кандидат не выбирается до конца цикла и не раньше QUARANTINE_MS.
 * Временный отказ ≠ постоянный бан.
 */
class CandidateCycle(
    private val quarantineMs: Long = QUARANTINE_MS,
) {
    companion object {
        const val QUARANTINE_MS = 10 * 60 * 1000L
    }

    private val failedInCycle = LinkedHashSet<String>()
    private val quarantinedUntil = HashMap<String, Long>()
    private var cycleId = 0

    /** Новый цикл: «внутрицикловые» отказы забываются, quarantine остаётся. */
    fun beginCycle(): Int {
        cycleId += 1
        failedInCycle.clear()
        return cycleId
    }

    /** true, если цикл что-то пытался сделать (иначе все пропуски — в quarantine). */
    fun attemptedSomething(): Boolean = failedInCycle.isNotEmpty()

    fun canTry(id: String, nowMs: Long): Boolean =
        id !in failedInCycle && nowMs >= (quarantinedUntil[id] ?: 0L)

    fun failures(): List<String> = failedInCycle.toList()

    /** Время ближайшего освобождения из quarantine; null если никто не quarantine. */
    fun nextEligibleAt(nowMs: Long): Long? =
        quarantinedUntil.values.filter { it > nowMs }.minOrNull()

    fun recordFailure(id: String, nowMs: Long, reason: String) {
        failedInCycle += id
        quarantinedUntil[id] = nowMs + quarantineMs
    }
}

/** Классификация причины: чисто по исключению/коду ответа (netprobe его передаёт). */
fun classifyFailure(kind: FailureKind?, httpCode: Int?, message: String): String {
    val base = when (kind) {
        FailureKind.HANDSHAKE -> "stage=handshake"
        FailureKind.DNS -> "stage=dns"
        FailureKind.HTTPS -> "stage=https"
        FailureKind.ENDPOINT_UNREACHABLE -> "stage=endpoint"
        FailureKind.UNKNOWN -> "stage=unknown"
        null -> "stage=unknown"
    }
    val code = httpCode?.let { ", http=$it" }.orEmpty()
    val msg = message.take(80)
    return if (msg.isBlank()) "$base$code" else "$base$code, $msg"
}

enum class Proceed { ProceedNow, Deferred, BypassHold }

/**
 * Разделение двух разных вопросов:
 *  - "подавляем ли переход между рабочими кандидатами" (anti-bounce);
 *  - "разрешаем ли переход с доказанно нерабочего маршрута" — всегда разрешён (bypassHold).
 */
object AntiBounceGate {
    /**
     * @param bypassHold true — обход антидребезга (ручное ПОДКЛЮЧИТЬ / failover с деградированного маршрута).
     * @param hasHealthySession была ли уже устойчивая сессия (если нет — попытка идёт всегда).
     */
    fun proceed(
        bypassHold: Boolean,
        hasHealthySession: Boolean,
        lastSwitchAt: Long,
        nowMs: Long,
        holdMinutes: Int,
    ): Proceed {
        if (bypassHold) return Proceed.BypassHold
        if (!hasHealthySession) return Proceed.ProceedNow
        if (holdMinutes <= 0) return Proceed.ProceedNow
        val holdMs = holdMinutes * 60_000L
        return if (nowMs - lastSwitchAt >= holdMs) Proceed.ProceedNow else Proceed.Deferred
    }
}

/** Результат одного probe-эндпоинта. */
data class EndpointProbe(
    val ok: Boolean,
    val httpCode: Int? = null,
    val detail: String = "",
) {
    companion object {
        fun pass(httpCode: Int) = EndpointProbe(ok = true, httpCode = httpCode)
        fun fail(kind: FailureKind, detail: String, httpCode: Int? = null) =
            EndpointProbe(ok = false, httpCode = httpCode, detail = detail)
    }
}

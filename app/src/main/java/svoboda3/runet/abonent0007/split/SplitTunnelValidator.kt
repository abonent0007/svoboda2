package svoboda3.runet.abonent0007.split

import android.content.Context

object SplitTunnelValidator {
    data class Result(val errors: List<String>, val warnings: List<String>) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    fun validate(context: Context, config: SplitTunnelConfig): Result {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val unique = config.packageNames.toSet()
        if (unique.size != config.packageNames.size) warnings += "Удалены повторяющиеся пакеты"

        val pm = context.packageManager
        val missing = unique.filter { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isFailure
        }
        if (missing.isNotEmpty()) warnings += "Не установлены: ${missing.take(5).joinToString()}${if (missing.size > 5) "…" else ""}"

        if (config.mode == SplitTunnelMode.ONLY_SELECTED && unique.isEmpty()) {
            warnings += "Список пуст: в режиме «только выбранные» весь трафик пойдёт напрямую"
        }
        if (config.mode == SplitTunnelMode.ALL_EXCEPT_SELECTED && unique.isEmpty()) {
            warnings += "Список пуст: весь трафик пойдёт через VPN"
        }

        return Result(errors, warnings)
    }
}

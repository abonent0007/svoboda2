package svoboda.runet.abonent0007.vpn

import kotlinx.coroutines.Job

/**
 * Единое хранилище health/reconnect-задач координатора.
 * Установка нового Job отменяет предыдущий — дублирующиеся циклы исключены.
 * Все операции синхронизированы. clear(expected) очищает только если сейчас
 * отслеживается именно этот Job — чужую/новую задачу не прервём.
 */
class SingleJobTracker {
    private var reconnectJob: Job? = null
    private var healthJob: Job? = null

    @Synchronized
    fun setReconnect(job: Job): Job {
        reconnectJob?.cancel()
        reconnectJob = job
        return job
    }

    /** Парная очистка reconnect-slot: убрать только tracked job (поздний/чужой no-op). */
    @Synchronized
    fun clearReconnect(expected: Job? = null): Boolean {
        if (expected == null) {
            reconnectJob?.cancel()
            reconnectJob = null
            return true
        }
        if (reconnectJob !== expected) return false
        reconnectJob?.cancel()
        reconnectJob = null
        return true
    }

    @Synchronized
    fun setHealth(job: Job): Job {
        healthJob?.cancel()
        healthJob = job
        return job
    }

    /** Парная очистка health-slot: убрать только tracked job (поздний/чужой no-op). */
    @Synchronized
    fun clearHealth(expected: Job? = null): Boolean {
        if (expected == null) {
            healthJob?.cancel()
            healthJob = null
            return true
        }
        if (healthJob !== expected) return false
        healthJob?.cancel()
        healthJob = null
        return true
    }

    @Synchronized
    fun snapshotHealth(): Job? = healthJob

    @Synchronized
    fun snapshotReconnect(): Job? = reconnectJob

    val activeReconnect: Boolean @Synchronized get() = reconnectJob?.isActive == true
    val activeHealth: Boolean @Synchronized get() = healthJob?.isActive == true
}

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

    /** Удалить reconnect-job ТОЛЬКО если прямо сейчас tracked — именно этот (иначе no-op). */
    @Synchronized
    fun clearReconnectIfCurrent(expected: Job): Boolean {
        if (reconnectJob !== expected) return false
        reconnectJob?.cancel()
        reconnectJob = null
        return true
    }

    /** Безусловная очистка reconnect-slot — пользовательский отключение/сброс. */
    @Synchronized
    fun clearAllReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    @Synchronized
    fun setHealth(job: Job): Job {
        healthJob?.cancel()
        healthJob = job
        return job
    }

    /** Удалить health-job ТОЛЬКО если прямо сейчас tracked — именно этот (иначе no-op). */
    @Synchronized
    fun clearHealthIfCurrent(expected: Job): Boolean {
        if (healthJob !== expected) return false
        healthJob?.cancel()
        healthJob = null
        return true
    }

    /** Безусловная очистка health-slot — пользовательский отключение/сброс. */
    @Synchronized
    fun clearAllHealth() {
        healthJob?.cancel()
        healthJob = null
    }

    @Synchronized
    fun snapshotHealth(): Job? = healthJob

    @Synchronized
    fun snapshotReconnect(): Job? = reconnectJob

    val activeReconnect: Boolean @Synchronized get() = reconnectJob?.isActive == true
    val activeHealth: Boolean @Synchronized get() = healthJob?.isActive == true
}

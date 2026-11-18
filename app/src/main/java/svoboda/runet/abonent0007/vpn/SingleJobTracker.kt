package svoboda.runet.abonent0007.vpn

import kotlinx.coroutines.Job

/**
 * Единое хранилище health/reconnect-задач координатора.
 * Установка нового Job отменяет предыдущий — дублирующиеся циклы исключены.
 * Все операции синхронизированы: tracked-job всегда единственно актуален.
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

    @Synchronized
    fun clearReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    @Synchronized
    fun setHealth(job: Job): Job {
        healthJob?.cancel()
        healthJob = job
        return job
    }

    @Synchronized
    fun clearHealth() {
        healthJob?.cancel()
        healthJob = null
    }

    val activeReconnect: Boolean @Synchronized get() = reconnectJob?.isActive == true
    val activeHealth: Boolean @Synchronized get() = healthJob?.isActive == true
}

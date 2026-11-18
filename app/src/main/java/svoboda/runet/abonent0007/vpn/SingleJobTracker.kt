package svoboda.runet.abonent0007.vpn

import kotlinx.coroutines.Job

/**
 * Единое хранилище health/reconnect-задач координатора.
 * Установка нового Job отменяет предыдущий — дублирующиеся циклы исключены.
 */
class SingleJobTracker {
    private var reconnectJob: Job? = null
    private var healthJob: Job? = null

    fun setReconnect(job: Job): Job {
        reconnectJob?.cancel()
        reconnectJob = job
        return job
    }

    fun clearReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    fun setHealth(job: Job): Job {
        healthJob?.cancel()
        healthJob = job
        return job
    }

    fun clearHealth() {
        healthJob?.cancel()
        healthJob = null
    }

    val activeReconnect: Boolean get() = reconnectJob?.isActive == true
    val activeHealth: Boolean get() = healthJob?.isActive == true
}

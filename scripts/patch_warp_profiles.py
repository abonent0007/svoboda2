# 1) GeneratedProfileStore: remove + больше профилей
p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\warp\GeneratedProfileStore.kt'
s = open(p, encoding='utf-8').read()
s = s.replace('private const val MAX_ENTRIES = 30', 'private const val MAX_ENTRIES = 60', 1)
old = """    fun clear() {
        runCatching { file.delete() }
    }"""
new = """    /** Удалить конкретный профиль (по времени создания и endpoint'у). */
    fun remove(profile: GeneratedProfile) {
        runCatching {
            val updated = list().filterNot { it.createdAt == profile.createdAt && it.host == profile.host && it.port == profile.port }
            saveEncrypted(updated)
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('store: remove() added')

# 2) ConnectionManager: подключение/удаление сгенерированного профиля
p = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()
old = "    fun resetLabel() {"
new = """    /** Подключиться к конкретному сгенерированному WARP-профилю. */
    suspend fun connectGeneratedProfile(profile: svoboda.runet.abonent0007.warp.GeneratedProfile): Boolean {
        controller.disconnect()
        controller.currentProfileId = "warp:${profile.host}:${profile.port}"
        _profileLabel.value = "WARP · ${profile.host}:${profile.port}"
        val ok = controller.connect(profile.config)
        if (ok) rememberGood(profile.config)
        return ok
    }

    /** Удалить сгенерированный профиль из хранилища. */
    fun removeGeneratedProfile(profile: svoboda.runet.abonent0007.warp.GeneratedProfile) {
        store.remove(profile)
    }

    fun resetLabel() {"""
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ConnectionManager: generated profile connect/remove added')

import re

root = r'C:\Users\Abonen0007\Documents\svoboda2\app\src\main\java\svoboda\runet\abonent0007'

# 1) Prefs: выбранные сервисы «напрямую»
p = root + r'\data\Prefs.kt'
s = open(p, encoding='utf-8').read()
anchor = """    var dpiBenchmarkDomains: Set<String>"""
addition = """    /** Сервисы, которые направляются мимо туннеля (раздельное туннелирование, WARP). */
    var serviceDirectSelection: Set<String>
        get() = prefs.getStringSet("service_direct_selection", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("service_direct_selection", value).apply()

""" + anchor
assert s.count(anchor) == 1
s = s.replace(anchor, addition, 1)
open(p, 'w', encoding='utf-8').write(s)
print('Prefs: serviceDirectSelection added')

# 2) ConnectionManager: применять split «сервисы напрямую» к WARP-профилям
p = root + r'\vpn\ConnectionManager.kt'
s = open(p, encoding='utf-8').read()
old = """    /** Подключиться к конкретному сгенерированному WARP-профилю. */"""
new = """    /**
     * Раздельное туннелирование: выбранные сервисы (YouTube/Telegram/...) идут мимо туннеля.
     * Применяется к конкретному профилю (у каждого WARP-профиля свой набор).
     */
    private fun applyServiceDirect(profileId: String) {
        val ids = prefs.serviceDirectSelection
        if (ids.isEmpty()) return
        val packages = ServiceCatalog.packagesFor(AppGraph.appContext, ids) + ServiceCatalog.fallbackPackages(ids)
        if (packages.isEmpty()) return
        AppGraph.splitManager.update(
            profileId,
            SplitTunnelConfig(
                profileId = profileId,
                mode = SplitTunnelMode.ALL_EXCEPT_SELECTED,
                packageNames = packages,
                presetId = "services_direct",
            ),
        )
    }

    /** Подключиться к конкретному сгенерированному WARP-профилю. */"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

old = """        controller.currentProfileId = "warp:${profile.host}:${profile.port}"
        _profileLabel.value = "WARP · ${profile.host}:${profile.port}\""""
new = """        controller.currentProfileId = "warp:${profile.host}:${profile.port}"
        applyServiceDirect(controller.currentProfileId)
        _profileLabel.value = "WARP · ${profile.host}:${profile.port}\""""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# в connectWarpWithGeneration: профиль "warp"
old = """        controller.currentProfileId = "warp"
        // Jc/Jmin/Jmax"""
new = """        controller.currentProfileId = "warp"
        applyServiceDirect("warp")
        // Jc/Jmin/Jmax"""
assert s.count(old) == 1
s = s.replace(old, new, 1)

# imports
old = "import svoboda.runet.abonent0007.warp.SeedCatalog\n"
new = old + "import svoboda.runet.abonent0007.split.ServiceCatalog\nimport svoboda.runet.abonent0007.split.SplitTunnelConfig\nimport svoboda.runet.abonent0007.split.SplitTunnelMode\n"
assert s.count(old) == 1
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ConnectionManager: service-direct split applied')

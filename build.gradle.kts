// Свобода 2.0 — корневой build-файл.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Модуль :tunnel (amneziawg-android) рассчитан на compileSdk 36;
// приводим к нашему SDK 35 (platform 36 не установлен).
subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            compileSdk = 35
            buildToolsVersion = "35.0.0"
            defaultConfig {
                minSdk = 26
            }
            ndkVersion = "27.2.12479018"
        }
    }
}

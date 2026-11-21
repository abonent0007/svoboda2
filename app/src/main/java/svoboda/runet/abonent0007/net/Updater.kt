package svoboda.runet.abonent0007.net

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import svoboda.runet.abonent0007.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Проверка и установка обновлений с GitHub Releases. */
object Updater {

    data class Release(
        val versionName: String,
        val apkUrl: String,
        val apkName: String,
        val publishedAt: String,
    )

    private const val REPO = "abonent0007/svoboda2"

    /** Основной источник: GitHub API. Может отдавать 403 (rate limit) при выходе через WARP. */
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /** Fallback без API: редирект страницы latest на тег + ассет с фиксированным именем. */
    private const val LATEST_PAGE = "https://github.com/$REPO/releases/latest"
    private const val LATEST_APK =
        "https://github.com/$REPO/releases/latest/download/app-universal-release.apk"
    private const val UA = "Svoboda2-Updater"

    /** Числовое сравнение версий "2.0.10" > "2.0.9". Нечисловые суффиксы игнорируются. */
    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val c = current.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        if (r.isEmpty()) return false
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    /** Возвращает новый релиз, только если он новее установленного. */
    suspend fun checkLatest(): Release? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        apiLatest() ?: redirectLatest()
    }

    /**
     * Fallback без GitHub API: `/releases/latest` отвечает 302 на `/releases/tag/<tag>`.
     * Нужен, когда API отдаёт 403 из-за rate limit (общий IP Cloudflare WARP).
     */
    private fun redirectLatest(): Release? = runCatching {
        val conn = (URL(LATEST_PAGE).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", UA)
            connectTimeout = 20_000
            readTimeout = 20_000
        }
        val code = conn.responseCode
        val location = conn.getHeaderField("Location").orEmpty()
        if (code !in 300..399) return@runCatching null
        val tag = Regex("/tag/v?([0-9][^/?#]*)").find(location)?.groupValues?.get(1).orEmpty()
        if (tag.isBlank() || !isNewer(tag, BuildConfig.VERSION_NAME)) return@runCatching null
        Release(tag, LATEST_APK, "svoboda2-latest-universal.apk", "")
    }.getOrNull()

    private fun apiLatest(): Release? = runCatching {
            val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", UA)
                connectTimeout = 20_000
                readTimeout = 20_000
            }
            if (conn.responseCode != 200) return@runCatching null
            val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name").trim().removePrefix("v")
            // Сравниваем версии численно: v2.0.10 новее v2.0.9; откат не предлагаем.
            if (tag.isBlank() || !isNewer(tag, BuildConfig.VERSION_NAME)) return@runCatching null
            val assets = json.optJSONArray("assets") ?: return@runCatching null
            var apkUrl: String? = null
            var apkName: String? = null
            // Сначала universal (подходит любой ABI), иначе — первый .apk.
            for (pass in 0 until 2) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val name = a.optString("name")
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    val universal = name.contains("universal", ignoreCase = true)
                    if (pass == 0 && !universal) continue
                    apkUrl = a.optString("browser_download_url")
                    apkName = name
                    break
                }
                if (apkUrl != null) break
            }
            if (apkUrl.isNullOrBlank()) return@runCatching null
            Release(tag, apkUrl, apkName!!, json.optString("published_at"))
    }.getOrNull()

    /** Загружает APK во внешний каталог приложения; возвращает файл. */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit = {},
    ): File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val file = File(dir, "svoboda2-update.apk")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", UA)
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 60_000
            }
            val total = conn.contentLength
            conn.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buf = ByteArray(8192)
                    var read: Int
                    var done = 0
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) onProgress((done * 100) / total)
                    }
                }
            }
            file
        }.getOrNull()
    }

    /** Intent на установку APK через FileProvider. */
    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

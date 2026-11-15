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

    private const val LATEST_URL =
        "https://api.github.com/repos/abonent0007/svoboda2/releases/latest"

    /** Возвращает новый релиз, если его версия отличается от текущей. */
    suspend fun checkLatest(): Release? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 20_000
                readTimeout = 20_000
            }
            if (conn.responseCode != 200) return@runCatching null
            val json = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.optString("tag_name").trim().removePrefix("v")
            if (tag.isBlank() || tag == BuildConfig.VERSION_NAME) return@runCatching null
            val assets = json.optJSONArray("assets") ?: return@runCatching null
            var apkUrl: String? = null
            var apkName: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url")
                    apkName = name
                    break
                }
            }
            if (apkUrl.isNullOrBlank()) return@runCatching null
            Release(tag, apkUrl, apkName!!, json.optString("published_at"))
        }.getOrNull()
    }

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

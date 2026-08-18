package com.wemade.smartnoti

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 업데이트 확인·설치가 어디까지 갔는지 */
sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data object Failed : UpdateState

    /** 새 버전이 있다 */
    data class Available(val version: String, val apkUrl: String?) : UpdateState

    /** 내려받는 중 */
    data class Downloading(val version: String, val percent: Int) : UpdateState

    /** 설치를 시스템에 넘겼다. 확인 화면이 뜰 수도, 조용히 끝날 수도 있다 */
    data class Installing(val version: String) : UpdateState
}

/**
 * GitHub 릴리스에서 새 버전을 가져와 스스로 갈아끼운다.
 *
 * 안드로이드는 앱이 저 혼자 설치되는 것을 막아 둔다. 다만 "이미 이 앱이 설치한 앱"을
 * 같은 서명으로 다시 설치하는 경우는 확인 화면을 건너뛸 수 있다(안드로이드 12+).
 * 그래서 처음 한 번만 확인 화면이 뜨고, 그 뒤로는 조용히 끝난다.
 */
object Updater {

    private const val OWNER_REPO = "moveju112/smart_android"
    private const val RELEASE_API = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    const val RELEASE_PAGE = "https://github.com/$OWNER_REPO/releases/latest"

    private const val PREFS = "updater"
    private const val KEY_AUTO = "auto"
    private const val KEY_LAST_CHECK = "lastCheck"
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** null이면 아직 확인해 본 적이 없다는 뜻 */
    val state = MutableStateFlow<UpdateState?>(null)

    fun isAutoEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, true)

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO, enabled).apply()
    }

    /** 하루에 한 번만 스스로 확인한다. 앱이 켜질 때마다 GitHub를 두드릴 이유는 없다 */
    suspend fun checkAutomatically(context: Context) {
        if (!isAutoEnabled(context)) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

        check(BuildConfig.VERSION_NAME)
        val found = state.value
        if (found is UpdateState.Available && found.apkUrl != null) {
            downloadAndInstall(context, found.version, found.apkUrl)
        }
    }

    /** 최신 릴리스를 조회해 지금 버전과 견준다 */
    suspend fun check(currentVersion: String) {
        state.value = UpdateState.Checking
        state.value = withContext(Dispatchers.IO) {
            runCatching {
                // 응답이 안 오면 "확인 중"에 영원히 매달린다 — 연결·읽기 5초씩에 끊는다
                val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = Json.parseToJsonElement(body) as JsonObject

                val latest = (json["tag_name"] as? JsonPrimitive)?.content.orEmpty().removePrefix("v")
                val apkUrl = (json["assets"] as? JsonArray)?.firstNotNullOfOrNull { asset ->
                    ((asset as JsonObject)["browser_download_url"] as? JsonPrimitive)
                        ?.content?.takeIf { it.endsWith(".apk") }
                }

                // "다르면 새 버전"이 아니라 실제로 높은지 본다.
                // 릴리스보다 앞선 로컬 빌드에서 옛 APK를 새 버전이라고 안내하는 사고를 막는다
                if (isNewer(latest, currentVersion)) UpdateState.Available(latest, apkUrl)
                else UpdateState.UpToDate
            }.getOrElse { UpdateState.Failed }
        }
    }

    /** APK를 내려받아 설치까지 맡긴다 */
    suspend fun downloadAndInstall(context: Context, version: String, apkUrl: String) {
        val apk = withContext(Dispatchers.IO) {
            runCatching {
                state.value = UpdateState.Downloading(version, 0)
                val connection = URL(apkUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                val total = connection.contentLength.toLong()

                // 받다 만 파일이 남아 있으면 그걸 설치하려 들 수 있으니 매번 새로 쓴다
                val file = File(context.cacheDir, "update.apk")
                var read = 0L
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            read += n
                            if (total > 0) {
                                state.value = UpdateState.Downloading(version, (read * 100 / total).toInt())
                            }
                        }
                    }
                }
                file
            }.getOrNull()
        }

        if (apk == null || apk.length() == 0L) {
            state.value = UpdateState.Failed
            return
        }

        state.value = UpdateState.Installing(version)
        val handed = withContext(Dispatchers.IO) { runCatching { install(context, apk) }.isSuccess }
        if (!handed) state.value = UpdateState.Failed
    }

    /** 시스템 설치기에 APK를 넘긴다 */
    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        // 이 앱이 이 앱을 갈아끼우는 것이므로, 조건이 맞으면 확인 화면 없이 끝난다
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("update", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pending = android.app.PendingIntent.getBroadcast(
                context, sessionId, intent,
                android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            session.commit(pending.intentSender)
        }
    }

    /** 점으로 나뉜 숫자 버전 비교. "-beta" 같은 꼬리표는 무시하고 자리별 숫자로 본다 */
    fun isNewer(latest: String, current: String): Boolean {
        if (latest.isBlank()) return false
        val a = latest.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        repeat(maxOf(a.size, b.size)) { i ->
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

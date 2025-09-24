package com.cloudphone.tool

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    private lateinit var listView: RecyclerView
    private lateinit var adapter: ApkAdapter
    private val items = mutableListOf<ApkItem>()
    private val preloadedIds = mutableSetOf<String>()
    private lateinit var tabLayout: TabLayout
    private lateinit var logContainer: View
    private lateinit var logView: TextView
    private val loadingIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.recycler)
        tabLayout = findViewById(R.id.tab_layout)
        logContainer = findViewById(R.id.log_container)
        logView = findViewById(R.id.log_view)

        adapter = ApkAdapter(items, preloadedIds,
            isLoading = { id -> loadingIds.contains(id) },
            onInstall = { item -> onInstallClicked(item) },
            onDelete = { item ->
                items.removeAll { it.id == item.id }
                saveItems()
                adapter.notifyDataSetChanged()
                log("Đã xóa mục: ${item.name}")
            }
        )
        listView.layoutManager = LinearLayoutManager(this)
        listView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        listView.adapter = adapter

        setupTabs()

        loadItems()
        mergePreloadedApps()
        updateCachedVersions()
        adapter.notifyDataSetChanged()
        log("Khởi động xong. Tổng mục: ${items.size}")
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Ứng dụng"))
        tabLayout.addTab(tabLayout.newTab().setText("Log"))
        showAppsTab()
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (tab.position == 0) showAppsTab() else showLogTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                if (tab.position == 1) scrollLogToBottom()
            }
        })
    }

    private fun showAppsTab() {
        listView.visibility = View.VISIBLE
        logContainer.visibility = View.GONE
    }

    private fun showLogTab() {
        listView.visibility = View.GONE
        logContainer.visibility = View.VISIBLE
        scrollLogToBottom()
    }

    private fun log(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logView.append("[$ts] $msg\n")
        scrollLogToBottom()
    }

    private fun logBg(msg: String) = runOnUiThread { log(msg) }

    private fun scrollLogToBottom() {
        val parent = logView.parent
        if (parent is ScrollView) {
            parent.post { parent.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun mergePreloadedApps() {
        try {
            val preloaded = loadPreloadedApps()
            preloadedIds.clear()
            for (p in preloaded) {
                val id = stableIdFromUrl(p.url)
                preloadedIds.add(id)
                val idx = items.indexOfFirst { it.id == id }
                val normalized = normalizeUrl(p.url)
                if (idx >= 0) {
                    // Cập nhật tên/url nếu khác
                    val exist = items[idx]
                    items[idx] = exist.copy(
                        name = p.name,
                        sourceType = SourceType.URL,
                        url = normalized,
                        uri = null,
                        versionName = exist.versionName ?: p.versionName,
                        versionCode = exist.versionCode ?: p.versionCode
                    )
                } else {
                    items.add(
                        ApkItem(
                            id = id,
                            name = p.name,
                            sourceType = SourceType.URL,
                            url = normalized,
                            uri = null,
                            versionName = p.versionName,
                            versionCode = p.versionCode
                        )
                    )
                }
                log("Preload: ${p.name} -> ${normalized}")
            }
        } catch (_: Exception) {
            // bỏ qua nếu không có raw hoặc lỗi parse
            log("Không thể nạp danh sách preload từ raw/preload_apps.json")
        }
    }

    @Suppress("DEPRECATION")
    private fun extractApkVersion(file: File): Pair<String?, Long?> {
        return try {
            val pm = packageManager
            val pi = pm.getPackageArchiveInfo(file.absolutePath, 0)
            if (pi != null) {
                val name = pi.versionName
                val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
                log("Đọc phiên bản từ APK: v=$name (code $code)")
                name to code
            } else null to null
        } catch (_: Exception) {
            log("Không đọc được phiên bản từ APK")
            null to null
        }
    }

    private fun loadPreloadedApps(): List<PreloadApp> {
        val isr = InputStreamReader(resources.openRawResource(R.raw.preload_apps))
        isr.use {
            val type = object : TypeToken<List<PreloadApp>>() {}.type
            return Gson().fromJson(it, type)
        }
    }

    private fun stableIdFromUrl(url: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val bytes = md.digest(url.toByteArray())
            bytes.joinToString("") { b -> "%02x".format(b) }
        } catch (_: Exception) {
            url.hashCode().toString()
        }
    }

    private fun onInstallClicked(item: ApkItem) {
        lifecycleScope.launch {
            val idxLoading = items.indexOfFirst { it.id == item.id }
            if (idxLoading >= 0) {
                loadingIds.add(item.id)
                adapter.notifyItemChanged(idxLoading)
            }
            try {
                val apkFile = when (item.sourceType) {
                    SourceType.LOCAL -> copyFromUriIfNeeded(Uri.parse(item.uri!!))
                    SourceType.URL -> downloadApk(item)
                }
                if (apkFile == null) {
                    toast("Không thể chuẩn bị tệp APK")
                    log("Lỗi: Không thể chuẩn bị tệp APK cho ${item.name}")
                    return@launch
                }

                if (!isLikelyApk(apkFile)) {
                    log("Tệp tải về không phải APK (size=${apkFile.length()}B). Có thể là trang HTML xác nhận của Google Drive.")
                    toast("Tệp tải về không phải APK. Kiểm tra lại link tải.")
                    return@launch
                }

                // Cập nhật phiên bản hiển thị trước khi cài đặt
                val (vName, vCode) = extractApkVersion(apkFile)
                val idx = items.indexOfFirst { it.id == item.id }
                if (idx >= 0) {
                    val cur = items[idx]
                    items[idx] = cur.copy(versionName = vName, versionCode = vCode)
                    saveItems()
                    adapter.notifyItemChanged(idx)
                    log("Cập nhật phiên bản hiển thị cho ${cur.name}: v=${vName ?: "?"} code=${vCode ?: -1}")
                }

                val rooted = RootInstaller.isDeviceRooted()
                log("Thiết bị root: $rooted. Bắt đầu cài đặt ${item.name}")
                if (rooted) {
                    val (ok, msg) = withContext(Dispatchers.IO) { RootInstaller.installApkWithRoot(apkFile) }
                    if (ok) {
                        toast("Cài đặt (root) thành công")
                        log("Cài đặt (root) thành công. pm output: $msg")
                    } else {
                        toast("Cài đặt (root) thất bại: $msg. Thử cách thường…")
                        log("Cài đặt (root) thất bại: $msg. Thử cách thường…")
                        installNormally(apkFile)
                    }
                } else {
                    log("Mở trình cài đặt thường (FileProvider)")
                    installNormally(apkFile)
                }
            } catch (e: Exception) {
                toast("Lỗi: ${e.message}")
                log("Lỗi: ${e.message}")
            } finally {
                if (idxLoading >= 0) {
                    loadingIds.remove(item.id)
                    adapter.notifyItemChanged(idxLoading)
                }
            }
        }
    }

    private fun installNormally(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                // Yêu cầu người dùng cho phép cài đặt từ nguồn không xác định
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(intent)
                    toast("Hãy cấp quyền, sau đó bấm lại để cài đặt")
                } catch (e: ActivityNotFoundException) {
                    // ignore
                }
            }
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("Không mở được installer: ${e.message}")
        }
    }

    private suspend fun downloadApk(item: ApkItem): File? = withContext(Dispatchers.IO) {
        val url = item.url ?: return@withContext null
        val normalized = url
        logBg("Bắt đầu tải: ${item.name} từ $normalized")
        val outFile = cacheFileFor(item)
        if (isGoogleDriveUrl(normalized)) {
            val ok = downloadFromGoogleDrive(normalized, outFile)
            if (ok) {
                logBg("Đã tải (Drive): ${outFile.absolutePath} (${outFile.length()} B)")
                return@withContext outFile
            } else return@withContext null
        }
        val req = Request.Builder()
            .url(normalized)
            .header("User-Agent", "Mozilla/5.0 (Android) CloudPhoneTool/1.0")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                logBg("Tải thất bại: HTTP ${'$'}{resp.code}")
                throw IllegalStateException("HTTP ${'$'}{resp.code}")
            }
            resp.body?.byteStream()?.use { input ->
                FileOutputStream(outFile).use { out -> copyStreamWithProgress(input, out) }
            }
            logBg("Đã tải xong: ${outFile.absolutePath} (${outFile.length()} B), content-type=${resp.header("Content-Type")}")
            outFile
        }
    }

    private fun isGoogleDriveUrl(url: String): Boolean {
        return try {
            val u = Uri.parse(url)
            (u.host ?: "").contains("drive.google.com")
        } catch (_: Exception) { false }
    }

    private fun extractDriveId(url: String): String? {
        val g1 = Regex("https?://drive\\.google\\.com/file/d/([a-zA-Z0-9_-]+)/view.*")
        val g2 = Regex("https?://drive\\.google\\.com/open\\?id=([a-zA-Z0-9_-]+).*")
        val g3 = Regex("https?://drive\\.google\\.com/uc\\?export=download&id=([a-zA-Z0-9_-]+).*")
        return when {
            g1.matches(url) -> g1.find(url)!!.groupValues[1]
            g2.matches(url) -> g2.find(url)!!.groupValues[1]
            g3.matches(url) -> g3.find(url)!!.groupValues[1]
            else -> null
        }
    }

    private fun collectCookies(resp: okhttp3.Response): String {
        val set = resp.headers("Set-Cookie")
        val list = mutableListOf<String>()
        for (c in set) {
            val part = c.substringBefore(';').trim()
            if (part.isNotBlank()) list.add(part)
        }
        return list.joinToString("; ")
    }

    private fun downloadFromGoogleDrive(url: String, outFile: File): Boolean {
        val id = extractDriveId(url)
        val firstUrl = if (id != null) "https://drive.google.com/uc?export=download&id=$id" else url
        val firstReq = Request.Builder()
            .url(firstUrl)
            .header("User-Agent", "Mozilla/5.0 (Android) CloudPhoneTool/1.0")
            .build()
        client.newCall(firstReq).execute().use { resp1 ->
            if (!resp1.isSuccessful) return false
            val disp = resp1.header("Content-Disposition")
            val ctype = resp1.header("Content-Type") ?: ""
            if (disp?.contains("attachment") == true || !ctype.contains("text/html")) {
                resp1.body?.byteStream()?.use { input ->
                    FileOutputStream(outFile).use { out -> copyStreamWithProgress(input, out) }
                }
                return true
            }
            // HTML confirm page -> extract confirm token
            val html = resp1.body?.string() ?: return false
            val token = Regex("confirm=([0-9A-Za-z_\\-]+)").find(html)?.groupValues?.getOrNull(1)
            val realId = id ?: Regex("id=([0-9A-Za-z_\\-]+)").find(html)?.groupValues?.getOrNull(1)
            if (token == null || realId == null) return false
            val cookies = collectCookies(resp1)
            val confirmUrl = "https://drive.google.com/uc?export=download&confirm=$token&id=$realId"
            val req2 = Request.Builder()
                .url(confirmUrl)
                .header("User-Agent", "Mozilla/5.0 (Android) CloudPhoneTool/1.0")
                .header("Cookie", cookies)
                .build()
            client.newCall(req2).execute().use { resp2 ->
                if (!resp2.isSuccessful) return false
                resp2.body?.byteStream()?.use { input ->
                    FileOutputStream(outFile).use { out -> copyStreamWithProgress(input, out) }
                }
                return true
            }
        }
    }

    private fun ensureApkExtension(name: String): String {
        return if (name.lowercase(Locale.ROOT).endsWith(".apk")) name else "$name.apk"
    }

    private fun copyStreamWithProgress(input: InputStream, out: FileOutputStream) {
        val buf = ByteArray(8 * 1024)
        while (true) {
            val r = input.read(buf)
            if (r == -1) break
            out.write(buf, 0, r)
        }
        out.flush()
    }

    private fun cacheFileFor(item: ApkItem): File {
        val dir = File(cacheDir, "apks").apply { mkdirs() }
        return File(dir, "${item.id}.apk")
    }

    // Kiểm tra nhanh file có phải APK (ZIP) bằng signature 'PK'
    private fun isLikelyApk(file: File): Boolean {
        return try {
            if (!file.exists() || file.length() < 4) return false
            file.inputStream().use { ins ->
                val sig = ByteArray(2)
                val r = ins.read(sig)
                r == 2 && sig[0] == 0x50.toByte() && sig[1] == 0x4B.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun copyFromUriIfNeeded(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val name = queryDisplayName(uri) ?: "picked.apk"
            val dir = File(cacheDir, "apks").apply { mkdirs() }
            val outFile = File(dir, ensureApkExtension(name))
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { out ->
                    copyStreamWithProgress(input, out)
                }
            }
            outFile
        } catch (e: Exception) {
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
        } catch (_: Exception) {
            null
        }
    }

    // Chuyển đổi một số link Drive sang link tải trực tiếp
    private fun normalizeUrl(raw: String): String {
        var url = raw
        val g1 = Regex("https?://drive\\.google\\.com/file/d/([a-zA-Z0-9_-]+)/view.*")
        val g2 = Regex("https?://drive\\.google\\.com/open\\?id=([a-zA-Z0-9_-]+).*")
        val g3 = Regex("https?://drive\\.google\\.com/uc\\?export=download&id=([a-zA-Z0-9_-]+).*")
        when {
            g1.matches(url) -> {
                val id = g1.find(url)!!.groupValues[1]
                url = "https://drive.google.com/uc?export=download&id=$id"
            }
            g2.matches(url) -> {
                val id = g2.find(url)!!.groupValues[1]
                url = "https://drive.google.com/uc?export=download&id=$id"
            }
            g3.matches(url) -> return url
        }
        return url
    }

    private fun guessNameFromUrl(url: String): String {
        return try {
            val path = Uri.parse(url).lastPathSegment ?: "download.apk"
            if (path.contains('.')) path else "$path.apk"
        } catch (_: Exception) {
            "download.apk"
        }
    }

    private fun guessNameFromHeaders(disposition: String?): String? {
        if (disposition == null) return null
        val regex = Regex("filename=\\\"?(.*?)\\\"?(;|$)")
        val m = regex.find(disposition)
        return m?.groupValues?.getOrNull(1)
    }

    private fun saveItems() {
        val prefs = getSharedPreferences("apk_items", Context.MODE_PRIVATE)
        val json = ApkItem.toJsonList(items)
        prefs.edit().putString("list", json).apply()
    }

    private fun loadItems() {
        val prefs = getSharedPreferences("apk_items", Context.MODE_PRIVATE)
        val json = prefs.getString("list", null)
        val loaded = ApkItem.fromJsonList(json)
        items.clear()
        items.addAll(loaded)
    }

    private fun updateCachedVersions() {
        var changed = false
        for ((index, it) in items.withIndex()) {
            if (it.sourceType == SourceType.URL) {
                val f = cacheFileFor(it)
                if (f.exists() && (it.versionName == null || it.versionCode == null)) {
                    val (vn, vc) = extractApkVersion(f)
                    if (vn != null || vc != null) {
                        items[index] = it.copy(versionName = vn, versionCode = vc)
                        changed = true
                    }
                }
            }
        }
        if (changed) saveItems()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

// Data & Adapter

data class ApkItem(
    val id: String,
    val name: String,
    val sourceType: SourceType,
    val url: String?,
    val uri: String?,
    val versionName: String? = null,
    val versionCode: Long? = null
) {
    companion object {
        fun toJsonList(list: List<ApkItem>): String {
            // Simple manual JSON to avoid adding extra dependency
            val sb = StringBuilder()
            sb.append('[')
            list.forEachIndexed { i, it ->
                if (i > 0) sb.append(',')
                sb.append('{')
                sb.append("\"id\":\"${it.id}\",")
                sb.append("\"name\":\"${escape(it.name)}\",")
                sb.append("\"sourceType\":\"${it.sourceType}\",")
                sb.append("\"url\":${if (it.url != null) "\"${escape(it.url)}\"" else "null"},")
                sb.append("\"uri\":${if (it.uri != null) "\"${escape(it.uri)}\"" else "null"},")
                sb.append("\"versionName\":${if (it.versionName != null) "\"${escape(it.versionName)}\"" else "null"},")
                sb.append("\"versionCode\":${it.versionCode?.toString() ?: "null"}")
                sb.append('}')
            }
            sb.append(']')
            return sb.toString()
        }

        fun fromJsonList(json: String?): List<ApkItem> {
            if (json.isNullOrBlank()) return emptyList()
            // Very small and naive JSON parser for our fixed schema
            val list = mutableListOf<ApkItem>()
            val items = json.trim().removePrefix("[").removeSuffix("]")
            if (items.isBlank()) return emptyList()
            val parts = splitTopLevel(items)
            for (p in parts) {
                val map = parseObject(p)
                val id = map["id"] ?: UUID.randomUUID().toString()
                val name = map["name"] ?: "APK"
                val sourceType = try { SourceType.valueOf(map["sourceType"] ?: "URL") } catch (_: Exception) { SourceType.URL }
                val url = map["url"]
                val uri = map["uri"]
                val versionName = map["versionName"]
                val versionCode = map["versionCode"]?.toLongOrNull()
                list.add(ApkItem(id, name, sourceType, url, uri, versionName, versionCode))
            }
            return list
        }

        private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

        private fun splitTopLevel(s: String): List<String> {
            val res = mutableListOf<String>()
            var level = 0
            var start = 0
            for (i in s.indices) {
                when (s[i]) {
                    '{' -> if (level++ == 0) start = i
                    '}' -> if (--level == 0) res.add(s.substring(start, i + 1))
                }
            }
            return res
        }

        private fun parseObject(s: String): Map<String, String?> {
            val map = mutableMapOf<String, String?>()
            // remove { }
            var body = s.trim().removePrefix("{").removeSuffix("}")
            // split by commas not inside quotes
            val parts = mutableListOf<String>()
            val sb = StringBuilder()
            var inStr = false
            for (ch in body) {
                if (ch == '"') inStr = !inStr
                if (ch == ',' && !inStr) { parts.add(sb.toString()); sb.clear() } else sb.append(ch)
            }
            if (sb.isNotEmpty()) parts.add(sb.toString())
            for (p in parts) {
                val idx = p.indexOf(":")
                if (idx > 0) {
                    val key = p.substring(0, idx).trim().removeSurrounding("\"", "\"")
                    var valueStr = p.substring(idx + 1).trim()
                    var value: String? = if (valueStr == "null") null else valueStr.removeSurrounding("\"", "\"")
                    if (value != null) value = value.replace("\\\"", "\"").replace("\\\\", "\\")
                    map[key] = value
                }
            }
            return map
        }
    }
}

enum class SourceType { URL, LOCAL }

data class PreloadApp(
    val name: String,
    val url: String,
    val versionName: String? = null,
    val versionCode: Long? = null
)

class ApkAdapter(
    private val data: List<ApkItem>,
    private val nonDeletableIds: Set<String>,
    private val isLoading: (String) -> Boolean,
    private val onInstall: (ApkItem) -> Unit,
    private val onDelete: (ApkItem) -> Unit
) : RecyclerView.Adapter<ApkAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.txt_name)
        val src: TextView = v.findViewById(R.id.txt_src)
        val version: TextView = v.findViewById(R.id.txt_version)
        val btnInstall: Button = v.findViewById(R.id.btn_install)
        val btnDelete: ImageButton = v.findViewById(R.id.btn_delete)
        val progress: ProgressBar = v.findViewById(R.id.progress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_apk, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = data[position]
        holder.name.text = it.name
        holder.src.text = when (it.sourceType) {
            SourceType.URL -> it.url ?: ""
            SourceType.LOCAL -> it.uri ?: ""
        }
        holder.version.text = "Phiên bản: ${it.versionName ?: "(chưa rõ)"}${it.versionCode?.let { c -> " (code $c)" } ?: ""}"
        val loadingNow = isLoading(it.id)
        holder.progress.visibility = if (loadingNow) View.VISIBLE else View.GONE
        holder.btnInstall.isEnabled = !loadingNow
        holder.btnInstall.setOnClickListener { _ -> if (!loadingNow) onInstall(it) }
        val deletable = !nonDeletableIds.contains(it.id)
        holder.btnDelete.visibility = if (deletable) View.VISIBLE else View.GONE
        holder.btnDelete.setOnClickListener { _ -> if (deletable) onDelete(it) }
    }

    override fun getItemCount(): Int = data.size
}

object RootInstaller {
    fun isDeviceRooted(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val exit = p.waitFor()
            exit == 0
        } catch (_: Exception) {
            false
        }
    }

    fun installApkWithRoot(file: File): Pair<Boolean, String> {
        return try {
            val size = file.length()
            // Streaming install to avoid SELinux/path access issues
            val p = ProcessBuilder("su", "-c", "pm install -r -S $size")
                .redirectErrorStream(true)
                .start()
            file.inputStream().use { input ->
                p.outputStream.use { out ->
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val r = input.read(buf)
                        if (r == -1) break
                        out.write(buf, 0, r)
                    }
                    out.flush()
                }
            }
            val exit = p.waitFor()
            val output = p.inputStream.bufferedReader().readText()
            if (exit == 0) true to output else false to output
        } catch (e: Exception) {
            false to (e.message ?: "unknown error")
        }
    }
}

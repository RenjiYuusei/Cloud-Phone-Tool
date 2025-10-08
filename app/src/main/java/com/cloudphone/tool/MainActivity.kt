package com.cloudphone.tool

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.android.material.tabs.TabLayout
import android.app.PendingIntent
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.core.content.pm.PackageInfoCompat
 
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import android.text.Editable
import android.text.TextWatcher
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }
    private val DEFAULT_SOURCE_URL = "https://raw.githubusercontent.com/RenjiYuusei/Cloud-Phone-Tool/main/source/apps.json"

    private lateinit var listView: RecyclerView
    private lateinit var installedListView: RecyclerView
    private lateinit var adapter: ApkAdapter
    private lateinit var installedAdapter: InstalledAppsAdapter
    private val items = mutableListOf<ApkItem>()            // nguồn dữ liệu đầy đủ
    private val filteredItems = mutableListOf<ApkItem>()     // danh sách sau khi lọc để hiển thị
    private val preloadedIds = mutableSetOf<String>()
    private lateinit var tabLayout: TabLayout
    private lateinit var sourceBar: View
    private lateinit var searchInput: EditText
    private lateinit var btnRefreshSource: Button
    private lateinit var logContainer: View
    private lateinit var logView: TextView
    private val loadingIds = mutableSetOf<String>()
    private val installedItems = mutableListOf<InstalledAppItem>()
    private val installedFilteredItems = mutableListOf<InstalledAppItem>()
    private var currentQuery: String = ""
    private var currentInstalledQuery: String = ""
    private var currentTab: Int = 0 // 0: Ứng dụng, 1: Đã cài đặt, 2: Nhật ký
    private var suppressSearchWatcher: Boolean = false
    private lateinit var globalProgress: LinearProgressIndicator
    private lateinit var statsBar: View
    private lateinit var txtStats: TextView
    private lateinit var btnClearCache: Button
    private lateinit var btnSort: Button
    private var sortMode: SortMode = SortMode.NAME_ASC
    
    enum class SortMode {
        NAME_ASC, NAME_DESC, SIZE_DESC, DATE_DESC
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        listView = findViewById(R.id.recycler)
        installedListView = findViewById(R.id.recycler_installed)
        tabLayout = findViewById(R.id.tab_layout)
        sourceBar = findViewById(R.id.source_bar)
        logContainer = findViewById(R.id.log_container)
        logView = findViewById(R.id.log_view)
        searchInput = findViewById(R.id.search_input)
        btnRefreshSource = findViewById(R.id.btn_refresh_source)
        globalProgress = findViewById(R.id.global_progress)
        statsBar = findViewById(R.id.stats_bar)
        txtStats = findViewById(R.id.txt_stats)
        btnClearCache = findViewById(R.id.btn_clear_cache)
        btnSort = findViewById(R.id.btn_sort)
        btnRefreshSource.setOnClickListener {
            lifecycleScope.launch {
                setBusy(true)
                refreshPreloadedApps()
                setBusy(false)
                toast("Đã làm mới nguồn")
            }
        }
        btnSort.setOnClickListener { showSortMenu() }
        btnClearCache.setOnClickListener { clearCache() }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (suppressSearchWatcher) return
                val q = s?.toString() ?: ""
                if (currentTab == 0) {
                    applyFilter(q)
                } else if (currentTab == 1) {
                    applyInstalledFilter(q)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        adapter = ApkAdapter(filteredItems, preloadedIds,
            isLoading = { id -> loadingIds.contains(id) },
            getCachedFile = { item -> cacheFileFor(item) },
            onInstall = { item -> onInstallClicked(item) },
            onDelete = { item ->
                items.removeAll { it.id == item.id }
                saveItems()
                applyFilter(currentQuery)
                updateStats()
                log("Đã xóa mục: ${item.name}")
            }
        )
        listView.layoutManager = LinearLayoutManager(this)
        listView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        listView.adapter = adapter

        installedAdapter = InstalledAppsAdapter(installedFilteredItems,
            onOpen = { app -> openInstalledApp(app) },
            onInfo = { app -> openInstalledAppInfo(app) },
            onUninstall = { app -> uninstallApp(app) }
        )
        installedListView.layoutManager = LinearLayoutManager(this)
        installedListView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        installedListView.adapter = installedAdapter

        setupTabs()
        
        // Khởi tạo log với thông báo chào mừng
        logView.text = "" // Clear placeholder text
        log("=== Cloud Phone Tool v1.1.0 ===")
        log("Chào mừng! Ứng dụng đã khởi động.")
        log("Đang tải danh sách ứng dụng...")

        loadItems()
        // Nạp preload từ nguồn mặc định (cố định)
        lifecycleScope.launch {
            refreshPreloadedApps(initial = true)
            applyFilter("")
            updateStats()
            log("Đã tải xong danh sách ứng dụng từ nguồn.")
        }
        updateCachedVersions()
        applyFilter(currentQuery)
        applyInstalledFilter(currentInstalledQuery)
        
        // Log thông tin môi trường
        logEnvForDebug("STARTUP")
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Ứng dụng").setIcon(R.drawable.ic_apps))
        tabLayout.addTab(tabLayout.newTab().setText("Đã cài đặt").setIcon(R.drawable.ic_installed))
        tabLayout.addTab(tabLayout.newTab().setText("Nhật ký").setIcon(R.drawable.ic_log))
        showAppsTab()
        syncSearchInputWithTab()
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> { currentTab = 0; showAppsTab(); syncSearchInputWithTab() }
                    1 -> { currentTab = 1; showInstalledTab(); syncSearchInputWithTab() }
                    else -> { currentTab = 2; showLogTab(); syncSearchInputWithTab() }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                if (tab.position == 2) scrollLogToBottom()
            }
        })
    }

    private fun syncSearchInputWithTab() {
        suppressSearchWatcher = true
        try {
            when (currentTab) {
                0 -> { // Apps tab
                    val want = currentQuery
                    if ((searchInput.text?.toString() ?: "") != want) searchInput.setText(want)
                }
                1 -> { // Installed tab
                    val want = currentInstalledQuery
                    if ((searchInput.text?.toString() ?: "") != want) searchInput.setText(want)
                }
                else -> {
                    if (!searchInput.text.isNullOrEmpty()) searchInput.setText("")
                }
            }
        } finally {
            suppressSearchWatcher = false
        }
    }

    private fun showAppsTab() {
        listView.visibility = View.VISIBLE
        installedListView.visibility = View.GONE
        logContainer.visibility = View.GONE
        sourceBar.visibility = View.VISIBLE
        btnRefreshSource.visibility = View.VISIBLE
        btnSort.visibility = View.VISIBLE
        updateStats()
    }

    private fun showLogTab() {
        listView.visibility = View.GONE
        installedListView.visibility = View.GONE
        logContainer.visibility = View.VISIBLE
        sourceBar.visibility = View.GONE
        statsBar.visibility = View.GONE
        // Ensure log is properly initialized and visible
        if (::logView.isInitialized) {
            scrollLogToBottom()
        }
    }

    private fun showInstalledTab() {
        listView.visibility = View.GONE
        logContainer.visibility = View.GONE
        installedListView.visibility = View.VISIBLE
        sourceBar.visibility = View.VISIBLE
        btnRefreshSource.visibility = View.GONE
        btnSort.visibility = View.GONE
        statsBar.visibility = View.GONE
        // nạp danh sách ứng dụng đã cài
        loadInstalledApps()
    }

    private fun log(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logView.append("[$ts] $msg\n")
        // Giữ log gọn: giới hạn ~4000 ký tự cuối
        val txt = logView.text?.toString() ?: ""
        if (txt.length > 6000) {
            logView.text = txt.takeLast(4000)
        }
        scrollLogToBottom()
    }

    private fun logBg(msg: String) = runOnUiThread { log(msg) }

    private fun scrollLogToBottom() {
        val parent = logView.parent
        if (parent is ScrollView) {
            parent.post { parent.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun setBusy(busy: Boolean) {
        globalProgress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    // Ghi log thông tin hệ thống và môi trường root để hỗ trợ chẩn đoán
    private fun logEnvForDebug(stage: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val env = RootInstaller.probeRootEnv()
            val androidRelease = try { Build.VERSION.RELEASE } catch (_: Exception) { "?" }
            val rom = try { Build.DISPLAY } catch (_: Exception) { Build.ID }
            val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val lines = listOf(
                "ENV/$stage: Android $androidRelease (SDK ${Build.VERSION.SDK_INT}), ROM=$rom, Device=$device",
                "ENV/$stage: Root provider=${env.provider}, suPath=${env.suPath ?: "-"}, suVer=${env.suVersion ?: "-"}, uid0=${env.uid0}",
                "ENV/$stage: Magisk=${env.magiskVersionName ?: "-"} (code=${env.magiskVersionCode ?: "-"}), KernelSU=${env.kernelSuVersion ?: "-"}"
            )
            withContext(Dispatchers.Main) { lines.forEach { log(it) } }
        }
    }

    private fun mergePreloaded(preloaded: List<PreloadApp>) {
        preloadedIds.clear()
        var updated = 0
        for (p in preloaded) {
            val id = stableIdFromUrl(p.url)
            preloadedIds.add(id)
            val idx = items.indexOfFirst { it.id == id }
            val normalized = normalizeUrl(p.url)
            if (idx >= 0) {
                val exist = items[idx]
                items[idx] = exist.copy(
                    name = p.name,
                    sourceType = SourceType.URL,
                    url = normalized,
                    uri = null,
                    versionName = exist.versionName ?: p.versionName,
                    versionCode = exist.versionCode ?: p.versionCode,
                    iconUrl = p.iconUrl
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
                        versionCode = p.versionCode,
                        iconUrl = p.iconUrl
                    )
                )
            }
            updated++
        }
        log("Đã nạp danh sách online: $updated mục")
    }

    private suspend fun fetchPreloadedAppsRemote(url: String): List<PreloadApp>? = withContext(Dispatchers.IO) {
        try {
            logBg("Tải danh sách ứng dụng online từ: $url")
            val req = Request.Builder().url(url).header("User-Agent", "CloudPhoneTool/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    logBg("Nguồn online thất bại: HTTP ${resp.code}")
                    return@withContext null
                }
                val body = resp.body?.string() ?: return@withContext null
                // Tránh dùng TypeToken để không phụ thuộc generic signature khi minify
                val arr: Array<PreloadApp> = Gson().fromJson(body, Array<PreloadApp>::class.java)
                arr.toList()
            }
        } catch (e: Exception) {
            logBg("Lỗi tải nguồn online: ${e.message}")
            null
        }
    }

    private suspend fun refreshPreloadedApps(initial: Boolean = false) {
        val preloaded: List<PreloadApp>? = fetchPreloadedAppsRemote(DEFAULT_SOURCE_URL)
        if (preloaded != null) {
            mergePreloaded(preloaded)
            withContext(Dispatchers.Main) { applyFilter(currentQuery) }
        } else {
            if (!initial) logBg("Không thể nạp danh sách preload từ nguồn mặc định")
        }
    }

    private fun applyFilter(q: String) {
        currentQuery = q
        val needle = q.trim().lowercase(Locale.getDefault())
        filteredItems.clear()
        val filtered = if (needle.isEmpty()) {
            items.toList()
        } else {
            items.filter {
                it.name.lowercase(Locale.getDefault()).contains(needle)
                        || (it.url?.lowercase(Locale.getDefault())?.contains(needle) == true)
                        || (it.uri?.lowercase(Locale.getDefault())?.contains(needle) == true)
            }
        }
        
        // Áp dụng sắp xếp
        val sorted = when (sortMode) {
            SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortMode.SIZE_DESC -> filtered.sortedByDescending { 
                val f = cacheFileFor(it)
                if (f.exists()) f.length() else 0L
            }
            SortMode.DATE_DESC -> filtered.sortedByDescending { 
                val f = cacheFileFor(it)
                if (f.exists()) f.lastModified() else 0L
            }
        }
        
        filteredItems.addAll(sorted)
        adapter.notifyDataSetChanged()
    }

    private fun applyInstalledFilter(q: String) {
        currentInstalledQuery = q
        val needle = q.trim().lowercase(Locale.getDefault())
        installedFilteredItems.clear()
        if (needle.isEmpty()) {
            installedFilteredItems.addAll(installedItems)
        } else {
            installedFilteredItems.addAll(
                installedItems.filter {
                    it.appName.lowercase(Locale.getDefault()).contains(needle)
                            || it.packageName.lowercase(Locale.getDefault()).contains(needle)
                }
            )
        }
        installedAdapter.notifyDataSetChanged()
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

    // Đã loại bỏ nạp từ raw/preload_apps.json để cố định nguồn online mặc định

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
                applyFilter(currentQuery)
                setBusy(true)
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

                val isApks = (item.url?.lowercase(Locale.ROOT)?.contains(".apks") == true)
                        || apkFile.name.lowercase(Locale.ROOT).endsWith(".apks")
                if (isApks) {
                    // Xử lý cài đặt gói chia nhỏ (.apks)
                    val splits = withContext(Dispatchers.IO) { extractSplitsFromApks(apkFile) }
                    if (splits.isEmpty()) {
                        toast("Không tìm thấy APK bên trong file .apks")
                        log(".apks không chứa APK hợp lệ: ${apkFile.absolutePath}")
                        return@launch
                    }
                    logEnvForDebug("install:start-split")
                    val rooted = RootInstaller.isDeviceRooted()
                    log("Thiết bị root: $rooted. Bắt đầu cài đặt (split) ${item.name}")
                    if (rooted) {
                        val resSplit: Pair<Boolean, String> = withContext(Dispatchers.IO) { RootInstaller.installApks(splits) }
                        val (ok, msg) = resSplit
                        if (ok) {
                            toast("Cài đặt (root) thành công")
                            log("Cài đặt (root) thành công (split). pm: $msg")
                            logEnvForDebug("install:root-success-split")
                        } else {
                            toast("Cài đặt (root) thất bại: $msg. Thử cách thường…")
                            log("Cài đặt (root) thất bại (split): $msg. Thử cách thường…")
                            logEnvForDebug("install:root-fail-split")
                            installSplitsNormally(splits)
                        }
                    } else {
                        logEnvForDebug("install:no-root-split")
                        installSplitsNormally(splits)
                    }
                    return@launch
                }

                if (!isLikelyApk(apkFile)) {
                    log("Tệp tải về không phải APK (size=${apkFile.length()}B). Có thể là trang HTML hoặc sai link.")
                    toast("Tệp tải về không phải APK. Kiểm tra lại link tải.")
                    return@launch
                }

                val rooted = RootInstaller.isDeviceRooted()
                logEnvForDebug("install:start-apk")
                log("Thiết bị root: $rooted. Bắt đầu cài đặt ${item.name}")
                if (rooted) {
                    val resApk: Pair<Boolean, String> = withContext(Dispatchers.IO) { RootInstaller.installApk(apkFile) }
                    val (ok, msg) = resApk
                    if (ok) {
                        toast("Cài đặt (root) thành công")
                        log("Cài đặt (root) thành công. pm output: $msg")
                        logEnvForDebug("install:root-success-apk")
                    } else {
                        toast("Cài đặt (root) thất bại: $msg. Thử cách thường…")
                        log("Cài đặt (root) thất bại: $msg. Thử cách thường…")
                        logEnvForDebug("install:root-fail-apk")
                        installNormally(apkFile)
                    }
                } else {
                    logEnvForDebug("install:no-root-apk")
                    log("Mở trình cài đặt thường (FileProvider)")
                    installNormally(apkFile)
                }
            } catch (e: Exception) {
                toast("Lỗi: ${e.message}")
                log("Lỗi: ${e.message}")
            } finally {
                if (idxLoading >= 0) {
                    loadingIds.remove(item.id)
                    applyFilter(currentQuery)
                    if (loadingIds.isEmpty()) setBusy(false)
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
        val req = Request.Builder()
            .url(normalized)
            .header("User-Agent", "Mozilla/5.0 (Android) CloudPhoneTool/1.0")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                logBg("Tải thất bại: HTTP ${resp.code}")
                throw IllegalStateException("HTTP ${resp.code}")
            }
            resp.body?.byteStream()?.use { input ->
                FileOutputStream(outFile).use { out ->
                    copyStreamWithProgress(input, out)
                }
            }
            logBg("Đã tải xong: ${outFile.absolutePath} (${outFile.length()} B), content-type=${resp.header("Content-Type")}")
            outFile
        }
    }

    // Đã loại bỏ toàn bộ xử lý Google Drive, chỉ sử dụng tải trực tiếp (ưu tiên Dropbox direct)

    private fun ensureApkExtension(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
        return if (lower.endsWith(".apk") || lower.endsWith(".apks")) name else "$name.apk"
    }

    // Giải nén file .apks để lấy danh sách các APK (base + split)
    private fun extractSplitsFromApks(apksFile: File): List<File> {
        val outDir = File(cacheDir, "splits/${apksFile.nameWithoutExtension}")
        if (outDir.exists()) outDir.deleteRecursively()
        outDir.mkdirs()
        val results = mutableListOf<File>()
        try {
            ZipInputStream(FileInputStream(apksFile)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.endsWith(".apk")) {
                        val outFile = File(outDir, entry.name.substringAfterLast('/'))
                        outFile.outputStream().use { out ->
                            val buf = ByteArray(8 * 1024)
                            while (true) {
                                val r = zis.read(buf)
                                if (r == -1) break
                                out.write(buf, 0, r)
                            }
                            out.flush()
                        }
                        results.add(outFile)
                    }
                    zis.closeEntry()
                }
            }
        } catch (e: Exception) {
            log("Lỗi giải nén .apks: ${e.message}")
        }
        // Đảm bảo base.apk (nếu có) đứng đầu danh sách
        return results.sortedWith(compareBy({ it.name != "base.apk" }, { it.name }))
    }

    // Cài đặt nhiều APK (split) theo cách thường bằng PackageInstaller
    private fun installSplitsNormally(files: List<File>) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    toast("Hãy cấp quyền cài đặt ứng dụng không xác định, sau đó thử lại")
                    return
                }
            }
            val installer = packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            try {
                for (f in files) {
                    FileInputStream(f).use { input ->
                        session.openWrite(f.name, 0, f.length()).use { out ->
                            val buf = ByteArray(8 * 1024)
                            while (true) {
                                val r = input.read(buf)
                                if (r == -1) break
                                out.write(buf, 0, r)
                            }
                            session.fsync(out)
                        }
                    }
                }
                val action = "${packageName}.INSTALL_COMMIT"
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        try { unregisterReceiver(this) } catch (_: Exception) {}
                        val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE) ?: PackageInstaller.STATUS_FAILURE
                        val msg = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                        if (status == PackageInstaller.STATUS_SUCCESS) {
                            toast("Cài đặt thành công")
                            log("Cài đặt splits (thường) thành công")
                        } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                            val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                                intent?.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION") intent?.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                            }
                            try { startActivity(confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                        } else {
                            toast("Cài đặt thất bại: $msg")
                            log("Cài đặt splits (thường) thất bại: $msg")
                        }
                    }
                }
                registerReceiver(receiver, IntentFilter(action))
                val pi = PendingIntent.getBroadcast(this, sessionId, Intent(action), PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0))
                session.commit(pi.intentSender)
                toast("Đang tiến hành cài đặt…")
            } finally {
                session.close()
            }
        } catch (e: Exception) {
            toast("Lỗi cài đặt splits: ${e.message}")
            log("Lỗi cài đặt splits (thường): ${e.message}")
        }
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
        val ext = try {
            val u = item.url?.lowercase(Locale.ROOT)
            if (u != null && u.contains(".apks")) "apks" else "apk"
        } catch (_: Exception) { "apk" }
        return File(dir, "${item.id}.$ext")
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

    // Chuẩn hoá Dropbox -> direct download; bỏ toàn bộ xử lý Google Drive
    private fun normalizeUrl(raw: String): String {
        var url = raw
        if (url.contains("dropbox.com")) {
            var u2 = url
            u2 = u2.replace("://www.dropbox.com", "://dl.dropboxusercontent.com")
            u2 = u2.replace("://dropbox.com", "://dl.dropboxusercontent.com")
            u2 = if (u2.contains("dl=0")) u2.replace("dl=0", "dl=1") else if (u2.contains("dl=")) u2 else u2 + (if (u2.contains("?")) "&dl=1" else "?dl=1")
            return u2
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
        log("Đang tải danh sách ứng dụng đã lưu...")
        val prefs = getSharedPreferences("apk_items", Context.MODE_PRIVATE)
        val json = prefs.getString("list", null)
        val loaded = ApkItem.fromJsonList(json)
        items.clear()
        items.addAll(loaded)
        log("Đã tải ${items.size} ứng dụng từ bộ nhớ.")
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

    // Chức năng sắp xếp và thống kê
    private fun showSortMenu() {
        val options = arrayOf(
            getString(R.string.sort_by_name),
            getString(R.string.sort_by_name_desc),
            getString(R.string.sort_by_size),
            getString(R.string.sort_by_date)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.sort))
            .setItems(options) { _, which ->
                sortMode = when (which) {
                    0 -> SortMode.NAME_ASC
                    1 -> SortMode.NAME_DESC
                    2 -> SortMode.SIZE_DESC
                    else -> SortMode.DATE_DESC
                }
                applyFilter(currentQuery)
            }
            .show()
    }
    
    private fun updateStats() {
        val cachedCount = items.count { 
            val f = cacheFileFor(it)
            f.exists()
        }
        val totalSize = items.mapNotNull { 
            val f = cacheFileFor(it)
            if (f.exists()) f.length() else null
        }.sum()
        
        val sizeStr = formatFileSize(totalSize)
        txtStats.text = getString(R.string.stats_format, items.size, "$cachedCount ($sizeStr)")
        statsBar.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
        btnClearCache.visibility = if (cachedCount > 0) View.VISIBLE else View.GONE
    }
    
    private fun clearCache() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cacheDir = File(cacheDir, "apks")
            var count = 0
            var size = 0L
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    size += file.length()
                    file.delete()
                    count++
                }
            }
            withContext(Dispatchers.Main) {
                val sizeStr = formatFileSize(size)
                toast(getString(R.string.cache_cleared, count, sizeStr))
                updateStats()
                applyFilter(currentQuery)
            }
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    // Khu vực quản lý ứng dụng đã cài đặt
    private fun loadInstalledApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pm = packageManager
                val pkgs = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val list = pkgs.map { appInfo ->
                    val pkg = appInfo.packageName
                    val pi = try { pm.getPackageInfo(pkg, 0) } catch (e: Exception) { null }
                    val vName = pi?.versionName
                    val vCode = pi?.let { PackageInfoCompat.getLongVersionCode(it) }
                    InstalledAppItem(
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        packageName = pkg,
                        versionName = vName,
                        versionCode = vCode,
                        icon = pm.getApplicationIcon(appInfo)
                    )
                }.sortedBy { it.appName.lowercase(Locale.getDefault()) }
                withContext(Dispatchers.Main) {
                    installedItems.clear()
                    installedItems.addAll(list)
                    applyInstalledFilter(currentInstalledQuery)
                    log("Nạp danh sách ứng dụng đã cài: ${list.size} ứng dụng")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { log("Lỗi nạp ứng dụng đã cài: ${e.message}") }
            }
        }
    }

    private fun openInstalledApp(app: InstalledAppItem) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                toast("Ứng dụng không có Activity khởi chạy")
            }
        } catch (e: Exception) {
            toast("Không mở được ứng dụng: ${e.message}")
        }
    }

    private fun openInstalledAppInfo(app: InstalledAppItem) {
        try {
            val uri = Uri.parse("package:${app.packageName}")
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Không mở được trang thông tin: ${e.message}")
        }
    }

    private fun uninstallApp(app: InstalledAppItem) {
        lifecycleScope.launch {
            val rooted = RootInstaller.isDeviceRooted()
            if (rooted) {
                val resUn: Pair<Boolean, String> = withContext(Dispatchers.IO) { RootInstaller.uninstall(app.packageName) }
                val (ok, msg) = resUn
                if (ok) {
                    toast("Đã gỡ cài đặt (root): ${app.appName}")
                    log("Gỡ cài đặt (root) thành công: ${app.packageName}. pm: $msg")
                    loadInstalledApps()
                } else {
                    toast("Gỡ (root) thất bại, thử cách thường…")
                    log("Gỡ (root) thất bại: $msg. Thử cách thường…")
                    uninstallNormally(app.packageName)
                }
            } else {
                uninstallNormally(app.packageName)
            }
        }
    }

    private fun uninstallNormally(pkg: String) {
        try {
            val uri = Uri.parse("package:$pkg")
            val i = Intent(Intent.ACTION_DELETE, uri)
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Không thể mở gỡ cài đặt: ${e.message}")
        }
    }
}

// Data & Adapter

data class ApkItem(
    val id: String,
    val name: String,
    val sourceType: SourceType,
    val url: String?,
    val uri: String?,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val iconUrl: String? = null
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
                sb.append("\"versionCode\":${it.versionCode?.toString() ?: "null"},")
                sb.append("\"iconUrl\":${if (it.iconUrl != null) "\"${escape(it.iconUrl)}\"" else "null"}")
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
                val iconUrl = map["iconUrl"]
                list.add(ApkItem(id, name, sourceType, url, uri, versionName, versionCode, iconUrl))
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
    val versionCode: Long? = null,
    val iconUrl: String? = null
)

// RootInstaller đã được tách sang file riêng: RootInstaller.kt

package com.extensionbox.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.net.URL
import com.extensionbox.app.ui.screens.AppCategory
import com.extensionbox.app.ui.screens.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.withContext
import android.widget.Toast

data class PackageDebugInfo(
    val packageName: String,
    val installed: Boolean,
    val isSystem: Boolean,
    val category: String,
    val hasLauncher: Boolean
)

class DebloatViewModel : ViewModel() {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _isRooted = MutableStateFlow(false)
    val isRooted: StateFlow<Boolean> = _isRooted

    private val _hasShizuku = MutableStateFlow(false)
    val hasShizuku: StateFlow<Boolean> = _hasShizuku

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps
    // dynamic maps populated from UAD list
    private val removalMap = mutableMapOf<String, String>()
    private val descriptionMap = mutableMapOf<String, String>()
    // Common popular apps that should be treated as user apps for debloating purposes
    private val extraUserPackages = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.google.android.youtube",
        "com.google.android.youtube.tv",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.youtube.kids",
        "com.google.android.gms",
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.maps",
        "com.zhiliaoapp.musically", // TikTok
        "com.ubercab",
        "com.facebook.katana",
        "com.instagram.android",
        "com.snapchat.android",
        "com.whatsapp",
        "com.twitter.android",
        "com.netflix.mediaclient",
        "com.spotify.music"
    )

    init {
        checkCapabilities()
        fetchUadList()
    }

    private fun checkCapabilities() {
        viewModelScope.launch {
            _isRooted.value = isDeviceRooted()
            _hasShizuku.value = Shizuku.pingBinder() && Shizuku.checkSelfPermission() == 0
        }
    }

    private fun fetchUadList() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uadUrl = "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/refs/heads/main/resources/assets/uad_lists.json"
                val text = URL(uadUrl).readText()
                val root = JSONObject(text)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = root.optJSONObject(key) ?: continue
                    val removal = obj.optString("removal", "")
                    val desc = obj.optString("description", "")
                    if (removal == "Recommended" || removal == "Advanced") {
                        removalMap[key] = removal
                        descriptionMap[key] = desc
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadApps(packageManager: PackageManager) {
        viewModelScope.launch {
            val installedApps = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            val appList = installedApps.map { packageInfo ->
                val appInfo = packageInfo.applicationInfo!!
                val label = appInfo.loadLabel(packageManager).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val pkg = packageInfo.packageName
                val category = when {
                    !isSystem -> AppCategory.SAFE
                    pkg in extraUserPackages -> AppCategory.SAFE
                    removalMap.containsKey(pkg) -> AppCategory.CAUTION
                    else -> AppCategory.EXTREME
                }
                val desc = descriptionMap[pkg] ?: if (pkg in extraUserPackages) "Common user app" else ""
                AppInfo(
                    packageName = pkg,
                    label = label,
                    isSystem = isSystem,
                    category = category,
                    description = desc
                )
            }.sortedBy { it.label }
            _apps.value = appList
        }
    }

    fun removeApp(packageName: String) {
        _apps.value = _apps.value.filter { it.packageName != packageName }
    }

    suspend fun inspectExtraPackages(packageManager: PackageManager): List<PackageDebugInfo> {
        return withContext(Dispatchers.IO) {
            val installed = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
                .associateBy { it.packageName }

            extraUserPackages.map { pkg ->
                val pInfo = installed[pkg]
                val installedFlag = pInfo != null
                val appInfo = pInfo?.applicationInfo
                val isSystemFlag = appInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false
                val category = when {
                    pInfo == null -> "missing"
                    !isSystemFlag -> "SAFE"
                    removalMap.containsKey(pkg) -> "CAUTION"
                    else -> "EXTREME"
                }
                val hasLauncher = packageManager.getLaunchIntentForPackage(pkg) != null
                PackageDebugInfo(
                    packageName = pkg,
                    installed = installedFlag,
                    isSystem = isSystemFlag,
                    category = category,
                    hasLauncher = hasLauncher
                )
            }
        }
    }

    suspend fun uninstallApp(context: Context, app: AppInfo, recoverable: Boolean = false) {
        android.util.Log.d("DebloatVM", "uninstallApp called for ${app.packageName}, system=${app.isSystem}, recoverable=$recoverable")
        // If we have elevated rights, prefer using pm uninstall directly for all packages
        if (_hasShizuku.value) {
            uninstallWithShizuku(app.packageName, recoverable)
            return
        } else if (_isRooted.value) {
            uninstallWithRoot(app.packageName, recoverable)
            return
        }

        // No elevation available: handle user apps via intent, system apps cannot be removed
        if (!app.isSystem) {
            // Launch uninstall prompt
            val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${app.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val uninstallPkgIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:${app.packageName}")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                withContext(Dispatchers.Main) {
                    context.startActivity(uninstallPkgIntent)
                }
            } catch (e: Exception) {
                android.util.Log.w("DebloatVM", "uninstall intent failed, falling back: ${e.message}")
                try {
                    withContext(Dispatchers.Main) {
                        context.startActivity(deleteIntent)
                    }
                } catch (ex: Exception) {
                    android.util.Log.e("DebloatVM", "fallback uninstall intent failed: ${ex.message}", ex)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Unable to start uninstall for ${app.packageName}: ${ex.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            // system app without root/Shizuku
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Cannot uninstall system app without root or Shizuku", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun uninstallWithShizuku(packageName: String, recoverable: Boolean) {
        // Use Shizuku to run pm uninstall. If recoverable is requested, include -k --user 0
        val cmd = if (recoverable) arrayOf("pm", "uninstall", "-k", "--user", "0", packageName)
        else arrayOf("pm", "uninstall", packageName)
        try {
            Shizuku.newProcess(cmd, null, null).waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun uninstallWithRoot(packageName: String, recoverable: Boolean) {
        // Use root to run pm uninstall. If recoverable is requested, include -k --user 0
        try {
            val cmd = if (recoverable) "pm uninstall -k --user 0 $packageName" else "pm uninstall $packageName"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setShowSystemApps(show: Boolean) {
        _showSystemApps.value = show
    }

    private fun isDeviceRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo test"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            output == "test"
        } catch (e: Exception) {
            false
        }
    }
}
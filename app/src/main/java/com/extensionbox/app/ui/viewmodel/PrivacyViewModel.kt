package com.extensionbox.app.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ui.screens.AppCategory
import com.extensionbox.app.ui.screens.AppInfo
import com.extensionbox.app.ui.screens.PermissionInfo
import com.extensionbox.app.ui.screens.PrivacyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrivacyViewModel : ViewModel() {
    private val _events = MutableStateFlow<List<PrivacyEvent>>(emptyList())
    val events: StateFlow<List<PrivacyEvent>> = _events

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val _selectedAppPermissions = MutableStateFlow<List<PermissionInfo>>(emptyList())
    val selectedAppPermissions: StateFlow<List<PermissionInfo>> = _selectedAppPermissions

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    fun loadData(ctx: Context, sys: SystemAccess) {
        viewModelScope.launch {
            _isRefreshing.value = true
            withContext(Dispatchers.IO) {
                val history = sys.getPrivacyHistory(ctx)
                _events.value = history

                val pm = ctx.packageManager
                val installed = pm.getInstalledPackages(0)
                val appList = installed.map { 
                    val appInfo = it.applicationInfo!!
                    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    AppInfo(
                        packageName = it.packageName,
                        label = appInfo.loadLabel(pm).toString(),
                        isSystem = isSystem,
                        category = if (isSystem) AppCategory.EXTREME else AppCategory.SAFE
                    )
                }.sortedBy { it.label }
                _apps.value = appList
            }
            _isRefreshing.value = false
        }
    }

    fun loadAppPermissions(sys: SystemAccess, packageName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _selectedAppPermissions.value = sys.getAppPermissions(packageName)
            }
        }
    }

    fun updatePermission(sys: SystemAccess, packageName: String, opName: String, mode: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (sys.setAppPermission(packageName, opName, mode)) {
                    _selectedAppPermissions.value = sys.getAppPermissions(packageName)
                }
            }
        }
    }
}

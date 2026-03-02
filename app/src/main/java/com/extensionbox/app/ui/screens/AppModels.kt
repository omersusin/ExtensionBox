package com.extensionbox.app.ui.screens

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val category: AppCategory,
    val description: String = ""
)

enum class AppCategory {
    SAFE, CAUTION, EXTREME
}

data class PrivacyEvent(
    val packageName: String,
    val appLabel: String,
    val opName: String, // e.g., CAMERA, COARSE_LOCATION
    val lastAccessTime: Long,
    val duration: Long = 0,
    val isRunning: Boolean = false
)

data class PermissionInfo(
    val opCode: Int,
    val opName: String,
    val mode: Int, // 0: ALLOW, 1: IGNORE, 2: DENY, 3: DEFAULT
    val modeName: String
)

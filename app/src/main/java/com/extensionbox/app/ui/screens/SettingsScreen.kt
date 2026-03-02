package com.extensionbox.app.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ThemeHelper
import com.extensionbox.app.ui.components.AppCard
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

import androidx.lifecycle.viewmodel.compose.viewModel
import com.extensionbox.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    val themeIndex by viewModel.themeIndex.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val isShizukuRunning by viewModel.isShizukuRunning.collectAsState()
    val shizukuPermissionGranted by viewModel.shizukuPermissionGranted.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshShizukuState()
                
                if (isShizukuRunning && !shizukuPermissionGranted) {
                    try {
                        Shizuku.requestPermission(1001)
                    } catch (_: Exception) {}
                }
            }
        }
        
        val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            viewModel.setShizukuPermission(grantResult == PackageManager.PERMISSION_GRANTED)
        }
        
        val binderListener = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                viewModel.refreshShizukuState()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderListener)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderListener)
        }
    }

    // Reactive System Access
    var sysAccess by remember { mutableStateOf<SystemAccess?>(null) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        val newAccess = withContext(Dispatchers.IO) {
            SystemAccess(context)
        }
        sysAccess = newAccess
    }

    // Refresh function
    fun refreshAccess() {
        sysAccess = null // Show loading
        scope.launch {
            val newAccess = withContext(Dispatchers.IO) {
                SystemAccess(context)
            }
            sysAccess = newAccess
            viewModel.refreshShizukuState()
        }
    }

    // Launchers
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                val jsonObject = JSONObject()
                Prefs.getAll(context).forEach { (k, v) -> jsonObject.put(k, v) }
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(jsonObject.toString(4).toByteArray())
                }
                Toast.makeText(context, R.string.settings_exported, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { `is` ->
                    val json = BufferedReader(InputStreamReader(`is`)).readText()
                    Prefs.importJson(context, json)
                    Toast.makeText(context, R.string.settings_imported, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, R.string.import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- Permissions Section ---
        SettingsGroup(title = stringResource(id = R.string.permissions), icon = Icons.Default.Security) {
            AppCard {
                val currentAccess = sysAccess
                
                if (currentAccess == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.checking_system_access), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    SettingsItem(
                        title = stringResource(id = R.string.system_access),
                        summary = stringResource(id = R.string.system_access_summary, currentAccess.tier),
                        icon = if (currentAccess.isEnhanced()) Icons.Default.Verified else Icons.Default.AdminPanelSettings,
                        color = if (currentAccess.isEnhanced()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        trailing = {
                            IconButton(onClick = { refreshAccess() }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(id = R.string.refresh))
                            }
                        },
                        onClick = { refreshAccess() }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                SettingsItem(
                    title = stringResource(id = R.string.shizuku_service),
                    summary = if (isShizukuRunning) stringResource(id = R.string.service_is_active) else stringResource(id = R.string.service_not_found),
                    icon = Icons.Default.Terminal,
                    onClick = {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (intent != null) context.startActivity(intent)
                            else Toast.makeText(context, R.string.shizuku_not_found, Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) { /* ignore */ }
                    }
                )

                if (isShizukuRunning && !shizukuPermissionGranted) {
                    SettingsItem(
                        title = stringResource(id = R.string.grant_permission),
                        summary = stringResource(id = R.string.allow_system_file_access_via_shizuku),
                        icon = Icons.Default.VpnKey,
                        onClick = { Shizuku.requestPermission(1001) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                SettingsItem(
                    title = stringResource(id = R.string.battery_optimization),
                    summary = stringResource(id = R.string.allow_background_monitoring),
                    icon = Icons.Default.BatterySaver,
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    }
                )
            }
        }

        // --- Notification Section ---
        SettingsGroup(title = stringResource(id = R.string.notification), icon = Icons.Default.Notifications) {
            AppCard {
                var refreshRate by remember { mutableStateOf(Prefs.getLong(context, "notif_refresh_ms", 10000L).toFloat()) }
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(id = R.string.refresh_rate), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(id = R.string.refresh_rate_value, (refreshRate.toInt() / 1000)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = refreshRate,
                        onValueChange = { 
                            refreshRate = it
                            Prefs.setLong(context, "notif_refresh_ms", it.toLong())
                        },
                        valueRange = 1000f..60000f,
                        steps = 58
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var dismissible by remember { mutableStateOf(Prefs.getBool(context, "notif_dismissible", false)) }
                SettingsToggle(
                    title = stringResource(id = R.string.dismissible),
                    summary = stringResource(id = R.string.allow_swiping_away_to_stop_service),
                    icon = Icons.Default.Swipe,
                    checked = dismissible,
                    onCheckedChange = {
                        dismissible = it
                        Prefs.setBool(context, "notif_dismissible", it)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var layoutExpanded by remember { mutableStateOf(false) }
                var layoutStyle by remember { mutableStateOf(Prefs.getString(context, "notif_layout_style", "PUZZLE") ?: "PUZZLE") }
                
                SettingsItem(
                    title = stringResource(id = R.string.layout_style),
                    summary = layoutStyle,
                    icon = Icons.Default.Dashboard,
                    onClick = { layoutExpanded = true }
                )
                
                DropdownMenu(expanded = layoutExpanded, onDismissRequest = { layoutExpanded = false }) {
                    listOf("PUZZLE", "LIST", "GRID").forEach { style ->
                        DropdownMenuItem(
                            text = { Text(style) },
                            onClick = {
                                layoutStyle = style
                                Prefs.setString(context, "notif_layout_style", style)
                                layoutExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // --- Appearance Section ---
        SettingsGroup(title = stringResource(id = R.string.appearance), icon = Icons.Default.Palette) {
            AppCard {
                SettingsItem(
                    title = stringResource(id = R.string.app_theme),
                    summary = ThemeHelper.NAMES[themeIndex.coerceIn(0, ThemeHelper.NAMES.size - 1)],
                    icon = Icons.Default.ColorLens,
                    onClick = { expanded = true }
                )
                
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ThemeHelper.NAMES.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                viewModel.updateTheme(index)
                                expanded = false
                            }
                        )
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                    (themeIndex == ThemeHelper.MONET || themeIndex == ThemeHelper.AMOLED)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    var dynamicColor by remember { mutableStateOf(Prefs.getBool(context, "dynamic_color", true)) }
                    SettingsToggle(
                        title = stringResource(id = R.string.dynamic_color),
                        summary = stringResource(id = R.string.use_system_wallpaper_colors),
                        icon = Icons.Default.InvertColors,
                        checked = dynamicColor,
                        onCheckedChange = {
                            dynamicColor = it
                            Prefs.setBool(context, "dynamic_color", it)
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var expandCards by remember { mutableStateOf(Prefs.getBool(context, "dash_expand_cards", true)) }
                SettingsToggle(
                    title = stringResource(id = R.string.expandable_cards),
                    summary = stringResource(id = R.string.show_expansion_toggle_on_dashboard),
                    icon = Icons.Default.ViewStream,
                    checked = expandCards,
                    onCheckedChange = {
                        expandCards = it
                        Prefs.setBool(context, "dash_expand_cards", it)
                    }
                )
            }
        }

        // --- Monitoring Section ---
        SettingsGroup(title = stringResource(id = R.string.monitoring), icon = Icons.Default.Analytics) {
            AppCard {
                var resetFull by remember { mutableStateOf(Prefs.getBool(context, "scr_reset_full", true)) }
                SettingsToggle(
                    title = stringResource(id = R.string.reset_on_full_charge),
                    summary = stringResource(id = R.string.clear_stats_when_battery_reaches_100),
                    icon = Icons.Default.BatteryChargingFull,
                    checked = resetFull,
                    onCheckedChange = {
                        resetFull = it
                        Prefs.setBool(context, "scr_reset_full", it)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var resetPlugged by remember { mutableStateOf(Prefs.getBool(context, "scr_reset_plugged", false)) }
                SettingsToggle(
                    title = "Reset on Plugged",
                    summary = "Clear stats when device is plugged in",
                    icon = Icons.Default.Power,
                    checked = resetPlugged,
                    onCheckedChange = {
                        resetPlugged = it
                        Prefs.setBool(context, "scr_reset_plugged", it)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var resetBoot by remember { mutableStateOf(Prefs.getBool(context, "scr_reset_boot", true)) }
                SettingsToggle(
                    title = stringResource(id = R.string.reset_on_reboot),
                    summary = stringResource(id = R.string.clear_stats_after_system_restart),
                    icon = Icons.Default.RestartAlt,
                    checked = resetBoot,
                    onCheckedChange = {
                        resetBoot = it
                        Prefs.setBool(context, "scr_reset_boot", it)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                var contextAware by remember { mutableStateOf(Prefs.getBool(context, "notif_context_aware", true)) }
                SettingsToggle(
                    title = stringResource(id = R.string.context_aware),
                    summary = stringResource(id = R.string.dynamic_titles_based_on_battery),
                    icon = Icons.Default.NotificationAdd,
                    checked = contextAware,
                    onCheckedChange = {
                        contextAware = it
                        Prefs.setBool(context, "notif_context_aware", it)
                    }
                )
            }
        }

        // --- Data Section ---
        SettingsGroup(title = stringResource(id = R.string.data_backup), icon = Icons.Default.Storage) {
            AppCard {
                var retentionDays by remember { mutableStateOf(Prefs.getDataRetentionDays(context).toFloat()) }
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(id = R.string.data_retention), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(id = R.string.data_retention_days, retentionDays.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = retentionDays,
                        onValueChange = { 
                            retentionDays = it
                            Prefs.setDataRetentionDays(context, it.toInt())
                        },
                        valueRange = 1f..30f,
                        steps = 29
                    )
                    Text(
                        text = stringResource(id = R.string.history_older_than_this_will_be_deleted_daily),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                SettingsItem(
                    title = stringResource(id = R.string.export_settings),
                    summary = stringResource(id = R.string.save_config_to_json),
                    icon = Icons.Default.UploadFile,
                    onClick = { exportLauncher.launch("extensionbox_config.json") }
                )
                SettingsItem(
                    title = stringResource(id = R.string.import_settings),
                    summary = stringResource(id = R.string.restore_from_json),
                    icon = Icons.Default.FileDownload,
                    onClick = { importLauncher.launch(arrayOf("application/json")) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                SettingsItem(
                    title = stringResource(id = R.string.reset_all_data),
                    summary = stringResource(id = R.string.clear_all_stats_and_preferences),
                    icon = Icons.Default.DeleteSweep,
                    color = MaterialTheme.colorScheme.error,
                    onClick = {
                        Prefs.clearAll(context)
                        Toast.makeText(context, R.string.data_cleared, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun SettingsGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        content()
    }
}

@Composable
fun SettingsItem(
    title: String,
    summary: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun SettingsToggle(
    title: String,
    summary: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsItem(
        title = title,
        summary = summary,
        icon = icon,
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

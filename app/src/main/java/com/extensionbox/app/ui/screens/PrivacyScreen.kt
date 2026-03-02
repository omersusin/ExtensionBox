package com.extensionbox.app.ui.screens

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.ui.viewmodel.PrivacyViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    sys: SystemAccess,
    viewModel: PrivacyViewModel = viewModel()
) {
    val context = LocalContext.current
    val events by viewModel.events.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedAppPermissions by viewModel.selectedAppPermissions.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedAppPackage by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableIntStateOf(0) } // 0: Timeline, 1: Apps

    LaunchedEffect(Unit) {
        viewModel.loadData(context, sys)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Dashboard") },
                actions = {
                    IconButton(onClick = { viewModel.loadData(context, sys) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
                    Text("Timeline", modifier = Modifier.padding(16.dp))
                }
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                    Text("Apps", modifier = Modifier.padding(16.dp))
                }
            }

            if (activeTab == 0) {
                TimelineView(events)
            } else {
                AppListView(
                    apps = apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) },
                    query = query,
                    onQueryChange = { query = it },
                    selectedApp = selectedAppPackage,
                    onAppSelect = { 
                        selectedAppPackage = it
                        if (it != null) viewModel.loadAppPermissions(sys, it)
                    },
                    permissions = selectedAppPermissions,
                    onPermissionUpdate = { op, mode ->
                        selectedAppPackage?.let { pkg -> viewModel.updatePermission(sys, pkg, op, mode) }
                    }
                )
            }
        }
    }
}

@Composable
fun TimelineView(events: List<PrivacyEvent>) {
    if (events.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No recent privacy events detected", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            items(events) { event ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (event.opName) {
                            "CAMERA" -> Icons.Default.Videocam
                            "RECORD_AUDIO" -> Icons.Default.Mic
                            "FINE_LOCATION", "COARSE_LOCATION" -> Icons.Default.LocationOn
                            "READ_CLIPBOARD" -> Icons.Default.ContentPaste
                            else -> Icons.Default.Shield
                        }
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(event.appLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(event.opName, style = MaterialTheme.typography.bodySmall)
                            Text(sdf.format(Date(event.lastAccessTime)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppListView(
    apps: List<AppInfo>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedApp: String?,
    onAppSelect: (String?) -> Unit,
    permissions: List<PermissionInfo>,
    onPermissionUpdate: (String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(apps, key = { it.packageName }) { app ->
                AppPermissionCard(
                    app = app,
                    isExpanded = selectedApp == app.packageName,
                    onClick = { onAppSelect(if (selectedApp == app.packageName) null else app.packageName) },
                    permissions = permissions,
                    onUpdate = onPermissionUpdate
                )
            }
        }
    }
}

@Composable
fun AppPermissionCard(
    app: AppInfo,
    isExpanded: Boolean,
    onClick: () -> Unit,
    permissions: List<PermissionInfo>,
    onUpdate: (String, String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                }
                if (app.isSystem) {
                    Text("SYSTEM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp))
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    if (permissions.isEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                    } else {
                        permissions.filter { it.opName in setOf("CAMERA", "RECORD_AUDIO", "FINE_LOCATION", "COARSE_LOCATION", "READ_CLIPBOARD", "SYSTEM_ALERT_WINDOW", "WRITE_SETTINGS") }.forEach { perm ->
                            PermissionRow(perm, onUpdate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRow(perm: PermissionInfo, onUpdate: (String, String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(perm.opName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        
        var expanded by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(perm.modeName.uppercase(), color = when(perm.mode) {
                    0 -> Color.Green
                    1, 2 -> Color.Red
                    else -> MaterialTheme.colorScheme.onSurface
                })
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Allow") }, onClick = { onUpdate(perm.opName, "allow"); expanded = false })
                DropdownMenuItem(text = { Text("Ignore (Fake success)") }, onClick = { onUpdate(perm.opName, "ignore"); expanded = false })
                DropdownMenuItem(text = { Text("Deny") }, onClick = { onUpdate(perm.opName, "deny"); expanded = false })
                DropdownMenuItem(text = { Text("Default") }, onClick = { onUpdate(perm.opName, "default"); expanded = false })
            }
        }
    }
}

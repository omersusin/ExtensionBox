package com.extensionbox.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.extensionbox.app.MonitorService
import com.extensionbox.app.Prefs
import com.extensionbox.app.SystemAccess
import com.extensionbox.app.db.AppDatabase
import com.extensionbox.app.db.ModuleDataEntity
import com.extensionbox.app.ui.ModuleRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)

    private val _isRunning = MutableStateFlow(Prefs.isRunning(context))
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    private val _dashData = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val dashData: StateFlow<Map<String, Map<String, String>>> = _dashData.asStateFlow()

    private val _historyData = MutableStateFlow<Map<String, List<ModuleDataEntity>>>(emptyMap())
    val historyData: StateFlow<Map<String, List<ModuleDataEntity>>> = _historyData.asStateFlow()

    private val _sysAccess = MutableStateFlow<com.extensionbox.app.SystemAccess?>(null)
    val sysAccess: StateFlow<com.extensionbox.app.SystemAccess?> = _sysAccess.asStateFlow()

    val moduleOrder = mutableStateListOf<String>()
    val expandedStates = mutableStateMapOf<String, Boolean>()

    private val _visibleModules = MutableStateFlow<List<String>>(emptyList())
    val visibleModules: StateFlow<List<String>> = _visibleModules.asStateFlow()

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _sysAccess.value = com.extensionbox.app.SystemAccess(context)
        }
        loadOrder()
        startDataPolling()
    }

    private fun loadOrder() {
        val savedOrder = Prefs.getString(context, "dash_card_order", "") ?: ""
        val initialOrder = if (savedOrder.isEmpty()) {
            (0 until ModuleRegistry.count()).map { ModuleRegistry.keyAt(it) }
        } else {
            savedOrder.split(",").filter { it.isNotEmpty() }
        }
        moduleOrder.clear()
        moduleOrder.addAll(initialOrder)
    }

    fun updateOrder(fromIndex: Int, toIndex: Int) {
        val visible = _visibleModules.value
        // Offset by 1 for StatusHero header
        val fromVisibleIdx = fromIndex - 1
        val toVisibleIdx = toIndex - 1
        
        if (fromVisibleIdx < 0 || toVisibleIdx < 0 || 
            fromVisibleIdx >= visible.size || toVisibleIdx >= visible.size) return
        
        val fromKey = visible[fromVisibleIdx]
        val toKey = visible[toVisibleIdx]
        
        // Find positions in global order
        val globalFrom = moduleOrder.indexOf(fromKey)
        val globalTo = moduleOrder.indexOf(toKey)
        
        if (globalFrom != -1 && globalTo != -1) {
            moduleOrder.apply {
                add(globalTo, removeAt(globalFrom))
            }
            Prefs.setString(context, "dash_card_order", moduleOrder.joinToString(","))
            updateVisibleModules()
        }
    }

    private fun updateVisibleModules() {
        val data = _dashData.value
        _visibleModules.value = moduleOrder.filter { data.containsKey(it) }
    }

    private fun startDataPolling() {
        viewModelScope.launch {
            while (true) {
                val running = Prefs.isRunning(context)
                _isRunning.value = running
                
                var count = 0
                val dataMap = mutableMapOf<String, Map<String, String>>()
                val histMap = mutableMapOf<String, List<ModuleDataEntity>>()
                val fifteenMinsAgo = System.currentTimeMillis() - (15 * 60 * 1000)

                for (i in 0 until ModuleRegistry.count()) {
                    val key = ModuleRegistry.keyAt(i)
                    if (Prefs.isModuleEnabled(context, key, ModuleRegistry.defAt(i))) {
                        count++
                        
                        var mData = MonitorService.getModuleData(key)
                        
                        // Special handling for modules that might not have background data yet or at all
                        if (mData == null && (key == "habit" || key == "privacy")) {
                            val module = ModuleRegistry.getModule(key)
                            if (module != null) {
                                module.start(context, com.extensionbox.app.SystemAccess(context))
                                mData = module.dataPoints()
                            }
                        }

                        if (mData != null && mData.isNotEmpty()) {
                            dataMap[key] = mData
                            
                            // Fetch history
                            val history = database.moduleDataDao().getHistoryList(key, fifteenMinsAgo)
                            if (history.isNotEmpty()) {
                                histMap[key] = history
                            }
                        }
                    }
                }
                _activeCount.value = count
                _dashData.value = dataMap
                _historyData.value = histMap
                updateVisibleModules()
                
                delay(2000)
            }
        }
    }

    fun toggleExpansion(key: String) {
        expandedStates[key] = !(expandedStates[key] ?: false)
    }

    fun isExpanded(key: String): Boolean {
        return if (Prefs.getBool(context, "dash_expand_cards", true)) {
            expandedStates[key] ?: false
        } else {
            true
        }
    }
}

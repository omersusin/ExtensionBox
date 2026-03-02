package com.extensionbox.app.modules

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import com.extensionbox.app.Prefs
import com.extensionbox.app.R
import com.extensionbox.app.SystemAccess
import java.util.LinkedHashMap

class ConnectionModule : Module {

    private var ctx: Context? = null
    private var running = false
    private var connType = "None"
    private var wifiSsid = "—"
    private var wifiRssi = "—"
    private var wifiLinkSpeed = 0
    private var wifiSpeed = "—"
    private var wifiFreq = "—"
    private var carrier = "—"
    private var netType = "—"
    private var vpnActive = false

    override fun key(): String = "connection"
    override fun name(): String = ctx?.getString(R.string.connection_module_name) ?: "Connection Info"
    override fun description(): String = ctx?.getString(R.string.connection_module_description) ?: "WiFi, cellular, VPN status"
    override fun defaultEnabled(): Boolean = false
    override fun alive(): Boolean = running
    override fun priority(): Int = 90

    override fun tickIntervalMs(): Int = ctx?.let { Prefs.getInt(it, "con_interval", 10000) } ?: 10000

    override fun start(ctx: Context, sys: SystemAccess) {
        this.ctx = ctx
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun tick() {
        val c = ctx ?: return
        try {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            if (net == null) {
                connType = c.getString(R.string.connection_module_type_none)
                return
            }

            val caps = cm.getNetworkCapabilities(net)
            if (caps == null) {
                connType = c.getString(R.string.connection_module_type_none)
                return
            }

            vpnActive = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    connType = c.getString(R.string.connection_module_type_wifi)
                    readWifi()
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    connType = c.getString(R.string.connection_module_type_mobile)
                    readCell()
                }
                else -> {
                    connType = c.getString(R.string.connection_module_type_other)
                }
            }
        } catch (e: Exception) {
            connType = c.getString(R.string.connection_module_type_error)
        }
    }

    private fun readWifi() {
        val c = ctx ?: return
        try {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            val wi = caps?.transportInfo as? android.net.wifi.WifiInfo
            
            if (wi != null) {
                val ssid = wi.ssid
                wifiSsid = if (ssid != null && ssid != "<unknown ssid>") {
                    ssid.replace("\"", "")
                } else {
                    c.getString(R.string.connection_module_hidden_ssid)
                }
                wifiRssi = "${wi.rssi}${c.getString(R.string.connection_module_dbm)}"
                wifiLinkSpeed = wi.linkSpeed
                wifiSpeed = "${wi.linkSpeed}${c.getString(R.string.connection_module_mbps)}"
                val freq = wi.frequency
                wifiFreq = if (freq > 4900) c.getString(R.string.connection_module_5ghz) else c.getString(R.string.connection_module_2_4ghz)
            }
        } catch (e: Exception) {
            wifiSsid = c.getString(R.string.connection_module_type_error)
        }
    }

    private fun readCell() {
        val c = ctx ?: return
        try {
            val tm = c.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            carrier = tm.networkOperatorName.takeIf { it.isNotEmpty() } ?: c.getString(R.string.connection_module_unknown_carrier)
            val type = try { tm.dataNetworkType } catch (e: SecurityException) { TelephonyManager.NETWORK_TYPE_UNKNOWN }
            netType = when (type) {
                TelephonyManager.NETWORK_TYPE_LTE -> c.getString(R.string.connection_module_network_type_lte)
                TelephonyManager.NETWORK_TYPE_NR -> c.getString(R.string.connection_module_network_type_5g)
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA -> c.getString(R.string.connection_module_network_type_3g)
                TelephonyManager.NETWORK_TYPE_EDGE -> c.getString(R.string.connection_module_network_type_2g)
                else -> c.getString(R.string.connection_module_network_type_unknown)
            }
        } catch (e: Exception) {
            carrier = "—"
            netType = "—"
        }
    }

    override fun compact(): String {
        val c = ctx ?: return ""
        return when (connType) {
            c.getString(R.string.connection_module_type_wifi) -> c.getString(R.string.connection_module_compact_wifi, wifiRssi)
            c.getString(R.string.connection_module_type_mobile) -> c.getString(R.string.connection_module_compact_mobile, carrier, netType)
            else -> c.getString(R.string.connection_module_compact_other, connType)
        }
    }

    override fun detail(): String {
        val c = ctx ?: return ""
        val sb = StringBuilder()
        when (connType) {
            c.getString(R.string.connection_module_type_wifi) -> {
                sb.append(c.getString(R.string.connection_module_detail_wifi, wifiSsid, wifiRssi))
                sb.append(c.getString(R.string.connection_module_detail_wifi_stats, wifiSpeed, wifiFreq))
            }
            c.getString(R.string.connection_module_type_mobile) -> {
                sb.append(c.getString(R.string.connection_module_detail_mobile, carrier, netType))
            }
            else -> {
                sb.append(c.getString(R.string.connection_module_detail_other, connType))
            }
        }
        if (vpnActive) sb.append(c.getString(R.string.connection_module_vpn_active))
        return sb.toString()
    }

    override fun dataPoints(): LinkedHashMap<String, String> {
        val c = ctx ?: return LinkedHashMap()
        val d = LinkedHashMap<String, String>()
        d["conn.type"] = connType
        if (c.getString(R.string.connection_module_type_wifi) == connType) {
            d["conn.ssid"] = wifiSsid
            d["conn.rssi"] = wifiRssi
            d["conn.speed"] = wifiSpeed
            d["conn.freq"] = wifiFreq
        } else if (c.getString(R.string.connection_module_type_mobile) == connType) {
            d["conn.carrier"] = carrier
            d["conn.network"] = netType
        }
        d["conn.vpn"] = if (vpnActive) c.getString(R.string.connection_module_vpn_status_active) else c.getString(R.string.connection_module_vpn_status_none)
        return d
    }

    override fun checkAlerts(ctx: Context) {}
}

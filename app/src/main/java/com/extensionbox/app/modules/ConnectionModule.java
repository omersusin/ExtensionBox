package com.extensionbox.app.modules;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;

import com.extensionbox.app.Prefs;
import com.extensionbox.app.SystemAccess;

import java.util.LinkedHashMap;

public class ConnectionModule implements Module {

    private Context ctx;
    private boolean running = false;
    private String connType = "None";
    private String wifiSsid = "—", wifiRssi = "—", wifiSpeed = "—", wifiFreq = "—";
    private String carrier = "—", netType = "—";
    private boolean vpnActive = false;

    @Override public String key() { return "connection"; }
    @Override public String name() { return "Connection Info"; }
    @Override public String emoji() { return "📡"; }
    @Override public String description() { return "WiFi, cellular, VPN status"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean alive() { return running; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "con_interval", 10000) : 10000;
    }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        running = true;
    }

    @Override public void stop() { running = false; }

    @Override
    public void tick() {
        if (ctx == null) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm.getActiveNetwork();
            if (net == null) { connType = "None"; return; }

            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            if (caps == null) { connType = "None"; return; }

            vpnActive = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                connType = "WiFi";
                readWifi();
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                connType = "Mobile";
                readCell();
            } else {
                connType = "Other";
            }
        } catch (Exception e) {
            connType = "Error";
        }
    }

    private void readWifi() {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            if (wi != null) {
                String ssid = wi.getSSID();
                wifiSsid = (ssid != null && !ssid.equals("<unknown ssid>")) ? ssid.replace("\"", "") : "Hidden";
                wifiRssi = wi.getRssi() + " dBm";
                wifiSpeed = wi.getLinkSpeed() + " Mbps";
                int freq = wi.getFrequency();
                wifiFreq = freq > 4900 ? "5 GHz" : "2.4 GHz";
            }
        } catch (Exception e) {
            wifiSsid = "Error";
        }
    }

    private void readCell() {
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            carrier = tm.getNetworkOperatorName();
            if (carrier == null || carrier.trim().isEmpty()) carrier = "Unknown Carrier";

            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                int type = tm.getDataNetworkType();
                switch (type) {
                    case TelephonyManager.NETWORK_TYPE_LTE: netType = "LTE"; break;
                    case TelephonyManager.NETWORK_TYPE_NR: netType = "5G"; break;
                    case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_HSUPA:
                    case TelephonyManager.NETWORK_TYPE_HSPA: netType = "3G"; break;
                    case TelephonyManager.NETWORK_TYPE_EDGE: netType = "2G"; break;
                    default: netType = "Unknown Type"; break;
                }
            } else {
                netType = "No Perm";
            }
        } catch (Exception e) {
            carrier = "—"; netType = "—";
        }
    }

    @Override
    public String compact() {
        if ("WiFi".equals(connType)) return "WiFi " + wifiRssi;
        if ("Mobile".equals(connType)) return carrier + " " + netType;
        return "📡 " + connType;
    }

    @Override
    public String detail() {
        StringBuilder sb = new StringBuilder();
        if ("WiFi".equals(connType)) {
            sb.append("📡 WiFi: ").append(wifiSsid).append(" (").append(wifiRssi).append(")");
            sb.append("\n   ").append(wifiSpeed).append(" • ").append(wifiFreq);
        } else if ("Mobile".equals(connType)) {
            sb.append("📡 Mobile: ").append(carrier).append(" ").append(netType);
        } else {
            sb.append("📡 ").append(connType);
        }
        if (vpnActive) sb.append("\n   VPN: Active");
        return sb.toString();
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        d.put("conn.type", connType);
        if ("WiFi".equals(connType)) {
            d.put("conn.ssid", wifiSsid);
            d.put("conn.rssi", wifiRssi);
            d.put("conn.speed", wifiSpeed);
            d.put("conn.freq", wifiFreq);
        } else if ("Mobile".equals(connType)) {
            d.put("conn.carrier", carrier);
            d.put("conn.network", netType);
        }
        d.put("conn.vpn", vpnActive ? "Active" : "None");
        return d;
    }

    @Override public void checkAlerts(Context ctx) { }
}

package com.extensionbox.app.modules;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import com.extensionbox.app.Prefs;
import com.extensionbox.app.SystemAccess;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Locale;

public class SpeedTestModule implements Module {

    private Context ctx;
    private boolean running = false;
    private Handler handler;
    private Runnable testRunnable;

    private String dlResult = "Waiting...";
    private String ulResult = "—";
    private String pingResult = "—";
    private long lastTestTime = 0;
    private int testsToday = 0;
    private boolean testing = false;

    // Multiple test URLs for fallback
    private static final String[] TEST_URLS = {
            "https://speed.cloudflare.com/__down?bytes=5000000",
            "https://proof.ovh.net/files/1Mb.dat",
            "https://ash-speed.hetzner.com/1MB.bin"
    };

    // Upload test URL
    private static final String UPLOAD_TEST_URL = "https://speed.cloudflare.com/__up";

    @Override public String key() { return "speedtest"; }
    @Override public String name() { return "Speed Test"; }
    @Override public String emoji() { return "🏎"; }
    @Override public String description() { return "Periodic download/upload speed test"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean alive() { return running; }
    @Override public int priority() { return 80; }

    @Override
    public int tickIntervalMs() {
        return ctx != null ? Prefs.getInt(ctx, "spd_interval", 30000) : 30000;
    }

    @Override
    public void start(Context c, SystemAccess sys) {
        ctx = c;
        running = true;
        handler = new Handler(Looper.getMainLooper());

        boolean autoTest = Prefs.getBool(c, "spd_auto_test", true);
        if (autoTest) {
            int freqMin = Prefs.getInt(c, "spd_test_freq", 60);
            testRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!running) return;
                    runTest();
                    handler.postDelayed(this, freqMin * 60000L);
                }
            };
            // First test after 10s
            handler.postDelayed(testRunnable, 10000);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }

    @Override public void tick() { }

    public void runTestNow() { runTest(); }

    private void runTest() {
        if (ctx == null || testing) return;

        boolean wifiOnly = Prefs.getBool(ctx, "spd_wifi_only", true);
        if (wifiOnly && !isOnWifi()) {
            dlResult = "Skipped (not WiFi)";
            lastTestTime = System.currentTimeMillis();
            return;
        }

        int dailyLimit = Prefs.getInt(ctx, "spd_daily_limit", 10);
        if (dailyLimit > 0 && dailyLimit < 9999 && testsToday >= dailyLimit) {
            dlResult = "Daily limit reached";
            lastTestTime = System.currentTimeMillis();
            return;
        }

        testing = true;
        dlResult = "Testing...";
        ulResult = "...";

        new Thread(() -> {
            // --- Ping Test ---
            if (Prefs.getBool(ctx, "spd_show_ping", true)) {
                pingResult = doPing();
            }

            // --- Download Test (try multiple URLs) ---
            dlResult = doDownloadTest();

            // --- Upload Test ---
            ulResult = doUploadTest();

            testsToday++;
            lastTestTime = System.currentTimeMillis();
            testing = false;
        }).start();
    }

    private String doPing() {
        try {
            // TCP connect-based ping to Cloudflare
            long start = System.currentTimeMillis();
            Socket sock = new Socket();
            sock.connect(new java.net.InetSocketAddress("1.1.1.1", 443), 5000);
            long elapsed = System.currentTimeMillis() - start;
            sock.close();
            return elapsed + "ms";
        } catch (Exception e) {
            // Fallback: try InetAddress reachability
            try {
                long start = System.currentTimeMillis();
                InetAddress addr = InetAddress.getByName("1.1.1.1");
                if (addr.isReachable(5000)) {
                    long elapsed = System.currentTimeMillis() - start;
                    return elapsed + "ms";
                }
            } catch (Exception ignored) {}
            return "—";
        }
    }

    private String doDownloadTest() {
        for (String testUrl : TEST_URLS) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(testUrl).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", "ExtensionBox/1.0");
                c.setRequestProperty("Accept", "*/*");

                long start = System.currentTimeMillis();
                InputStream is = c.getInputStream();
                byte[] buf = new byte[32768]; // Larger buffer for better throughput
                long total = 0;
                int r;

                while ((r = is.read(buf)) != -1) {
                    total += r;
                    // Timeout after 15 seconds max
                    if (System.currentTimeMillis() - start > 15000) break;
                }

                long ms = System.currentTimeMillis() - start;
                is.close();
                c.disconnect();

                if (ms > 0 && total > 0) {
                    double mbps = (total * 8.0) / (ms / 1000.0) / 1_000_000.0;
                    return String.format(Locale.US, "%.1f Mbps", mbps);
                }
            } catch (Exception e) {
                // Try next URL
                continue;
            }
        }
        return "Failed";
    }

    private String doUploadTest() {
        try {
            // Generate 1MB of random data to upload
            byte[] uploadData = new byte[1024 * 1024];
            for (int i = 0; i < uploadData.length; i++) {
                uploadData[i] = (byte) (i & 0xFF);
            }

            HttpURLConnection c = (HttpURLConnection) new URL(UPLOAD_TEST_URL).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(30000);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/octet-stream");
            c.setRequestProperty("Content-Length", String.valueOf(uploadData.length));
            c.setFixedLengthStreamingMode(uploadData.length);

            long start = System.currentTimeMillis();
            OutputStream os = c.getOutputStream();
            // Write in chunks
            int offset = 0;
            int chunkSize = 32768;
            while (offset < uploadData.length) {
                int len = Math.min(chunkSize, uploadData.length - offset);
                os.write(uploadData, offset, len);
                offset += len;
                // Timeout after 15 seconds
                if (System.currentTimeMillis() - start > 15000) break;
            }
            os.flush();
            os.close();

            // Read response to complete the request
            int code = c.getResponseCode();
            long ms = System.currentTimeMillis() - start;
            c.disconnect();

            if (ms > 0 && offset > 0) {
                double mbps = (offset * 8.0) / (ms / 1000.0) / 1_000_000.0;
                return String.format(Locale.US, "%.1f Mbps", mbps);
            }
        } catch (Exception e) {
            // Upload test is optional, don't fail hard
        }
        return "—";
    }

    private boolean isOnWifi() {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) { return false; }
    }

    @Override
    public String compact() {
        return "🏎↓" + dlResult;
    }

    @Override
    public String detail() {
        long ago = lastTestTime > 0 ? (System.currentTimeMillis() - lastTestTime) / 60000 : -1;
        String agoStr = ago < 0 ? "" : ago < 1 ? " (just now)" : " (" + ago + "m ago)";
        String ping = Prefs.getBool(ctx, "spd_show_ping", true) ? " • Ping: " + pingResult : "";
        return "🏎 DL: " + dlResult + " • UL: " + ulResult + ping + agoStr +
                "\n   Tests today: " + testsToday;
    }

    @Override
    public LinkedHashMap<String, String> dataPoints() {
        LinkedHashMap<String, String> d = new LinkedHashMap<>();
        d.put("speedtest.download", dlResult);
        d.put("speedtest.upload", ulResult);
        d.put("speedtest.ping", pingResult);
        d.put("speedtest.tests_today", String.valueOf(testsToday));
        return d;
    }

    @Override public void checkAlerts(Context ctx) { }
}

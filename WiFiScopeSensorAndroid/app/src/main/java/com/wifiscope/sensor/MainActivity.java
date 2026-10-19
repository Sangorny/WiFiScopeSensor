
package com.wifiscope.sensor;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQUEST_PERMISSIONS = 1001;
    private static final int PORT = 8765;

    private WifiManager wifiManager;
    private TextView txtStatus;
    private TextView txtEndpoint;
    private TextView txtNetworks;
    private Button btnServer;

    private volatile List<ScanResult> lastResults = new ArrayList<>();
    private volatile boolean serverRunning = false;
    private ServerSocket serverSocket;
    private Thread serverThread;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            loadScanResults(updated);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.wifiscope.sensor.R.layout.activity_main);

        txtStatus = findViewById(R.id.txtStatus);
        txtEndpoint = findViewById(R.id.txtEndpoint);
        txtNetworks = findViewById(R.id.txtNetworks);
        btnServer = findViewById(R.id.btnServer);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        findViewById(R.id.btnPermissions).setOnClickListener(v -> requestNeededPermissions());
        findViewById(R.id.btnScan).setOnClickListener(v -> startWifiScan());
        btnServer.setOnClickListener(v -> {
            if (serverRunning) {
                stopServer();
            } else {
                startServer();
            }
        });

        registerReceiver(
                scanReceiver,
                new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        );

        updateEndpoint();
        updateStatus();
    }

    private void requestNeededPermissions() {
        ArrayList<String> perms = new ArrayList<>();

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                        != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }

        if (perms.isEmpty()) {
            txtStatus.setText("Permisos concedidos.");
            return;
        }

        requestPermissions(perms.toArray(new String[0]), REQUEST_PERMISSIONS);
    }

    private boolean hasPermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                        != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        return true;
    }

    private void startWifiScan() {
        if (!hasPermissions()) {
            txtStatus.setText("Faltan permisos. Pulsa «Conceder permisos».");
            requestNeededPermissions();
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            txtStatus.setText("Activa el Wi‑Fi del móvil.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                !isLocationEnabled()) {
            txtStatus.setText(
                    "Android exige tener activada la ubicación para devolver resultados del escaneo Wi‑Fi."
            );
            return;
        }

        txtStatus.setText("Escaneando redes Wi‑Fi...");
        boolean accepted = wifiManager.startScan();

        // Newer Android versions may throttle active scans.
        // Always load cached/fresh results as well.
        if (!accepted) {
            loadScanResults(false);
            txtStatus.setText(
                    "Android ha limitado el escaneo activo. Mostrando los últimos resultados disponibles."
            );
        }
    }

    private boolean isLocationEnabled() {
        try {
            android.location.LocationManager lm =
                    (android.location.LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 28) {
                return lm.isLocationEnabled();
            }
            int mode = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.LOCATION_MODE
            );
            return mode != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Exception e) {
            return false;
        }
    }

    private void loadScanResults(boolean updated) {
        if (!hasPermissions()) {
            return;
        }

        try {
            List<ScanResult> results = wifiManager.getScanResults();
            if (results == null) {
                results = new ArrayList<>();
            }

            ArrayList<ScanResult> copy = new ArrayList<>(results);
            Collections.sort(copy, new Comparator<ScanResult>() {
                @Override
                public int compare(ScanResult a, ScanResult b) {
                    return Integer.compare(b.level, a.level);
                }
            });

            lastResults = copy;
            renderResults();
            txtStatus.setText(
                    "Escaneo terminado: " + copy.size() + " BSSID detectados."
            );
        } catch (SecurityException e) {
            txtStatus.setText("Android ha bloqueado el acceso al escaneo: " + e.getMessage());
        }
    }

    private void renderResults() {
        StringBuilder sb = new StringBuilder();

        for (ScanResult r : lastResults) {
            String ssid = "";
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    ssid = r.getWifiSsid() != null ? r.getWifiSsid().toString() : "";
                } catch (Exception ignored) {}
            }
            if (ssid == null || ssid.isEmpty() || "<unknown ssid>".equalsIgnoreCase(ssid)) {
                ssid = r.SSID;
            }
            if (ssid == null || ssid.isEmpty()) {
                ssid = "<oculta>";
            }

            sb.append(ssid).append("\n");
            sb.append("  BSSID: ").append(r.BSSID).append("\n");
            sb.append("  RSSI: ").append(r.level).append(" dBm\n");
            sb.append("  Frecuencia: ").append(r.frequency).append(" MHz\n");
            sb.append("  Canal: ").append(channelFromFrequency(r.frequency)).append("\n\n");
        }

        txtNetworks.setText(sb.toString());
    }

    private int channelFromFrequency(int freq) {
        if (freq == 2484) return 14;
        if (freq >= 2412 && freq <= 2472) return (freq - 2407) / 5;
        if (freq >= 5000 && freq <= 5900) return (freq - 5000) / 5;
        if (freq >= 5955 && freq <= 7115) return (freq - 5950) / 5;
        return 0;
    }

    private JSONObject buildJson() {
        JSONObject root = new JSONObject();
        JSONArray arr = new JSONArray();

        try {
            root.put("app", "WiFiScope Sensor");
            root.put("version", "0.1.0");
            root.put("timestamp", System.currentTimeMillis());
            root.put("deviceIp", getLocalIpAddress());

            for (ScanResult r : lastResults) {
                JSONObject n = new JSONObject();

                String ssid = r.SSID;
                if (ssid == null || ssid.isEmpty()) ssid = "<oculta>";

                n.put("ssid", ssid);
                n.put("bssid", r.BSSID);
                n.put("rssi", r.level);
                n.put("frequency", r.frequency);
                n.put("channel", channelFromFrequency(r.frequency));
                n.put("capabilities", r.capabilities);

                arr.put(n);
            }

            root.put("networks", arr);
        } catch (Exception ignored) {
        }

        return root;
    }

    private void startServer() {
        if (serverRunning) return;

        serverRunning = true;
        btnServer.setText("Detener servidor");

        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);

                runOnUiThread(() -> {
                    updateEndpoint();
                    txtStatus.setText("Servidor activo. WiFiScope puede consultar el móvil.");
                });

                while (serverRunning) {
                    Socket client = serverSocket.accept();
                    handleClient(client);
                }
            } catch (Exception e) {
                if (serverRunning) {
                    runOnUiThread(() ->
                            txtStatus.setText("Error del servidor: " + e.getMessage())
                    );
                }
            } finally {
                serverRunning = false;
                runOnUiThread(() -> btnServer.setText("Iniciar servidor"));
            }
        });

        serverThread.start();
    }

    private void handleClient(Socket client) {
        new Thread(() -> {
            try (
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(client.getInputStream())
                    );
                    BufferedWriter out = new BufferedWriter(
                            new OutputStreamWriter(client.getOutputStream())
                    )
            ) {
                String first = in.readLine();
                String path = "/";

                if (first != null) {
                    String[] parts = first.split(" ");
                    if (parts.length >= 2) path = parts[1];
                }

                String body;
                int status = 200;

                if ("/scan".equals(path) || "/networks".equals(path)) {
                    body = buildJson().toString();
                } else if ("/status".equals(path) || "/".equals(path)) {
                    JSONObject s = new JSONObject();
                    s.put("ok", true);
                    s.put("app", "WiFiScope Sensor");
                    s.put("version", "0.1.0");
                    s.put("networks", lastResults.size());
                    s.put("endpoint", "http://" + getLocalIpAddress() + ":" + PORT + "/scan");
                    body = s.toString();
                } else {
                    status = 404;
                    body = "{\"error\":\"not found\"}";
                }

                out.write("HTTP/1.1 " + status + (status == 200 ? " OK" : " Not Found") + "\r\n");
                out.write("Content-Type: application/json; charset=utf-8\r\n");
                out.write("Access-Control-Allow-Origin: *\r\n");
                out.write("Connection: close\r\n");
                out.write("Content-Length: " + body.getBytes("UTF-8").length + "\r\n");
                out.write("\r\n");
                out.write(body);
                out.flush();

            } catch (Exception ignored) {
            } finally {
                try { client.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    private void stopServer() {
        serverRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {}
        btnServer.setText("Iniciar servidor");
        txtStatus.setText("Servidor detenido.");
    }

    private void updateEndpoint() {
        String ip = getLocalIpAddress();
        txtEndpoint.setText(
                "API: http://" + ip + ":" + PORT + "/scan"
        );
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();

            for (NetworkInterface nif : Collections.list(ifaces)) {
                if (!nif.isUp() || nif.isLoopback()) continue;

                for (java.net.InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String name = nif.getName().toLowerCase();
                        if (name.contains("wlan") || name.contains("wifi")) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }

            // Fallback: first active IPv4
            ifaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface nif : Collections.list(ifaces)) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (java.net.InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}

        return "0.0.0.0";
    }

    private void updateStatus() {
        if (!hasPermissions()) {
            txtStatus.setText("Concede permisos y activa la ubicación para escanear Wi‑Fi.");
        } else if (!wifiManager.isWifiEnabled()) {
            txtStatus.setText("Activa el Wi‑Fi del móvil.");
        } else {
            txtStatus.setText("Sensor preparado.");
        }
    }

    @Override
    protected void onDestroy() {
        stopServer();
        try {
            unregisterReceiver(scanReceiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}

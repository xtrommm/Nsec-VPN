package com.nsec.vpn.speedtest;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/** Signature-protected worker: every connection is bound to a captured VPN Network. */
public final class SpeedTestService extends Service {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Session active;
    private final Messenger endpoint = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        handleRequest(message); return true;
    }));
    private void handleRequest(Message message) {
            String[] packages = getPackageManager().getPackagesForUid(message.sendingUid);
            boolean trusted = false;
            if (packages != null) for (String pkg : packages) {
                if ("com.nsec.vpn".equals(pkg) && getPackageManager().checkSignatures(
                        message.sendingUid, android.os.Process.myUid()) == PackageManager.SIGNATURE_MATCH) trusted = true;
            }
            if (!trusted) return;
            if (message.what == 2) { if (active != null) active.cancel(); return; }
            if (message.what != 1 || message.replyTo == null) return;
            if (active != null) active.cancel();
            Session session = new Session(message.replyTo, message.getData().getString("expectedIp", ""));
            active = session;
            worker.execute(session);
    }
    @Override public IBinder onBind(Intent intent) { return endpoint.getBinder(); }
    @Override public boolean onUnbind(Intent intent) { if (active != null) active.cancel(); return false; }
    @Override public void onDestroy() {
        if (active != null) active.cancel();
        worker.shutdownNow(); super.onDestroy();
    }

    private final class Session implements Runnable {
        private final Messenger reply;
        private final String expectedIp;
        private volatile boolean cancelled;
        private volatile HttpsURLConnection connection;
        private Network vpn;
        private long deadline;
        private double ping, jitter, download, upload;
        Session(Messenger reply, String expectedIp) { this.reply = reply; this.expectedIp = expectedIp; }
        void cancel() { cancelled = true; HttpsURLConnection c = connection; if (c != null) c.disconnect(); }
        void check() {
            if (cancelled) throw new IllegalStateException("Test cancelled or VPN network changed.");
            if (SystemClock.elapsedRealtime() >= deadline) throw new IllegalStateException("Speed test timed out.");
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            NetworkCapabilities caps = cm.getNetworkCapabilities(vpn);
            if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                throw new IllegalStateException("The VPN network was lost. Reconnect and retry.");
        }
        HttpsURLConnection open(String suffix) throws Exception {
            check();
            HttpsURLConnection c = (HttpsURLConnection) vpn.openConnection(URI.create("https://speed.cloudflare.com" + suffix).toURL());
            connection = c;
            c.setConnectTimeout(5000); c.setReadTimeout(5000); c.setInstanceFollowRedirects(false);
            c.setUseCaches(false); c.setRequestProperty("Accept-Encoding", "identity");
            return c;
        }
        void status(HttpsURLConnection c) throws Exception {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("Speed provider returned HTTP " + code + ". Try again later.");
        }
        byte[] smallGet(String suffix, int limit) throws Exception {
            HttpsURLConnection c = open(suffix);
            try {
                status(c);
                try (InputStream input = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024]; int n;
                    while ((n = input.read(buffer)) != -1) {
                        check(); if (out.size() + n > limit) throw new IllegalStateException("Unexpected provider response.");
                        out.write(buffer, 0, n);
                    }
                    return out.toByteArray();
                }
            } finally { c.disconnect(); connection = null; }
        }
        void emit(String phase, double speed, String error) {
            Message m = Message.obtain(null, error == null ? ("complete".equals(phase) ? 4 : 3) : 5);
            Bundle b = new Bundle(); b.putString("phase", phase); b.putDouble("speed", speed);
            b.putDouble("ping", ping); b.putDouble("jitter", jitter); b.putDouble("download", download); b.putDouble("upload", upload);
            if (error != null) b.putString("error", error);
            m.setData(b);
            try { reply.send(m); } catch (RemoteException ignored) { cancel(); }
        }
        @Override public void run() {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
                @Override public void onLost(Network network) { if (network.equals(vpn)) { emit("error", 0, "VPN network lost. Reconnect and retry."); cancel(); } }
            };
            boolean registered = false;
            deadline = SystemClock.elapsedRealtime() + 60000;
            try {
                vpn = cm.getActiveNetwork();
                if (vpn == null) throw new IllegalStateException("The helper is not included in the VPN. Update split tunneling and reconnect.");
                check();
                cm.registerNetworkCallback(new NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN).build(), callback);
                registered = true;
                emit("Verifying VPN exit", 0, null);
                if (!expectedIp.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalStateException("A verified node public IP is required.");
                String trace = new String(smallGet("/cdn-cgi/trace", 16384), StandardCharsets.UTF_8);
                String observed = "";
                for (String line : trace.split("\n")) if (line.startsWith("ip=")) observed = line.substring(3).trim();
                if (!expectedIp.equals(observed)) throw new IllegalStateException("The test did not exit from the connected node. No speed result was saved.");
                emit("HTTP latency", 0, null);
                List<Double> samples = new ArrayList<>();
                smallGet("/__down?bytes=0", 1024); // Warm connection; not included in latency samples.
                for (int i = 0; i < 5; i++) {
                    long started = System.nanoTime(); smallGet("/__down?bytes=0", 1024);
                    samples.add((System.nanoTime() - started) / 1_000_000.0);
                }
                ping = SpeedMetrics.median(samples); jitter = SpeedMetrics.jitter(samples);
                download = download(); upload = upload(); check(); emit("complete", upload, null);
            } catch (Exception error) {
                if (!cancelled) {
                    String detail = error instanceof IllegalStateException ? error.getMessage() : "Speed provider could not be reached through the VPN. Try again.";
                    emit("error", 0, detail);
                }
            } finally {
                cancel(); if (registered) cm.unregisterNetworkCallback(callback);
            }
        }
        double download() throws Exception {
            long start = System.nanoTime(), bytes = 0, last = start;
            while (System.nanoTime() - start < 8_000_000_000L && bytes < 128L * 1024 * 1024) {
                HttpsURLConnection c = open("/__down?bytes=8388608");
                try {
                    status(c);
                    try (InputStream in = c.getInputStream()) {
                        byte[] buffer = new byte[32768]; int n;
                        while ((n = in.read(buffer)) != -1) {
                            check(); bytes += n; long now = System.nanoTime();
                            if (now - last > 250_000_000L) { emit("Download", SpeedMetrics.mbps(bytes, now - start), null); last = now; }
                            if (now - start >= 8_000_000_000L || bytes >= 128L * 1024 * 1024) break;
                        }
                    }
                } finally { c.disconnect(); connection = null; }
            }
            if (bytes == 0) throw new IllegalStateException("The provider returned no download payload.");
            return SpeedMetrics.mbps(bytes, System.nanoTime() - start);
        }
        double upload() throws Exception {
            long start = System.nanoTime(), acknowledged = 0;
            byte[] payload = new byte[4 * 1024 * 1024]; new java.security.SecureRandom().nextBytes(payload);
            int chunk = 262144;
            while (System.nanoTime() - start < 8_000_000_000L && acknowledged < 64L * 1024 * 1024) {
                chunk = (int) Math.min(chunk, 64L * 1024 * 1024 - acknowledged);
                HttpsURLConnection c = open("/__up");
                try {
                    c.setRequestMethod("POST"); c.setDoOutput(true); c.setFixedLengthStreamingMode(chunk);
                    c.setRequestProperty("Content-Type", "application/octet-stream");
                    try (OutputStream out = c.getOutputStream()) { out.write(payload, 0, chunk); }
                    check(); status(c);
                    try (InputStream in = c.getInputStream()) {
                        byte[] buffer = new byte[1024]; int total = 0, n;
                        while ((n = in.read(buffer)) != -1) { check(); total += n; if (total > 65536) throw new IllegalStateException("Unexpected upload acknowledgement."); }
                    }
                    acknowledged += chunk;
                    if (chunk < payload.length) chunk = Math.min(payload.length, chunk * 2);
                    emit("Upload", SpeedMetrics.mbps(acknowledged, System.nanoTime() - start), null);
                } finally { c.disconnect(); connection = null; }
            }
            if (acknowledged == 0) throw new IllegalStateException("No upload was acknowledged.");
            return SpeedMetrics.mbps(acknowledged, System.nanoTime() - start);
        }
    }
}

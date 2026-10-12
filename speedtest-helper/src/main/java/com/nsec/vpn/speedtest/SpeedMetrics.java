package com.nsec.vpn.speedtest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Values derived only from measured payloads and monotonic durations. */
public final class SpeedMetrics {
    private SpeedMetrics() {}
    public static double mbps(long bytes, long nanos) {
        if (bytes < 0 || nanos <= 0) throw new IllegalArgumentException("Invalid measurement");
        return bytes * 8000.0 / nanos;
    }
    public static double median(List<Double> samples) {
        if (samples.isEmpty()) throw new IllegalArgumentException("No latency samples");
        List<Double> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 0 ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2 : sorted.get(n / 2);
    }
    public static double jitter(List<Double> samples) {
        if (samples.size() < 2) throw new IllegalArgumentException("Insufficient latency samples");
        double total = 0;
        for (int i = 1; i < samples.size(); i++) total += Math.abs(samples.get(i) - samples.get(i - 1));
        return total / (samples.size() - 1);
    }
}

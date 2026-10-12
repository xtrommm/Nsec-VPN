package com.nsec.vpn.speedtest;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
public class SpeedMetricsTest {
    @Test public void usesMeasuredPayloadAndElapsedTime() {
        assertEquals(8.0, SpeedMetrics.mbps(1000000, 1000000000L), 0.00001);
        assertEquals(4.0, SpeedMetrics.mbps(1000000, 2000000000L), 0.00001);
        assertEquals(0.0, SpeedMetrics.mbps(0, 1000000000L), 0.00001);
    }
    @Test public void measuresMedianAndSuccessiveJitter() {
        assertEquals(40.0, SpeedMetrics.median(Arrays.asList(30.0, 60.0, 40.0)), 0.00001);
        assertEquals(45.0, SpeedMetrics.median(Arrays.asList(30.0, 60.0)), 0.00001);
        assertEquals(15.0, SpeedMetrics.jitter(Arrays.asList(30.0, 40.0, 60.0)), 0.00001);
    }
    @Test(expected = IllegalArgumentException.class) public void rejectsInvalidClock() { SpeedMetrics.mbps(100, 0); }
    @Test(expected = IllegalArgumentException.class) public void rejectsInsufficientSamples() { SpeedMetrics.jitter(Arrays.asList(10.0)); }
}

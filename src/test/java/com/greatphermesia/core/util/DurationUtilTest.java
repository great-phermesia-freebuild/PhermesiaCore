package com.greatphermesia.core.util;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import org.junit.Test;

public class DurationUtilTest {

    @Test
    public void returnsZeroForNegativeDuration() {
        assertEquals("0h 0m", DurationUtil.hoursMinutes(Duration.ofMinutes(-5)));
    }

    @Test
    public void returnsZeroForZeroDuration() {
        assertEquals("0h 0m", DurationUtil.hoursMinutes(Duration.ZERO));
    }

    @Test
    public void formatsHoursAndMinutesForPositiveDuration() {
        assertEquals("2h 30m", DurationUtil.hoursMinutes(Duration.ofMinutes(150)));
    }

    @Test
    public void preservesLargeHourValues() {
        assertEquals("26h 5m", DurationUtil.hoursMinutes(Duration.ofMinutes(1565)));
    }
}

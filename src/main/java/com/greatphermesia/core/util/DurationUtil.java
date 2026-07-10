package com.greatphermesia.core.util;

import java.time.Duration;

public final class DurationUtil {

    private DurationUtil() {
    }

    public static String hoursMinutes(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return "0h 0m";
        }
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        return hours + "h " + minutes + "m";
    }
}

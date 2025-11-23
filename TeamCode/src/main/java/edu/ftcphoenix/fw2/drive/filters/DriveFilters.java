package edu.ftcphoenix.fw2.drive.filters;

import java.util.function.BiFunction;

import edu.ftcphoenix.fw2.core.FrameClock;
import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.drive.DriveSource;
import edu.ftcphoenix.fw2.filters.Filter;

/**
 * Utilities to adapt Filter<DriveSignal> to DriveSource while preserving type.
 * Internal helper used by DriveSource's default methods.
 */
public final class DriveFilters {
    private DriveFilters() {
    }

    public static DriveSource filtered(DriveSource upstream, Filter<DriveSignal> filter) {
        return new DriveSource() {
            @Override
            public DriveSignal get(FrameClock clock) {
                DriveSignal raw = upstream.get(clock);
                return (filter != null) ? filter.apply(raw, clock.dtSec()) : raw;
            }
        };
    }

    public static DriveSource map(DriveSource upstream, BiFunction<DriveSignal, Double, DriveSignal> f) {
        return clock -> f.apply(upstream.get(clock), clock.dtSec());
    }
}

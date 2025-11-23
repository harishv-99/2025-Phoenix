package edu.ftcphoenix.fw2.drive;

import java.util.function.BiFunction;

import edu.ftcphoenix.fw2.core.FrameClock;
import edu.ftcphoenix.fw2.core.Source;
import edu.ftcphoenix.fw2.filters.Filter;

/**
 * Produces a {@link DriveSignal} once per loop.
 *
 * <p>Extends {@link Source} and adds ergonomics:
 * <ul>
 *   <li>Covariant {@link #filtered(Filter)} keeps {@code DriveSource} for fluent chains.</li>
 *   <li>{@link #transform(BiFunction)} for inline DriveSignal→DriveSignal tweaks (may use dt).</li>
 * </ul>
 * Map to other types with {@link Source#mapped(java.util.function.BiFunction)}.</p>
 */
public interface DriveSource extends Source<DriveSignal> {

    /** Covariant filter application (keeps DriveSource). */
    default DriveSource filtered(Filter<DriveSignal> filter) {
        if (filter == null) return this;
        return new DriveSource() {
            @Override public DriveSignal get(FrameClock clock) {
                return filter.apply(DriveSource.this.get(clock), clock.dtSec());
            }
        };
    }

    /** Inline DriveSignal→DriveSignal mapper (keeps DriveSource). */
    default DriveSource transform(BiFunction<DriveSignal, Double, DriveSignal> fn) {
        return new DriveSource() {
            @Override public DriveSignal get(FrameClock clock) {
                return fn.apply(DriveSource.this.get(clock), clock.dtSec());
            }
        };
    }
}

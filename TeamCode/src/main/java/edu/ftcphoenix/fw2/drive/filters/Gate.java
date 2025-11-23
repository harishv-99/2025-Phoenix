package edu.ftcphoenix.fw2.drive.filters;

import java.util.function.BooleanSupplier;

import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.filters.Filter;

/**
 * Gates a DriveSignal: when disabled, outputs ZERO; upstream still runs.
 * <p>
 * Use when:
 * - You want to mask a branch after it computed (keep upstream "hot"),
 * or apply a late-stage on/off after mixing.
 */
public final class Gate implements Filter<DriveSignal> {
    private final BooleanSupplier enabled;

    public Gate(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    @Override
    public DriveSignal apply(DriveSignal s, double dt) {
        return enabled.getAsBoolean() ? s : DriveSignal.ZERO;
    }
}

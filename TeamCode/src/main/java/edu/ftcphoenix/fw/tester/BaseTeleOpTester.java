package edu.ftcphoenix.fw.tester;

import edu.ftcphoenix.fw.input.Gamepads;
import edu.ftcphoenix.fw.input.binding.Bindings;

/**
 * Convenience base class for Phoenix testers.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link TesterContext} via {@link #ctx}</li>
 *   <li>{@link Gamepads} wrapper via {@link #gamepads}</li>
 *   <li>{@link Bindings} via {@link #bindings}</li>
 *   <li>Automatic input/binding updates each {@code initLoop()} and {@code loop()}</li>
 * </ul>
 *
 * <p>Override:
 * <ul>
 *   <li>{@link #onInit()} for one-time setup</li>
 *   <li>{@link #onInitLoop(double)} for INIT-phase menus/selections</li>
 *   <li>{@link #onLoop(double)} for RUN-phase logic</li>
 *   <li>{@link #onStart()} / {@link #onStop()} optionally</li>
 * </ul>
 */
public abstract class BaseTeleOpTester implements TeleOpTester {

    protected TesterContext ctx;
    protected Gamepads gamepads;
    protected final Bindings bindings = new Bindings();

    @Override
    public final void init(TesterContext ctx) {
        this.ctx = ctx;
        this.gamepads = Gamepads.create(ctx.gamepad1, ctx.gamepad2);
        onInit();
    }

    @Override
    public final void initLoop(double dtSec) {
        // Inputs + bindings first (consistent across all testers).
        gamepads.update(dtSec);
        bindings.update(dtSec);

        onInitLoop(dtSec);
    }

    @Override
    public final void start() {
        onStart();
    }

    @Override
    public final void loop(double dtSec) {
        // Inputs + bindings first (consistent across all testers).
        gamepads.update(dtSec);
        bindings.update(dtSec);

        onLoop(dtSec);
    }

    @Override
    public final void stop() {
        onStop();
    }

    /**
     * Override to set up hardware, bindings, and internal state.
     */
    protected void onInit() {
    }

    /**
     * Override to implement INIT-phase behavior (selection menus, camera selection, etc).
     *
     * <p>Avoid commanding actuators here; keep it to UI and setup.</p>
     */
    protected void onInitLoop(double dtSec) {
    }

    /**
     * Override to implement RUN-phase tester behavior.
     */
    protected abstract void onLoop(double dtSec);

    /**
     * Optional hook for OpMode start.
     */
    protected void onStart() {
    }

    /**
     * Optional hook for OpMode stop.
     */
    protected void onStop() {
    }

    // ---------------------------------------------------------------------------------------------
    // Telemetry helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Convenience: clear telemetry and draw a consistent header.
     */
    protected final void telemHeader(String title) {
        ctx.telemetry.clearAll();
        ctx.telemetry.addLine("=== " + title + " ===");
    }

    /**
     * Convenience: small control hint line.
     */
    protected final void telemHint(String hint) {
        ctx.telemetry.addLine(hint);
    }

    /**
     * Convenience: always remember to update.
     */
    protected final void telemUpdate() {
        ctx.telemetry.update();
    }
}

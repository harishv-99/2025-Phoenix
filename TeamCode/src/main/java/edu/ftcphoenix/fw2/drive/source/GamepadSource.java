package edu.ftcphoenix.fw2.drive.source;

import edu.ftcphoenix.fw2.core.FrameClock;
import edu.ftcphoenix.fw2.drive.DriveSignal;
import edu.ftcphoenix.fw2.drive.DriveSource;
import edu.ftcphoenix.fw2.gamepad.GamepadInputs;

/**
 * GamepadSource — maps named gamepad channels to a raw, robot-centric {@link DriveSignal}.
 *
 * <p><b>What it does:</b></p>
 * <ul>
 *   <li>Reads three named input channels from a {@link GamepadInputs} instance.</li>
 *   <li>Returns a {@link DriveSignal} with components:
 *     <ul>
 *       <li><b>lateral</b>  ( +left )</li>
 *       <li><b>axial</b>    ( +forward )</li>
 *       <li><b>omega</b>    ( +CCW rotation )</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>What it does <i>not</i> do:</b></p>
 * <ul>
 *   <li>No deadband, expo, slew, or scaling. Keep input shaping in your pipeline
 *       (e.g., {@code DriveAxesFilter} + scalar filters, or via {@code DriveGraph}).</li>
 *   <li>No field-centric transform; apply that upstream if needed.</li>
 * </ul>
 *
 * <p><b>When to use:</b></p>
 * <ul>
 *   <li>As the <i>raw driver input</i> source in TeleOp before any modifiers/filters.</li>
 *   <li>Anywhere you want a clean separation between input reading (this class)
 *       and signal shaping (filters/modifiers).</li>
 * </ul>
 *
 * <p><b>Naming conventions:</b></p>
 * <ul>
 *   <li>Channel names (e.g., {@code "left_stick_x"}) must match your {@link GamepadInputs}
 *       configuration. Typically:
 *       <ul>
 *         <li>{@code nameLat} → left/right stick X (strafe), +left</li>
 *         <li>{@code nameAx}  → left stick Y (forward/back), +forward</li>
 *         <li>{@code nameOm}  → right stick X (turn), +CCW</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * DriveSource driver = new GamepadSource(
 *     new GamepadInputs(gamepad1),
 *     "left_stick_x",   // lateral (+left)
 *     "left_stick_y",   // axial   (+forward)
 *     "right_stick_x"   // omega   (+ccw)
 * );
 *
 * // Later in your loop (with a FrameClock):
 * DriveSignal raw = driver.get(clock); // unshaped input
 * }</pre>
 */
public final class GamepadSource implements DriveSource {
    private final GamepadInputs gpIn;
    private final String nameLat, nameAx, nameOm;

    /**
     * Create a gamepad-backed {@link DriveSource}.
     *
     * @param gpIn    bound {@link GamepadInputs} instance (reads live values).
     * @param nameLat channel name for lateral (+left) input.
     * @param nameAx  channel name for axial   (+forward) input.
     * @param nameOm  channel name for omega   (+CCW) input.
     */
    public GamepadSource(GamepadInputs gpIn, String nameLat, String nameAx, String nameOm) {
        this.gpIn = gpIn;
        this.nameLat = nameLat;
        this.nameAx = nameAx;
        this.nameOm = nameOm;
    }

    /**
     * Get the current raw, robot-centric drive signal.
     *
     * <p>The provided {@link FrameClock} is accepted to conform to the
     * {@link DriveSource} contract (and to allow future time-aware inputs),
     * but this implementation does not use {@code dt}.</p>
     *
     * @param clock frame timing (unused here but required by interface).
     * @return raw {@link DriveSignal} (no deadband/expo/slew/scale applied).
     */
    @Override
    public DriveSignal get(FrameClock clock) {
        final double lat = gpIn.getInterval(nameLat).getValue(); // +left
        final double ax = gpIn.getInterval(nameAx).getValue();  // +forward
        final double om = gpIn.getInterval(nameOm).getValue();  // +CCW
        return new DriveSignal(lat, ax, om);
    }
}

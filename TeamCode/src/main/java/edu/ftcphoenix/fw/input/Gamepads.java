package edu.ftcphoenix.fw.input;

import com.qualcomm.robotcore.hardware.Gamepad;

/**
 * Pair of FTC gamepads with optional update hook.
 * Edge detection is not done here; see {@link edu.ftcphoenix.fw.input.binding.Bindings}.
 */
public final class Gamepads {
    private final GamepadDevice p1;
    private final GamepadDevice p2;

    private Gamepads(GamepadDevice p1, GamepadDevice p2) {
        this.p1 = p1; this.p2 = p2;
    }

    public static Gamepads create(Gamepad gp1, Gamepad gp2) {
        return new Gamepads(new GamepadDevice(gp1), new GamepadDevice(gp2));
    }

    public GamepadDevice p1() { return p1; }
    public GamepadDevice p2() { return p2; }

    /** Optional tick if you later want to integrate filters. Currently a no-op. */
    public void update(double dtSec) { /* no-op */ }
}

package edu.ftcphoenix.fw.tester;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

/**
 * Shared runtime context for Phoenix testers.
 *
 * <p>This is intentionally small and FTC-centric: it provides access to the FTC SDK
 * objects that testers commonly need (hardware map, telemetry, and gamepads).</p>
 *
 * <p>Testers should treat this as read-only (the fields are final).</p>
 */
public final class TesterContext {

    /** FTC hardware map. */
    public final HardwareMap hw;

    /** FTC telemetry (typically the Driver Hub telemetry). */
    public final Telemetry telemetry;

    /** FTC gamepad 1. */
    public final Gamepad gamepad1;

    /** FTC gamepad 2. */
    public final Gamepad gamepad2;

    public TesterContext(HardwareMap hw, Telemetry telemetry, Gamepad gamepad1, Gamepad gamepad2) {
        this.hw = hw;
        this.telemetry = telemetry;
        this.gamepad1 = gamepad1;
        this.gamepad2 = gamepad2;
    }
}

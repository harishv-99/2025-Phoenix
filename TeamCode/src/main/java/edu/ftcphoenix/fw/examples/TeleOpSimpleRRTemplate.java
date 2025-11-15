package edu.ftcphoenix.fw.examples;

import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;

/** Placeholder example showing where RoadRunner integration would go. */
@Disabled
@TeleOp(name="FW Example: Simple + RoadRunner (Template)")
public final class TeleOpSimpleRRTemplate extends OpMode {
    @Override public void init() { /* instantiate RR drive and wire telemetry here */ }
    @Override public void loop() { /* call RR update and push pose telemetry while still using FW inputs */ }
}

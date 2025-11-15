package edu.ftcphoenix.fw.examples;

//import com.acmerobotics.roadrunner.geometry.Pose2d;
//import com.acmerobotics.roadrunner.trajectory.Trajectory;
//import com.qualcomm.robotcore.eventloop.opmode.OpMode;
//import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
//
//import edu.ftcphoenix.fw.input.DriverKit;
//import edu.ftcphoenix.fw.input.Gamepads;
//import edu.ftcphoenix.fw.input.binding.Bindings;
//import edu.ftcphoenix.fw.util.LoopClock;
//
//// TODO: adjust this import to wherever your RR drive class lives.
//import org.firstinspires.ftc.teamcode.drive.SampleMecanumDrive;
//
///**
// * Example showing how to combine Phoenix FW input plumbing with a RoadRunner
// * drive for mecanum.
// *
// * <h2>Behavior</h2>
// * <ul>
// *   <li>Left stick X: strafe left/right (manual drive).</li>
// *   <li>Left stick Y: forward/back (manual drive).</li>
// *   <li>Right stick X: rotate (manual drive).</li>
// *   <li>Button A (p1): build and follow a 24" forward trajectory from
// *       the <em>current pose</em> using RoadRunner.</li>
// * </ul>
// *
// * <h2>Design notes</h2>
// * <ul>
// *   <li>Phoenix FW handles input: {@link Gamepads}, {@link DriverKit},
// *       {@link Bindings}.</li>
// *   <li>RoadRunner handles drivetrain kinematics, power, and trajectories.</li>
// *   <li>Manual stick drive is disabled while a trajectory is in progress
// *       (i.e., while {@code rrDrive.isBusy()} is true).</li>
// * </ul>
// */
//@TeleOp(name = "FW Example: Mecanum + RoadRunner", group = "Phoenix")
//public final class TeleOpMecanumRoadRunner extends OpMode {
//
//    // Input plumbing
//    private Gamepads gamepads;
//    private DriverKit driverKit;
//    private Bindings bindings;
//
//    // RoadRunner drive
//    private SampleMecanumDrive rrDrive;
//
//    // Example trajectory (rebuilt on each A press)
//    private Trajectory exampleTraj;
//
//    // Loop timing
//    private final LoopClock clock = new LoopClock();
//
//    @Override
//    public void init() {
//        // 1) Inputs
//        gamepads = Gamepads.create(gamepad1, gamepad2);
//        driverKit = DriverKit.of(gamepads);
//        bindings = new Bindings();
//
//        // 2) RoadRunner drive
//        rrDrive = new SampleMecanumDrive(hardwareMap);
//        rrDrive.setPoseEstimate(new Pose2d(0.0, 0.0, 0.0));
//
//        // 3) Bindings
//
//        // Press A to build and follow a simple 24" forward trajectory
//        bindings.onPress(
//                driverKit.p1().buttonA(),
//                new Runnable() {
//                    @Override
//                    public void run() {
//                        // Build from the CURRENT pose so you can press A multiple times.
//                        Pose2d startPose = rrDrive.getPoseEstimate();
//                        exampleTraj = rrDrive.trajectoryBuilder(startPose)
//                                .forward(24.0)
//                                .build();
//
//                        // Start following asynchronously
//                        rrDrive.followTrajectoryAsync(exampleTraj);
//                    }
//                }
//        );
//
//        telemetry.addLine("FW RR TeleOp: init complete");
//        telemetry.update();
//    }
//
//    @Override
//    public void start() {
//        clock.reset(getRuntime());
//    }
//
//    @Override
//    public void loop() {
//        // 1) Loop timing
//        clock.update(getRuntime());
//        double dtSec = clock.dtSec();
//
//        // 2) Input + bindings
//        gamepads.update(dtSec);
//        bindings.update(dtSec);
//
//        // 3) Manual drive only when not following a trajectory
//        if (!rrDrive.isBusy()) {
//            double lx = driverKit.p1().leftX().get();
//            double ly = driverKit.p1().leftY().get();
//            double rx = driverKit.p1().rightX().get();
//
//            // Robot-centric drive command. If your SampleMecanumDrive uses
//            // a different API (e.g., setDrivePower), adjust this call.
//            rrDrive.setWeightedDrivePower(
//                    new Pose2d(
//                            ly,   // forward
//                            lx,   // strafe
//                            rx    // turn
//                    )
//            );
//        } else {
//            // Optionally, ensure no manual command is sent while busy.
//            // rrDrive.setWeightedDrivePower(new Pose2d(0, 0, 0));
//        }
//
//        // 4) RoadRunner update (pose + followers)
//        rrDrive.update();
//
//        // 5) Telemetry
//        Pose2d pose = rrDrive.getPoseEstimate();
//        telemetry.addLine("RR Pose")
//                .addData("x", pose.getX())
//                .addData("y", pose.getY())
//                .addData("heading", pose.getHeading());
//        telemetry.addData("busy", rrDrive.isBusy());
//        telemetry.update();
//    }
//
//    @Override
//    public void stop() {
//        // Stop any motion cleanly
//        rrDrive.setWeightedDrivePower(new Pose2d(0.0, 0.0, 0.0));
//        rrDrive.update();
//    }
//}

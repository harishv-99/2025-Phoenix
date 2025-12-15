package edu.ftcphoenix.fw.adapters.ftc;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;

import edu.ftcphoenix.fw.tester.TeleOpTester;
import edu.ftcphoenix.fw.tester.TesterContext;
import edu.ftcphoenix.fw.util.LoopClock;

/**
 * FTC SDK {@link OpMode} base class for running a Phoenix {@link TeleOpTester}.
 *
 * <p>This lets testers live outside TeamCode (e.g., in {@code edu.ftcphoenix.robots...})
 * while TeamCode provides only a tiny wrapper OpMode that returns a tester (usually a
 * {@link edu.ftcphoenix.fw.tester.TesterSuite}).</p>
 *
 * <h2>Lifecycle mapping</h2>
 * <ul>
 *   <li>{@link #init()} → {@link TeleOpTester#init(TesterContext)}</li>
 *   <li>{@link #init_loop()} → {@link TeleOpTester#initLoop(double)}</li>
 *   <li>{@link #start()} → {@link TeleOpTester#start()}</li>
 *   <li>{@link #loop()} → {@link TeleOpTester#loop(double)} with {@code dtSec}</li>
 *   <li>{@link #stop()} → {@link TeleOpTester#stop()}</li>
 * </ul>
 *
 * <p>The {@code init_loop()} hook is especially useful for selection menus (e.g., choose
 * a camera from configured devices) and for doing HardwareMap lookups during INIT.</p>
 */
public abstract class FtcTeleOpTesterOpMode extends OpMode {

    private final LoopClock clock = new LoopClock();

    private TesterContext ctx;
    private TeleOpTester tester;

    /**
     * Return the tester to run. Most commonly this is a {@code TesterSuite} that
     * registers multiple testers for selection from a menu.
     */
    protected abstract TeleOpTester createTester();

    @Override
    public final void init() {
        ctx = new TesterContext(hardwareMap, telemetry, gamepad1, gamepad2);

        tester = createTester();
        if (tester == null) {
            telemetry.addLine("ERROR: createTester() returned null.");
            telemetry.update();
            return;
        }

        tester.init(ctx);

        // Start dt tracking immediately so init_loop() also has stable dt.
        clock.reset(getRuntime());

        telemetry.addLine("Ready: " + tester.name());
        telemetry.update();
    }

    @Override
    public final void init_loop() {
        if (tester == null) return;

        clock.update(getRuntime());
        tester.initLoop(clock.dtSec());
    }

    @Override
    public final void start() {
        if (tester == null) return;

        // Reset dt at transition to RUNNING so loop dt is clean.
        clock.reset(getRuntime());
        tester.start();
    }

    @Override
    public final void loop() {
        if (tester == null) return;

        clock.update(getRuntime());
        tester.loop(clock.dtSec());
    }

    @Override
    public final void stop() {
        if (tester == null) return;
        tester.stop();
    }
}

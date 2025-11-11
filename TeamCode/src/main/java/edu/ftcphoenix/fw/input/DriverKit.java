package edu.ftcphoenix.fw.input;

import edu.ftcphoenix.fw.input.Inputs.Dpad;
import edu.ftcphoenix.fw.input.extras.Combos;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * DriverKit: opinionated, ready-to-use inputs for p1/p2 with sensible defaults.
 *
 * <h2>Purpose</h2>
 * Most FTC robots shape sticks the same way (deadband + expo + optional slew), want LB as a
 * slow-mode long-hold, a simple safety chord (LB + A), PS-style aliases, and an easy way to
 * treat the left stick like a DPAD (with hysteresis). DriverKit packages that “happy path”
 * so your teleop code is tiny and consistent.
 *
 * <h2>Update model</h2>
 * All inputs inside DriverKit register with the same {@link InputRegistry} that your
 * {@link Gamepads} holds. Call {@link Gamepads#update(double)} exactly once per loop.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Gamepads pads = Gamepads.create(gamepad1, gamepad2);
 * DriverKit kit = DriverKit.of(pads); // uses InputDefaults.standard()
 *
 * // Drive:
 * bind.stream(kit.p1().lx(), drive::setLateral);
 * bind.stream(kit.p1().ly(), drive::setAxial);
 * bind.streamScaled(kit.p1().rx(), kit.p1().slowModeScale(0.5), drive::setOmega);
 *
 * // Shooter:
 * bind.onPress(kit.p1().chordLB_A(), shooter::fireOnce);   // safety chord
 * bind.toggle(kit.p1().triangle(), shooter::setEnabled);   // PS alias
 *
 * // Lift with operator (p2):
 * bind.stream(kit.p2().dpadYAxis(), lift::setPower);       // up/down from DPAD-like stick
 * }</pre>
 */
public final class DriverKit {

    private final Gamepads pads;
    private final InputRegistry reg;
    private final InputDefaults defs;

    private final Player p1;
    private final Player p2;

    private DriverKit(Gamepads pads, InputDefaults defs) {
        this.pads = Objects.requireNonNull(pads, "pads");
        this.reg = pads.registry();
        this.defs = Objects.requireNonNull(defs, "defs");
        this.p1 = new Player(reg, pads.p1(), defs);
        this.p2 = new Player(reg, pads.p2(), defs);
    }

    /**
     * Construct a kit using {@link InputDefaults#standard()}.
     */
    public static DriverKit of(Gamepads pads) {
        return new DriverKit(pads, InputDefaults.standard());
    }

    /**
     * Construct a kit with explicit defaults (tune once per robot if desired).
     */
    public static DriverKit of(Gamepads pads, InputDefaults defaults) {
        return new DriverKit(pads, defaults);
    }

    /**
     * Primary driver inputs (gamepad1).
     */
    public Player p1() {
        return p1;
    }

    /**
     * Secondary operator inputs (gamepad2).
     */
    public Player p2() {
        return p2;
    }

    /**
     * Quick sanity telemetry for both players.
     */
    public void addTelemetry(Telemetry t) {
        if (t == null) return;
        t.addLine("DriverKit");
        t.addData("defaults.db", defs.deadband)
                .addData("expo", defs.expo)
                .addData("slew", defs.slewPerSec)
                .addData("hyst", String.format("%.2f/%.2f", defs.hystLow, defs.hystHigh))
                .addData("dblTap", defs.doubleTapWindowSec)
                .addData("longHold", defs.longHoldSec);
        p1.addTelemetry(t, "p1");
        p2.addTelemetry(t, "p2");
    }

    // =====================================================================
    // Player: per-controller ready-to-use inputs
    // =====================================================================

    /**
     * A named set of shaped axes, buttons, gestures, and convenience virtuals for one controller.
     *
     * <p>All getters return cached instances (created once, registered with the registry).
     * You can call the getters as many times as you like without duplicating registrations.</p>
     */
    public static final class Player {
        private final InputRegistry reg;
        private final GamepadDevice dev;
        private final InputDefaults defs;

        // Axes (created lazily on first access and then cached)
        private Axis lx, ly, rx, ry, lt, rt;
        // DPAD derived from left stick (with hysteresis)
        private Dpad dpad;
        // Buttons (RAW)
        private Button a, b, x, y, lb, rb, start, back, lsb, rsb, dUp, dDown, dLeft, dRight;
        // PS aliases
        private Button cross, circle, square, triangle;
        // Common chords/gestures
        private Button chordLB_A;

        Player(InputRegistry reg, GamepadDevice dev, InputDefaults defs) {
            this.reg = Objects.requireNonNull(reg);
            this.dev = Objects.requireNonNull(dev);
            this.defs = Objects.requireNonNull(defs);
        }

        // ---------------------------
        // Shaped axes (deadband + expo + optional slew)
        // ---------------------------

        /**
         * Left X axis, shaped with defaults (deadband+expo+slew).
         */
        public Axis lx() {
            if (lx == null) {
                lx = Inputs.leftX(reg, dev).deadband(defs.deadband).expo(defs.expo).rateLimit(defs.slewPerSec);
            }
            return lx;
        }

        /**
         * Left Y axis, shaped with defaults (deadband+expo).
         */
        public Axis ly() {
            if (ly == null) {
                ly = Inputs.leftY(reg, dev).deadband(defs.deadband).expo(defs.expo);
            }
            return ly;
        }

        /**
         * Right X axis, shaped with defaults (deadband+expo+slew).
         */
        public Axis rx() {
            if (rx == null) {
                rx = Inputs.rightX(reg, dev).deadband(defs.deadband).expo(defs.expo).rateLimit(defs.slewPerSec);
            }
            return rx;
        }

        /**
         * Right Y axis, shaped with defaults (deadband+expo). Rarely used for driving.
         */
        public Axis ry() {
            if (ry == null) {
                ry = Inputs.rightY(reg, dev).deadband(defs.deadband).expo(defs.expo);
            }
            return ry;
        }

        /**
         * Left trigger axis [0..1], raw (no shaping).
         */
        public Axis lt() {
            if (lt == null) lt = Inputs.leftTriggerAxis(reg, dev);
            return lt;
        }

        /**
         * Right trigger axis [0..1], raw (no shaping).
         */
        public Axis rt() {
            if (rt == null) rt = Inputs.rightTriggerAxis(reg, dev);
            return rt;
        }

        // ---------------------------
        // Buttons (RAW) + PS aliases
        // ---------------------------

        public Button a() {
            if (a == null) a = Inputs.a(reg, dev);
            return a;
        }

        public Button b() {
            if (b == null) b = Inputs.b(reg, dev);
            return b;
        }

        public Button x() {
            if (x == null) x = Inputs.x(reg, dev);
            return x;
        }

        public Button y() {
            if (y == null) y = Inputs.y(reg, dev);
            return y;
        }

        /**
         * PS: CROSS ↔ A
         */
        public Button cross() {
            if (cross == null) cross = Inputs.cross(reg, dev);
            return cross;
        }

        /**
         * PS: CIRCLE ↔ B
         */
        public Button circle() {
            if (circle == null) circle = Inputs.circle(reg, dev);
            return circle;
        }

        /**
         * PS: SQUARE ↔ X
         */
        public Button square() {
            if (square == null) square = Inputs.square(reg, dev);
            return square;
        }

        /**
         * PS: TRIANGLE ↔ Y
         */
        public Button triangle() {
            if (triangle == null) triangle = Inputs.triangle(reg, dev);
            return triangle;
        }

        public Button lb() {
            if (lb == null) {
                lb = Inputs.leftBumper(reg, dev);
                lb.configureLongHold(defs.longHoldSec);
            }
            return lb;
        }

        public Button rb() {
            if (rb == null) {
                rb = Inputs.rightBumper(reg, dev);
                rb.configureDoubleTap(defs.doubleTapWindowSec);
            }
            return rb;
        }

        public Button start() {
            if (start == null) start = Inputs.start(reg, dev);
            return start;
        }

        public Button back() {
            if (back == null) back = Inputs.back(reg, dev);
            return back;
        }

        public Button leftStickButton() {
            if (lsb == null) lsb = Inputs.leftStickButton(reg, dev);
            return lsb;
        }

        public Button rightStickButton() {
            if (rsb == null) rsb = Inputs.rightStickButton(reg, dev);
            return rsb;
        }

        public Button dpadUp() {
            if (dUp == null) dUp = Inputs.dpadUp(reg, dev);
            return dUp;
        }

        public Button dpadDown() {
            if (dDown == null) dDown = Inputs.dpadDown(reg, dev);
            return dDown;
        }

        public Button dpadLeft() {
            if (dLeft == null) dLeft = Inputs.dpadLeft(reg, dev);
            return dLeft;
        }

        public Button dpadRight() {
            if (dRight == null) dRight = Inputs.dpadRight(reg, dev);
            return dRight;
        }

        // ---------------------------
        // DPAD-like view of the left stick (with default hysteresis)
        // ---------------------------

        /**
         * A DPAD derived from (lx, ly) with default hysteresis to prevent chatter.
         */
        public Dpad dpad() {
            if (dpad == null) {
                dpad = Inputs.split2DToDpad(reg, lx(), ly(), defs.hystLow, defs.hystHigh);
            }
            return dpad;
        }

        /**
         * Convenience: a single-axis DPAD-style Y control derived from the left stick.
         */
        public Axis dpadYAxis() {
            // Map up->+1, down->-1 using the virtual buttons from dpad()
            Button up = dpad().up, down = dpad().down;
            return Inputs.axisFromButtons(reg, down, up); // [-1..1]
        }

        // ---------------------------
        // Common chords & helpers
        // ---------------------------

        /**
         * Safety chord: LB + A within the default chord window (uses {@link InputDefaults#doubleTapWindowSec}).
         * Latches ON while both held; resets on release. Bind with onPress(...) for actions like firing.
         */
        public Button chordLB_A() {
            if (chordLB_A == null) {
                chordLB_A = Combos.chordSec(reg, defs.doubleTapWindowSec, lb(), a());
            }
            return chordLB_A;
        }

        /**
         * Supplier that returns {@code slowFactor} while LB long-hold is active; otherwise 1.0.
         * Use with {@code Bindings.streamScaled(...)} to scale, e.g., your omega (rotation) axis.
         */
        public Supplier<Double> slowModeScale(double slowFactor) {
            double k = Math.max(0.0, slowFactor);
            return () -> lb().isLongHeld() ? k : 1.0;
        }

        // ---------------------------
        // Telemetry
        // ---------------------------

        private void addTelemetry(Telemetry t, String name) {
            if (t == null) return;
            t.addLine(name);
            lx().addTelemetry(t, name + ".lx");
            ly().addTelemetry(t, name + ".ly");
            rx().addTelemetry(t, name + ".rx");
            t.addData(name + ".LB.longHeld", lb().isLongHeld());
            t.addData(name + ".RB.dblTapEdge", rb().justDoubleTapped());
        }
    }
}

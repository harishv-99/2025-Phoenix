package edu.ftcphoenix.fw.input;

/**
 * High-level driver IO facade over {@link Gamepads} with explicit, consistent names.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Provide readable, stable accessors to one player's inputs.</li>
 *   <li>Hide any {@link GamepadDevice} plumbing from robot code.</li>
 *   <li>Offer helpers to treat triggers/axes as digital buttons.</li>
 * </ul>
 *
 * <p>Canonical API per player:
 * <ul>
 *   <li>Axes: {@code leftX/leftY/rightX/rightY/leftTrigger/rightTrigger}</li>
 *   <li>Buttons: {@code buttonA/buttonB/buttonX/buttonY}</li>
 *   <li>Bumpers: {@code leftBumper/rightBumper}</li>
 *   <li>Stick presses: {@code leftStickButton/rightStickButton}</li>
 *   <li>D-pad: {@code dpadUp/dpadDown/dpadLeft/dpadRight}</li>
 * </ul>
 *
 * <p>There are also a small number of <em>shorthand aliases</em> (e.g. {@code a()},
 * {@code lb()}, {@code rb()}) kept for convenience and migration. New code should
 * prefer the canonical names for clarity.
 */
public final class DriverKit {

    private final Gamepads pads;
    private final Player p1;
    private final Player p2;

    /**
     * Factory method: wrap an existing {@link Gamepads} instance.
     */
    public static DriverKit of(Gamepads pads) {
        if (pads == null) {
            throw new IllegalArgumentException("Gamepads is required");
        }
        return new DriverKit(pads);
    }

    private DriverKit(Gamepads pads) {
        this.pads = pads;
        this.p1 = new Player(pads.p1());
        this.p2 = new Player(pads.p2());
    }

    /**
     * Primary driver (player 1).
     */
    public Player p1() {
        return p1;
    }

    /**
     * Secondary/co-driver (player 2).
     */
    public Player p2() {
        return p2;
    }

    /**
     * Per-player view. Delegates to {@link GamepadDevice} and exposes a
     * canonical, stable API for axes and buttons.
     *
     * <p>Usage pattern in OpModes:
     * <pre>
     * DriverKit dk = DriverKit.of(Gamepads.create(gamepad1, gamepad2));
     *
     * double lateral = dk.p1().leftX().get();
     * double axial   = dk.p1().leftY().get();
     * Button shoot   = dk.p1().buttonX();
     * </pre>
     */
    public static final class Player {
        private final GamepadDevice dev;

        Player(GamepadDevice dev) {
            if (dev == null) {
                throw new IllegalArgumentException("GamepadDevice is required");
            }
            this.dev = dev;
        }

        // ---- Canonical axes ------------------------------------------------

        /**
         * Left stick X axis: strafe left/right, [-1..+1].
         */
        public Axis leftX() {
            return dev.leftX();
        }

        /**
         * Left stick Y axis: forward/back, “up is +”, [-1..+1].
         */
        public Axis leftY() {
            return dev.leftY();
        }

        /**
         * Right stick X axis: turn, [-1..+1].
         */
        public Axis rightX() {
            return dev.rightX();
        }

        /**
         * Right stick Y axis, [-1..+1].
         */
        public Axis rightY() {
            return dev.rightY();
        }

        /**
         * Left trigger axis, [0..1].
         */
        public Axis leftTrigger() {
            return dev.leftTrigger();
        }

        /**
         * Right trigger axis, [0..1].
         */
        public Axis rightTrigger() {
            return dev.rightTrigger();
        }

        // ---- Canonical buttons --------------------------------------------

        /**
         * Gamepad A button.
         */
        public Button buttonA() {
            return dev.buttonA();
        }

        /**
         * Gamepad B button.
         */
        public Button buttonB() {
            return dev.buttonB();
        }

        /**
         * Gamepad X button.
         */
        public Button buttonX() {
            return dev.buttonX();
        }

        /**
         * Gamepad Y button.
         */
        public Button buttonY() {
            return dev.buttonY();
        }

        // Bumpers & stick presses

        /**
         * Left bumper.
         */
        public Button leftBumper() {
            return dev.leftBumper();
        }

        /**
         * Right bumper.
         */
        public Button rightBumper() {
            return dev.rightBumper();
        }

        /**
         * Left stick button (L3).
         */
        public Button leftStickButton() {
            return dev.leftStickButton();
        }

        /**
         * Right stick button (R3).
         */
        public Button rightStickButton() {
            return dev.rightStickButton();
        }

        // D-pad

        public Button dpadUp() {
            return dev.dpadUp();
        }

        public Button dpadDown() {
            return dev.dpadDown();
        }

        public Button dpadLeft() {
            return dev.dpadLeft();
        }

        public Button dpadRight() {
            return dev.dpadRight();
        }

        // ---- Helpers: threshold axes into buttons -------------------------

        /**
         * Treat left trigger as a digital button when it crosses the given threshold
         * (in [0..1]).
         */
        public Button leftTriggerOver(double threshold) {
            return leftTrigger().asButton(threshold);
        }

        /**
         * Treat right trigger as a digital button when it crosses the given threshold
         * (in [0..1]).
         */
        public Button rightTriggerOver(double threshold) {
            return rightTrigger().asButton(threshold);
        }

        /**
         * Threshold any axis to a digital button without exposing registry details.
         */
        public Button axisOver(Axis axis, double threshold) {
            if (axis == null) {
                throw new IllegalArgumentException("Axis is required");
            }
            return axis.asButton(threshold);
        }

        // ---- Shorthand / migration aliases --------------------------------
        //
        // These exist for convenience and to ease migration from previous styles
        // (e.g., using raw gamepad fields or very short names). New code should
        // prefer the canonical names above for maximum clarity.

        public Button a() {
            return buttonA();
        }

        public Button b() {
            return buttonB();
        }

        public Button x() {
            return buttonX();
        }

        public Button y() {
            return buttonY();
        }

        public Button lb() {
            return leftBumper();
        }

        public Button rb() {
            return rightBumper();
        }

        public Axis lt() {
            return leftTrigger();
        }

        public Axis rt() {
            return rightTrigger();
        }
    }
}

package edu.ftcphoenix.fw.gamepad;

import com.qualcomm.robotcore.hardware.Gamepad;

import edu.ftcphoenix.fw.robotbase.periodicrunner.PeriodicRunnable;
import edu.ftcphoenix.fw.robotbase.periodicrunner.PeriodicRunner;

/**
 * Control a gamepad.  This is typically used from the tele-op controller.
 */
public class GamepadController {
    private final Gamepad gamepad;
    private final GamepadInputs gamepadInputs;
    private final PeriodicRunner periodicRunner;

    /**
     * Create a controller for a gamepad.  This also saves any created named-buttons or
     * named-intervals in the {@link GamepadInputs} collection.
     *
     * @param gamepad        The FTC gamepad object to control.
     * @param gamepadInputs  The collection to use to save any new created buttons or intervals.
     * @param periodicRunner The runner object to register any created buttons/triggers.
     */
    public GamepadController(Gamepad gamepad, GamepadInputs gamepadInputs,
                             PeriodicRunner periodicRunner) {
        this.gamepad = gamepad;
        this.gamepadInputs = gamepadInputs;
        this.periodicRunner = periodicRunner;
    }

    /**
     * Create a button from a gamepad button.
     *
     * @param id     The string ID for this button.
     * @param button The gamepad button to use.
     * @return The {@link InputButton} created.
     */
    public InputButton createButton(String id, GamepadKeys.Button button) {
        InputButtonPlain inputButton = new InputButtonPlain(this, button,
                periodicRunner);

        // Add the button to the lookup
        gamepadInputs.addButton(id, inputButton);

        return inputButton;
    }

    /**
     * Create a button from a trigger.  The trigger will be considered to be pressed based
     * on the threshold specified.
     *
     * @param id               The string ID for this button.
     * @param trigger          The trigger on the gamepad to use as the button.
     * @param triggerThreshold A number in the range [0, 1] to use as the threshold to cross
     *                         before this button is considered to be pressed.
     * @return The {@link InputButton} created.
     */
    public InputButton createButton(String id, GamepadKeys.Trigger trigger, double triggerThreshold) {
        InputButtonTrigger inputButton = new InputButtonTrigger(this,
                trigger, triggerThreshold, periodicRunner);

        // Add the button to the lookup
        gamepadInputs.addButton(id, inputButton);

        return inputButton;
    }

    /**
     * Create an interval [0,1] using a button.  Pressing a button would be considered a 1.
     *
     * @param id     The string ID for this interval.
     * @param button The button to use for the interval.
     * @return The {@link InputInterval} created.
     */
    public InputInterval createIntervalUnit(String id, GamepadKeys.Button button) {
        InputIntervalUnitButton inputInterval = new InputIntervalUnitButton(this,
                button);

        // Add the interval to the lookup
        gamepadInputs.addInterval(id, inputInterval);

        return inputInterval;
    }

    /**
     * Create an interval [0,1] using a trigger.
     *
     * @param id      The string ID for this interval.
     * @param trigger The trigger to use for the interval.
     * @return The {@link InputInterval} created.
     */
    public InputInterval createIntervalUnit(String id, GamepadKeys.Trigger trigger) {
        InputIntervalUnitTrigger inputInterval = new InputIntervalUnitTrigger(this,
                trigger);

        // Add the interval to the lookup
        gamepadInputs.addInterval(id, inputInterval);

        return inputInterval;
    }

    /**
     * Create an interval [-1, 1] using two triggers.  The left trigger operates in the
     * range [-1, 0] and the right operates in the range [0, 1].
     *
     * @param id The string ID for this interval.
     * @return The {@link InputInterval} created.
     */
    public InputInterval createIntervalDoubleUnitUsingTriggers(String id) {
        return createIntervalDoubleUnit(
                id,
                GamepadKeys.Trigger.LEFT_TRIGGER,
                GamepadKeys.Trigger.RIGHT_TRIGGER
        );
    }

    /**
     * Create an interval [-1, 1] using two buttons.
     *
     * @param id      The string ID for this interval.
     * @param button1 The first button which, when pressed, makes the value -1.
     * @param button2 The second button which, when pressed, makes the value 1.
     * @return The {@link InputInterval} created.
     */
    public InputInterval createIntervalDoubleUnit(String id,
                                                  GamepadKeys.Button button1,
                                                  GamepadKeys.Button button2) {
        InputIntervalDoubleUnitButton inputInterval = new InputIntervalDoubleUnitButton(
                this,
                button1,
                button2
        );

        // Add the interval to the lookup
        gamepadInputs.addInterval(id, inputInterval);

        return inputInterval;
    }

    /**
     * Create an interval [-1, 1] using two triggers
     *
     * @param id       The string ID for this interval.
     * @param trigger1 The first trigger that operates in the range [-1, 0].
     * @param trigger2 The second trigger that operates in the range [0, 1].
     * @return The {@link InputInterval} created.
     */
    public InputInterval createIntervalDoubleUnit(String id,
                                                  GamepadKeys.Trigger trigger1,
                                                  GamepadKeys.Trigger trigger2) {
        InputIntervalDoubleUnitTrigger inputInterval = new InputIntervalDoubleUnitTrigger(
                this,
                trigger1,
                trigger2
        );

        // Add the interval to the lookup
        gamepadInputs.addInterval(id, inputInterval);

        return inputInterval;
    }

    /**
     * Create an interval [-1, 1] using a stick.
     *
     * @param id    The string ID for this interval.
     * @param stick The stick to use.
     * @return The {@link InputInterval} created.
     */
    public InputInterval createIntervalDoubleUnit(String id,
                                                  GamepadKeys.Stick stick) {
        InputIntervalDoubleUnitStick inputInterval = new InputIntervalDoubleUnitStick(
                this,
                stick
        );

        // Add the interval to the lookup
        gamepadInputs.addInterval(id, inputInterval);

        return inputInterval;
    }


    /**
     * Get the collection where the created inputs are saved.
     *
     * @return The collection of the gamepad inputs that were created.
     */
    public GamepadInputs getGamepadInputs() {
        return gamepadInputs;
    }


    /**
     * Get the value of the button based on the Button enum.
     *
     * @param button The button which has to be looked up.
     * @return The value of the chosen button.
     */
    boolean getValue(GamepadKeys.Button button) {
        switch (button) {
            case Y:     // Same as TRIANGLE
            case TRIANGLE:
                return gamepad.y;

            case X:     // Same as SQUARE
            case SQUARE:
                return gamepad.x;

            case A:     // Same as CROSS
            case CROSS:
                return gamepad.a;

            case B:     // Same as CIRCLE
            case CIRCLE:
                return gamepad.b;

            case LEFT_BUMPER:
                return gamepad.left_bumper;
            case RIGHT_BUMPER:
                return gamepad.right_bumper;
            case BACK:
                return gamepad.back;
            case START:
                return gamepad.start;
            case DPAD_UP:
                return gamepad.dpad_up;
            case DPAD_DOWN:
                return gamepad.dpad_down;
            case DPAD_LEFT:
                return gamepad.dpad_left;
            case DPAD_RIGHT:
                return gamepad.dpad_right;
            case LEFT_STICK_BUTTON:
                return gamepad.left_stick_button;
            case RIGHT_STICK_BUTTON:
                return gamepad.right_stick_button;
            default:
                throw new IllegalArgumentException("Invalid button specified");
        }
    }

    /**
     * Get the value of the trigger based on the Trigger enum.
     *
     * @param trigger The trigger which has to be looked up.
     * @return The value of the chosen trigger.
     */
    double getValue(GamepadKeys.Trigger trigger) {
        switch (trigger) {
            case LEFT_TRIGGER:
                return gamepad.left_trigger;
            case RIGHT_TRIGGER:
                return gamepad.right_trigger;
            default:
                throw new IllegalArgumentException("Invalid trigger specified");
        }
    }

    /**
     * Get the value of the stick based on the Stick enum.
     *
     * @param stick The stick which has to be looked up.
     * @return The value of the chosen stick.
     */
    double getValue(GamepadKeys.Stick stick) {
        switch (stick) {
            case LEFT_STICK_X:
                return gamepad.left_stick_x;
            case LEFT_STICK_Y:
                // Invert the sign of the y stick values to make it consistent with
                //    normal geometric axes people think about.
                return -gamepad.left_stick_y;
            case RIGHT_STICK_X:
                return gamepad.right_stick_x;
            case RIGHT_STICK_Y:
                // Invert the sign of the y stick values to make it consistent with
                //    normal geometric axes people think about.
                return -gamepad.right_stick_y;
            default:
                throw new IllegalArgumentException("Invalid stick specified");
        }
    }
}


/**
 * Implements common functions for different types of keys to be implemented as buttons.
 * <p>
 * At the end of the derived class' constructor, call initializeButtonValue()
 */
abstract class AbstractInputButton implements InputButton, PeriodicRunnable {

    private boolean wasPressedPrior;
    private boolean isPressedNow;

    protected AbstractInputButton(PeriodicRunner periodicRunner) {
        // Register this button to be updated every loop so button states can be updated.
        periodicRunner.addPeriodicRunnable(this);
    }

    @Override
    public boolean isDown() {
        return isPressedNow;
    }

    @Override
    public boolean wasJustPressed() {
        return !wasPressedPrior && isPressedNow;
    }

    @Override
    public boolean wasJustReleased() {
        return wasPressedPrior && !isPressedNow;
    }

    @Override
    public boolean hasChangedState() {
        return wasPressedPrior != isPressedNow;
    }

    @Override
    public void onPeriodic() {
        wasPressedPrior = isPressedNow;
        updateIsPressedNow();
    }

    @Override
    public Priority getPeriodicRunnablePriority() {
        return Priority.PREPARE_TO_COMPUTE_STATE;
    }

    /**
     * Update the state of whether the button is currently pressed.  The implementation
     * should update the value using setIsPressedNow().
     */
    protected abstract void updateIsPressedNow();

    /**
     * Use to set a new value for whether the button is currently pressed.  This would usually
     * be called from the derived class' implementation of updateIsPressedNow().
     *
     * @param isPressedNow The new value of the button
     */
    protected void setIsPressedNow(boolean isPressedNow) {
        this.isPressedNow = isPressedNow;
    }

    /**
     * Initialize the prior and current state of the button with the current value.  This
     * should be called at the end of the derived class' constructor.
     */
    protected void initializeButtonValue() {
        // Initialize the prior and current state with current value
        updateIsPressedNow();
        wasPressedPrior = isPressedNow;
    }
}


/**
 * Use a button on the gamepad as a button input.
 */
class InputButtonPlain extends AbstractInputButton {
    GamepadKeys.Button button;
    GamepadController gamepadController;

    InputButtonPlain(GamepadController gamepadController, GamepadKeys.Button button,
                     PeriodicRunner periodicRunner) {
        super(periodicRunner);

        this.gamepadController = gamepadController;
        this.button = button;

        initializeButtonValue();
    }

    @Override
    protected void updateIsPressedNow() {
        setIsPressedNow(gamepadController.getValue(button));
    }
}


/**
 * Use a trigger as a button input. A threshold will decide whether the trigger has moved
 * enough to be considered as a pressed button.
 */
class InputButtonTrigger extends AbstractInputButton {
    GamepadKeys.Trigger trigger;
    GamepadController gamepadController;

    double triggerThreshold;

    InputButtonTrigger(GamepadController gamepadController,
                       GamepadKeys.Trigger trigger,
                       double triggerThreshold,
                       PeriodicRunner periodicRunner) {
        super(periodicRunner);

        this.gamepadController = gamepadController;
        this.trigger = trigger;
        this.triggerThreshold = triggerThreshold;

        initializeButtonValue();
    }

    @Override
    protected void updateIsPressedNow() {
        setIsPressedNow(gamepadController.getValue(trigger) > triggerThreshold);
    }
}

/**
 * Implements common functions for different keys to be used as intervals.  The range for the
 * interval can be specified.  Typically these are [0, 1] or [-1, 1].
 */
abstract class AbstractInputInterval implements InputInterval {
    protected double intervalRangeMin;
    protected double intervalRangeMax;
    protected double intervalRange;

    private double valueInInterval;

    /**
     * Specify the range of the desired interval.
     *
     * @param intervalRangeMin Minimum value of the interval
     * @param intervalRangeMax Maximum value of the interval
     */
    AbstractInputInterval(double intervalRangeMin, double intervalRangeMax) {
        this.intervalRangeMin = intervalRangeMin;
        this.intervalRangeMax = intervalRangeMax;

        intervalRange = intervalRangeMax - intervalRangeMin;
    }

    @Override
    public double getValue() {
        updateValueInInterval();
        return valueInInterval;
    }

    @Override
    public double getIntervalRangeMax() {
        return intervalRangeMax;
    }

    @Override
    public double getIntervalRangeMin() {
        return intervalRangeMin;
    }

    protected void setValueAfterScaling(double value, double rangeValueMin, double rangeValueMax) {
        double rangeValue = rangeValueMax - rangeValueMin;
        valueInInterval = ((value - rangeValueMin) / rangeValue * intervalRange) + intervalRangeMin;
    }

    /**
     * Update the value of the trigger.  The implementation should update the value using
     * setValueAfterScaling().
     */
    protected abstract void updateValueInInterval();
}

/**
 * Implement an interval in [0, 1] using a trigger button.
 */
class InputIntervalUnitTrigger extends AbstractInputInterval {

    GamepadController gamepadController;
    GamepadKeys.Trigger trigger;

    InputIntervalUnitTrigger(GamepadController gamepadController,
                             GamepadKeys.Trigger trigger) {
        // The range for the interval is [0, 1] since this is a unit interval
        super(0, 1);

        this.gamepadController = gamepadController;
        this.trigger = trigger;
    }

    @Override
    protected void updateValueInInterval() {
        // Set the value in the interval.  Triggers are in the range [0, 1].  So scale accordingly.
        setValueAfterScaling(gamepadController.getValue(trigger), 0F, 1F);
    }
}

/**
 * Implement an interval in [0, 1] using a plain button.
 */
class InputIntervalUnitButton extends AbstractInputInterval {

    GamepadController gamepadController;
    GamepadKeys.Button button;

    InputIntervalUnitButton(GamepadController gamepadController,
                            GamepadKeys.Button button) {
        // The range for the interval is [0, 1] since this is a unit interval
        super(0F, 1F);

        this.gamepadController = gamepadController;
        this.button = button;
    }

    @Override
    protected void updateValueInInterval() {
        // If the button is down, it will be interpreted as a 1; otherwise it is a 0.
        double valueInInterval = gamepadController.getValue(button) ? 1 : 0;

        // Set the value in the interval.  The button will be interpreted to be in the
        //    range [0, 1].  So no scaling will occur.
        setValueAfterScaling(valueInInterval, 0, 1);
    }
}

/**
 * Implement an interval in [-1, 1] using two trigger buttons.  One trigger will serve in the
 * range [-1, 0] and the other in [0, 1].  However, if both triggers are depressed, they will
 * negate each other.
 */
class InputIntervalDoubleUnitTrigger extends AbstractInputInterval {

    GamepadController gamepadController;
    GamepadKeys.Trigger trigger1;
    GamepadKeys.Trigger trigger2;

    InputIntervalDoubleUnitTrigger(GamepadController gamepadController,
                                   GamepadKeys.Trigger trigger1,
                                   GamepadKeys.Trigger trigger2) {
        // The range for the interval is [-1, 1] since this is a double unit interval
        super(-1, 1);

        this.gamepadController = gamepadController;
        this.trigger1 = trigger1;
        this.trigger2 = trigger2;
    }

    @Override
    protected void updateValueInInterval() {
        // Sum the values of the two triggers.  However the value of the first trigger will
        //    be multiplied by -1 to get the effect of reducing the interval by pressing it.
        //    The effective range of these numbers are now [-1, 1].
        double value1 = -1 * gamepadController.getValue(trigger1);
        double value2 = gamepadController.getValue(trigger2);
        double totalValue = value1 + value2;

        // Set the value in the interval.  The value is in the range [-1, 1].  (So no rescaling
        //    will be done implicitly.)
        setValueAfterScaling(totalValue, -1, 1);
    }
}


/**
 * Implement an interval in [-1, 1] using two plain buttons.  Pressing button 1 will be considered
 * to be -1, and pressing the other will be considered a 1.  Pressing neither will be a 0,
 * and pressing both will negate each other and be a 0 as well.
 */
class InputIntervalDoubleUnitButton extends AbstractInputInterval {

    GamepadController gamepadController;
    GamepadKeys.Button button1;
    GamepadKeys.Button button2;

    InputIntervalDoubleUnitButton(GamepadController gamepadController,
                                  GamepadKeys.Button button1,
                                  GamepadKeys.Button button2) {
        // The range for the interval is [-1, 1] since this is a double unit interval
        super(-1, 1);

        this.gamepadController = gamepadController;
        this.button1 = button1;
        this.button2 = button2;
    }

    @Override
    protected void updateValueInInterval() {
        // If a button is down, it will be interpreted as a 1; otherwise it is a 0.
        //
        // Sum the values of the two buttons.  However the value of the first button will be
        //    multiplied by -1 to get the effect of reducing the interval by pressing it.
        //    The effective range of these numbers are now [-1, 1].
        double value1 = -1 * (gamepadController.getValue(button1) ? 1F : 0F);
        double value2 = gamepadController.getValue(button2) ? 1F : 0F;
        double totalValue = value1 + value2;

        // Set the value in the interval.  The button will be interpreted to be in the
        //    range [0, 1].  So no scaling will occur.
        setValueAfterScaling(totalValue, -1F, 1F);
    }
}


/**
 * Implement an interval in [-1, 1] using a stick.
 */
class InputIntervalDoubleUnitStick extends AbstractInputInterval {

    GamepadController gamepadController;
    GamepadKeys.Stick stick;

    InputIntervalDoubleUnitStick(GamepadController gamepadController,
                                 GamepadKeys.Stick stick) {
        // The range for the interval is [-, 1] since this is a double unit interval
        super(-1F, 1F);

        this.gamepadController = gamepadController;
        this.stick = stick;
    }

    @Override
    protected void updateValueInInterval() {
        // Pass along the value of the stick as the interval
        double value = gamepadController.getValue(stick);

        // Set the value in the interval.  The value is in the range [-1, 1].  (So no rescaling
        //    will be done implicitly.)
        setValueAfterScaling(value, -1F, 1F);
    }
}


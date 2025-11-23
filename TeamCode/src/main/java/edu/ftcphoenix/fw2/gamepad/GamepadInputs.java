package edu.ftcphoenix.fw2.gamepad;

import java.util.HashMap;

public class GamepadInputs {
    private final HashMap<String, InputButton> lookupButton = new HashMap<>();
    private final HashMap<String, InputInterval> lookupInterval = new HashMap<>();

    /**
     * Is the specified button one of the alread-defined button gamepad inputs?
     *
     * @param buttonName Name of the button to search.
     * @return Whether the button was already defined.
     */
    public boolean hasButton(String buttonName) {
        return lookupButton.containsKey(buttonName);
    }

    /**
     * Is the specified interval one of the already-defined interval gamepad inputs?
     *
     * @param intervalName Name of the interval to search.
     * @return Whether the interval was already defined.
     */
    public boolean hasInterval(String intervalName) {
        return lookupInterval.containsKey(intervalName);
    }

    /**
     * Add a button to the collection of buttons.
     *
     * @param buttonName  Name of the button to add.
     * @param inputButton The button instance.
     */
    public void addButton(String buttonName, InputButton inputButton) {
        if (hasButton(buttonName))
            throw new IllegalArgumentException("Adding button [" + buttonName + "] when it already exists.");

        lookupButton.put(buttonName, inputButton);
    }

    /**
     * Add an interval to the collection of intervals.
     *
     * @param intervalName  Name of the interval to add.
     * @param inputInterval The interval instance.
     */
    public void addInterval(String intervalName, InputInterval inputInterval) {
        if (hasInterval(intervalName))
            throw new IllegalArgumentException("Adding interval [" + intervalName + "] when it already exists.");

        lookupInterval.put(intervalName, inputInterval);
    }

    /**
     * Get the {@link InputButton} based on the ID specified.
     *
     * @param buttonName The string ID of the button to look for
     * @return The {@link InputButton} found
     */
    public InputButton getButton(String buttonName) {
        return lookupButton.get(buttonName);
    }

    /**
     * Get the {@link InputInterval} based on the ID specified.
     *
     * @param intervalName The string ID of the button to look for
     * @return The {@link InputButton} found
     */
    public InputInterval getInterval(String intervalName) {
        return lookupInterval.get(intervalName);
    }
}

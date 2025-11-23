package edu.ftcphoenix.fw2.robotbase.statehistory;

import java.util.HashMap;
import java.util.Map;

import edu.ftcphoenix.fw2.robotbase.statehistory.componentstate.ComponentStateEntry;

/**
 * Contains the state of the robot at a point in time.  This will further contain the state of
 * each of the components.
 *
 * @param <C> Enum defining the various components of the robot.  State can be saved for each
 *            of the components defined in this enum.
 */
public class RobotStateEntry<C> {
    private final Map<C, ComponentStateEntry> componentStateEntryMap = new HashMap<>();

    /**
     * Get a component's state entry object.  We need to specify the class the state entry object
     * is of and it has to be extended from {@link ComponentStateEntry}.
     *
     * @param component The component being referenced.
     * @param type The class type of the entry.  The return object will be type casted to this type.
     * @return The component's state entry.
     * @param <T> The type of the component's state entry.
     */
    public <T extends ComponentStateEntry> T getComponentStateEntry(C component, Class<T> type) {
        return type.cast(componentStateEntryMap.get(component));
    }

    /**
     * Add a component's state entry to the robot's state entry.
     *
     * @param component           The component that needs to be added.
     * @param componentStateEntry The component's state entry object.
     */
    public void addComponentStateEntry(C component, ComponentStateEntry componentStateEntry) {
        componentStateEntryMap.put(component, componentStateEntry);
    }
}

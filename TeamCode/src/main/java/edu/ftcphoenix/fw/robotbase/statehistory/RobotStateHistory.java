package edu.ftcphoenix.fw.robotbase.statehistory;

import edu.ftcphoenix.fw.robotbase.statehistory.componentstate.ComponentStateEntry;
import edu.ftcphoenix.fw.util.LookupTable;

/**
 * Contains the history of robot state entries ({@link RobotStateEntry}).  These entries can be
 * searched for a specific timestamp.
 *
 * @param <C> Enum defining the various components of the robot.  State can be saved for each
 *            of the components defined in this enum.
 */
public class RobotStateHistory<C extends Enum<C>> {
    final LookupTable<Long, RobotStateEntry<C>> lookupTable = new LookupTable<>();
    final int maxNumEntries;
    RobotStateEntry<C> latestRobotStateEntry;

    /**
     * Create a history of poses with a limited number of entries to save.
     *
     * @param maxNumEntries Maximum number of entries to save.
     */
    public RobotStateHistory(int maxNumEntries) {
        this.maxNumEntries = maxNumEntries;
    }

    /**
     * Add a robot state entry to the history with the timestamp as the key for lookup.
     *
     * @param robotStateEntry The robot state entry to add.
     * @param nanoSeconds     The timestamp of the pose.
     */
    public void addRobotStateEntry(RobotStateEntry<C> robotStateEntry, long nanoSeconds) {
        lookupTable.add(nanoSeconds, robotStateEntry);
        latestRobotStateEntry = robotStateEntry;

        // Remove the older entry if needed.
        maintainMaxNumEntries();
    }

    /**
     * If the maximum number of entries has been exceeded, cleanup older entries.
     */
    private void maintainMaxNumEntries() {
        if (lookupTable.size() > maxNumEntries)
            lookupTable.remove(lookupTable.firstKey());
    }

    /**
     * Get the robot state entry based on an approx-timestamp match.
     *
     * @param nanoSeconds The timestamp for which we want the closest state entry.
     * @return The closest state entry found.
     */
    public RobotStateEntry<C> getRobotStateEntry(long nanoSeconds) {
        return lookupTable.getClosest(nanoSeconds);
    }

    /**
     * Get a component state entry based on a approx-timestamp match.
     *
     * @param nanoSeconds The timestamp for which we want the closest component entry
     * @param component   The component whose state we want
     * @param type        The type of the class representing the component state
     * @param <T>         The type of the component's state entry.
     * @return The component state entry found
     */
    <T extends ComponentStateEntry> T getComponentStateEntry(long nanoSeconds, C component,
                                                             Class<T> type) {
        return getRobotStateEntry(nanoSeconds).getComponentStateEntry(component, type);
    }

    /**
     * Get the most recent robot state entry.
     *
     * @return The most recent robot state entry.
     */
    public RobotStateEntry<C> getLatestRobotStateEntry() {
        return latestRobotStateEntry;
    }

    /**
     * Get the most recent component state entry for the specified component.
     *
     * @param component The component whose state we want.
     * @param type      The type of the class representing the component state
     * @param <T>       The type of the component's state entry
     * @return The component state entry found
     */
    <T extends ComponentStateEntry> T getLatestComponentStateEntry(C component, Class<T> type) {
        return getLatestRobotStateEntry().getComponentStateEntry(component, type);
    }
}

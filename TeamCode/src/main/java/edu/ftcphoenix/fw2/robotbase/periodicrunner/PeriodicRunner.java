package edu.ftcphoenix.fw2.robotbase.periodicrunner;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Register {@link PeriodicRunnable} functions to process each loop.
 */
public class PeriodicRunner {
    private final int INIT_NUM_RUNNERS = 100;

    ArrayList<PeriodicRunnable> toUpdatePeriodically = new ArrayList<>(INIT_NUM_RUNNERS);;

    /**
     * Add an object that has to be periodically run.
     *
     * @param periodicRunnable The object whose {@link PeriodicRunnable#onPeriodic()} method has
     *                         to be run.
     */
    public void addPeriodicRunnable(PeriodicRunnable periodicRunnable) {
        // Add the runnable object.
        toUpdatePeriodically.add(periodicRunnable);

        // Sort the list of runnable objects for correct retrieval.
        sortPeriodicRunnablesList();
    }

    /**
     * Remove an object from being run periodically.
     *
     * @param periodicRunnable The object to remove.
     */
    public void removePeriodicRunnable(PeriodicRunnable periodicRunnable) {
        // Remove the runnable object.
        toUpdatePeriodically.remove(periodicRunnable);

        // Sort the list of runnable objects for correct retrieval.
        sortPeriodicRunnablesList();
    }

    /**
     * This method has to be executed from within the main loop of the robot.
     */
    public void runAllPeriodicRunnables() {
        for (PeriodicRunnable cur : toUpdatePeriodically)
            cur.onPeriodic();
    }

    /**
     * Anytime the list of runnable objects has changed, sort the list.  This is required
     * for proper execution.
     *
     * <P/>It is better to execute this after updating a list rather than each time the list is
     * queried in {@link #runAllPeriodicRunnables()} since that will happen much more frequently.
     */
    void sortPeriodicRunnablesList() {
        toUpdatePeriodically.sort(new PeriodicRunnablePriorityComparator());
    }
}


/**
 * Comparator will sort the {@link PeriodicRunnable} objects according to their priority as defined
 * by {@link PeriodicRunnable#getPeriodicRunnablePriority()}.
 */
class PeriodicRunnablePriorityComparator implements Comparator<PeriodicRunnable> {
    @Override
    public int compare(PeriodicRunnable o1, PeriodicRunnable o2) {
        return Integer.compare(o1.getPeriodicRunnablePriority().getPriority(),
                o2.getPeriodicRunnablePriority().getPriority());
    }
}
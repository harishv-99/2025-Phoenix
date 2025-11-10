package edu.ftcphoenix.fw2.robotbase.statehistory;

import edu.ftcphoenix.fw2.robotbase.periodicrunner.PeriodicRunnable;
import edu.ftcphoenix.fw2.robotbase.periodicrunner.PeriodicRunner;

/**
 * Extend from this class to write the robot state in the beginning of each turn.  Implement
 * the {@link PeriodicRunnable#onPeriodic()} method to execute the logic to capture the state
 * information.
 */
public abstract class AbstractRobotStateWriter implements PeriodicRunnable {
    public AbstractRobotStateWriter(PeriodicRunner periodicRunner) {
        periodicRunner.addPeriodicRunnable(this);
    }

    @Override
    public Priority getPeriodicRunnablePriority() {
        return Priority.COMPUTE_STATE;
    }
}

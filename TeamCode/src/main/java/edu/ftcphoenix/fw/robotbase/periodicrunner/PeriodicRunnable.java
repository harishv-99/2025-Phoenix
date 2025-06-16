package edu.ftcphoenix.fw.robotbase.periodicrunner;

/**
 * Implementors of this class will be able to process an event every time an
 * loop occurs during the active op-mode.
 */
public interface PeriodicRunnable {
    /**
     * This method will be called for each loop
     */
    void onPeriodic();

    /**
     * Get the priority of this object when running the {@link #onPeriodic()} method.
     *
     * @return The priority
     */
    Priority getPeriodicRunnablePriority();


    enum Priority {
        /**
         * High priority to be executed to prepare for reading input.  Items here should NOT
         * read inputs from any hardware devices.  This should be used to clear buffers, etc.
         */
        PREPARE_TO_READ_INPUT(0),

        /**
         * Process signals that will inform the current state of the robot.
         */
        PREPARE_TO_COMPUTE_STATE(1),

        /**
         * Compute the state of the robot at the very end and before running the methods
         * like init...(), onPeriodic...(), and exit...() in the robot.
         */
        COMPUTE_STATE(2);

        private final int priority;

        Priority(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }
    }
}

package edu.ftcphoenix.fw.fsm;

/**
 * Represent a state in a finite state machine.
 */
public interface FsmState {
    /**
     * Initialize the state for execution for the first time.
     */
    void initState();

    /**
     * Execute the state repeatedly as part of a loop.
     */
    void executeState();

    /**
     * Called once when transitioning out of this state.
     */
    void exitState();

    /**
     * Get the name of this state.
     *
     * @return Name of state.
     */
    String getName();

    /**
     * Set the container object that holds this state.  This is usually called by the container
     * when this state is added to it.
     *
     * @param container The container holding this state.
     */
    void setContainer(FsmContainer container);

    /**
     * Get the container finite state machine.
     *
     * @return The container holding this state.
     */
    FsmContainer getContainer();
}

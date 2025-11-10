package edu.ftcphoenix.fw2.deprecated.fsm;

public class States {
    public static boolean isNull(FsmState state) {
        return (state == null || state == NullState.getInstance());
    }
}

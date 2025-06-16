package edu.ftcphoenix.fw.gamepad;

public class GamepadKeys {

    public enum Button {
        Y, X, A, B,

        // Mapping of A, B, X, and Y into PS4 keys
        TRIANGLE, SQUARE, CIRCLE, CROSS,

        LEFT_BUMPER, RIGHT_BUMPER, BACK,
        START, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
        LEFT_STICK_BUTTON, RIGHT_STICK_BUTTON

    }

    public enum Trigger {
        LEFT_TRIGGER, RIGHT_TRIGGER
    }

    public enum Stick {
        LEFT_STICK_X, LEFT_STICK_Y, RIGHT_STICK_X, RIGHT_STICK_Y
    }
}

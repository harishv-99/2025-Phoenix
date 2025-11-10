package edu.ftcphoenix.fw2.gamepad;

public interface InputButton {
    boolean isDown();

    boolean wasJustPressed();

    boolean wasJustReleased();

    boolean hasChangedState();
}

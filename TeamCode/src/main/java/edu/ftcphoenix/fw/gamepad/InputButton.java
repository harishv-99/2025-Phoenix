package edu.ftcphoenix.fw.gamepad;

public interface InputButton {
    boolean isDown();

    boolean wasJustPressed();

    boolean wasJustReleased();

    boolean hasChangedState();
}

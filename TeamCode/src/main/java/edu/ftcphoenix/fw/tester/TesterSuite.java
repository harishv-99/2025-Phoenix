package edu.ftcphoenix.fw.tester;

import java.util.function.Supplier;

import edu.ftcphoenix.fw.tester.ui.SelectionMenu;

/**
 * A menu-based "tester runner" that lets you select and run any registered {@link TeleOpTester}.
 *
 * <h2>Controls (gamepad1)</h2>
 * <ul>
 *   <li><b>Dpad Up/Down</b>: select (menu only)</li>
 *   <li><b>A</b>: enter selected tester (menu only)</li>
 *   <li><b>BACK</b>: return to menu (stops active tester)</li>
 * </ul>
 *
 * <p>This suite supports INIT-time selections: you can enter a tester during INIT, allow that
 * tester to run its own {@link TeleOpTester#initLoop(double)} (e.g., camera selection), then press
 * Start and continue into RUN without leaving the tester.</p>
 */
public final class TesterSuite extends BaseTeleOpTester {

    private final SelectionMenu<Supplier<TeleOpTester>> menu =
            new SelectionMenu<Supplier<TeleOpTester>>()
                    .setTitle("Phoenix Tester Menu")
                    .setHelp("Dpad: select | A: enter | BACK: menu");

    private TeleOpTester active = null;
    private boolean inMenu = true;
    private boolean opModeStarted = false;

    /**
     * Register a tester (no help).
     */
    public TesterSuite add(String name, Supplier<TeleOpTester> factory) {
        return add(name, null, factory);
    }

    /**
     * Register a tester with optional one-line help.
     */
    public TesterSuite add(String name, String help, Supplier<TeleOpTester> factory) {
        menu.addItem(name, help, factory);
        return this;
    }

    @Override
    public String name() {
        return "Tester Suite";
    }

    @Override
    protected void onInit() {
        // Menu navigation and selection are ONLY active while inMenu.
        menu.bind(
                bindings,
                gamepads.p1().dpadUp(),
                gamepads.p1().dpadDown(),
                gamepads.p1().a(),
                () -> inMenu,
                item -> enter(item.value)
        );

        // BACK always returns to menu (INIT or RUN) if a tester is active.
        bindings.onPress(gamepads.p1().back(), () -> {
            if (inMenu) return;
            stopActive();
            inMenu = true;
        });

        inMenu = true;
        opModeStarted = false;
        active = null;
    }

    @Override
    protected void onInitLoop(double dtSec) {
        if (inMenu) {
            renderMenu();
            return;
        }

        if (active != null) {
            dispatchActiveInitLoop(dtSec);
            return;
        }

        // Fail-safe
        inMenu = true;
        renderMenu();
    }

    @Override
    protected void onStart() {
        opModeStarted = true;
        if (active != null) {
            active.start();
        }
    }

    @Override
    protected void onLoop(double dtSec) {
        if (inMenu) {
            renderMenu();
            return;
        }

        if (active != null) {
            dispatchActiveLoop(dtSec);
            return;
        }

        // Fail-safe
        inMenu = true;
        renderMenu();
    }

    @Override
    protected void onStop() {
        stopActive();
    }

    /**
     * Dispatch INIT-loop callbacks to the active tester without double-updating global button state.
     *
     * <p>Both {@link TesterSuite} and most testers extend {@link BaseTeleOpTester}, which performs
     * {@code gamepads.update()} / {@code bindings.update()} in their {@code initLoop()} and {@code loop()}.
     * When nested (suite calling into a tester), calling {@code active.initLoop()} would cause a second
     * button update in the same OpMode cycle, wiping out edge events (e.g. Dpad/A presses).
     */
    private void dispatchActiveInitLoop(double dtSec) {
        if (active instanceof BaseTeleOpTester) {
            BaseTeleOpTester bt = (BaseTeleOpTester) active;
            // Global buttons were already updated by the suite's BaseTeleOpTester.
            // We still need to update the active tester's Bindings so its actions can fire.
            bt.bindings.update(clock);
            bt.onInitLoop(dtSec);
        } else {
            active.initLoop(dtSec);
        }
    }

    /**
     * Dispatch RUN-loop callbacks to the active tester without double-updating global button state.
     */
    private void dispatchActiveLoop(double dtSec) {
        if (active instanceof BaseTeleOpTester) {
            BaseTeleOpTester bt = (BaseTeleOpTester) active;
            bt.bindings.update(clock);
            bt.onLoop(dtSec);
        } else {
            active.loop(dtSec);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------------

    private void enter(Supplier<TeleOpTester> factory) {
        stopActive();

        active = factory.get();
        active.init(ctx);

        if (opModeStarted) {
            active.start();
        }

        inMenu = false;
    }

    private void stopActive() {
        if (active == null) return;
        active.stop();
        active = null;
    }

    private void renderMenu() {
        ctx.telemetry.clearAll();
        menu.render(ctx.telemetry);
        ctx.telemetry.addLine("");
        ctx.telemetry.addLine(opModeStarted
                ? "RUNNING: Enter tester with A."
                : "INIT: Enter a tester with A (it may have its own INIT menu).");
        ctx.telemetry.update();
    }
}

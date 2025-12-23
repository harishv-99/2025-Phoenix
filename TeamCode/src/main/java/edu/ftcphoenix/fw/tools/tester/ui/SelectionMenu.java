package edu.ftcphoenix.fw.tools.tester.ui;

import org.firstinspires.ftc.robotcore.external.Telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import edu.ftcphoenix.fw.input.Button;
import edu.ftcphoenix.fw.input.binding.Bindings;
import edu.ftcphoenix.fw.core.math.MathUtil;

/**
 * Simple scroll-and-select menu helper for TeleOp testers.
 *
 * <p>Opinionated by design so callers don't duplicate logic:
 * <ul>
 *   <li>Maintains a list of items and a selected index.</li>
 *   <li>Wrap-around navigation supported (default on).</li>
 *   <li>Renders the list + selected item + optional help text.</li>
 *   <li>Binds navigation to {@link Bindings} using Phoenix {@link Button} edge detection.</li>
 * </ul>
 *
 * <p><b>Important:</b> Use the {@link #bind(Bindings, Button, Button, Button, BooleanSupplier, Consumer)}
 * overload to gate input handling (e.g., only respond while a menu is active).</p>
 *
 * @param <T> value stored for each item (e.g., a factory, a device name, an enum)
 */
public final class SelectionMenu<T> {

    /**
     * A single menu row: label + optional help + attached value.
     */
    public static final class Item<T> {
        public final String label;
        public final String help;
        public final T value;

        public Item(String label, String help, T value) {
            this.label = label;
            this.help = help;
            this.value = value;
        }
    }

    private String title = "Menu";
    private String help = null;

    private final List<Item<T>> items = new ArrayList<>();
    private int selected = 0;

    private boolean wrap = true;

    public SelectionMenu<T> setTitle(String title) {
        this.title = title;
        return this;
    }

    /**
     * Sets a global help line shown near the top of the menu.
     * (Per-item help is shown near the bottom for the currently selected item.)
     */
    public SelectionMenu<T> setHelp(String help) {
        this.help = help;
        return this;
    }

    /**
     * If true, selection wraps around at ends. Default: true.
     */
    public SelectionMenu<T> setWrap(boolean wrap) {
        this.wrap = wrap;
        return this;
    }

    /**
     * Removes all items and resets selection to 0.
     */
    public SelectionMenu<T> clearItems() {
        items.clear();
        selected = 0;
        return this;
    }

    /**
     * Adds an item. {@code help} may be null.
     */
    public SelectionMenu<T> addItem(String label, String help, T value) {
        items.add(new Item<>(label, help, value));
        clampSelected();
        return this;
    }

    /**
     * Convenience overload (no per-item help).
     */
    public SelectionMenu<T> addItem(String label, T value) {
        return addItem(label, null, value);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int selectedIndex() {
        return selected;
    }

    public void setSelectedIndex(int index) {
        selected = index;
        clampSelected();
    }

    public Item<T> selectedItemOrNull() {
        if (items.isEmpty()) return null;
        return items.get(selected);
    }

    public T selectedValueOrNull() {
        Item<T> item = selectedItemOrNull();
        return item == null ? null : item.value;
    }

    public void up() {
        if (items.isEmpty()) return;
        if (selected > 0) {
            selected--;
        } else if (wrap) {
            selected = items.size() - 1;
        }
    }

    public void down() {
        if (items.isEmpty()) return;
        if (selected < items.size() - 1) {
            selected++;
        } else if (wrap) {
            selected = 0;
        }
    }

    /**
     * Register navigation bindings (always enabled).
     *
     * <p>If you need this to only respond sometimes (e.g., only while a menu is showing),
     * use the overload that takes an {@code enabled} predicate.</p>
     */
    public void bind(Bindings bindings,
                     Button upButton,
                     Button downButton,
                     Button selectButton,
                     Consumer<Item<T>> onSelect) {
        bind(bindings, upButton, downButton, selectButton, () -> true, onSelect);
    }

    /**
     * Register navigation bindings with an enable predicate.
     *
     * @param enabled if false, the menu ignores all bound inputs
     */
    public void bind(Bindings bindings,
                     Button upButton,
                     Button downButton,
                     Button selectButton,
                     BooleanSupplier enabled,
                     Consumer<Item<T>> onSelect) {

        bindings.onPress(upButton, () -> {
            if (!enabled.getAsBoolean()) return;
            up();
        });

        bindings.onPress(downButton, () -> {
            if (!enabled.getAsBoolean()) return;
            down();
        });

        if (selectButton != null && onSelect != null) {
            bindings.onPress(selectButton, () -> {
                if (!enabled.getAsBoolean()) return;
                Item<T> item = selectedItemOrNull();
                if (item != null) {
                    onSelect.accept(item);
                }
            });
        }
    }

    /**
     * Render the menu to telemetry.
     *
     * <p>This does not call {@code telemetry.update()} so the caller can compose screens.</p>
     */
    public void render(Telemetry telemetry) {
        telemetry.addLine("=== " + title + " ===");
        if (help != null && !help.isEmpty()) telemetry.addLine(help);
        telemetry.addLine("");

        if (items.isEmpty()) {
            telemetry.addLine("(no items)");
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            String prefix = (i == selected) ? ">> " : "   ";
            telemetry.addLine(prefix + items.get(i).label);
        }

        Item<T> sel = items.get(selected);

        telemetry.addLine("");
        telemetry.addLine("Selected: " + sel.label);
        if (sel.help != null && !sel.help.isEmpty()) {
            telemetry.addLine("Info: " + sel.help);
        }
    }

    private void clampSelected() {
        if (items.isEmpty()) {
            selected = 0;
            return;
        }
        selected = MathUtil.clamp(selected, 0, items.size() - 1);
    }
}

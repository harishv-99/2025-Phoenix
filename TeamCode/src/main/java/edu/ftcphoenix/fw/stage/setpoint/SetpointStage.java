package edu.ftcphoenix.fw.stage.setpoint;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import edu.ftcphoenix.fw.actuation.Plant;
import edu.ftcphoenix.fw.stage.Stage;

/**
 * Generic setpoint stage: maps high-level goals to numeric targets and
 * drives a {@link Plant}.
 *
 * <p>Typical usage:
 * <pre>{@code
 * // Enum of high-level goals
 * enum ShooterGoal { STOP, IDLE, SHOOT }
 *
 * // Build from an enum with explicit targets
 * SetpointStage<ShooterGoal> shooter = SetpointStage
 *     .enumBuilder(ShooterGoal.class)
 *     .name("Shooter")
 *     .plant(FtcPlants.velocity(
 *             hardwareMap,
 *             "shooter",
 *             ticksPerRev,
 *             false))
 *     .target(ShooterGoal.STOP, 0.0)
 *     .target(ShooterGoal.IDLE, Units.rpmToRadPerSec(1000.0))
 *     .target(ShooterGoal.SHOOT, Units.rpmToRadPerSec(3500.0))
 *     .build();
 * }</pre>
 *
 * <p>Your robot code then drives the stage with high-level goals:</p>
 * <pre>{@code
 * shooterStage.setGoal(ShooterGoal.IDLE);
 * ...
 * shooterStage.setGoal(ShooterGoal.SHOOT);
 * ...
 * shooterStage.update(dtSec);
 * }</pre>
 */
public final class SetpointStage<G> implements Stage {

    // ---------------------------------------------------------------------
    // Goal → target mapping
    // ---------------------------------------------------------------------

    /**
     * Immutable mapping from goals to numeric targets.
     *
     * <p>Most callers will not construct this directly; prefer the enum-based
     * builder via {@link #enumBuilder(Class)} whenever your goals are an enum.</p>
     */
    public static final class GoalMap<G> {
        private final Map<G, Double> map;

        private GoalMap(Map<G, Double> map) {
            this.map = map;
        }

        /**
         * Convert a goal to its target value.
         *
         * @throws IllegalArgumentException if no mapping exists for the goal
         */
        public double toTarget(G g) {
            Double v = map.get(g);
            if (v == null) {
                throw new IllegalArgumentException("No target configured for goal: " + g);
            }
            return v.doubleValue();
        }

        /**
         * Convenience: build a GoalMap from an existing map copy.
         */
        public static <G> GoalMap<G> fromMap(Map<G, Double> src) {
            return new GoalMap<G>(new HashMap<G, Double>(src));
        }
    }

    /**
     * Helpers for building {@link GoalMap} instances, especially for enums.
     */
    public static final class GoalMaps {
        private GoalMaps() {
            // utility holder
        }

        /**
         * Builder for a {@link GoalMap} over an enum.
         *
         * <p>Example:
         * <pre>
         * GoalMap&lt;ShooterGoal&gt; map = SetpointStage.GoalMaps
         *         .enumMapBuilder(ShooterGoal.class)
         *         .put(ShooterGoal.STOP,  0.0)
         *         .put(ShooterGoal.IDLE,  1.0)
         *         .put(ShooterGoal.SHOOT, 2.0)
         *         .build();
         * </pre>
         */
        public static final class EnumMapBuilder<E extends Enum<E>> {
            private final Class<E> enumClass;
            private final EnumMap<E, Double> targets;

            public EnumMapBuilder(Class<E> enumClass) {
                this.enumClass = enumClass;
                this.targets = new EnumMap<E, Double>(enumClass);
            }

            public EnumMapBuilder<E> put(E goal, double target) {
                targets.put(goal, Double.valueOf(target));
                return this;
            }

            /**
             * Build a {@link GoalMap}, defaulting any unspecified enum constants to 0.0.
             */
            public GoalMap<E> build() {
                for (E constant : enumClass.getEnumConstants()) {
                    if (!targets.containsKey(constant)) {
                        targets.put(constant, Double.valueOf(0.0));
                    }
                }
                return GoalMap.fromMap(targets);
            }
        }

        /**
         * Convenience factory for {@link EnumMapBuilder}.
         */
        public static <E extends Enum<E>> EnumMapBuilder<E> enumMapBuilder(Class<E> cls) {
            return new EnumMapBuilder<E>(cls);
        }
    }

    // ---------------------------------------------------------------------
    // Instance state
    // ---------------------------------------------------------------------

    private final String name;
    private final Plant plant;
    private final GoalMap<G> goals;

    private G currentGoal;

    private SetpointStage(String name, Plant plant, GoalMap<G> goals) {
        if (plant == null) {
            throw new IllegalArgumentException("Plant is required");
        }
        if (goals == null) {
            throw new IllegalArgumentException("GoalMap is required");
        }
        this.name = name != null ? name : "Setpoint";
        this.plant = plant;
        this.goals = goals;
    }

    // ---------------------------------------------------------------------
    // Generic builder (for arbitrary goal types)
    // ---------------------------------------------------------------------

    /**
     * Generic builder for {@link SetpointStage} where goals are not necessarily enums.
     */
    public static final class Builder<G> {
        private String name = "Setpoint";
        private Plant plant;
        private GoalMap<G> goals;

        public Builder<G> name(String n) {
            this.name = n;
            return this;
        }

        public Builder<G> plant(Plant p) {
            this.plant = p;
            return this;
        }

        public Builder<G> goalMap(GoalMap<G> m) {
            this.goals = m;
            return this;
        }

        public SetpointStage<G> build() {
            return new SetpointStage<G>(name, plant, goals);
        }
    }

    /**
     * Generic builder for non-enum goals.
     */
    public static <G> Builder<G> builder() {
        return new Builder<G>();
    }

    // ---------------------------------------------------------------------
    // Enum-specialized builder (preferred)
    // ---------------------------------------------------------------------

    /**
     * Enum-specialized builder for {@link SetpointStage}.
     *
     * <p>This lets you configure both the plant and the goal → target mapping in one place:
     * <pre>
     * SetpointStage&lt;MyEnum&gt; stage = SetpointStage
     *         .enumBuilder(MyEnum.class)
     *         .name("MyStage")
     *         .plant(myPlant)
     *         .target(MyEnum.A, 1.0)
     *         .target(MyEnum.B, 2.0)
     *         .build();
     * </pre>
     */
    public static final class EnumBuilder<E extends Enum<E>> {
        private final Class<E> goalClass;
        private final EnumMap<E, Double> targets;

        private String name = "Setpoint";
        private Plant plant;

        private EnumBuilder(Class<E> goalClass) {
            this.goalClass = goalClass;
            this.targets = new EnumMap<E, Double>(goalClass);
        }

        public EnumBuilder<E> name(String name) {
            this.name = name;
            return this;
        }

        public EnumBuilder<E> plant(Plant plant) {
            this.plant = plant;
            return this;
        }

        /**
         * Set the target value for a specific goal.
         */
        public EnumBuilder<E> target(E goal, double target) {
            targets.put(goal, Double.valueOf(target));
            return this;
        }

        /**
         * Build the {@link SetpointStage}, defaulting unspecified enum constants to 0.0.
         */
        public SetpointStage<E> build() {
            GoalMaps.EnumMapBuilder<E> gm =
                    new GoalMaps.EnumMapBuilder<E>(goalClass);
            for (E constant : goalClass.getEnumConstants()) {
                Double v = targets.get(constant);
                gm.put(constant, v != null ? v.doubleValue() : 0.0);
            }
            GoalMap<E> goalMap = gm.build();
            return new SetpointStage<E>(name, plant, goalMap);
        }
    }

    /**
     * Convenience factory for the enum-specialized builder.
     */
    public static <E extends Enum<E>> EnumBuilder<E> enumBuilder(Class<E> goalClass) {
        return new EnumBuilder<E>(goalClass);
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * @return human-readable name, useful for telemetry or logging.
     */
    public String getName() {
        return name;
    }

    /**
     * @return last commanded goal (may be {@code null} if never set).
     */
    public G getGoal() {
        return currentGoal;
    }

    /**
     * Set a new goal and immediately forward its target to the underlying {@link Plant}.
     */
    public void setGoal(G goal) {
        currentGoal = goal;
        plant.setTarget(goals.toTarget(goal));
    }

    /**
     * Convenience: set the numeric target directly, bypassing the goal map.
     */
    public void setRawTarget(double target) {
        currentGoal = null;
        plant.setTarget(target);
    }

    /**
     * Shortcut: command zero target.
     */
    public void stop() {
        setRawTarget(0.0);
    }

    /**
     * Update the underlying plant. Call once per loop.
     */
    public void update(double dtSec) {
        plant.update(dtSec);
    }

    /**
     * @return true if the plant reports it is at setpoint.
     */
    public boolean atSetpoint() {
        return plant.atSetpoint();
    }
}

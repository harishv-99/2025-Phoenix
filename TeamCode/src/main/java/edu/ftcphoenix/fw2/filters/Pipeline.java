package edu.ftcphoenix.fw2.filters;

import java.util.Collection;

/**
 * A tiny builder-style wrapper for composing {@link Filter} stages.
 *
 * <p>This class exists purely for ergonomics now that {@link Filter#then(Filter)} enables
 * direct chaining. Use {@code Pipeline} when you want to:</p>
 * <ul>
 *   <li>Build a chain incrementally (possibly conditionally) and keep it as one object.</li>
 *   <li>Convey intent/readability (e.g., “build pipeline once in the constructor”).</li>
 *   <li>Append stages from different places without tracking the current head manually.</li>
 * </ul>
 *
 * <p>If you prefer, you can replace {@code Pipeline} with direct chaining:
 * {@code Filter<Double> f = Filter.identity().then(a).then(b).then(c);} —
 * both are equivalent.</p>
 *
 * @param <T> value type processed by the pipeline
 */
public final class Pipeline<T> implements Filter<T> {

    /**
     * The composed chain; starts as identity and accumulates via {@link Filter#then(Filter)}.
     */
    private Filter<T> chain = Filter.identity();

    /**
     * Append a stage if non-null.
     *
     * @param f filter stage to append (ignored if null)
     * @return this for chaining
     */
    public Pipeline<T> add(Filter<T> f) {
        if (f != null) chain = chain.then(f);
        return this;
    }

    /**
     * Append all stages in order (nulls are ignored).
     *
     * @param filters ordered collection of stages
     * @return this for chaining
     */
    public Pipeline<T> addAll(Collection<? extends Filter<T>> filters) {
        if (filters != null) {
            for (Filter<T> f : filters) add(f);
        }
        return this;
    }

    /**
     * Conditionally append a stage.
     *
     * @param condition if true, append {@code f}
     * @param f         stage to append when condition is true
     * @return this for chaining
     */
    public Pipeline<T> addIf(boolean condition, Filter<T> f) {
        if (condition) add(f);
        return this;
    }

    /**
     * Prepend a stage (runs before the current chain).
     *
     * @param f stage to run first
     * @return this for chaining
     */
    public Pipeline<T> prepend(Filter<T> f) {
        if (f != null) chain = f.then(chain);
        return this;
    }

    /**
     * Current number of effective stages (best-effort; cheap and optional).
     * <p>Note: this returns 0 for a brand-new pipeline and increases when you call {@link #add(Filter)}.
     * It is not used in filtering logic.</p>
     */
    private int stages = 0;

    /**
     * Internal hook to bump stage count when adding.
     */
    private void bump() {
        stages++;
    }

    // Override add/prepend to increment the counter nicely
    @Override
    public T apply(T in, double dtSeconds) {
        return chain.apply(in, dtSeconds);
    }

    /**
     * Factory: build a pipeline from varargs, skipping nulls.
     */
    @SafeVarargs
    public static <T> Pipeline<T> of(Filter<T>... filters) {
        Pipeline<T> p = new Pipeline<>();
        if (filters != null) {
            for (Filter<T> f : filters) p.add(f);
        }
        return p;
    }
}

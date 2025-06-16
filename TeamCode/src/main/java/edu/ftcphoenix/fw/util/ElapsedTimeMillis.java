package edu.ftcphoenix.fw.util;

import java.util.concurrent.TimeUnit;

public class ElapsedTimeMillis {
    public static final long SECOND_IN_MILLIS = 1000L;
    protected volatile long millisStartTime;

    public ElapsedTimeMillis() {
        this.reset();
    }

    public ElapsedTimeMillis(long startTimeMillis) {
        this.millisStartTime = startTimeMillis;
    }

    long millisNow() {
        return System.currentTimeMillis();
    }

    long now(TimeUnit unit) {
        return unit.convert(this.millisNow(), TimeUnit.MILLISECONDS);
    }

    public void reset() {
        this.millisStartTime = this.millisNow();
    }

    public long getStartTimeMilliseconds() {
        return this.millisStartTime;
    }

    public long getElapsedTime(TimeUnit unit) {
        return unit.convert(this.getElapsedMilliseconds(), TimeUnit.MILLISECONDS);
    }

    public double getElapsedSeconds() {
        return (double)this.getElapsedMilliseconds() / SECOND_IN_MILLIS;
    }

    public long getElapsedMilliseconds() {
        return this.millisNow() - this.millisStartTime;
    }
}

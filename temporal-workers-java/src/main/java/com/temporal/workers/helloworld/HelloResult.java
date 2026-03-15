package com.temporal.workers.helloworld;

/**
 * Result returned by the Hello World long-running activity.
 */
public class HelloResult {

    private String greeting;
    private int stepsCompleted;

    /** No-arg constructor required for Temporal serialization. */
    public HelloResult() {}

    public HelloResult(String greeting, int stepsCompleted) {
        this.greeting = greeting;
        this.stepsCompleted = stepsCompleted;
    }

    public String getGreeting()                 { return greeting; }
    public void   setGreeting(String g)         { this.greeting = g; }
    public int    getStepsCompleted()           { return stepsCompleted; }
    public void   setStepsCompleted(int s)      { this.stepsCompleted = s; }

    @Override
    public String toString() {
        return String.format("HelloResult{greeting='%s', stepsCompleted=%d}", greeting, stepsCompleted);
    }
}

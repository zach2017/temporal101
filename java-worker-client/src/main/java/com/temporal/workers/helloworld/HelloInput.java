package com.temporal.workers.helloworld;

/**
 * Input parameters for the Hello World long-running activity.
 */
public class HelloInput {

    private String name;
    private int totalSteps;
    private double stepDelaySecs;

    /** No-arg constructor required for Temporal serialization. */
    public HelloInput() {
        this.totalSteps = 20;
        this.stepDelaySecs = 3.0;
    }

    public HelloInput(String name) {
        this();
        this.name = name;
    }

    public HelloInput(String name, int totalSteps, double stepDelaySecs) {
        this.name = name;
        this.totalSteps = totalSteps;
        this.stepDelaySecs = stepDelaySecs;
    }

    public String getName()           { return name; }
    public void   setName(String n)   { this.name = n; }
    public int    getTotalSteps()     { return totalSteps; }
    public void   setTotalSteps(int s){ this.totalSteps = s; }
    public double getStepDelaySecs()  { return stepDelaySecs; }
    public void   setStepDelaySecs(double d) { this.stepDelaySecs = d; }
}

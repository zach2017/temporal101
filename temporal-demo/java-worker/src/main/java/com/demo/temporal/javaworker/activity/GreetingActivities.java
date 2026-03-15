package com.demo.temporal.javaworker.activity;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface GreetingActivities {

    String composeGreeting(String name);
}

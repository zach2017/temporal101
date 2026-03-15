package com.example.temporaldemo.temporal;

import com.example.temporaldemo.model.FileResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface FileInspectorActivities {
    @ActivityMethod
    FileResult inspect(String filename);
}

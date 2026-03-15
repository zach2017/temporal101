package com.docprocessor.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface DocumentActivities {

    /** Extracts text from a PDF file stored at the given path. */
    @ActivityMethod
    String extractTextFromPdf(String pdfFilePath);

    /** Saves extracted text to the filesystem, returns the output file path. */
    @ActivityMethod
    String saveTextToFile(String documentId, String originalFileName, String textContent);

    /** Reads a text file from storage and returns its content. */
    @ActivityMethod
    String readTextFile(String documentId);

    /** Checks if a document exists in storage. */
    @ActivityMethod
    boolean documentExists(String documentId);
}

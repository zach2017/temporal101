package com.example.ocr.common.enums;

/**
 * Identifies how the source file was resolved.
 */
public enum FileSourceType {
    /** AWS S3 bucket (s3://bucket/key) */
    S3,
    /** Local or shared filesystem path */
    LOCAL,
    /** HTTP/HTTPS URL download */
    URL
}

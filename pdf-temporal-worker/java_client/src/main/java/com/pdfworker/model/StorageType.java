package com.pdfworker.model;

/**
 * Storage backend — values MUST match Python {@code models.StorageType}.
 */
public enum StorageType {
    S3,
    NFS,
    URL;

    public static StorageType fromString(String v) {
        return valueOf(v.trim().toUpperCase());
    }
}

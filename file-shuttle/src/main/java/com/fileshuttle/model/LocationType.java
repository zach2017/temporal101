package com.fileshuttle.model;

/**
 * Supported storage location types for file transfer.
 * Each maps to a StorageProvider implementation.
 */
public enum LocationType {

    /** Local or container-mounted filesystem path */
    LOCAL,

    /** AWS S3 or S3-compatible (MinIO). URI: s3://bucket/key */
    S3,

    /** NFS-mounted filesystem. Physically a POSIX path but semantically
     *  a network share. Same I/O as LOCAL, separate for metrics/routing. */
    NFS,

    /** HTTP/HTTPS URL. INPUT=GET download, OUTPUT=PUT/POST upload */
    URL;

    /** Case-insensitive parse with aliases: "http","api"→URL, "file"→LOCAL, etc. */
    public static LocationType fromString(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("LocationType cannot be null or blank");
        return switch (value.trim().toLowerCase()) {
            case "local", "file", "fs"          -> LOCAL;
            case "s3", "minio"                  -> S3;
            case "nfs", "network"               -> NFS;
            case "url", "http", "https", "api"  -> URL;
            default -> throw new IllegalArgumentException(
                "Unknown location type: '%s'. Valid: local, s3, nfs, url".formatted(value));
        };
    }
}

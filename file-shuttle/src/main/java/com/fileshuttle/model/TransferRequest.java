package com.fileshuttle.model;

import java.util.Map;
import java.util.Optional;

/**
 * Immutable descriptor for a single file transfer operation.
 * Shared contract: CLI → orchestrator → provider → metrics.
 *
 * @param fileName       Logical filename for logging / default output key
 * @param inputType      Source backend type
 * @param inputLocation  Backend-specific source address
 * @param outputType     Destination backend type
 * @param outputLocation Backend-specific destination address
 * @param options        Tuning knobs (bufferSize, multipartPartSize, httpMethod)
 */
public record TransferRequest(
        String fileName,
        LocationType inputType,
        String inputLocation,
        LocationType outputType,
        String outputLocation,
        Map<String, String> options
) {
    public TransferRequest {
        if (fileName == null || fileName.isBlank())
            throw new IllegalArgumentException("fileName is required");
        if (inputLocation == null || inputLocation.isBlank())
            throw new IllegalArgumentException("inputLocation is required");
        if (outputLocation == null || outputLocation.isBlank())
            throw new IllegalArgumentException("outputLocation is required");
        options = (options == null) ? Map.of() : Map.copyOf(options);
    }

    public static TransferRequest of(String fileName,
                                     LocationType inType, String inLoc,
                                     LocationType outType, String outLoc) {
        return new TransferRequest(fileName, inType, inLoc, outType, outLoc, Map.of());
    }

    /** Buffer size for stream copying (default 64 KB). */
    public int bufferSize() {
        return intOption("bufferSize", 65_536);
    }

    /** S3 multipart part size in bytes (default 8 MB). */
    public long multipartPartSize() {
        return longOption("multipartPartSize", 8 * 1024 * 1024L);
    }

    /** HTTP upload method: PUT or POST (default PUT). */
    public String httpMethod() {
        return options.getOrDefault("httpMethod", "PUT");
    }

    private int intOption(String key, int def) {
        return Optional.ofNullable(options.get(key)).map(Integer::parseInt).orElse(def);
    }

    private long longOption(String key, long def) {
        return Optional.ofNullable(options.get(key)).map(Long::parseLong).orElse(def);
    }
}

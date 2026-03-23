package com.docprocessing.tika.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Input payload for the {@code detect_file_type_tika} activity.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TikaDetectionRequest(
        @JsonProperty("file_name") String fileName,
        @JsonProperty("file_location") String fileLocation,
        @JsonProperty("file_type") String fileType
) {}

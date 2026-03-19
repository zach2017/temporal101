package com.example.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared JSON serialization utility.
 * Both services reuse this instead of duplicating Gson setup.
 */
public final class JsonUtil {

    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonUtil() {} // prevent instantiation

    public static String toJson(Object obj) {
        log.debug("Serializing {}", obj.getClass().getSimpleName());
        return GSON.toJson(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        log.debug("Deserializing to {}", clazz.getSimpleName());
        return GSON.fromJson(json, clazz);
    }
}

package com.fileshuttle.provider;

import com.fileshuttle.model.LocationType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory/registry mapping LocationType → StorageProvider.
 * Providers are lazily created and cached (clients are expensive but thread-safe).
 */
public class ProviderFactory {

    private final Map<LocationType, StorageProvider> cache = new ConcurrentHashMap<>();

    public StorageProvider getProvider(LocationType type) {
        return cache.computeIfAbsent(type, this::createProvider);
    }

    /** Register a custom provider (for testing / MinIO override). */
    public void register(LocationType type, StorageProvider provider) {
        cache.put(type, provider);
    }

    private StorageProvider createProvider(LocationType type) {
        return switch (type) {
            case LOCAL -> new LocalFSProvider();
            case S3    -> new S3Provider();
            case NFS   -> new LocalFSProvider();  // NFS mounts are POSIX paths
            case URL   -> new UrlProvider();
        };
    }
}

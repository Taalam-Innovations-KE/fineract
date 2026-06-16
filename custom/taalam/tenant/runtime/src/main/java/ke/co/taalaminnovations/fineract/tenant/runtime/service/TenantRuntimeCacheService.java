/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class TenantRuntimeCacheService {

    private static final List<String> TENANT_RUNTIME_CACHES = List.of("tenantsById", "usersByUsername");

    private final CacheManager cacheManager;

    public TenantRuntimeCacheService(@Qualifier("runtimeDelegatingCacheManager") CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void clearTenantRuntimeCaches() {
        for (String cacheName : TENANT_RUNTIME_CACHES) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}

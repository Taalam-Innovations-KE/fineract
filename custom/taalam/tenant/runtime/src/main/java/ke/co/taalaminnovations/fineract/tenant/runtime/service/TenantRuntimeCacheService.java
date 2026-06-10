/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
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

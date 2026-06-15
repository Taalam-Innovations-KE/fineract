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

import jakarta.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.List;
import javax.sql.DataSource;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRegistrationRequest;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRegistrationResponse;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.database.DatabasePasswordEncryptor;
import org.apache.fineract.infrastructure.core.service.database.DatabaseType;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.apache.fineract.infrastructure.core.service.database.RoutingDataSource;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TenantRuntimeRefreshService {

    private final DatabasePasswordEncryptor databasePasswordEncryptor;
    private final DatabaseTypeResolver databaseTypeResolver;
    private final SingleTenantMigrationService migrationService;
    private final TenantStoreRegistrationService tenantStoreRegistrationService;
    private final TenantRuntimeCacheService cacheService;
    private final TenantDetailsService tenantDetailsService;
    private final RoutingDataSource routingDataSource;
    private final TenantRuntimeAuthenticationVerifier authenticationVerifier;
    private final List<TenantIdentityProviderProvisioningService> identityProviderProvisioningServices;

    public RuntimeTenantRegistrationResponse registerAndRefresh(RuntimeTenantRegistrationRequest request) {
        RuntimeTenantRegistrationRequest normalizedRequest = normalizeAndValidate(request);
        ensureIdentityProviderTenant(normalizedRequest);
        boolean tenantAlreadyRegistered = tenantStoreRegistrationService.isTenantRegistered(normalizedRequest.tenantIdentifier());
        FineractPlatformTenant migrationTenant = tenantAlreadyRegistered ? loadTenant(normalizedRequest.tenantIdentifier())
                : transientTenant(normalizedRequest);

        boolean migrated = migrationService.migrate(migrationTenant);
        boolean registered = false;
        if (!tenantAlreadyRegistered) {
            RuntimeTenantConnection connection = transientConnection(normalizedRequest);
            tenantStoreRegistrationService.register(normalizedRequest, connection.encryptedPassword(), connection.masterPasswordHash());
            registered = true;
        }

        cacheService.clearTenantRuntimeCaches();
        FineractPlatformTenant runtimeTenant = loadTenant(normalizedRequest.tenantIdentifier());
        warmTenantDataSource(runtimeTenant);
        boolean authenticationVerified = authenticationVerifier.verifyIfEnabled(runtimeTenant);
        return new RuntimeTenantRegistrationResponse(runtimeTenant.getTenantIdentifier(), runtimeTenant.getConnection().getSchemaName(),
                registered, migrated, true, authenticationVerified, "READY", Instant.now());
    }

    private void ensureIdentityProviderTenant(RuntimeTenantRegistrationRequest request) {
        for (TenantIdentityProviderProvisioningService service : identityProviderProvisioningServices) {
            try {
                service.ensureTenant(request);
            } catch (RuntimeException e) {
                throw new TenantRuntimeException(Response.Status.BAD_GATEWAY,
                        "Tenant identity provider provisioning failed for tenant " + request.tenantIdentifier(), e);
            }
        }
    }

    private RuntimeTenantRegistrationRequest normalizeAndValidate(RuntimeTenantRegistrationRequest request) {
        if (request == null) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, "Tenant runtime request is required");
        }
        String tenantIdentifier = requireText(request.tenantIdentifier(), "tenant_identifier");
        String displayName = StringUtils.hasText(request.displayName()) ? request.displayName() : tenantIdentifier;
        String timezone = StringUtils.hasText(request.timezone()) ? request.timezone() : "UTC";
        validateTimezone(timezone);
        DatabaseType databaseType = normalizeDatabaseType(request.databaseType());
        String databaseHost = StringUtils.hasText(request.databaseHost()) ? request.databaseHost() : "localhost";
        Integer databasePort = request.databasePort() == null ? defaultPort(databaseType) : request.databasePort();
        if (databasePort < 1 || databasePort > 65_535) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, "database_port must be between 1 and 65535");
        }
        String databaseName = requireText(request.databaseName(), "database_name");
        String runtimeUsername = requireText(request.runtimeUsername(), "runtime_username");
        String runtimePassword = requireText(request.runtimePassword(), "runtime_password");
        return new RuntimeTenantRegistrationRequest(tenantIdentifier, displayName, timezone, databaseType.name(), databaseHost,
                databasePort, databaseName, runtimeUsername, runtimePassword, request.connectionParameters());
    }

    private DatabaseType normalizeDatabaseType(String requestedDatabaseType) {
        DatabaseType configuredDatabaseType = databaseTypeResolver.databaseType();
        if (!StringUtils.hasText(requestedDatabaseType)) {
            return configuredDatabaseType;
        }
        String normalized = requestedDatabaseType.trim().toUpperCase();
        DatabaseType requestedType;
        if ("MARIADB".equals(normalized)) {
            requestedType = DatabaseType.MYSQL;
        } else {
            try {
                requestedType = DatabaseType.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                throw new TenantRuntimeException(Response.Status.BAD_REQUEST, "database_type is not supported by this Fineract runtime", e);
            }
        }
        if (requestedType != configuredDatabaseType) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST,
                    "database_type must match the configured Fineract runtime database type: " + configuredDatabaseType.name());
        }
        return requestedType;
    }

    private int defaultPort(DatabaseType databaseType) {
        return databaseType.isMySql() ? 3306 : 5432;
    }

    private FineractPlatformTenant transientTenant(RuntimeTenantRegistrationRequest request) {
        return transientConnection(request).toTenant(-1L);
    }

    private RuntimeTenantConnection transientConnection(RuntimeTenantRegistrationRequest request) {
        return new RuntimeTenantConnection(request, databasePasswordEncryptor.encrypt(request.runtimePassword()),
                databasePasswordEncryptor.getMasterPasswordHash());
    }

    private FineractPlatformTenant loadTenant(String tenantIdentifier) {
        try {
            cacheService.clearTenantRuntimeCaches();
            return tenantDetailsService.loadTenantById(tenantIdentifier);
        } catch (RuntimeException e) {
            throw new TenantRuntimeException(Response.Status.NOT_FOUND, "Tenant " + tenantIdentifier + " is not registered in Fineract", e);
        }
    }

    private void warmTenantDataSource(FineractPlatformTenant tenant) {
        try {
            ThreadLocalContextUtil.setTenant(tenant);
            DataSource dataSource = routingDataSource.determineTargetDataSource();
            try (Connection connection = dataSource.getConnection()) {
                connection.isValid(1);
                // Open and close once so the runtime pool is created before the first login request.
            }
        } catch (SQLException e) {
            throw new TenantRuntimeException(Response.Status.INTERNAL_SERVER_ERROR,
                    "Unable to initialize runtime datasource for tenant " + tenant.getTenantIdentifier(), e);
        } finally {
            ThreadLocalContextUtil.reset();
        }
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (ZoneRulesException e) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, "timezone must be a valid IANA timezone", e);
        }
    }
}

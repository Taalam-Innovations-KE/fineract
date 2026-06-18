/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import jakarta.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRefreshRegisteredRequest;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRegistrationRequest;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRegistrationResponse;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserSyncRequest;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserSyncResponse;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
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
    private final TenantRuntimeUserSyncService tenantRuntimeUserSyncService;

    public RuntimeTenantRegistrationResponse registerAndRefresh(RuntimeTenantRegistrationRequest request) {
        RuntimeTenantRegistrationRequest normalizedRequest = normalizeAndValidate(request);
        boolean tenantAlreadyRegistered = tenantStoreRegistrationService.isTenantRegistered(normalizedRequest.tenantIdentifier());
        boolean registered = false;
        if (!tenantAlreadyRegistered) {
            tenantStoreRegistrationService.register(normalizedRequest,
                    databasePasswordEncryptor.encrypt(normalizedRequest.runtimePassword()),
                    databasePasswordEncryptor.getMasterPasswordHash());
            registered = true;
        }

        cacheService.clearTenantRuntimeCaches();
        FineractPlatformTenant runtimeTenant = loadTenant(normalizedRequest.tenantIdentifier());
        validateRegisteredTenantMatchesRegistrationRequest(runtimeTenant, normalizedRequest);
        return refreshRuntimeTenant(runtimeTenant, normalizedRequest, registered);
    }

    public RuntimeTenantRegistrationResponse refreshRegistered(RuntimeTenantRefreshRegisteredRequest request) {
        RuntimeTenantRefreshRegisteredRequest normalizedRequest = normalizeAndValidateRegisteredRefresh(request);
        FineractPlatformTenant runtimeTenant = loadTenant(normalizedRequest.tenantIdentifier());
        validateRegisteredTenantMatchesRefreshRequest(runtimeTenant, normalizedRequest);
        RuntimeTenantRegistrationRequest identityProviderRequest = toIdentityProviderRequest(normalizedRequest, runtimeTenant);
        return refreshRuntimeTenant(runtimeTenant, identityProviderRequest, false);
    }

    private RuntimeTenantRegistrationResponse refreshRuntimeTenant(FineractPlatformTenant runtimeTenant,
            RuntimeTenantRegistrationRequest identityProviderRequest, boolean registered) {
        ensureIdentityProviderTenant(identityProviderRequest);
        boolean migrated = migrationService.migrate(runtimeTenant);
        cacheService.clearTenantRuntimeCaches();
        FineractPlatformTenant refreshedTenant = loadTenant(runtimeTenant.getTenantIdentifier());
        warmTenantDataSource(refreshedTenant);
        boolean authenticationVerified = authenticationVerifier.verifyIfEnabled(refreshedTenant);
        TenantRuntimeUserSyncResponse userSyncResponse = syncUsersIfRequested(refreshedTenant, identityProviderRequest);
        return new RuntimeTenantRegistrationResponse(refreshedTenant.getTenantIdentifier(), refreshedTenant.getConnection().getSchemaName(),
                registered, migrated, true, authenticationVerified, "READY", Instant.now(), userSyncResponse);
    }

    private TenantRuntimeUserSyncResponse syncUsersIfRequested(FineractPlatformTenant tenant, RuntimeTenantRegistrationRequest request) {
        if (request.userSync() == null) {
            return null;
        }
        TenantRuntimeUserSyncRequest requestedSync = request.userSync();
        TenantRuntimeUserSyncRequest syncRequest = new TenantRuntimeUserSyncRequest(tenant.getTenantIdentifier(),
                tenant.getConnection().getSchemaName(), requestedSync.dryRun(), requestedSync.provisionIdentity(),
                requestedSync.userDefaults(), requestedSync.users());
        return tenantRuntimeUserSyncService.syncUsers(syncRequest);
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
                databasePort, databaseName, runtimeUsername, runtimePassword, request.connectionParameters(), request.userSync());
    }

    private RuntimeTenantRefreshRegisteredRequest normalizeAndValidateRegisteredRefresh(RuntimeTenantRefreshRegisteredRequest request) {
        if (request == null) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, "Tenant runtime refresh request is required");
        }
        String tenantIdentifier = requireText(request.tenantIdentifier(), "tenant_identifier");
        String displayName = StringUtils.hasText(request.displayName()) ? request.displayName().trim() : tenantIdentifier;
        String timezone = StringUtils.hasText(request.timezone()) ? request.timezone().trim() : "UTC";
        validateTimezone(timezone);
        String databaseName = requireText(request.databaseName(), "database_name");
        return new RuntimeTenantRefreshRegisteredRequest(tenantIdentifier, displayName, timezone, databaseName);
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

    private void validateRegisteredTenantMatchesRegistrationRequest(FineractPlatformTenant tenant,
            RuntimeTenantRegistrationRequest request) {
        FineractPlatformTenantConnection connection = tenant.getConnection();
        requireTenantConnection(tenant, connection);
        assertMatches("database_name", connection.getSchemaName(), request.databaseName(), tenant.getTenantIdentifier());
        assertMatches("database_host", connection.getSchemaServer(), request.databaseHost(), tenant.getTenantIdentifier());
        assertMatches("database_port", connection.getSchemaServerPort(), String.valueOf(request.databasePort()),
                tenant.getTenantIdentifier());
        assertMatches("runtime_username", connection.getSchemaUsername(), request.runtimeUsername(), tenant.getTenantIdentifier());
    }

    private void validateRegisteredTenantMatchesRefreshRequest(FineractPlatformTenant tenant,
            RuntimeTenantRefreshRegisteredRequest request) {
        FineractPlatformTenantConnection connection = tenant.getConnection();
        requireTenantConnection(tenant, connection);
        assertMatches("database_name", connection.getSchemaName(), request.databaseName(), tenant.getTenantIdentifier());
    }

    private void requireTenantConnection(FineractPlatformTenant tenant, FineractPlatformTenantConnection connection) {
        if (connection == null || !StringUtils.hasText(connection.getSchemaName())) {
            throw new TenantRuntimeException(Response.Status.CONFLICT,
                    "Tenant " + tenant.getTenantIdentifier() + " exists without a usable runtime database connection");
        }
    }

    private void assertMatches(String fieldName, String registeredValue, String requestedValue, String tenantIdentifier) {
        if (!Objects.equals(registeredValue, requestedValue)) {
            throw new TenantRuntimeException(Response.Status.CONFLICT, "Registered tenant " + tenantIdentifier + " has " + fieldName + " ["
                    + registeredValue + "] but request contains [" + requestedValue + "]");
        }
    }

    private RuntimeTenantRegistrationRequest toIdentityProviderRequest(RuntimeTenantRefreshRegisteredRequest request,
            FineractPlatformTenant tenant) {
        FineractPlatformTenantConnection connection = tenant.getConnection();
        return new RuntimeTenantRegistrationRequest(request.tenantIdentifier(), request.displayName(), request.timezone(),
                databaseTypeResolver.databaseType().name(), connection.getSchemaServer(), schemaServerPort(tenant, connection),
                connection.getSchemaName(), connection.getSchemaUsername(), null, connection.getSchemaConnectionParameters());
    }

    private Integer schemaServerPort(FineractPlatformTenant tenant, FineractPlatformTenantConnection connection) {
        try {
            return Integer.valueOf(connection.getSchemaServerPort());
        } catch (NumberFormatException e) {
            throw new TenantRuntimeException(Response.Status.CONFLICT,
                    "Tenant " + tenant.getTenantIdentifier() + " has an invalid runtime database port", e);
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

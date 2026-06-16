/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRegistrationRequest;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRegistrationResponse;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.database.DatabasePasswordEncryptor;
import org.apache.fineract.infrastructure.core.service.database.DatabaseType;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.apache.fineract.infrastructure.core.service.database.RoutingDataSource;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantRuntimeRefreshServiceTest {

    private final DatabasePasswordEncryptor databasePasswordEncryptor = mock(DatabasePasswordEncryptor.class);
    private final DatabaseTypeResolver databaseTypeResolver = mock(DatabaseTypeResolver.class);
    private final SingleTenantMigrationService migrationService = mock(SingleTenantMigrationService.class);
    private final TenantStoreRegistrationService tenantStoreRegistrationService = mock(TenantStoreRegistrationService.class);
    private final TenantRuntimeCacheService cacheService = mock(TenantRuntimeCacheService.class);
    private final TenantDetailsService tenantDetailsService = mock(TenantDetailsService.class);
    private final RoutingDataSource routingDataSource = mock(RoutingDataSource.class);
    private final TenantRuntimeAuthenticationVerifier authenticationVerifier = mock(TenantRuntimeAuthenticationVerifier.class);
    private final TenantIdentityProviderProvisioningService identityProviderProvisioningService = mock(
            TenantIdentityProviderProvisioningService.class);

    @AfterEach
    void resetThreadLocalContext() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void registerAndRefreshVerifiesAuthenticationForTheNewTenant() throws Exception {
        RuntimeTenantRegistrationRequest request = new RuntimeTenantRegistrationRequest("new_ke", "New Kenya Tenant", "Africa/Nairobi",
                "POSTGRESQL", "localhost", 5432, "fineract_new_ke", "fineract_new_ke", "tenant-password", null);
        FineractPlatformTenant runtimeTenant = tenant("new_ke", 33L);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(databasePasswordEncryptor.encrypt("tenant-password")).thenReturn("encrypted-password");
        when(databaseTypeResolver.databaseType()).thenReturn(DatabaseType.POSTGRESQL);
        when(databasePasswordEncryptor.getMasterPasswordHash()).thenReturn("master-password-hash");
        when(tenantStoreRegistrationService.isTenantRegistered("new_ke")).thenReturn(false);
        when(migrationService.migrate(any(FineractPlatformTenant.class))).thenReturn(true);
        when(tenantStoreRegistrationService.register(eq(request), eq("encrypted-password"), eq("master-password-hash"))).thenReturn(33L);
        when(tenantDetailsService.loadTenantById("new_ke")).thenReturn(runtimeTenant);
        when(routingDataSource.determineTargetDataSource()).thenAnswer(invocation -> {
            assertThat(ThreadLocalContextUtil.getTenant()).isEqualTo(runtimeTenant);
            return dataSource;
        });
        when(dataSource.getConnection()).thenReturn(connection);
        when(authenticationVerifier.verifyIfEnabled(runtimeTenant)).thenReturn(true);
        TenantRuntimeRefreshService underTest = new TenantRuntimeRefreshService(databasePasswordEncryptor, databaseTypeResolver,
                migrationService, tenantStoreRegistrationService, cacheService, tenantDetailsService, routingDataSource,
                authenticationVerifier, List.of(identityProviderProvisioningService));

        RuntimeTenantRegistrationResponse response = underTest.registerAndRefresh(request);

        assertThat(response.tenantIdentifier()).isEqualTo("new_ke");
        assertThat(response.registered()).isTrue();
        assertThat(response.migrated()).isTrue();
        assertThat(response.refreshed()).isTrue();
        assertThat(response.authenticationVerified()).isTrue();
        verify(identityProviderProvisioningService).ensureTenant(request);
        verify(migrationService).migrate(any(FineractPlatformTenant.class));
        verify(tenantStoreRegistrationService).register(eq(request), eq("encrypted-password"), eq("master-password-hash"));
        verify(cacheService, atLeastOnce()).clearTenantRuntimeCaches();
        verify(authenticationVerifier).verifyIfEnabled(runtimeTenant);
    }

    @Test
    void registerAndRefreshFailsBeforeMigrationWhenIdentityProviderProvisioningFails() {
        RuntimeTenantRegistrationRequest request = new RuntimeTenantRegistrationRequest("new_ke", "New Kenya Tenant", "Africa/Nairobi",
                "POSTGRESQL", "localhost", 5432, "fineract_new_ke", "fineract_new_ke", "tenant-password", null);
        when(databaseTypeResolver.databaseType()).thenReturn(DatabaseType.POSTGRESQL);
        org.mockito.Mockito.doThrow(new IllegalStateException("keycloak unavailable")).when(identityProviderProvisioningService)
                .ensureTenant(request);
        TenantRuntimeRefreshService underTest = new TenantRuntimeRefreshService(databasePasswordEncryptor, databaseTypeResolver,
                migrationService, tenantStoreRegistrationService, cacheService, tenantDetailsService, routingDataSource,
                authenticationVerifier, List.of(identityProviderProvisioningService));

        assertThatThrownBy(() -> underTest.registerAndRefresh(request)).isInstanceOf(TenantRuntimeException.class)
                .hasMessageContaining("Tenant identity provider provisioning failed for tenant new_ke");

        verify(identityProviderProvisioningService).ensureTenant(request);
        verifyNoInteractions(migrationService, tenantStoreRegistrationService, tenantDetailsService, routingDataSource,
                authenticationVerifier);
    }

    private FineractPlatformTenant tenant(String identifier, Long connectionId) {
        return new FineractPlatformTenant(1L, identifier, "New Tenant", "Africa/Nairobi",
                new FineractPlatformTenantConnection(connectionId, "fineract_new_ke", "localhost", "5432", null, "fineract_new_ke",
                        "encrypted", true, 5, 30_000L, true, 60, true, 50, 40, 20, 10, 60, 34_000, 60_000, true, null, null, null, null,
                        null, null, "hash"));
    }
}

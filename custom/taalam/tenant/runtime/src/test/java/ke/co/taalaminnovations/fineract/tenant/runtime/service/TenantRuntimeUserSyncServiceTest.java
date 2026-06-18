/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserSyncDefaults;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserSyncRequest;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserSyncResponse;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserSyncUser;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.apache.fineract.infrastructure.security.service.PlatformPasswordEncoder;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.organisation.staff.domain.StaffRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.domain.UserDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class TenantRuntimeUserSyncServiceTest {

    private final TenantDetailsService tenantDetailsService = mock(TenantDetailsService.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final UserDomainService userDomainService = mock(UserDomainService.class);
    private final OfficeRepository officeRepository = mock(OfficeRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final PlatformPasswordEncoder platformPasswordEncoder = mock(PlatformPasswordEncoder.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final TenantRuntimeUserIdentityProvisioningService identityProvisioningService = mock(
            TenantRuntimeUserIdentityProvisioningService.class);
    private final TenantRuntimeUserSyncService underTest = new TenantRuntimeUserSyncService(tenantDetailsService, appUserRepository,
            userDomainService, officeRepository, roleRepository, staffRepository, platformPasswordEncoder, new FromJsonHelper(),
            transactionTemplate, List.of(identityProvisioningService));

    private final Office office = mock(Office.class);
    private final Role role = new Role("Super user", "All permissions");

    @BeforeEach
    void setUp() {
        role.setId(1L);
        when(office.getId()).thenReturn(1L);
        when(officeRepository.findById(1L)).thenReturn(Optional.of(office));
        when(roleRepository.getRoleByName("Super user")).thenReturn(role);
        when(tenantDetailsService.loadTenantById("new_ke")).thenReturn(tenant("new_ke"));
        when(identityProvisioningService.provisionUser(eq("new_ke"), any(TenantRuntimeUserSyncUser.class)))
                .thenReturn(new TenantRuntimeUserIdentityProvisioningResult("CREATED", "created Keycloak user"));
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());
    }

    @AfterEach
    void resetThreadLocalContext() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void createsLocalUserAndProvisionsIdentity() {
        doAnswer(invocation -> {
            AppUser appUser = invocation.getArgument(0);
            appUser.setId(42L);
            return null;
        }).when(userDomainService).create(any(AppUser.class), eq(Boolean.FALSE));

        TenantRuntimeUserSyncResponse response = underTest.syncUsers(request(user("jane", "jane@example.org")));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).appUserId()).isEqualTo(42L);
        assertThat(response.results().get(0).localStatus()).isEqualTo("CREATED");
        assertThat(response.results().get(0).identityStatus()).isEqualTo("CREATED");

        ArgumentCaptor<AppUser> appUserCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userDomainService).create(appUserCaptor.capture(), eq(Boolean.FALSE));
        assertThat(appUserCaptor.getValue().getUsername()).isEqualTo("jane");
        assertThat(appUserCaptor.getValue().getEmail()).isEqualTo("jane@example.org");
        verify(identityProvisioningService).provisionUser(eq("new_ke"), any(TenantRuntimeUserSyncUser.class));
    }

    @Test
    void reportsDuplicateUsersInRequestWithoutCallingRepositoriesForDuplicates() {
        TenantRuntimeUserSyncResponse response = underTest
                .syncUsers(request(user("jane", "jane@example.org"), user("jane", "jane.duplicate@example.org")));

        assertThat(response.status()).isEqualTo("COMPLETED_WITH_CONFLICTS");
        assertThat(response.requested()).isEqualTo(2);
        assertThat(response.conflicts()).isEqualTo(1);
        assertThat(response.results()).extracting("localStatus").contains("CREATED", "CONFLICT");
    }

    @Test
    void userOfficeSelectionOverridesDefaultOfficeSelection() {
        doAnswer(invocation -> {
            AppUser appUser = invocation.getArgument(0);
            appUser.setId(42L);
            return null;
        }).when(userDomainService).create(any(AppUser.class), eq(Boolean.FALSE));

        TenantRuntimeUserSyncRequest request = new TenantRuntimeUserSyncRequest("new_ke", "fineract_new_ke", false, true,
                new TenantRuntimeUserSyncDefaults(null, "head-office", List.of("Super user")),
                List.of(new TenantRuntimeUserSyncUser("jane", "jane", "jane@example.org", "Jane", "Doe", 1L, null, null, null)));

        TenantRuntimeUserSyncResponse response = underTest.syncUsers(request);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.created()).isEqualTo(1);
        verify(officeRepository).findById(1L);
    }

    @Test
    void reportsConflictWhenEmailBelongsToAnotherActiveUser() {
        AppUser existingByEmail = mock(AppUser.class);
        when(existingByEmail.getId()).thenReturn(99L);
        when(appUserRepository.findActiveUserByEmail("jane@example.org")).thenReturn(existingByEmail);

        TenantRuntimeUserSyncResponse response = underTest.syncUsers(request(user("jane", "jane@example.org")));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.conflicts()).isEqualTo(1);
        assertThat(response.results().get(0).message()).isEqualTo("Email is already assigned to another active user");
        verifyNoInteractions(identityProvisioningService);
        verify(userDomainService, never()).create(any(), any());
    }

    @Test
    void reportsIdentityFailureWithoutRollingBackLocalCreate() {
        doAnswer(invocation -> {
            AppUser appUser = invocation.getArgument(0);
            appUser.setId(42L);
            return null;
        }).when(userDomainService).create(any(AppUser.class), eq(Boolean.FALSE));
        when(identityProvisioningService.provisionUser(eq("new_ke"), any(TenantRuntimeUserSyncUser.class)))
                .thenReturn(new TenantRuntimeUserIdentityProvisioningResult("FAILED", "Keycloak unavailable"));

        TenantRuntimeUserSyncResponse response = underTest.syncUsers(request(user("jane", "jane@example.org")));

        assertThat(response.status()).isEqualTo("COMPLETED_WITH_CONFLICTS");
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.results().get(0).localStatus()).isEqualTo("CREATED");
        assertThat(response.results().get(0).identityStatus()).isEqualTo("FAILED");
        verify(userDomainService).create(any(AppUser.class), eq(Boolean.FALSE));
    }

    private TenantRuntimeUserSyncRequest request(TenantRuntimeUserSyncUser... users) {
        return new TenantRuntimeUserSyncRequest("new_ke", "fineract_new_ke", false, true,
                new TenantRuntimeUserSyncDefaults(1L, null, List.of("Super user")), List.of(users));
    }

    private TenantRuntimeUserSyncUser user(String username, String email) {
        return new TenantRuntimeUserSyncUser(username, username, email, "Jane", "Doe", null, null, null, null);
    }

    private FineractPlatformTenant tenant(String identifier) {
        return new FineractPlatformTenant(1L, identifier, "New Tenant", "Africa/Nairobi",
                new FineractPlatformTenantConnection(33L, "fineract_new_ke", "localhost", "5432", null, "fineract_new_ke", "encrypted",
                        true, 5, 30_000L, true, 60, true, 50, 40, 20, 10, 60, 34_000, 60_000, true, null, null, null, null, null, null,
                        "hash"));
    }
}

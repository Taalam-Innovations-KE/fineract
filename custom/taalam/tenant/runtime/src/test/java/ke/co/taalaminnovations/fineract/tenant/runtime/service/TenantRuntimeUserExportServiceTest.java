/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Set;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserExportRequest;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserExportResponse;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class TenantRuntimeUserExportServiceTest {

    private final TenantDetailsService tenantDetailsService = mock(TenantDetailsService.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final TenantRuntimeUserExportService underTest = new TenantRuntimeUserExportService(tenantDetailsService, appUserRepository,
            transactionTemplate);

    private final Office office = mock(Office.class);
    private final Staff staff = mock(Staff.class);

    @BeforeEach
    void setUp() {
        when(tenantDetailsService.loadTenantById("default")).thenReturn(tenant("default"));
        when(office.getId()).thenReturn(1L);
        when(office.getName()).thenReturn("Head Office");
        when(office.getExternalId()).thenReturn(new ExternalId("office-ext"));
        when(staff.getId()).thenReturn(11L);
        when(staff.getDisplayName()).thenReturn("Jane Doe");
        when(staff.getExternalId()).thenReturn("staff-ext");
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @AfterEach
    void resetThreadLocalContext() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void exportsActiveRealUsersAndSkipsServiceDisabledAndDeletedUsersByDefault() {
        List<AppUser> users = List.of(user(4L, "jane", true, false), user(2L, "system", true, false), user(3L, "interopUser", true, false),
                user(5L, "disabled", false, false), user(6L, "deleted", true, true));
        when(appUserRepository.findAll()).thenReturn(users);

        TenantRuntimeUserExportResponse response = underTest.exportUsers(request(false, false, false));

        assertThat(response.tenantIdentifier()).isEqualTo("default");
        assertThat(response.databaseName()).isEqualTo("fineract_default");
        assertThat(response.totalFineractUsers()).isEqualTo(5);
        assertThat(response.exported()).isEqualTo(1);
        assertThat(response.skipped()).isEqualTo(4);
        assertThat(response.users()).hasSize(1);
        assertThat(response.users().get(0).sourceUserId()).isEqualTo("4");
        assertThat(response.users().get(0).username()).isEqualTo("jane");
        assertThat(response.users().get(0).officeExternalId()).isEqualTo("office-ext");
        assertThat(response.users().get(0).staffExternalId()).isEqualTo("staff-ext");
        assertThat(response.users().get(0).roleNames()).containsExactly("Super user", "Teller");
    }

    @Test
    void canIncludeDisabledDeletedAndSystemUsersWhenRequested() {
        List<AppUser> users = List.of(user(4L, "jane", true, false), user(2L, "system", true, false), user(3L, "interopUser", true, false),
                user(5L, "disabled", false, false), user(6L, "deleted", true, true));
        when(appUserRepository.findAll()).thenReturn(users);

        TenantRuntimeUserExportResponse response = underTest.exportUsers(request(true, true, true));

        assertThat(response.exported()).isEqualTo(5);
        assertThat(response.skipped()).isZero();
        assertThat(response.users()).extracting("username").containsExactly("system", "interopUser", "jane", "disabled", "deleted");
    }

    @Test
    void rejectsDatabaseNameMismatchWhenProvided() {
        TenantRuntimeUserExportRequest request = new TenantRuntimeUserExportRequest("default", "other_database", false, false, false);

        assertThatThrownBy(() -> underTest.exportUsers(request)).isInstanceOfSatisfying(TenantRuntimeException.class, exception -> {
            assertThat(exception.getStatus()).isEqualTo(Response.Status.CONFLICT);
            assertThat(exception.getMessage()).contains("database_name");
        });
    }

    private TenantRuntimeUserExportRequest request(boolean includeDisabled, boolean includeDeleted, boolean includeSystemUsers) {
        return new TenantRuntimeUserExportRequest("default", null, includeDisabled, includeDeleted, includeSystemUsers);
    }

    private AppUser user(Long id, String username, boolean enabled, boolean deleted) {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        when(user.getEmail()).thenReturn(username + "@example.org");
        when(user.getFirstname()).thenReturn("Jane");
        when(user.getLastname()).thenReturn("Doe");
        when(user.getDisplayName()).thenReturn("Jane Doe");
        when(user.isEnabled()).thenReturn(enabled);
        when(user.isDeleted()).thenReturn(deleted);
        when(user.getOffice()).thenReturn(office);
        when(user.getStaff()).thenReturn(staff);
        when(user.getRoles()).thenReturn(Set.of(new Role("Teller", "Teller role"), new Role("Super user", "All permissions")));
        return user;
    }

    private FineractPlatformTenant tenant(String identifier) {
        return new FineractPlatformTenant(1L, identifier, "Default Tenant", "Africa/Nairobi",
                new FineractPlatformTenantConnection(33L, "fineract_default", "localhost", "5432", null, "fineract_default", "encrypted",
                        true, 5, 30_000L, true, 60, true, 50, 40, 20, 10, 60, 34_000, 60_000, true, null, null, null, null, null, null,
                        "hash"));
    }
}

/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserExportRequest;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserExportResponse;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserExportedUser;
import lombok.RequiredArgsConstructor;
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
import org.apache.fineract.useradministration.service.AppUserConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TenantRuntimeUserExportService {

    private static final Set<String> DEFAULT_SERVICE_USERNAMES = Set.of(AppUserConstants.SYSTEM_USER_NAME.toLowerCase(Locale.ROOT),
            "interopuser");

    private final TenantDetailsService tenantDetailsService;
    private final AppUserRepository appUserRepository;
    private final TransactionTemplate transactionTemplate;

    public TenantRuntimeUserExportResponse exportUsers(final TenantRuntimeUserExportRequest request) {
        final TenantRuntimeUserExportRequest normalizedRequest = normalizeAndValidate(request);
        final FineractPlatformTenant tenant = loadTenant(normalizedRequest.tenantIdentifier());
        validateDatabaseNameIfProvided(tenant, normalizedRequest.databaseName());

        try {
            ThreadLocalContextUtil.setTenant(tenant);
            return transactionTemplate.execute(status -> exportTenantUsers(tenant, normalizedRequest));
        } finally {
            ThreadLocalContextUtil.reset();
        }
    }

    private TenantRuntimeUserExportRequest normalizeAndValidate(final TenantRuntimeUserExportRequest request) {
        if (request == null) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, "Tenant user export request is required");
        }
        final String tenantIdentifier = requireText(request.tenantIdentifier(), "tenant_identifier");
        final String databaseName = StringUtils.hasText(request.databaseName()) ? request.databaseName().trim() : null;
        return new TenantRuntimeUserExportRequest(tenantIdentifier, databaseName, Boolean.TRUE.equals(request.includeDisabled()),
                Boolean.TRUE.equals(request.includeDeleted()), Boolean.TRUE.equals(request.includeSystemUsers()));
    }

    private FineractPlatformTenant loadTenant(final String tenantIdentifier) {
        try {
            return tenantDetailsService.loadTenantById(tenantIdentifier);
        } catch (RuntimeException e) {
            throw new TenantRuntimeException(Response.Status.NOT_FOUND, "Tenant " + tenantIdentifier + " is not registered in Fineract", e);
        }
    }

    private void validateDatabaseNameIfProvided(final FineractPlatformTenant tenant, final String databaseName) {
        final FineractPlatformTenantConnection connection = tenant.getConnection();
        if (connection == null || !StringUtils.hasText(connection.getSchemaName())) {
            throw new TenantRuntimeException(Response.Status.CONFLICT,
                    "Tenant " + tenant.getTenantIdentifier() + " exists without a usable runtime database connection");
        }
        if (StringUtils.hasText(databaseName) && !Objects.equals(connection.getSchemaName(), databaseName)) {
            throw new TenantRuntimeException(Response.Status.CONFLICT, "Registered tenant " + tenant.getTenantIdentifier()
                    + " has database_name [" + connection.getSchemaName() + "] but request contains [" + databaseName + "]");
        }
    }

    private TenantRuntimeUserExportResponse exportTenantUsers(final FineractPlatformTenant tenant,
            final TenantRuntimeUserExportRequest request) {
        final List<AppUser> allUsers = appUserRepository.findAll();
        final List<TenantRuntimeUserExportedUser> exportedUsers = allUsers.stream().filter(user -> shouldExport(user, request))
                .sorted(Comparator.comparing(AppUser::getId)).map(this::toExportedUser).toList();

        return new TenantRuntimeUserExportResponse(tenant.getTenantIdentifier(), tenant.getConnection().getSchemaName(), allUsers.size(),
                exportedUsers.size(), allUsers.size() - exportedUsers.size(), List.copyOf(exportedUsers), Instant.now());
    }

    private boolean shouldExport(final AppUser user, final TenantRuntimeUserExportRequest request) {
        if (!Boolean.TRUE.equals(request.includeDeleted()) && user.isDeleted()) {
            return false;
        }
        if (!Boolean.TRUE.equals(request.includeDisabled()) && !user.isEnabled()) {
            return false;
        }
        return Boolean.TRUE.equals(request.includeSystemUsers()) || !DEFAULT_SERVICE_USERNAMES.contains(key(user.getUsername()));
    }

    private TenantRuntimeUserExportedUser toExportedUser(final AppUser appUser) {
        final Office office = appUser.getOffice();
        final Staff staff = appUser.getStaff();
        return new TenantRuntimeUserExportedUser(String.valueOf(appUser.getId()), appUser.getId(), appUser.getUsername(),
                appUser.getEmail(), appUser.getFirstname(), appUser.getLastname(), appUser.getDisplayName(), appUser.isEnabled(),
                appUser.isDeleted(), appUser.isEnabled() && !appUser.isDeleted(), office == null ? null : office.getId(),
                office == null ? null : office.getName(), office == null ? null : externalIdValue(office.getExternalId()),
                staff == null ? null : staff.getId(), staff == null ? null : staff.getDisplayName(),
                staff == null ? null : staff.getExternalId(), appUser.getRoles().stream().map(Role::getName).sorted().toList());
    }

    private String externalIdValue(final ExternalId externalId) {
        return externalId == null || externalId.isEmpty() ? null : externalId.getValue();
    }

    private String key(final String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String requireText(final String value, final String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }
}

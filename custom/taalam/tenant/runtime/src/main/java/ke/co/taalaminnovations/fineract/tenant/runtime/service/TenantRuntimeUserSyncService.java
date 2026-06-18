/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserSyncDefaults;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserSyncRequest;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserSyncResponse;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserSyncResult;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.TenantRuntimeUserSyncUser;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.apache.fineract.infrastructure.security.service.PlatformPasswordEncoder;
import org.apache.fineract.infrastructure.security.service.RandomPasswordGenerator;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.organisation.office.exception.OfficeNotFoundException;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepository;
import org.apache.fineract.organisation.staff.exception.StaffNotFoundException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.domain.UserDomainService;
import org.apache.fineract.useradministration.exception.RoleNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TenantRuntimeUserSyncService {

    private static final String CREATED = "CREATED";
    private static final String UPDATED = "UPDATED";
    private static final String UNCHANGED = "UNCHANGED";
    private static final String CONFLICT = "CONFLICT";
    private static final String FAILED = "FAILED";
    private static final String SKIPPED = "SKIPPED";

    private final TenantDetailsService tenantDetailsService;
    private final AppUserRepository appUserRepository;
    private final UserDomainService userDomainService;
    private final OfficeRepository officeRepository;
    private final RoleRepository roleRepository;
    private final StaffRepository staffRepository;
    private final PlatformPasswordEncoder platformPasswordEncoder;
    private final FromJsonHelper fromJsonHelper;
    private final TransactionTemplate transactionTemplate;
    private final List<TenantRuntimeUserIdentityProvisioningService> identityProvisioningServices;

    public TenantRuntimeUserSyncResponse syncUsers(final TenantRuntimeUserSyncRequest request) {
        final TenantRuntimeUserSyncRequest normalizedRequest = normalizeAndValidate(request);
        final FineractPlatformTenant tenant = loadTenant(normalizedRequest.tenantIdentifier());
        validateDatabaseName(tenant, normalizedRequest.databaseName());

        final List<TenantRuntimeUserSyncResult> results = new ArrayList<>();
        results.addAll(batchDuplicateResults(normalizedRequest.users()));

        final Set<Integer> duplicateIndexes = duplicateIndexes(normalizedRequest.users());
        final boolean dryRun = Boolean.TRUE.equals(normalizedRequest.dryRun());
        final boolean provisionIdentity = !Boolean.FALSE.equals(normalizedRequest.provisionIdentity());

        try {
            ThreadLocalContextUtil.setTenant(tenant);
            for (int i = 0; i < normalizedRequest.users().size(); i++) {
                if (duplicateIndexes.contains(i)) {
                    continue;
                }
                final TenantRuntimeUserSyncUser user = normalizedRequest.users().get(i);
                if (dryRun) {
                    results.add(validateUserOnly(normalizedRequest, user));
                    continue;
                }
                final TenantRuntimeUserSyncResult localResult = transactionTemplate
                        .execute(status -> processLocalUser(normalizedRequest, user));
                results.add(withIdentityResult(normalizedRequest.tenantIdentifier(), user, localResult, provisionIdentity));
            }
        } finally {
            ThreadLocalContextUtil.reset();
        }

        return response(tenant, results, normalizedRequest.users().size());
    }

    private TenantRuntimeUserSyncRequest normalizeAndValidate(final TenantRuntimeUserSyncRequest request) {
        if (request == null) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, "Tenant user sync request is required");
        }
        final String tenantIdentifier = requireText(request.tenantIdentifier(), "tenant_identifier");
        final String databaseName = requireText(request.databaseName(), "database_name");
        final List<TenantRuntimeUserSyncUser> users = request.users() == null ? List.of() : List.copyOf(request.users());
        if (users.isEmpty()) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, "users must contain at least one user");
        }
        return new TenantRuntimeUserSyncRequest(tenantIdentifier, databaseName, request.dryRun(), request.provisionIdentity(),
                request.userDefaults(), users);
    }

    private FineractPlatformTenant loadTenant(final String tenantIdentifier) {
        try {
            return tenantDetailsService.loadTenantById(tenantIdentifier);
        } catch (RuntimeException e) {
            throw new TenantRuntimeException(Response.Status.NOT_FOUND, "Tenant " + tenantIdentifier + " is not registered in Fineract", e);
        }
    }

    private void validateDatabaseName(final FineractPlatformTenant tenant, final String databaseName) {
        final FineractPlatformTenantConnection connection = tenant.getConnection();
        if (connection == null || !StringUtils.hasText(connection.getSchemaName())) {
            throw new TenantRuntimeException(Response.Status.CONFLICT,
                    "Tenant " + tenant.getTenantIdentifier() + " exists without a usable runtime database connection");
        }
        if (!Objects.equals(connection.getSchemaName(), databaseName)) {
            throw new TenantRuntimeException(Response.Status.CONFLICT, "Registered tenant " + tenant.getTenantIdentifier()
                    + " has database_name [" + connection.getSchemaName() + "] but request contains [" + databaseName + "]");
        }
    }

    private List<TenantRuntimeUserSyncResult> batchDuplicateResults(final List<TenantRuntimeUserSyncUser> users) {
        final Map<String, Integer> usernames = new HashMap<>();
        final Map<String, Integer> emails = new HashMap<>();
        final Map<Integer, String> duplicateMessages = new HashMap<>();
        final List<TenantRuntimeUserSyncResult> results = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            final TenantRuntimeUserSyncUser user = users.get(i);
            final String usernameKey = key(user.username());
            final String emailKey = key(user.email());
            if (usernameKey != null && usernames.putIfAbsent(usernameKey, i) != null) {
                duplicateMessages.put(i, "Duplicate username in request: " + user.username());
            }
            if (emailKey != null && emails.putIfAbsent(emailKey, i) != null) {
                duplicateMessages.merge(i, "Duplicate email in request: " + user.email(), (left, right) -> left + "; " + right);
            }
        }
        duplicateMessages.forEach((index, message) -> results.add(conflict(users.get(index), message)));
        return results;
    }

    private Set<Integer> duplicateIndexes(final List<TenantRuntimeUserSyncUser> users) {
        final Map<String, Integer> usernames = new HashMap<>();
        final Map<String, Integer> emails = new HashMap<>();
        final Set<Integer> duplicateIndexes = new HashSet<>();
        for (int i = 0; i < users.size(); i++) {
            final TenantRuntimeUserSyncUser user = users.get(i);
            final String usernameKey = key(user.username());
            final String emailKey = key(user.email());
            final Integer usernameIndex = usernameKey == null ? null : usernames.putIfAbsent(usernameKey, i);
            final Integer emailIndex = emailKey == null ? null : emails.putIfAbsent(emailKey, i);
            if (usernameIndex != null || emailIndex != null) {
                duplicateIndexes.add(i);
            }
        }
        return duplicateIndexes;
    }

    private TenantRuntimeUserSyncResult validateUserOnly(final TenantRuntimeUserSyncRequest request, final TenantRuntimeUserSyncUser user) {
        try {
            resolvedUser(request, user);
            return new TenantRuntimeUserSyncResult(user.sourceUserId(), user.username(), user.email(), null, SKIPPED, SKIPPED, "dry_run");
        } catch (RuntimeException e) {
            return failed(user, message(e));
        }
    }

    private TenantRuntimeUserSyncResult processLocalUser(final TenantRuntimeUserSyncRequest request, final TenantRuntimeUserSyncUser user) {
        try {
            final ResolvedUser resolved = resolvedUser(request, user);
            final AppUser existingByUsername = appUserRepository.findAppUserByName(resolved.username());
            final AppUser existingByEmail = appUserRepository.findActiveUserByEmail(resolved.email());

            if (existingByEmail != null
                    && (existingByUsername == null || !Objects.equals(existingByEmail.getId(), existingByUsername.getId()))) {
                return conflict(user, "Email is already assigned to another active user");
            }

            final LocalSync localSync = existingByUsername == null ? createUser(resolved) : updateUser(existingByUsername, resolved);
            return new TenantRuntimeUserSyncResult(user.sourceUserId(), resolved.username(), resolved.email(), localSync.appUserId(),
                    localSync.status(), SKIPPED, null);
        } catch (RuntimeException e) {
            return failed(user, message(e));
        }
    }

    private TenantRuntimeUserSyncResult withIdentityResult(final String tenantIdentifier, final TenantRuntimeUserSyncUser user,
            final TenantRuntimeUserSyncResult localResult, final boolean provisionIdentity) {
        if (FAILED.equals(localResult.localStatus()) || CONFLICT.equals(localResult.localStatus())) {
            return localResult;
        }
        final TenantRuntimeUserIdentityProvisioningResult identityResult;
        try {
            identityResult = provisionIdentity ? provisionIdentity(tenantIdentifier, user)
                    : TenantRuntimeUserIdentityProvisioningResult.skipped("identity provisioning disabled for request");
        } catch (RuntimeException e) {
            return new TenantRuntimeUserSyncResult(localResult.sourceUserId(), localResult.username(), localResult.email(),
                    localResult.appUserId(), localResult.localStatus(), FAILED, message(e));
        }
        return new TenantRuntimeUserSyncResult(localResult.sourceUserId(), localResult.username(), localResult.email(),
                localResult.appUserId(), localResult.localStatus(), identityResult.status(), identityResult.message());
    }

    private LocalSync createUser(final ResolvedUser resolved) {
        final String password = new RandomPasswordGenerator(20).generate();
        final AppUser appUser = new AppUser(resolved.office(),
                new org.springframework.security.core.userdetails.User(resolved.username(), password, true, true, true, true,
                        Collections.emptyList()),
                resolved.roles(), resolved.email(), resolved.firstName(), resolved.lastName(), resolved.staff(), true, false);
        userDomainService.create(appUser, Boolean.FALSE);
        return new LocalSync(CREATED, appUser.getId());
    }

    private LocalSync updateUser(final AppUser appUser, final ResolvedUser resolved) {
        final JsonObject json = new JsonObject();
        json.addProperty("username", resolved.username());
        json.addProperty("email", resolved.email());
        json.addProperty("firstname", resolved.firstName());
        json.addProperty("lastname", resolved.lastName());
        json.addProperty("officeId", resolved.office().getId());
        if (resolved.staff() != null) {
            json.addProperty("staffId", resolved.staff().getId());
        } else if (appUser.getStaff() != null) {
            json.add("staffId", null);
        }
        final JsonArray roleIds = new JsonArray();
        resolved.roles().stream().map(Role::getId).map(String::valueOf).forEach(roleIds::add);
        json.add("roles", roleIds);

        final JsonCommand command = new JsonCommand(appUser.getId(), json, fromJsonHelper);
        final Map<String, Object> changes = appUser.update(command, platformPasswordEncoder);
        if (changes.containsKey("officeId")) {
            appUser.changeOffice(resolved.office());
        }
        if (changes.containsKey("staffId")) {
            appUser.changeStaff(resolved.staff());
        }
        if (changes.containsKey("roles")) {
            appUser.updateRoles(resolved.roles());
        }
        if (changes.isEmpty()) {
            return new LocalSync(UNCHANGED, appUser.getId());
        }
        appUserRepository.saveAndFlush(appUser);
        return new LocalSync(UPDATED, appUser.getId());
    }

    private TenantRuntimeUserIdentityProvisioningResult provisionIdentity(final String tenantIdentifier,
            final TenantRuntimeUserSyncUser user) {
        TenantRuntimeUserIdentityProvisioningResult result = TenantRuntimeUserIdentityProvisioningResult
                .skipped("no identity provisioning service configured");
        for (TenantRuntimeUserIdentityProvisioningService service : identityProvisioningServices) {
            result = service.provisionUser(tenantIdentifier, user);
            if (FAILED.equals(result.status()) || CONFLICT.equals(result.status())) {
                return result;
            }
        }
        return result;
    }

    private ResolvedUser resolvedUser(final TenantRuntimeUserSyncRequest request, final TenantRuntimeUserSyncUser user) {
        final String username = requireText(user.username(), "username");
        final String email = requireText(user.email(), "email");
        final String firstName = requireText(user.firstName(), "first_name");
        final String lastName = requireText(user.lastName(), "last_name");
        final Office office = resolveOffice(request.userDefaults(), user);
        final Set<Role> roles = resolveRoles(request.userDefaults(), user);
        final Staff staff = resolveStaff(user.staffId(), office);
        return new ResolvedUser(username, email, firstName, lastName, office, roles, staff);
    }

    private Office resolveOffice(final TenantRuntimeUserSyncDefaults defaults, final TenantRuntimeUserSyncUser user) {
        final boolean userHasOfficeId = user.officeId() != null;
        final boolean userHasOfficeExternalId = StringUtils.hasText(user.officeExternalId());
        if (userHasOfficeId && userHasOfficeExternalId) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, "Specify either office_id or office_external_id, not both");
        }

        final Long officeId;
        final String officeExternalId;
        if (userHasOfficeId || userHasOfficeExternalId) {
            officeId = user.officeId();
            officeExternalId = user.officeExternalId();
        } else {
            officeId = defaults == null ? null : defaults.officeId();
            officeExternalId = defaults == null ? null : defaults.officeExternalId();
        }
        if (officeId != null && StringUtils.hasText(officeExternalId)) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, "Specify either office_id or office_external_id, not both");
        }
        if (officeId != null) {
            return officeRepository.findById(officeId).orElseThrow(() -> new OfficeNotFoundException(officeId));
        }
        if (StringUtils.hasText(officeExternalId)) {
            final ExternalId externalId = ExternalIdFactory.produce(officeExternalId.trim());
            return officeRepository.findByExternalId(externalId).orElseThrow(() -> new TenantRuntimeException(Response.Status.BAD_REQUEST,
                    "Office with external id " + officeExternalId + " does not exist"));
        }
        throw new TenantRuntimeException(Response.Status.BAD_REQUEST, "office_id or office_external_id is required");
    }

    private Set<Role> resolveRoles(final TenantRuntimeUserSyncDefaults defaults, final TenantRuntimeUserSyncUser user) {
        final List<String> roleNames = user.roleNames() == null || user.roleNames().isEmpty()
                ? defaults == null ? List.of() : defaults.roleNames()
                : user.roleNames();
        if (roleNames == null || roleNames.isEmpty()) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, "role_names must contain at least one role");
        }
        final Set<Role> roles = new LinkedHashSet<>();
        for (String roleName : roleNames) {
            final String normalizedRoleName = requireText(roleName, "role_names");
            final Role role = roleRepository.getRoleByName(normalizedRoleName);
            if (role == null) {
                throw new RoleNotFoundException(normalizedRoleName);
            }
            roles.add(role);
        }
        return roles;
    }

    private Staff resolveStaff(final Long staffId, final Office office) {
        if (staffId == null) {
            return null;
        }
        return staffRepository.findByOffice(staffId, office.getId()).orElseThrow(() -> new StaffNotFoundException(staffId));
    }

    private TenantRuntimeUserSyncResponse response(final FineractPlatformTenant tenant, final List<TenantRuntimeUserSyncResult> results,
            final int requested) {
        final long created = count(results, CREATED);
        final long updated = count(results, UPDATED);
        final long unchanged = count(results, UNCHANGED);
        final long conflicts = countAny(results, CONFLICT);
        final long failed = countAny(results, FAILED);
        final String status;
        if (failed > 0 || conflicts > 0) {
            status = created > 0 || updated > 0 || unchanged > 0 ? "COMPLETED_WITH_CONFLICTS" : "FAILED";
        } else {
            status = "COMPLETED";
        }
        return new TenantRuntimeUserSyncResponse(tenant.getTenantIdentifier(), tenant.getConnection().getSchemaName(), status, requested,
                Math.toIntExact(created), Math.toIntExact(updated), Math.toIntExact(unchanged), Math.toIntExact(conflicts),
                Math.toIntExact(failed), List.copyOf(results), Instant.now());
    }

    private long count(final List<TenantRuntimeUserSyncResult> results, final String localStatus) {
        return results.stream().filter(result -> localStatus.equals(result.localStatus())).count();
    }

    private long countAny(final List<TenantRuntimeUserSyncResult> results, final String status) {
        return results.stream().filter(result -> status.equals(result.localStatus()) || status.equals(result.identityStatus())).count();
    }

    private TenantRuntimeUserSyncResult conflict(final TenantRuntimeUserSyncUser user, final String message) {
        return new TenantRuntimeUserSyncResult(user.sourceUserId(), user.username(), user.email(), null, CONFLICT, SKIPPED, message);
    }

    private TenantRuntimeUserSyncResult failed(final TenantRuntimeUserSyncUser user, final String message) {
        return new TenantRuntimeUserSyncResult(user.sourceUserId(), user.username(), user.email(), null, FAILED, SKIPPED, message);
    }

    private String key(final String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : null;
    }

    private String requireText(final String value, final String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new TenantRuntimeException(Response.Status.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private String message(final RuntimeException e) {
        return StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName();
    }

    private record ResolvedUser(String username, String email, String firstName, String lastName, Office office, Set<Role> roles,
            Staff staff) {
    }

    private record LocalSync(String status, Long appUserId) {
    }
}

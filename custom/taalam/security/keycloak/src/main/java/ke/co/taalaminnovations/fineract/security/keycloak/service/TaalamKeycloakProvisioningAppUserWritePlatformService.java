/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.security.keycloak.service;

import ke.co.taalaminnovations.fineract.security.keycloak.config.TaalamKeycloakResourceServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformPasswordEncoder;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.staff.domain.StaffRepository;
import org.apache.fineract.useradministration.domain.AppUserPreviousPasswordRepository;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.domain.UserDomainService;
import org.apache.fineract.useradministration.service.AppUserWritePlatformService;
import org.apache.fineract.useradministration.service.AppUserWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.useradministration.service.UserDataValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "fineract.security.oauth2.external.provisioning", name = "enabled", havingValue = "true")
public class TaalamKeycloakProvisioningAppUserWritePlatformService implements AppUserWritePlatformService {

    private final AppUserWritePlatformService delegate;
    private final TaalamKeycloakUserProvisioningService provisioningService;

    @Autowired
    public TaalamKeycloakProvisioningAppUserWritePlatformService(final PlatformSecurityContext context,
            final UserDomainService userDomainService, final PlatformPasswordEncoder platformPasswordEncoder,
            final AppUserRepository appUserRepository, final OfficeRepositoryWrapper officeRepositoryWrapper,
            final RoleRepository roleRepository, final UserDataValidator fromApiJsonDeserializer,
            final AppUserPreviousPasswordRepository appUserPreviewPasswordRepository, final StaffRepository staffRepository,
            final ConfigurationDomainService configurationDomainService, final TaalamKeycloakResourceServerProperties properties,
            final TaalamKeycloakUserProvisioningService provisioningService) {
        this(new AppUserWritePlatformServiceJpaRepositoryImpl(context,
                suppressLocalPasswordEmailWhenProvisioningEnabled(userDomainService, properties), platformPasswordEncoder,
                appUserRepository, officeRepositoryWrapper, roleRepository, fromApiJsonDeserializer, appUserPreviewPasswordRepository,
                staffRepository, configurationDomainService), provisioningService);
    }

    TaalamKeycloakProvisioningAppUserWritePlatformService(final AppUserWritePlatformService delegate,
            final TaalamKeycloakUserProvisioningService provisioningService) {
        this.delegate = delegate;
        this.provisioningService = provisioningService;
    }

    @Override
    @Transactional
    @Caching(evict = { @CacheEvict(value = "users", allEntries = true), @CacheEvict(value = "usersByUsername", allEntries = true) })
    public CommandProcessingResult createUser(final JsonCommand command) {
        final CommandProcessingResult result = delegate.createUser(command);
        registerProvisioningAfterCommit(provisioningRequest(command));
        return result;
    }

    @Override
    @Transactional
    @Caching(evict = { @CacheEvict(value = "users", allEntries = true), @CacheEvict(value = "usersByUsername", allEntries = true) })
    public CommandProcessingResult changeUserPassword(final Long userId, final JsonCommand command) {
        return delegate.changeUserPassword(userId, command);
    }

    @Override
    @Transactional
    @Caching(evict = { @CacheEvict(value = "users", allEntries = true), @CacheEvict(value = "usersByUsername", allEntries = true) })
    public CommandProcessingResult updateUser(final Long userId, final JsonCommand command) {
        return delegate.updateUser(userId, command);
    }

    @Override
    @Transactional
    @Caching(evict = { @CacheEvict(value = "users", allEntries = true), @CacheEvict(value = "usersByUsername", allEntries = true) })
    public CommandProcessingResult deleteUser(final Long userId) {
        return delegate.deleteUser(userId);
    }

    private TaalamKeycloakUserProvisioningRequest provisioningRequest(final JsonCommand command) {
        final FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
        final String tenantIdentifier = tenant == null ? null : tenant.getTenantIdentifier();
        return new TaalamKeycloakUserProvisioningRequest(tenantIdentifier, command.stringValueOfParameterNamed("username"),
                command.stringValueOfParameterNamed("email"), command.stringValueOfParameterNamed("firstname"),
                command.stringValueOfParameterNamed("lastname"));
    }

    private void registerProvisioningAfterCommit(final TaalamKeycloakUserProvisioningRequest request) {
        final Runnable provisioning = () -> provisionUser(request);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    provisioning.run();
                }
            });
            return;
        }
        provisioning.run();
    }

    private void provisionUser(final TaalamKeycloakUserProvisioningRequest request) {
        try {
            provisioningService.provisionUser(request);
        } catch (final RuntimeException e) {
            log.error("Keycloak user provisioning failed for username [{}] in tenant [{}]", request.username(), request.tenantIdentifier(),
                    e);
        }
    }

    private static UserDomainService suppressLocalPasswordEmailWhenProvisioningEnabled(final UserDomainService userDomainService,
            final TaalamKeycloakResourceServerProperties properties) {
        return (appUser, sendPasswordToEmail) -> userDomainService.create(appUser,
                properties.getProvisioning().isEnabled() ? Boolean.FALSE : sendPasswordToEmail);
    }
}

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

import static org.apache.fineract.infrastructure.core.service.migration.TenantDatabaseUpgradeService.CUSTOM_CHANGELOG_CONTEXT;
import static org.apache.fineract.infrastructure.core.service.migration.TenantDatabaseUpgradeService.INITIAL_SWITCH_CONTEXT;
import static org.apache.fineract.infrastructure.core.service.migration.TenantDatabaseUpgradeService.TENANT_DB_CONTEXT;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.ws.rs.core.Response;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.migration.ExtendedSpringLiquibase;
import org.apache.fineract.infrastructure.core.service.migration.ExtendedSpringLiquibaseFactory;
import org.apache.fineract.infrastructure.core.service.migration.TenantDataSourceFactory;
import org.apache.fineract.infrastructure.core.service.migration.TenantDatabaseStateVerifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SingleTenantMigrationService {

    private final TenantDatabaseStateVerifier databaseStateVerifier;
    private final ExtendedSpringLiquibaseFactory liquibaseFactory;
    private final TenantDataSourceFactory tenantDataSourceFactory;

    public boolean migrate(FineractPlatformTenant tenant) {
        try {
            ThreadLocalContextUtil.setTenant(tenant);
            try (HikariDataSource tenantDataSource = tenantDataSourceFactory.create(tenant)) {
                boolean firstLiquibaseMigration = databaseStateVerifier.isFirstLiquibaseMigration(tenantDataSource);
                if (firstLiquibaseMigration) {
                    ExtendedSpringLiquibase initialLiquibase = liquibaseFactory.create(tenantDataSource, TENANT_DB_CONTEXT,
                            CUSTOM_CHANGELOG_CONTEXT, INITIAL_SWITCH_CONTEXT, tenant.getTenantIdentifier());
                    applyInitialLiquibase(tenantDataSource, initialLiquibase, tenant.getTenantIdentifier());
                }
                SpringLiquibase liquibase = liquibaseFactory.create(tenantDataSource, TENANT_DB_CONTEXT, CUSTOM_CHANGELOG_CONTEXT,
                        tenant.getTenantIdentifier());
                liquibase.afterPropertiesSet();
                return true;
            }
        } catch (TenantRuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Tenant runtime migration failed for tenant {}", tenant.getTenantIdentifier(), e);
            throw new TenantRuntimeException(Response.Status.INTERNAL_SERVER_ERROR,
                    "Fineract tenant migration failed for tenant " + tenant.getTenantIdentifier(), e);
        } finally {
            ThreadLocalContextUtil.reset();
        }
    }

    private void applyInitialLiquibase(HikariDataSource tenantDataSource, ExtendedSpringLiquibase liquibase, String tenantIdentifier)
            throws LiquibaseException {
        if (databaseStateVerifier.isFlywayPresent(tenantDataSource)) {
            if (!databaseStateVerifier.isTenantOnLatestUpgradableVersion(tenantDataSource)) {
                throw new TenantRuntimeException(Response.Status.CONFLICT,
                        "Tenant " + tenantIdentifier + " must be upgraded to the latest Flyway-supported Fineract schema first");
            }
            liquibase.changeLogSync();
        } else {
            liquibase.afterPropertiesSet();
        }
    }
}

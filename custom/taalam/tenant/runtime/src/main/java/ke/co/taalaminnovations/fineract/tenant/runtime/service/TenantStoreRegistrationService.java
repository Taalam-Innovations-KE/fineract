/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import jakarta.ws.rs.core.Response;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import ke.co.taalaminnovations.fineract.tenant.runtime.api.RuntimeTenantRegistrationRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

@Service
public class TenantStoreRegistrationService {

    private final JdbcTemplate jdbcTemplate;

    public TenantStoreRegistrationService(@Qualifier("hikariTenantDataSource") DataSource tenantStoreDataSource) {
        this.jdbcTemplate = new JdbcTemplate(tenantStoreDataSource);
    }

    public boolean isTenantRegistered(String tenantIdentifier) {
        Integer count = jdbcTemplate.queryForObject("select count(1) from tenants where identifier = ?", Integer.class, tenantIdentifier);
        return Objects.equals(count, 1);
    }

    public Long register(RuntimeTenantRegistrationRequest request, String encryptedPassword, String masterPasswordHash) {
        if (isTenantRegistered(request.tenantIdentifier())) {
            throw new TenantRuntimeException(Response.Status.CONFLICT, "Tenant " + request.tenantIdentifier() + " is already registered");
        }
        Optional<Long> reusableConnection = findReusableConnection(request);
        Long connectionId = reusableConnection.orElseGet(() -> insertConnection(request, encryptedPassword, masterPasswordHash));
        ensureConnectionIsUnassigned(request, connectionId);
        reusableConnection.ifPresent(id -> updateReusableConnectionSecret(id, request, encryptedPassword, masterPasswordHash));
        insertTenant(request, connectionId);
        return connectionId;
    }

    private Optional<Long> findReusableConnection(RuntimeTenantRegistrationRequest request) {
        try {
            ExistingConnection connection = jdbcTemplate.queryForObject("""
                    select id, schema_server, schema_server_port, schema_username
                    from tenant_server_connections
                    where schema_name = ?
                    """, (rs, rowNum) -> new ExistingConnection(rs.getLong("id"), rs.getString("schema_server"),
                    rs.getString("schema_server_port"), rs.getString("schema_username")), request.databaseName());
            if (connection == null) {
                return Optional.empty();
            }
            if (!Objects.equals(connection.schemaServer(), request.databaseHost())
                    || !Objects.equals(connection.schemaServerPort(), String.valueOf(request.databasePort()))
                    || !Objects.equals(connection.schemaUsername(), request.runtimeUsername())) {
                throw new TenantRuntimeException(Response.Status.CONFLICT,
                        "Tenant-store connection for database " + request.databaseName() + " already exists with different settings");
            }
            return Optional.of(connection.id());
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private Long insertConnection(RuntimeTenantRegistrationRequest request, String encryptedPassword, String masterPasswordHash) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into tenant_server_connections (
                        schema_server,
                        schema_name,
                        schema_server_port,
                        schema_username,
                        schema_password,
                        schema_connection_parameters,
                        master_password_hash
                    )
                    values (?, ?, ?, ?, ?, ?, ?)
                    """, new String[] { "id" });
            statement.setString(1, request.databaseHost());
            statement.setString(2, request.databaseName());
            statement.setString(3, String.valueOf(request.databasePort()));
            statement.setString(4, request.runtimeUsername());
            statement.setString(5, encryptedPassword);
            statement.setString(6, request.connectionParameters());
            statement.setString(7, masterPasswordHash);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new TenantRuntimeException(Response.Status.INTERNAL_SERVER_ERROR, "Tenant-store connection insert did not return an id");
        }
        return key.longValue();
    }

    private void updateReusableConnectionSecret(Long connectionId, RuntimeTenantRegistrationRequest request, String encryptedPassword,
            String masterPasswordHash) {
        jdbcTemplate.update("""
                update tenant_server_connections
                set schema_password = ?,
                    schema_connection_parameters = ?,
                    master_password_hash = ?
                where id = ?
                """, encryptedPassword, request.connectionParameters(), masterPasswordHash, connectionId);
    }

    private void ensureConnectionIsUnassigned(RuntimeTenantRegistrationRequest request, Long connectionId) {
        List<String> assignedTenants = jdbcTemplate.queryForList("""
                select identifier
                from tenants
                where oltp_id = ? or report_id = ?
                """, String.class, connectionId, connectionId);
        boolean assignedToAnotherTenant = assignedTenants.stream()
                .anyMatch(identifier -> !Objects.equals(identifier, request.tenantIdentifier()));
        if (assignedToAnotherTenant) {
            throw new TenantRuntimeException(Response.Status.CONFLICT, "Tenant-store connection is already assigned to another tenant");
        }
    }

    private void insertTenant(RuntimeTenantRegistrationRequest request, Long connectionId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                insert into tenants (
                    identifier,
                    name,
                    timezone_id,
                    created_date,
                    lastmodified_date,
                    oltp_id,
                    report_id
                )
                values (?, ?, ?, ?, ?, ?, ?)
                """, request.tenantIdentifier(), request.displayName(), request.timezone(), now, now, connectionId, connectionId);
    }

    private record ExistingConnection(Long id, String schemaServer, String schemaServerPort, String schemaUsername) {
    }
}

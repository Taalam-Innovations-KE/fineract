/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import ke.co.taalaminnovations.fineract.tenant.runtime.service.TenantRuntimeException;
import ke.co.taalaminnovations.fineract.tenant.runtime.service.TenantRuntimeRefreshService;
import ke.co.taalaminnovations.fineract.tenant.runtime.service.TenantRuntimeUserExportService;
import ke.co.taalaminnovations.fineract.tenant.runtime.service.TenantRuntimeUserSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Path("/v1/tenant-runtime")
@RequiredArgsConstructor
public class TenantRuntimeApiResource {

    private final ObjectMapper objectMapper;
    private final TenantRuntimeRefreshService tenantRuntimeRefreshService;
    private final TenantRuntimeUserSyncService tenantRuntimeUserSyncService;
    private final TenantRuntimeUserExportService tenantRuntimeUserExportService;

    @POST
    @Path("/register-and-refresh")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String registerAndRefresh(String requestBody) {
        try {
            RuntimeTenantRegistrationRequest request = objectMapper.readValue(requestBody, RuntimeTenantRegistrationRequest.class);
            return objectMapper.writeValueAsString(tenantRuntimeRefreshService.registerAndRefresh(request));
        } catch (TenantRuntimeException e) {
            throw toWebApplicationException(e.getStatus(), e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw toWebApplicationException(Response.Status.BAD_REQUEST, "Invalid tenant runtime request JSON", e);
        }
    }

    @POST
    @Path("/refresh-registered")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String refreshRegistered(String requestBody) {
        try {
            RuntimeTenantRefreshRegisteredRequest request = objectMapper.readValue(requestBody,
                    RuntimeTenantRefreshRegisteredRequest.class);
            return objectMapper.writeValueAsString(tenantRuntimeRefreshService.refreshRegistered(request));
        } catch (TenantRuntimeException e) {
            throw toWebApplicationException(e.getStatus(), e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw toWebApplicationException(Response.Status.BAD_REQUEST, "Invalid tenant runtime request JSON", e);
        }
    }

    @POST
    @Path("/users/sync")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String syncUsers(String requestBody) {
        try {
            TenantRuntimeUserSyncRequest request = objectMapper.readValue(requestBody, TenantRuntimeUserSyncRequest.class);
            return objectMapper.writeValueAsString(tenantRuntimeUserSyncService.syncUsers(request));
        } catch (TenantRuntimeException e) {
            throw toWebApplicationException(e.getStatus(), e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw toWebApplicationException(Response.Status.BAD_REQUEST, "Invalid tenant runtime user sync request JSON", e);
        }
    }

    @GET
    @Path("/users/export")
    @Produces({ MediaType.APPLICATION_JSON })
    public String exportUsers(@QueryParam("tenant_identifier") String tenantIdentifier, @QueryParam("database_name") String databaseName,
            @DefaultValue("false") @QueryParam("include_disabled") boolean includeDisabled,
            @DefaultValue("false") @QueryParam("include_deleted") boolean includeDeleted,
            @DefaultValue("false") @QueryParam("include_system_users") boolean includeSystemUsers) {
        try {
            TenantRuntimeUserExportRequest request = new TenantRuntimeUserExportRequest(tenantIdentifier, databaseName, includeDisabled,
                    includeDeleted, includeSystemUsers);
            return objectMapper.writeValueAsString(tenantRuntimeUserExportService.exportUsers(request));
        } catch (TenantRuntimeException e) {
            throw toWebApplicationException(e.getStatus(), e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw toWebApplicationException(Response.Status.INTERNAL_SERVER_ERROR,
                    "Unable to serialize tenant runtime user export response", e);
        }
    }

    private WebApplicationException toWebApplicationException(Response.Status status, String message, Throwable cause) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("status", status.getStatusCode(), "message", message));
            return new WebApplicationException(message, cause,
                    Response.status(status).type(MediaType.APPLICATION_JSON).entity(body).build());
        } catch (JsonProcessingException e) {
            e.addSuppressed(cause);
            return new WebApplicationException(message, e, status);
        }
    }
}

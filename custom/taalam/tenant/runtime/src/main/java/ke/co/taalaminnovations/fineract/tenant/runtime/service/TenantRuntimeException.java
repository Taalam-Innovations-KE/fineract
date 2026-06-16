/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.service;

import jakarta.ws.rs.core.Response;
import lombok.Getter;

@Getter
public class TenantRuntimeException extends RuntimeException {

    private final Response.Status status;

    public TenantRuntimeException(Response.Status status, String message) {
        super(message);
        this.status = status;
    }

    public TenantRuntimeException(Response.Status status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}

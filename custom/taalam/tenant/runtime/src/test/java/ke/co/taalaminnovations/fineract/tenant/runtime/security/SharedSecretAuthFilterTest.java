/**
 * Copyright (c) 2026 Taalam Innovations KE. All rights reserved.
 * Confidential and proprietary.
 */
package ke.co.taalaminnovations.fineract.tenant.runtime.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import ke.co.taalaminnovations.fineract.tenant.runtime.config.TaalamTenantRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SharedSecretAuthFilterTest {

    @Test
    void passesRequestWhenSharedSecretMatches() throws Exception {
        TaalamTenantRuntimeProperties properties = new TaalamTenantRuntimeProperties();
        properties.setSharedSecret("secret-value");
        SharedSecretAuthFilter underTest = new SharedSecretAuthFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tenant-runtime/register-and-refresh");
        request.addHeader(SharedSecretAuthFilter.HEADER_NAME, "secret-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        underTest.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsRequestWhenSharedSecretDoesNotMatch() throws Exception {
        TaalamTenantRuntimeProperties properties = new TaalamTenantRuntimeProperties();
        properties.setSharedSecret("secret-value");
        SharedSecretAuthFilter underTest = new SharedSecretAuthFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tenant-runtime/register-and-refresh");
        request.addHeader(SharedSecretAuthFilter.HEADER_NAME, "wrong-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        underTest.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void rejectsRequestWhenSharedSecretIsNotConfigured() throws Exception {
        SharedSecretAuthFilter underTest = new SharedSecretAuthFilter(new TaalamTenantRuntimeProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tenant-runtime/register-and-refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        underTest.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(503);
        verifyNoInteractions(filterChain);
    }
}

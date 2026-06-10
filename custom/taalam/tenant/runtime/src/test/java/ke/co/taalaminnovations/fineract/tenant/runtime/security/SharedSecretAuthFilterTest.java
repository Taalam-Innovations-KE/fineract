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

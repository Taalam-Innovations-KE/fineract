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

package org.apache.fineract.infrastructure.core.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityValidationConfig {

    @Value("${fineract.security.basicauth.enabled}")
    private Boolean basicAuthEnabled;

    @Value("${fineract.security.oauth2.enabled}")
    private Boolean oauthEnabled;

    @Value("${fineract.security.oauth2.external.enabled:false}")
    private Boolean externalOauthEnabled;

    @Value("${fineract.security.2fa.enabled}")
    private Boolean twoFactorEnabled;

    @PostConstruct
    public void validate() {
        final int enabledAuthenticationSchemes = countEnabledAuthenticationSchemes();
        if (enabledAuthenticationSchemes == 0) {
            // NOTE: while we are already doing consistency checks we might as well cover this case; should not happen
            // as defaults are set in application.properties
            throw new IllegalArgumentException(
                    "No authentication scheme selected. Please decide if you want to use basic, OAuth2, or external OAuth2 authentication.");
        }
        if (enabledAuthenticationSchemes > 1) {
            throw new IllegalArgumentException(
                    "Too many authentication schemes selected. Please decide if you want to use basic, OAuth2, or external OAuth2 authentication.");
        }
        if (Boolean.TRUE.equals(externalOauthEnabled) && Boolean.TRUE.equals(twoFactorEnabled)) {
            throw new IllegalArgumentException("External OAuth2 authentication cannot be combined with Fineract 2FA.");
        }
    }

    private int countEnabledAuthenticationSchemes() {
        int enabledAuthenticationSchemes = 0;
        if (Boolean.TRUE.equals(basicAuthEnabled)) {
            enabledAuthenticationSchemes++;
        }
        if (Boolean.TRUE.equals(oauthEnabled)) {
            enabledAuthenticationSchemes++;
        }
        if (Boolean.TRUE.equals(externalOauthEnabled)) {
            enabledAuthenticationSchemes++;
        }
        return enabledAuthenticationSchemes;
    }
}

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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityValidationConfigTest {

    @ParameterizedTest
    @CsvSource({ "true,false,false,false", "false,true,false,false", "false,false,true,false" })
    void validatesWhenExactlyOneAuthenticationSchemeIsEnabled(boolean basicAuthEnabled, boolean oauthEnabled, boolean externalOauthEnabled,
            boolean twoFactorEnabled) {
        SecurityValidationConfig underTest = config(basicAuthEnabled, oauthEnabled, externalOauthEnabled, twoFactorEnabled);

        assertThatCode(underTest::validate).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({ "false,false,false,false", "true,true,false,false", "true,false,true,false", "false,true,true,false",
            "true,true,true,false" })
    void rejectsInvalidAuthenticationSchemeCombinations(boolean basicAuthEnabled, boolean oauthEnabled, boolean externalOauthEnabled,
            boolean twoFactorEnabled) {
        SecurityValidationConfig underTest = config(basicAuthEnabled, oauthEnabled, externalOauthEnabled, twoFactorEnabled);

        assertThatThrownBy(underTest::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({ "false,false,true,true" })
    void rejectsExternalOauthWithFineractTwoFactor(boolean basicAuthEnabled, boolean oauthEnabled, boolean externalOauthEnabled,
            boolean twoFactorEnabled) {
        SecurityValidationConfig underTest = config(basicAuthEnabled, oauthEnabled, externalOauthEnabled, twoFactorEnabled);

        assertThatThrownBy(underTest::validate).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be combined with Fineract 2FA");
    }

    private SecurityValidationConfig config(boolean basicAuthEnabled, boolean oauthEnabled, boolean externalOauthEnabled,
            boolean twoFactorEnabled) {
        SecurityValidationConfig config = new SecurityValidationConfig();
        ReflectionTestUtils.setField(config, "basicAuthEnabled", basicAuthEnabled);
        ReflectionTestUtils.setField(config, "oauthEnabled", oauthEnabled);
        ReflectionTestUtils.setField(config, "externalOauthEnabled", externalOauthEnabled);
        ReflectionTestUtils.setField(config, "twoFactorEnabled", twoFactorEnabled);
        return config;
    }
}

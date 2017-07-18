/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.test.integration.elytron.sasl.mgmt;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.ConnectException;
import java.security.Provider;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.security.sasl.SaslException;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.ModelControllerClientConfiguration;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.credential.BearerTokenCredential;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.wildfly.test.security.common.elytron.ConfigurableElement;
import org.wildfly.test.security.common.elytron.ConstantPermissionMapper;
import org.wildfly.test.security.common.elytron.ConstantRoleMapper;
import org.wildfly.test.security.common.elytron.FileSystemRealm;
import org.wildfly.test.security.common.elytron.MechanismConfiguration;
import org.wildfly.test.security.common.elytron.MechanismRealmConfiguration;
import org.wildfly.test.security.common.elytron.PermissionRef;
import org.wildfly.test.security.common.elytron.SaslFilter;
import org.wildfly.test.security.common.elytron.SimpleConfigurableSaslServerFactory;
import org.wildfly.test.security.common.elytron.SimpleSaslAuthenticationFactory;
import org.wildfly.test.security.common.elytron.SimpleSecurityDomain;
import org.wildfly.test.security.common.elytron.SimpleSecurityDomain.SecurityDomainRealm;
import org.wildfly.test.security.common.other.SimpleMgmtNativeInterface;
import org.wildfly.test.security.common.other.SimpleSocketBinding;

/**
 * Parent class for management interface SASL tests.
 *
 * @author Josef Cacek
 */
public abstract class AbstractMgmtSaslTestBase {

    private static Logger LOGGER = Logger.getLogger(AbstractMgmtSaslTestBase.class);

    protected static final String NAME = AbstractMgmtSaslTestBase.class.getSimpleName();
    protected static final int PORT_NATIVE = 10567;
    protected static final String ROLE_SASL = "sasl";

    protected static final String USERNAME = "guest";

    // set-password algorithm names, used also as user names for testing given algorithm
    protected static final String DIGEST_ALGORITHM_MD5 = "digest-md5";
    protected static final String DIGEST_ALGORITHM_SHA = "digest-sha";
    protected static final String DIGEST_ALGORITHM_SHA256 = "digest-sha-256";
    protected static final String DIGEST_ALGORITHM_SHA512 = "digest-sha-512";

    protected static final int CONNECTION_TIMEOUT_IN_MS = TimeoutUtil.adjust(600 * 1000);

    protected static final Provider PROVIDER_ELYTRON = new WildFlyElytronProvider();

    /**
     * Token generated by https://kjur.github.io/jsrsasign/tool/tool_jwt.html (using HS256 signing algorithm with a default
     * shared key)
     *
     * <pre>
     * {
     *   "alg": "HS256",
     *   "typ": "JWT"
     * }
     * {
     *   "iss": "issuer.wildfly.org",
     *   "sub": "elytron@wildfly.org",
     *   "exp": 2051222399,  // 20341231235959Z
     *   "aud": "jwt"
     * }
     * </pre>
     */
    protected static final String JWT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJpc3N1ZXIud2lsZGZseS5vcmciLCJzdWIiOiJlbHl0cm9uQHdpbGRmbHkub3JnIiwiZXhwIjoyMDUxMjIyMzk5LCJhdWQiOiJqd3QifQ.jOcMdBLdI7HMuW_VsoGD_7LqeX6M14_wV5ebP2S4tOM";

    // password is just username with a suffix
    protected static final String PASSWORD_SFX = "-pwd";

    protected abstract String getMechanism();

    /**
     * Tests that invalid credentials results in authentication failure.
     */
    @Test
    public void testWrongCredentialsFail() throws Exception {
        String mechanismName = getMechanism();
        if ("ANONYMOUS".equals(mechanismName)) {
            LOGGER.info("We don't test ANONYMOUS mechanism with wrong user credentials.");
            return;
        }

        AuthenticationConfiguration authnCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString(mechanismName)).useName(USERNAME)
                .usePassword("wrongPassword").useProviders(() -> new Provider[] { PROVIDER_ELYTRON });
        AuthenticationContext.empty().with(MatchRule.ALL, authnCfg).run(() -> assertAuthenticationFails());
    }

    /**
     * Tests wrongly configured security Provider in the clients AuthenticationConfiguration.
     */
    @Test
    public void testNullProvider() throws Exception {
        String mechanismName = getMechanism();
        AuthenticationConfiguration authnCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString(mechanismName))
                .useProviders(() -> new Provider[] { null });
        if ("ANONYMOUS".equals(mechanismName)) {
            authnCfg = authnCfg.useAnonymous();
        } else if ("OAUTHBEARER".equals(mechanismName)) {
            authnCfg = authnCfg.useBearerTokenCredential(new BearerTokenCredential(JWT_TOKEN));
        } else if (!"JBOSS-LOCAL-USER".equals(mechanismName)) {
            authnCfg = authnCfg.useName(USERNAME).usePassword(USERNAME + PASSWORD_SFX);
        }
        AuthenticationContext.empty().with(MatchRule.ALL, authnCfg).run(() -> assertAuthenticationFails());
    }

    /**
     * Tests that client fails to use other mechanisms than the server allows.
     */
    @Test
    public void testOtherMechsFail() throws Exception {
        Arrays.asList("ANONYMOUS", "", "1" + getMechanism(), getMechanism() + "1", "DIGEST-MD5", "DIGEST-SHA", "DIGEST-SHA-256",
                "DIGEST-SHA-512", "PLAIN", "SCRAM-SHA-1", "JBOSS-LOCAL-USER").forEach(s -> {
                    if (!getMechanism().equals(s)) {
                        assertMechFails(s);
                    }
                });
    }

    /**
     * No other digest mechanism succeeds to authenticate - either due to the fact the SASL mechanism is not allowed on server
     * side or because of password digest in the security realm uses another hash function.
     */
    @Test
    public void testOtherDigestMechsFail() throws Exception {
        Arrays.asList("MD5", "SHA", "SHA-256", "SHA-512").forEach(s -> {
            final String mech = "DIGEST-" + s;
            if (!getMechanism().equals(mech)) {
                assertDigestMechFails(mech, mech.toLowerCase(Locale.ROOT));
            }
        });
    }

    /**
     * Asserts that execution of :whoami operation fail (default message is used).
     */
    protected static void assertAuthenticationFails() {
        assertAuthenticationFails("The failure of :whoami operation execution was expected, but the call passed");
    }

    /**
     * Asserts that execution of :whoami operation fail (custom message is used).
     */
    protected static void assertAuthenticationFails(String message) {
        assertAuthenticationFails(message, null);
    }

    /**
     * Asserts that execution of :whoami operation fail (custom message is used).
     */
    protected static void assertAuthenticationFails(String message, Class<? extends Exception> clazz) {
        final long startTime = System.currentTimeMillis();
        if (clazz == null) {
            clazz = SaslException.class;
        }
        try {
            executeWhoAmI();
            fail(message);
        } catch (IOException e) {
            assertTrue("Connection reached its timeout (hang).",
                    startTime + CONNECTION_TIMEOUT_IN_MS > System.currentTimeMillis());
            Throwable cause = e.getCause();
            assertThat("ConnectionException was expected as a cause when SASL authentication fails", cause,
                    is(instanceOf(ConnectException.class)));
            assertThat("An unexpected second Exception cause came when authentication failed", cause.getCause(),
                    is(instanceOf(clazz)));
        }
    }

    /**
     * Executes :whoami operation and returns the result ModelNode.
     */
    protected static ModelNode executeWhoAmI() throws IOException {
        try (ModelControllerClient client = ModelControllerClient.Factory
                .create(new ModelControllerClientConfiguration.Builder().setHostName(TestSuiteEnvironment.getServerAddress())
                        .setPort(PORT_NATIVE).setProtocol("remote").setConnectionTimeout(CONNECTION_TIMEOUT_IN_MS).build())) {

            ModelNode operation = new ModelNode();
            operation.get("operation").set("whoami");
            operation.get("verbose").set("true");

            return client.execute(operation);
        }
    }

    /**
     * Asserts that :whoami operation execution finishes successfully and returned identity (username) is the expected one.
     */
    protected static void assertWhoAmI(String expected) {
        try {
            ModelNode result = executeWhoAmI();
            assertTrue("The whoami operation should finish with success", Operations.isSuccessfulOutcome(result));
            assertEquals("The whoami operation returned unexpected value", expected,
                    Operations.readResult(result).get("identity").get("username").asString());
        } catch (IOException e) {
            LOGGER.warn("Operation execution failed", e);
            fail("The whoami operation failed - " + e.getMessage());
        }
    }

    protected AuthenticationContext createValidConfigForMechanism(String mechanismName, String username) {
        AuthenticationConfiguration authnCfg = AuthenticationConfiguration.empty()
                .setSaslMechanismSelector(SaslMechanismSelector.fromString(mechanismName)).useDefaultProviders();
        if ("ANONYMOUS".equals(mechanismName)) {
            authnCfg = authnCfg.useAnonymous();
        } else if ("OAUTHBEARER".equals(mechanismName)) {
            authnCfg = authnCfg.useBearerTokenCredential(new BearerTokenCredential(JWT_TOKEN));
        } else if (!"JBOSS-LOCAL-USER".equals(mechanismName)) {
            authnCfg = authnCfg.useName(username).usePassword(username + PASSWORD_SFX);
        }
        return AuthenticationContext.empty().with(MatchRule.ALL, authnCfg);
    }

    protected AuthenticationContext createValidConfigForMechanism(String mechanismName) {
        return createValidConfigForMechanism(mechanismName, USERNAME);
    }

    protected void assertMechFails(String mechanismName) {
        createValidConfigForMechanism(mechanismName).run(() -> assertAuthenticationFails(MessageFormat.format(
                "Operation execution with mechanism {0} should fail when {1} is configured.", mechanismName, getMechanism())));
    }

    protected void assertDigestMechFails(String mechanismName, String digestAlg) {
        createValidConfigForMechanism(mechanismName, digestAlg).run(() -> assertAuthenticationFails(MessageFormat
                .format("Operation execution with DIGEST mechanism {0} and user {1} should fail.", mechanismName, digestAlg)));
    }

    protected void assertMechPassWhoAmI(String mechanismName, String expectedUsername) {
        createValidConfigForMechanism(mechanismName).run(() -> assertWhoAmI(expectedUsername));
    }

    protected void assertDigestMechPassWhoAmI(String mechanismName, String digestAlg) {
        // as the "digest-*" users are not in the ManagementFsRealm, the management identity will be "anonymous" here
        createValidConfigForMechanism(mechanismName, digestAlg).run(() -> assertWhoAmI("anonymous"));
    }

    /**
     * Creates a new list or ConfigurableElements with basic SASL settings for native management interface.
     *
     * @param saslMechanismName Name of single SASL mechanism to be allowed on server.
     * @return new list (modifiable)
     */
    protected static List<ConfigurableElement> createConfigurableElementsForSaslMech(String saslMechanismName) {
        List<ConfigurableElement> elements = new ArrayList<>();

        elements.add(ConstantPermissionMapper.builder().withName(NAME)
                .withPermissions(PermissionRef.fromPermission(new LoginPermission())).build());
        elements.add(ConstantRoleMapper.builder().withName(NAME).withRoles(ROLE_SASL).build());
        elements.add(FileSystemRealm.builder().withName(NAME).withUser(USERNAME, USERNAME + PASSWORD_SFX, ROLE_SASL).build());
        elements.add(new ConfigurableElement() {
            @Override
            public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
                cli.sendLine("/subsystem=elytron/token-realm=JWT:add(jwt={}, principal-claim=aud)");
                cli.sendLine("/subsystem=elytron/constant-realm-mapper=JWT:add(realm-name=JWT)");
            }

            @Override
            public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
                cli.sendLine("/subsystem=elytron/constant-realm-mapper=JWT:remove()");
                cli.sendLine("/subsystem=elytron/token-realm=JWT:remove()");
            }

            @Override
            public String getName() {
                return "token-realm";
            }
        });
        elements.add(SimpleSecurityDomain.builder().withName(NAME).withDefaultRealm(NAME).withPermissionMapper(NAME)
                .withRealms(SecurityDomainRealm.builder().withRealm(NAME).build(),
                        SecurityDomainRealm.builder().withRealm("JWT").build())
                .withRoleMapper(NAME).build());

        elements.add(new ConfigurableElement() {

            private void addUserWithDigestPass(CLIWrapper cli, String algorithm) {
                cli.sendLine(
                        String.format("/subsystem=elytron/filesystem-realm=%s:add-identity(identity=%s)", NAME, algorithm));
                cli.sendLine(String.format(
                        "/subsystem=elytron/filesystem-realm=%1$s:set-password(identity=%2$s, digest={algorithm=%2$s, password=%2$s%3$s, realm=%1$s})",
                        NAME, algorithm, PASSWORD_SFX));
            }

            @Override
            public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
                cli.sendLine(String.format(
                        "/subsystem=elytron/security-domain=ManagementDomain:write-attribute(name=trusted-security-domains, value=[%s])",
                        NAME));

                // identities with digested PWD
                addUserWithDigestPass(cli, DIGEST_ALGORITHM_MD5);
                addUserWithDigestPass(cli, DIGEST_ALGORITHM_SHA);
                addUserWithDigestPass(cli, DIGEST_ALGORITHM_SHA256);
                addUserWithDigestPass(cli, DIGEST_ALGORITHM_SHA512);
            }

            @Override
            public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
                cli.sendLine(
                        "/subsystem=elytron/security-domain=ManagementDomain:undefine-attribute(name=trusted-security-domains)");
                // no need to remove identities, they'll be resolved with removing the FS realm
            }

            @Override
            public String getName() {
                return "domain-trust-and-identities";
            }
        });

        elements.add(SimpleConfigurableSaslServerFactory.builder().withName(NAME).withSaslServerFactory("elytron")
                .addFilter(SaslFilter.builder().withPatternFilter(saslMechanismName).build()).build());
        elements.add(
                SimpleSaslAuthenticationFactory.builder().withName(NAME).withSaslServerFactory(NAME).withSecurityDomain(NAME)
                        .addMechanismConfiguration(MechanismConfiguration.builder().withMechanismName("OAUTHBEARER")
                                .withRealmMapper("JWT").build())
                        .addMechanismConfiguration(
                                MechanismConfiguration.builder().withMechanismName(saslMechanismName)
                                        .addMechanismRealmConfiguration(
                                                MechanismRealmConfiguration.builder().withRealmName(NAME).build())
                                        .build())
                        .build());

        elements.add(SimpleSocketBinding.builder().withName(NAME).withPort(PORT_NATIVE).build());
        elements.add(SimpleMgmtNativeInterface.builder().withSocketBinding(NAME).withSaslAuthenticationFactory(NAME).build());
        return elements;
    }

}

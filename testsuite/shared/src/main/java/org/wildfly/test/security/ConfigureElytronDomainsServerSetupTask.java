/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.test.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.logging.Logger;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.test.security.elytron.SecurityDomain;

/**
 *
 * @author Josef Cacek
 */
public abstract class ConfigureElytronDomainsServerSetupTask implements ServerSetupTask {

    private static final Logger LOGGER = Logger.getLogger(ConfigureElytronDomainsServerSetupTask.class);

    private SecurityDomain[] securityDomains;

    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        setup(managementClient.getControllerClient());
    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        tearDown(managementClient.getControllerClient());
    }

    // Protected methods -----------------------------------------------------

    protected void setup(final ModelControllerClient modelControllerClient) throws Exception {
        securityDomains = getSecurityDomains();

        if (securityDomains == null || securityDomains.length == 0) {
            LOGGER.warn("Empty Elytron security domain configuration.");
            return;
        }

        try (CLIWrapper cli = new CLIWrapper(true)) {
            for (final SecurityDomain securityDomain : securityDomains) {
                LOGGER.infov("Adding security domain {}", securityDomain.getName());
                securityDomain.create(cli);
            }
        }
    }

    protected void tearDown(ModelControllerClient modelControllerClient) throws Exception {
        if (securityDomains == null || securityDomains.length == 0) {
            LOGGER.warn("Empty Elytron security domain configuration.");
            return;
        }

        List<SecurityDomain> domainsToRemove = Arrays.asList(securityDomains);
        Collections.reverse(domainsToRemove);

        try (CLIWrapper cli = new CLIWrapper(true)) {
            for (final SecurityDomain securityDomain : domainsToRemove) {
                LOGGER.infov("Removing security domain {}", securityDomain.getName());
                securityDomain.remove(cli);
            }
        }
        this.securityDomains = null;
    }

    /**
     * Returns configuration for creating security domains.
     *
     * @return array of SecurityDomain instances
     */
    protected abstract SecurityDomain[] getSecurityDomains() throws Exception;

}

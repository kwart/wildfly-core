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

package org.wildfly.test.security.elytron;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.jboss.as.test.integration.management.util.CLIWrapper;

/**
 *
 * @author Josef Cacek
 */
public class PropertyFileBasedDomain extends AbstractUserRolesSecurityDomain {

    private File tempFolder;

    private PropertyFileBasedDomain(Builder builder) {
        super(builder);
    }

    @Override
    public void create(CLIWrapper cli) {
        tempFolder = createTemporaryFolder("ely-" + getName());
        final Properties usersProperties = new Properties();
        final Properties rolesProperties = new Properties();
        for (UserWithRoles user : getUsersWithRoles()) {
            usersProperties.setProperty(user.getName(), user.getPassword());
            rolesProperties.setProperty(user.getName(), String.join(",", user.getRoles()));
        }
        File usersFile = writeProperties(usersProperties, "users.properties");
        File rolesFile = writeProperties(rolesProperties, "roles.properties");
        cli.sendLine("/subsystem=elytron/", ignoreError);
        cli.readAllAsOpResult();

    }

    private File writeProperties(Properties properties, String fileName) throws IOException {
        File result = new File(tempFolder, fileName);
        try (FileOutputStream fos = new FileOutputStream(result)) {
            properties.store(fos, fileName);
        }
        return result;
    }

    @Override
    public void remove(CLIWrapper cli) {
        FileUtils.deleteQuietly(tempFolder);
    }

    public static final class Builder extends AbstractUserRolesSecurityDomain.Builder {
        private Builder() {
            // empty
        }

        public PropertyFileBasedDomain build() {
            return new PropertyFileBasedDomain(this);
        }

    }

    /**
     * Creates a temporary folder name with given name prefix.
     * 
     * @param prefix folder name prefix
     * @return created folder
     */
    private static File createTemporaryFolder(String prefix) throws IOException {
        File createdFolder = File.createTempFile(prefix, "", null);
        createdFolder.delete();
        createdFolder.mkdir();
        return createdFolder;
    }
}

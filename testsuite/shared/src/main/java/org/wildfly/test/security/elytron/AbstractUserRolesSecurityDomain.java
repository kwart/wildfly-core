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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author Josef Cacek
 */
public abstract class AbstractUserRolesSecurityDomain implements UsersRolesSecurityDomain {

    private final String name;
    private final List<UserWithRoles> usersWithRoles;

    protected AbstractUserRolesSecurityDomain(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "Security domain name must not be null");
        this.usersWithRoles = new ArrayList<>(builder.usersWithRoles);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<UserWithRoles> getUsersWithRoles() {
        return usersWithRoles;
    }

    /**
     * Builder to build {@link AbstractUserRolesSecurityDomain}.
     */
    public static abstract class Builder {
        private String name;
        private List<UserWithRoles> usersWithRoles;

        protected Builder() {
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withUser(UserWithRoles userWithRoles) {
            this.usersWithRoles.add(userWithRoles);
            return this;
        }

        public Builder withUser(String username, String password, String... roles) {
            this.usersWithRoles.add(UserWithRoles.builder().withName(username).withPassword(password).withRoles(roles).build());
            return this;
        }

    }

}

/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.access.cubegrand.CubeGrandTestInstance;
import org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.access.defaultrole.DefaultRoleTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.api.Roles;
import org.junit.jupiter.api.Test;

/** {@code @Roles} on Connection parameters: named role, union path, default role. */
@RolapContextTest(CubeGrandTestInstance.class)
class RolesTest {

    @Test
    void namedRoleConnection(@Roles("role1") Connection connection) {
        assertThat(connection.getRole()).as("Connection carries the named role").isNotNull();
        assertThat(connection.getCatalog().getName()).contains("Access Cube Grant");
    }

    @Test
    void multipleRolesTakeTheUnionPath(@Roles({ "role1", "role1" }) Connection connection) {
        // The catalog defines only role1; the double entry validates the
        // RoleImpl.union path of the multiple role resolution.
        assertThat(connection.getRole()).isNotNull();
    }

    @Test
    @RolapContextTest(DefaultRoleTestInstance.class)
    void absentRolesMeansDefaultRole(Connection connection) {
        // DefaultRole catalog: role1 is configured as the default.
        assertThat(connection.getRole()).isNotNull();
    }
}

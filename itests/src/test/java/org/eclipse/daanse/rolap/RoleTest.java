/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *
 */
package org.eclipse.daanse.rolap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.db.DatabaseColumn;
import org.eclipse.daanse.olap.api.element.db.DatabaseSchema;
import org.eclipse.daanse.olap.api.element.db.DatabaseTable;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.api.Roles;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.CellKeyTest.FoodmartData;

@RolapContextTest(FoodmartTestInstance.class)
public class RoleTest {

    @Test
    void testDatabaseSchemaWithNoRole(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        try {
            CatalogReader schemaReader = connection.getCatalogReader();
            List<? extends DatabaseSchema> dsList = schemaReader.getDatabaseSchemas();
            assertEquals(
                1,
                dsList.size());
            DatabaseSchema ds = dsList.get(0);
            List<? extends DatabaseTable> tList = ds.getDbTables();
            assertEquals(
                    37,
                    tList.size());
            Optional<? extends DatabaseTable> oT = tList.stream().filter(t -> "sales_fact_1997".equals(t.getName())).findFirst();
            List<? extends DatabaseColumn> cList = oT.get().getDbColumns();
            assertEquals(
                    8,
                    cList.size());
        } finally {
            connection.close();
        }
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, RoleTestModifier.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testDatabaseSchemaWithRole(@Roles("Test") Connection connection) {
        try {
            CatalogReader schemaReader = connection.getCatalogReader();
            List<? extends DatabaseSchema> dsList = schemaReader.getDatabaseSchemas();
            assertEquals(
                1,
                dsList.size());
            DatabaseSchema ds = dsList.get(0);
            List<? extends DatabaseTable> tList = ds.getDbTables();
            assertEquals(
                    3,
                    tList.size());

            Optional<? extends DatabaseTable> oT = tList.stream().filter(t -> "sales_fact_1997".equals(t.getName())).findFirst();
            assertTrue(oT.isPresent());
            List<? extends DatabaseColumn> cList = oT.get().getDbColumns();
            assertEquals(
                    8,
                    cList.size());

            oT = tList.stream().filter(t -> "product".equals(t.getName())).findFirst();
            assertTrue(oT.isPresent());
            cList = oT.get().getDbColumns();
            assertEquals(
                    15,
                    cList.size());

            oT = tList.stream().filter(t -> "salary".equals(t.getName())).findFirst();
            assertTrue(oT.isPresent());
            cList = oT.get().getDbColumns();
            assertEquals(
                    2,
                    cList.size());

        } finally {
            connection.close();
        }
    }

    //withSchema(context, TestCachedNativeFilterModifier::new);
    //Connection connection = ((TestContext)context).getConnection(List.of("test"));

}

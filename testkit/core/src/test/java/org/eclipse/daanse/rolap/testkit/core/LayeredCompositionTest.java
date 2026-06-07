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
package org.eclipse.daanse.rolap.testkit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Catalog;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.cwm.testkit.api.dbcheck.DatabaseCheckSuite;
import org.eclipse.daanse.cwm.testkit.api.dbcheck.DatabaseColumnCheck;
import org.eclipse.daanse.cwm.testkit.api.dbcheck.DatabaseSchemaCheck;
import org.eclipse.daanse.cwm.testkit.api.dbcheck.DatabaseTableCheck;
import org.eclipse.daanse.cwm.testkit.data.DataLayer;
import org.eclipse.daanse.cwm.testkit.database.DatabaseCheckExecutor;
import org.eclipse.daanse.cwm.testkit.database.DatabaseLayer;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Layered testkit smoke tests: Layer 1, Layer 1+2, Layer 1+2+4b. */
class LayeredCompositionTest {

    private static final RelationalFactory REL = RelationalFactory.eINSTANCE;

    private Schema buildTinySchema() {
        Catalog catalog = REL.createCatalog();
        catalog.setName("APP");
        Schema schema = REL.createSchema();
        // intentionally no setName(...) → default schema (unqualified tables)
        catalog.getOwnedElement().add(schema);

        Table persons = REL.createTable();
        persons.setName("persons");
        schema.getOwnedElement().add(persons);
        persons.getFeature().add(column("id", JDBCType.INTEGER, NullableType.COLUMN_NO_NULLS));
        persons.getFeature().add(column("name", JDBCType.VARCHAR, NullableType.COLUMN_NULLABLE));
        return schema;
    }

    private static Column column(String name, JDBCType type, NullableType nullable) {
        SQLSimpleType t = REL.createSQLSimpleType();
        t.setName(type.getName());
        t.setTypeNumber(type.getVendorTypeNumber());
        if (type == JDBCType.VARCHAR) {
            t.setCharacterMaximumLength(100);
        }
        Column c = REL.createColumn();
        c.setName(name);
        c.setType(t);
        c.setIsNullable(nullable);
        return c;
    }

    private ActiveDatabase activeDb() {
        return DatabaseProvider.selected().activate();
    }

    @Test
    void layer1_databaseOnly() throws Exception {
        ActiveDatabase db = activeDb();
        Schema schema = buildTinySchema();
        DatabaseLayer.apply(db.dataSource(), db.dialect(), schema);

        try (Connection c = db.dataSource().getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("select count(*) from persons")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "freshly-created table should be empty");
        }
    }

    @Test
    void layer1_2_databaseAndData() throws Exception {
        ActiveDatabase db = activeDb();
        Schema schema = buildTinySchema();
        DatabaseLayer.apply(db.dataSource(), db.dialect(), schema);

        DataSupplier data = new DataSupplier() {
            @Override
            public Map<String, URL> csvResources() {
                Map<String, URL> m = new LinkedHashMap<>();
                m.put("persons", LayeredCompositionTest.class
                        .getResource("/org/eclipse/daanse/rolap/testkit/core/layered/persons.csv"));
                return m;
            }
        };
        DataLayer.apply(db.dataSource(), db.dialect(), schema, data);

        try (Connection c = db.dataSource().getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("select count(*) from persons")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "persons.csv has 2 rows");
        }
    }

    @TestFactory
    java.util.stream.Stream<DynamicTest> layer4b_databaseChecks() throws Exception {
        ActiveDatabase db = activeDb();
        Schema schema = buildTinySchema();
        DatabaseLayer.apply(db.dataSource(), db.dialect(), schema);

        DatabaseCheckSuite suite = new DatabaseCheckSuite("layered-composition",
                List.of(new DatabaseSchemaCheck("", List.of(new DatabaseTableCheck("persons", List
                        .of(new DatabaseColumnCheck("id", "INTEGER"), new DatabaseColumnCheck("name", "VARCHAR")))))));

        List<DynamicTest> tests = DatabaseCheckExecutor.execute("[layered]", db.dataSource(), suite);
        assertNotNull(tests);
        assertTrue(tests.size() >= 3, "expected at least: 1 table-present + 2 column-present + 2 type assertions");
        return tests.stream();
    }
}

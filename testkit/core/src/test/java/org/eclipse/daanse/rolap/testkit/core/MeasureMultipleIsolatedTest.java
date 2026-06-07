/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.daanse.rolap.testkit.core;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.cwm.testkit.api.DatabaseSupplier;
import org.eclipse.daanse.cwm.testkit.data.DataLayer;
import org.eclipse.daanse.cwm.testkit.database.DatabaseLayer;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.cube.measure.multiple.MeasureMultipleTestInstance;
import org.junit.jupiter.api.Test;

/**
 * Diagnostic: dumps the H2 catalog after loading
 * tutorial.cube.measure.multiple's Fact.csv via the CWM-driven DataLayer.
 */
class MeasureMultipleIsolatedTest {

    @Test
    void inspectMeasureMultipleCsvLoad() throws Exception {
        ActiveDatabase db = DatabaseProvider.selected().activate("MeasureMultipleIsolatedTest");

        MeasureMultipleTestInstance instance = new MeasureMultipleTestInstance();
        DatabaseSupplier dbSupplier = instance.databaseSupplier();
        DataSupplier dataSupplier = new DataSupplier() {
            @Override
            public java.util.Map<String, java.net.URL> csvResources() {
                return instance.csvResources();
            }
        };

        DatabaseLayer.apply(db.dataSource(), db.dialect(), dbSupplier.schema());
        DataLayer.apply(db.dataSource(), db.dialect(), dbSupplier.schema(), dataSupplier);

        System.out.println("=== H2 catalog after measure.multiple load ===");
        try (Connection c = db.dataSource().getConnection()) {
            try (Statement s = c.createStatement();
                    ResultSet rs = s.executeQuery("SELECT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLES "
                            + "WHERE TABLE_SCHEMA = 'PUBLIC' ORDER BY TABLE_NAME")) {
                while (rs.next()) {
                    System.out.println("  table: " + rs.getString(1) + "." + rs.getString(2));
                }
            }
            try (Statement s = c.createStatement();
                    ResultSet rs = s
                            .executeQuery("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                                    + "WHERE TABLE_SCHEMA = 'PUBLIC' AND UPPER(TABLE_NAME) LIKE '%FACT%' "
                                    + "ORDER BY TABLE_NAME, ORDINAL_POSITION")) {
                while (rs.next()) {
                    System.out.printf("  column: %s.%s (%s)%n", rs.getString(1), rs.getString(2), rs.getString(3));
                }
            }
            String sql = "select sum(\"Fact\".\"VALUE1\") as \"m0\" from \"Fact\" as \"Fact\"";
            System.out.println("Probe SQL: " + sql);
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
                if (rs.next()) {
                    System.out.println("  result: " + rs.getObject(1));
                }
            } catch (Exception ex) {
                System.out.println("  threw: " + ex.getClass().getSimpleName() + " — " + ex.getMessage());
            }
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("select sum(VALUE1) from Fact")) {
                if (rs.next()) {
                    System.out.println("  unquoted result: " + rs.getObject(1));
                }
            } catch (Exception ex) {
                System.out.println("  unquoted threw: " + ex.getClass().getSimpleName() + " — " + ex.getMessage());
            }
        }
    }
}

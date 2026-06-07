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

import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.daanse.cwm.testkit.data.DataLayer;
import org.eclipse.daanse.cwm.testkit.database.DatabaseLayer;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.SchoolDataSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.SchoolDatabaseSupplier;
import org.junit.jupiter.api.Test;

/**
 * Compares school CSV row counts to DB row counts after etl load. Always
 * passes; diagnostic-only.
 */
class SchoolEtlRowCountDiagnosticTest {

    @Test
    void compareEtlRowCountsAgainstCsv() throws Exception {
        ActiveDatabase db = DatabaseProvider.selected().activate();

        SchoolDatabaseSupplier dbSup = new SchoolDatabaseSupplier();
        DatabaseLayer.apply(db.dataSource(), db.dialect(), dbSup.schema());

        SchoolDataSupplier dataSup = new SchoolDataSupplier();
        DataLayer.apply(db.dataSource(), db.dialect(), dbSup.schema(), dataSup);

        Map<String, Integer> csvRowCounts = new TreeMap<>();
        for (Map.Entry<String, URL> e : dataSup.csvResources().entrySet()) {
            try (var stream = e.getValue().openStream();
                    var br = new java.io.BufferedReader(new java.io.InputStreamReader(stream))) {
                // CSVs are now header-only (Phase-2 form), so data rows = lines - 1.
                int lines = 0;
                while (br.readLine() != null) {
                    lines++;
                }
                csvRowCounts.put(e.getKey(), Math.max(0, lines - 1));
            }
        }

        Map<String, Integer> dbRowCounts = new TreeMap<>();
        try (Connection c = db.dataSource().getConnection()) {
            for (String t : csvRowCounts.keySet()) {
                try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + t)) {
                    rs.next();
                    dbRowCounts.put(t, rs.getInt(1));
                } catch (Exception ex) {
                    dbRowCounts.put(t, -1);
                }
            }
        }

        System.out.println("=== school etl row-count diagnostic ===");
        for (String t : csvRowCounts.keySet()) {
            int csv = csvRowCounts.get(t);
            int dbCount = dbRowCounts.get(t);
            String marker = (csv == dbCount) ? "OK" : "DIFF";
            System.out.printf("  %-30s csv=%4d  db=%4d  %s%n", t, csv, dbCount, marker);
        }

        // Spot-check schule rows to detect column-shuffle.
        try (Connection c = db.dataSource().getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s
                        .executeQuery("SELECT id, schul_nummer, schul_name, traeger_id, schul_art_id, ganztags_art_id "
                                + "FROM schule ORDER BY id LIMIT 3")) {
            System.out.println("--- schule rows (first 3) ---");
            while (rs.next()) {
                System.out.printf(
                        "  id=%d schul_nummer=%d schul_name=%s traeger_id=%d schul_art_id=%d ganztags_art_id=%d%n",
                        rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getInt(6));
            }
        }

        // Check schul_art (Schulart level source).
        try (Connection c = db.dataSource().getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT * FROM schul_art")) {
            System.out.println("--- schul_art rows ---");
            java.sql.ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            StringBuilder header = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                if (i > 1)
                    header.append(", ");
                header.append(meta.getColumnName(i));
            }
            System.out.println("  cols: " + header);
            while (rs.next()) {
                StringBuilder row = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    if (i > 1)
                        row.append(", ");
                    row.append(rs.getObject(i));
                }
                System.out.println("  " + row);
            }
        }
    }
}

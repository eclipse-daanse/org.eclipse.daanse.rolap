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
 *   SmartCity Jena - initial
 *
 */
package org.eclipse.daanse.rolap.poc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.daanse.cwm.testkit.data.DataLayer;
import org.eclipse.daanse.cwm.testkit.database.DatabaseLayer;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.SchoolDataSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.SchoolDatabaseSupplier;
import org.eclipse.daanse.rolap.testkit.core.TestContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


public class FirstTest {


    @Test
    void capturesMemberChildrenSqlFromSchoolCatalog() throws Exception {
        ActiveDatabase db = DatabaseProvider.selected().activate();
        SchoolDatabaseSupplier dbSup = new SchoolDatabaseSupplier();
        DatabaseLayer.apply(db.dataSource(), db.dialect(), dbSup.schema());
        DataLayer.apply(db.dataSource(), db.dialect(), dbSup.schema(), new SchoolDataSupplier());
        String mdx =
                "SELECT {[Measures].[Anzahl Schulen]} ON COLUMNS FROM [Schulen in Jena (Institutionen)]";
        TestContext ctx = new TestContext(db.dataSource(), db.dialect(), new CatalogSupplier());
        Connection conn = ((Context<?>) ctx).getConnectionWithDefaultRole();
        

        List<String> captured = new CopyOnWriteArrayList<>();
        RolapUtil.setHook(conn.getContext(), captured::add);
        try {
            assertNotNull(conn.execute(conn.parseQuery(mdx)));
        } finally {
            RolapUtil.setHook(conn.getContext(), null);
        }

        assertFalse(captured.isEmpty(),
                "expected at least one member SQL statement to be captured via RolapUtil hook");
        // The captured SQL is the golden baseline for the future builder-based member mapper.
        captured.forEach(sql -> System.out.println("[captured member SQL] " + sql));
    }
}

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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.daanse.cwm.testkit.data.DataLayer;
import org.eclipse.daanse.cwm.testkit.database.DatabaseLayer;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.SchoolDataSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.SchoolDatabaseSupplier;
import org.junit.jupiter.api.Test;

/** MDX query smoke test against the testkit-managed DB. */
class MdxQuerySmokeTest {

    @Test
    void schoolCubeAcceptsSimpleMdx() throws Exception {
        ActiveDatabase db = DatabaseProvider.selected().activate();

        SchoolDatabaseSupplier dbSup = new SchoolDatabaseSupplier();
        DatabaseLayer.apply(db.dataSource(), db.dialect(), dbSup.schema());
        DataLayer.apply(db.dataSource(), db.dialect(), dbSup.schema(), new SchoolDataSupplier());

        TestContext ctx = new TestContext(db.dataSource(), db.dialect(), new CatalogSupplier());
        Connection conn = ((Context<?>) ctx).getConnectionWithDefaultRole();

        Query query = conn.parseQuery("SELECT [Measures].Members ON COLUMNS FROM [Schulen in Jena (Institutionen)]");
        Result result = conn.execute(query);

        assertNotNull(result);
        assertNotNull(result.getAxes());
        assertTrue(result.getAxes().length >= 1, "expected at least one axis");
    }

    private record FixedDialectFactory(Dialect dialect)
            implements org.eclipse.daanse.jdbc.db.dialect.api.DialectFactory {
        @Override
        public Dialect createDialect(org.eclipse.daanse.jdbc.db.dialect.api.DialectInitData init) {
            return dialect;
        }
    }
}

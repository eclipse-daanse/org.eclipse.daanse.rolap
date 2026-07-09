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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.testkit.core;

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
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.SchoolDataSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.SchoolDatabaseSupplier;
import org.junit.jupiter.api.Test;

/**
 * Phase-2 verification substrate for the {@code SqlSelectQuery} replacement: drives a <em>real</em>
 * H2-backed catalog (School) and captures the actual member-level / member-children SQL the
 * legacy {@code SqlMemberSource} emits, via the {@link RolapUtil#setHook} hook fired in
 * {@code SqlStatement}. This is the golden baseline a new builder-based member mapper must
 * reproduce (whitespace-normalized) before cut-over.
 * <p>
 * It uses the in-code School {@code CatalogSupplier} (no XMI) and navigates the catalog
 * programmatically (no hard-coded MDX names): first multi-level non-measure hierarchy → its root
 * members → their children, which forces {@code makeChildMemberSql}.
 */
class MemberSqlCaptureTest {

    @Test
    void capturesMemberChildrenSqlFromSchoolCatalog() throws Exception {
        ActiveDatabase db = DatabaseProvider.selected().activate();
        SchoolDatabaseSupplier dbSup = new SchoolDatabaseSupplier();
        DatabaseLayer.apply(db.dataSource(), db.dialect(), dbSup.schema());
        DataLayer.apply(db.dataSource(), db.dialect(), dbSup.schema(), new SchoolDataSupplier());

        TestContext ctx = new TestContext(db.dataSource(), db.dialect(), new CatalogSupplier());
        Connection conn = ((Context<?>) ctx).getConnectionWithDefaultRole();
        CatalogReader reader = conn.getCatalogReader();

        // First multi-level, non-measure hierarchy in any cube — and the cube it belongs to.
        Hierarchy target = null;
        Cube targetCube = null;
        for (Cube cube : reader.getCubes()) {
            for (Hierarchy h : cube.getHierarchies()) {
                String name = h.getName() == null ? "" : h.getName();
                if (h.getLevels().size() >= 2 && !name.toLowerCase().contains("measure")) {
                    target = h;
                    targetCube = cube;
                    break;
                }
            }
            if (target != null) {
                break;
            }
        }
        assertNotNull(target, "expected a multi-level non-measure hierarchy in the School catalog");

        // Materialise the hierarchy's members on an axis via MDX. Running through the engine
        // binds the ExecutionContext that the member readers require, and forces the
        // member-level / member-children SQL the legacy SqlMemberSource emits.
        String mdx = "SELECT " + target.getUniqueName() + ".Members ON COLUMNS FROM ["
                + targetCube.getName() + "]";

        List<String> captured = new CopyOnWriteArrayList<>();
        RolapUtil.setHook(captured::add);
        try {
            assertNotNull(conn.execute(conn.parseQuery(mdx)));
        } finally {
            RolapUtil.setHook(null);
        }

        assertFalse(captured.isEmpty(),
                "expected at least one member SQL statement to be captured via RolapUtil hook");
        // The captured SQL is the golden baseline for the future builder-based member mapper.
        captured.forEach(sql -> System.out.println("[captured member SQL] " + sql));
    }
}

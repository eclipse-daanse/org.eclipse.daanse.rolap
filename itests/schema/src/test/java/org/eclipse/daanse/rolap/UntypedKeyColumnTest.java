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
package org.eclipse.daanse.rolap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.junit.jupiter.api.Test;

/**
 * A level whose key column the mapping declares without a SQL type holds
 * string keys, as it did before the CWM mapping and as Mondrian's default
 * {@code String} level type did.
 *
 * <p>Regression guard for a member-cache failure on Oracle: the untyped
 * {@code PRODUCT_GROUP} column was taken for NUMERIC, so the children of a
 * group rendered its key unquoted ({@code where PRODUCT_GROUP = G100}) and the
 * database read the key as an identifier ({@code ORA-00904}).
 */
@RolapContextTest(UntypedKeyColumnInstance.class)
class UntypedKeyColumnTest {

    private static final String HIERARCHY = "[" + UntypedKeyColumnInstance.DIMENSION + "].["
            + UntypedKeyColumnInstance.HIERARCHY + "]";

    @Test
    void untypedKeyColumnIsAStringKey(Connection connection) {
        RolapLevel group = (RolapLevel) level(connection, UntypedKeyColumnInstance.GROUP_LEVEL);

        assertThat(group.getKeyColumnPhysicalDatatype()).as("the mapping declares no type").isNull();
        assertThat(group.getDatatype()).isEqualTo(Datatype.VARCHAR);
    }

    @Test
    void childrenOfAGroupAreItsProducts(Connection connection) {
        assertThat(rows(connection, HIERARCHY + ".[G100].Children"))
                .containsExactly(HIERARCHY + ".[G100].[P100]", HIERARCHY + ".[G100].[P200]");
        assertThat(rows(connection, HIERARCHY + ".[G200].Children"))
                .containsExactly(HIERARCHY + ".[G200].[P300]");
    }

    @Test
    void everyGroupDrillsDownToItsProducts(Connection connection) {
        String mdx = "SELECT {[Measures].[" + UntypedKeyColumnInstance.MEASURE_NAME + "]} ON COLUMNS, "
                + "Descendants(" + HIERARCHY + ".[" + UntypedKeyColumnInstance.GROUP_LEVEL + "].Members, "
                + HIERARCHY + ".[" + UntypedKeyColumnInstance.PRODUCT_LEVEL + "], SELF_AND_BEFORE) ON ROWS "
                + "FROM [" + UntypedKeyColumnInstance.CUBE_NAME + "]";

        Result result = connection.execute(connection.parseQuery(mdx));

        List<Position> positions = result.getAxes()[1].getPositions();
        assertThat(positions).hasSize(5);
        assertThat(positions.get(0).get(0).getUniqueName()).isEqualTo(HIERARCHY + ".[G100]");
        assertThat(((Number) result.getCell(new int[] { 0, 0 }).getValue()).doubleValue()).isEqualTo(30.0);
    }

    private static List<String> rows(Connection connection, String set) {
        String mdx = "SELECT {[Measures].[" + UntypedKeyColumnInstance.MEASURE_NAME + "]} ON COLUMNS, "
                + set + " ON ROWS FROM [" + UntypedKeyColumnInstance.CUBE_NAME + "]";
        Result result = connection.execute(connection.parseQuery(mdx));
        return result.getAxes()[1].getPositions().stream()
                .map(position -> position.get(0).getUniqueName())
                .toList();
    }

    private static Level level(Connection connection, String name) {
        RolapCube cube = (RolapCube) connection.getCatalog()
                .lookupCube(UntypedKeyColumnInstance.CUBE_NAME)
                .orElseThrow();
        Hierarchy hierarchy = cube.getHierarchies().stream()
                .filter(h -> h.getUniqueName().equals(HIERARCHY))
                .findFirst()
                .orElseThrow();
        return hierarchy.getLevels().stream()
                .filter(l -> l.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}

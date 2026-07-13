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
package org.eclipse.daanse.rolap.testkit.nullsemantics;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;

import javax.sql.DataSource;

import org.eclipse.daanse.cwm.testkit.database.DatabaseLayer;
import org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase;
import org.eclipse.daanse.jdbc.datasource.testkit.api.DatabaseProvider;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.testkit.core.TestContext;

/**
 * Shared, lazily-initialized test setup for the NULL/decimal-semantics
 * characterization suite: an isolated testkit database with the
 * {@link NullSemanticsCatalogSupplier} schema, five explicit fact rows
 * (inserted via plain JDBC so SQL NULL and exact DECIMAL literals are under
 * test control) and one OLAP connection.
 *
 * <p>
 * Fact data (also mirrored as constants below):
 *
 * <pre>
 * KEY | VAL   | DEC_VAL
 * A   | 10.5  |  99999999999999.9999
 * B   | NULL  |               0.0001
 * C   | 20.25 |               0.0001
 * D   | NULL  |               0.0003
 * E   | 5.0   |  12345678901234.5678
 * </pre>
 */
final class NullSemanticsFixture {

    /** Exact DEC_VAL column values, for BigDecimal reference arithmetic. */
    static final List<BigDecimal> DEC_VALUES = List.of(
            new BigDecimal("99999999999999.9999"),
            new BigDecimal("0.0001"),
            new BigDecimal("0.0001"),
            new BigDecimal("0.0003"),
            new BigDecimal("12345678901234.5678"));

    private static Connection connection;

    private NullSemanticsFixture() {
    }

    static synchronized Connection connection() throws Exception {
        if (connection == null) {
            ActiveDatabase db = DatabaseProvider.selected().activate("NullSemanticsCharacterization");
            NullSemanticsCatalogSupplier supplier = new NullSemanticsCatalogSupplier();
            DatabaseLayer.apply(db.dataSource(), db.dialect(), supplier.schema());
            insertFactRows(db.dataSource());
            TestContext ctx = new TestContext(db.dataSource(), db.dialect(), supplier);
            connection = ((Context<?>) ctx).getConnectionWithDefaultRole();
        }
        return connection;
    }

    /** Parses and executes {@code mdx} against the shared connection. */
    static Result execute(String mdx) throws Exception {
        Connection conn = connection();
        return conn.execute(conn.parseQuery(mdx));
    }

    /** Index of the row axis position whose (last) member is named {@code memberName}. */
    static int rowIndexOf(Result result, String memberName) {
        List<Position> positions = result.getAxes()[1].getPositions();
        for (int i = 0; i < positions.size(); i++) {
            // Position extends List<Member>; getMembers() is not implemented here.
            Position position = positions.get(i);
            if (position.get(position.size() - 1).getName().equals(memberName)) {
                return i;
            }
        }
        throw new AssertionError("no row for member " + memberName + " in " + describeRows(result));
    }

    /** Member names on the row axis, in axis order. */
    static List<String> rowMemberNames(Result result) {
        return result.getAxes()[1].getPositions().stream()
                .map(p -> p.get(p.size() - 1).getName())
                .toList();
    }

    private static String describeRows(Result result) {
        return String.join(", ", rowMemberNames(result));
    }

    private static void insertFactRows(DataSource dataSource) throws Exception {
        try (java.sql.Connection jdbc = dataSource.getConnection();
                PreparedStatement ps = jdbc.prepareStatement(
                        "insert into \"FACT\" (\"KEY\", \"VAL\", \"DEC_VAL\") values (?, ?, ?)")) {
            insert(ps, "A", 10.5, DEC_VALUES.get(0));
            insert(ps, "B", null, DEC_VALUES.get(1));
            insert(ps, "C", 20.25, DEC_VALUES.get(2));
            insert(ps, "D", null, DEC_VALUES.get(3));
            insert(ps, "E", 5.0, DEC_VALUES.get(4));
        }
    }

    private static void insert(PreparedStatement ps, String key, Double val, BigDecimal decVal)
            throws Exception {
        ps.setString(1, key);
        if (val == null) {
            ps.setNull(2, Types.DOUBLE);
        } else {
            ps.setDouble(2, val);
        }
        ps.setBigDecimal(3, decVal);
        ps.executeUpdate();
    }
}

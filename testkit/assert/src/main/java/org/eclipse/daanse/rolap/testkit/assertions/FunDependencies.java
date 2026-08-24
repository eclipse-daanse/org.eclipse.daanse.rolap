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
 */
package org.eclipse.daanse.rolap.testkit.assertions;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.daanse.olap.api.calc.Calc;
import org.eclipse.daanse.olap.api.calc.ResultStyle;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.query.component.Expression;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.common.Util;
import org.opentest4j.AssertionFailedError;

/**
 * Fluent hierarchy-dependency assertions:
 * {@code FunDependencies.assertThatExpr(connection, cube, expr).dependsOn("[Gender].[Gender]")}.
 *
 * <p>
 * Replaces the legacy {@code TestUtil.assertExprDependsOn} / {@code assertMemberExprDependsOn} /
 * {@code assertSetExprDependsOn} family. Those three differ only in how the expression gets
 * compiled - as a scalar calculated member, as a singleton set, or as a set outright - which
 * changes what {@link Calc#dependsOn(Hierarchy)} reports (a set-compiled {@code Calc} can see
 * dependencies a scalar one wouldn't, and vice versa), so each gets its own entry point here
 * rather than being folded into one method with a mode flag:
 * <ul>
 * <li>{@link #assertThatExpr} - scalar, via a calculated {@code [Measures].[Foo]}.</li>
 * <li>{@link #assertThatMemberExpr} - {@code expr} wrapped as the singleton set {@code {expr}}.</li>
 * <li>{@link #assertThatSetExpr} - {@code expr} compiled directly as a set.</li>
 * </ul>
 *
 * <p>
 * {@code dependsOn(...)} takes the expected hierarchies as an unordered list - unlike the legacy
 * methods, which compared a literal {@code "{A, B}"} string and so were sensitive to the cube's
 * internal hierarchy-declaration order, this compares sets: same membership, order doesn't
 * matter. A mismatch throws an {@link AssertionFailedError} carrying the expected/actual
 * hierarchy sets (one per line, sorted) as its expected/actual fields, plus a rendered
 * side-by-side diff in the message.
 */
public final class FunDependencies {

    private FunDependencies() {
    }

    /** Compiles {@code expr} as a scalar calculated measure and asserts which hierarchies it depends on. */
    public static DependsOnAssert assertThatExpr(Connection connection, String cubeName, String expr) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(cubeName, "cubeName");
        Objects.requireNonNull(expr, "expr");
        String mdx = "WITH MEMBER [Measures].[Foo] AS " + Util.singleQuoteString(expr) + " SELECT FROM "
                + quoteCubeName(cubeName);
        return new DependsOnAssert(connection, mdx, true);
    }

    /** Compiles {@code {expr}} - a singleton set - and asserts which hierarchies it depends on. */
    public static DependsOnAssert assertThatMemberExpr(Connection connection, String cubeName, String expr) {
        Objects.requireNonNull(expr, "expr");
        return assertThatSetExpr(connection, cubeName, "{" + expr + "}");
    }

    /** Compiles {@code expr} as a set on the columns axis and asserts which hierarchies it depends on. */
    public static DependsOnAssert assertThatSetExpr(Connection connection, String cubeName, String expr) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(cubeName, "cubeName");
        Objects.requireNonNull(expr, "expr");
        String mdx = "SELECT {" + expr + "} ON COLUMNS FROM " + quoteCubeName(cubeName);
        return new DependsOnAssert(connection, mdx, false);
    }

    public static final class DependsOnAssert {

        private final Connection connection;
        private final String mdx;
        private final boolean scalar;

        private DependsOnAssert(Connection connection, String mdx, boolean scalar) {
            this.connection = connection;
            this.mdx = mdx;
            this.scalar = scalar;
        }

        /**
         * Fails unless the compiled expression depends on exactly this set of hierarchies - named
         * by {@link org.eclipse.daanse.olap.api.element.OlapElement#getUniqueName()} - no more, no
         * fewer. Zero arguments asserts the expression depends on nothing.
         */
        public void dependsOn(String... hierarchyUniqueNames) {
            Query query = connection.parseQuery(mdx);
            query.resolve();
            Expression expression = scalar ? query.getFormulas()[0].getExpression() : query.getAxes()[0].getSet();
            Calc calc = query.compileExpression(expression, scalar, scalar ? null : ResultStyle.ITERABLE);

            Set<String> actual = new LinkedHashSet<>();
            for (Hierarchy hierarchy : query.getCube().getHierarchies()) {
                if (calc.dependsOn(hierarchy)) {
                    actual.add(hierarchy.getUniqueName());
                }
            }
            Set<String> expected = new LinkedHashSet<>(Arrays.asList(hierarchyUniqueNames));

            if (!expected.equals(actual)) {
                throw mismatch(mdx, render(expected), render(actual));
            }
        }

        private static String render(Set<String> hierarchyUniqueNames) {
            return hierarchyUniqueNames.stream().sorted().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    private static String quoteCubeName(String cubeName) {
        return cubeName.indexOf(' ') >= 0 ? Util.quoteMdxIdentifier(cubeName) : cubeName;
    }

    private static AssertionFailedError mismatch(String mdx, String expected, String actual) {
        String diff = GridDiff.render(expected, actual);
        String message = "MDX hierarchy dependencies did not match" + System.lineSeparator() + "MDX:"
                + System.lineSeparator() + mdx + System.lineSeparator() + System.lineSeparator() + diff;
        return new AssertionFailedError(message, expected, actual);
    }
}

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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.Util;
import org.opentest4j.AssertionFailedError;

/**
 * Fluent MDX assertions: {@code assertThatQuery(connection, mdx).returnsGrid(expected)},
 * {@code assertThatAxis(connection, cube, expr).returns(expected)},
 * {@code assertThatExpr(connection, cube, expr).returns(expected)}.
 *
 * <p>
 * Each builder compares legacy-format text verbatim - character for character, formatting
 * (Locale-US et al.) included - same as the legacy {@code TestUtil.assertQueryReturns} /
 * {@code assertAxisReturns} / {@code assertExprReturns} family. What's different is the
 * failure: instead of an {@code org.junit.jupiter.api.Assertions#assertEquals} on two giant
 * strings, a mismatch throws an {@link AssertionFailedError} carrying the expected/actual
 * text as its expected/actual fields (so IDEs open their own diff view) plus a rendered
 * side-by-side diff in the message, for the terminal/CI case where nothing renders that view.
 */
public final class MdxAssert {

    private MdxAssert() {
    }

    /** Starts a fluent assertion for {@code mdx} run over {@code connection}. */
    public static QueryAssert assertThatQuery(Connection connection, String mdx) {
        return new QueryAssert(connection, mdx);
    }

    /** Starts a fluent assertion for the set {@code expression} evaluated on {@code cubeName}'s columns axis. */
    public static AxisAssert assertThatAxis(Connection connection, String cubeName, String expression) {
        return new AxisAssert(connection, cubeName, expression);
    }

    /** Starts a fluent assertion for the scalar {@code expression} evaluated against {@code cubeName}. */
    public static ExprAssert assertThatExpr(Connection connection, String cubeName, String expression) {
        return new ExprAssert(connection, cubeName, expression);
    }

    public static final class QueryAssert {

        private final Connection connection;
        private final String mdx;
        private Optional<Duration> timeout = Optional.empty();

        private QueryAssert(Connection connection, String mdx) {
            this.connection = Objects.requireNonNull(connection, "connection");
            this.mdx = Objects.requireNonNull(mdx, "mdx");
        }

        /** Bounds the query's execution; without this call it runs with no timeout. */
        public QueryAssert withTimeout(Duration timeout) {
            this.timeout = Optional.of(Objects.requireNonNull(timeout, "timeout"));
            return this;
        }

        /** Runs the query and fails unless its rendered grid is exactly {@code expectedGrid}. */
        public void returnsGrid(String expectedGrid) {
            Objects.requireNonNull(expectedGrid, "expectedGrid");
            String actualGrid = renderGrid(execute());
            if (!expectedGrid.equals(actualGrid)) {
                throw mismatch("MDX grid", mdx, expectedGrid, actualGrid);
            }
        }

        /**
         * Runs the query and fails unless it throws an exception whose stack trace (message,
         * causes, and frames) contains {@code pattern} as a literal substring - not a regex,
         * so MDX fragments with brackets/parens in the pattern don't need escaping.
         */
        public void throwsMessage(String pattern) {
            Objects.requireNonNull(pattern, "pattern");
            Throwable thrown = null;
            try {
                execute();
            } catch (Throwable t) {
                thrown = t;
            }
            checkThrows(mdx, thrown, pattern);
        }

        private Result execute() {
            return Mdx.execute(connection, mdx, timeout);
        }
    }

    public static final class AxisAssert {

        private final Connection connection;
        private final String cubeName;
        private final String expression;

        private AxisAssert(Connection connection, String cubeName, String expression) {
            this.connection = Objects.requireNonNull(connection, "connection");
            this.cubeName = Objects.requireNonNull(cubeName, "cubeName");
            this.expression = Objects.requireNonNull(expression, "expression");
        }

        /**
         * Runs {@code select {expression} on columns from cube} and fails unless the axis's
         * positions - one tuple per line, {@code {m1, m2}} for a compound position, plain
         * unique name for a single-member one - render exactly as {@code expected}.
         */
        public void returns(String expected) {
            Objects.requireNonNull(expected, "expected");
            String mdx = query();
            List<Position> positions = Mdx.execute(connection, mdx, Optional.empty()).getAxes()[0]
                    .getPositions();
            String actual = renderPositions(positions);
            if (!expected.equals(actual)) {
                throw mismatch("MDX axis", mdx, expected, actual);
            }
        }

        /** Fails unless evaluating the axis expression throws with a stack trace containing {@code pattern}. */
        public void throwsMessage(String pattern) {
            Objects.requireNonNull(pattern, "pattern");
            String mdx = query();
            Throwable thrown = null;
            try {
                Mdx.execute(connection, mdx, Optional.empty());
            } catch (Throwable t) {
                thrown = t;
            }
            checkThrows(mdx, thrown, pattern);
        }

        private String query() {
            return "select {" + expression + "} on columns from " + quoteCubeName(cubeName);
        }
    }

    public static final class ExprAssert {

        private final Connection connection;
        private final String cubeName;
        private final String expression;

        private ExprAssert(Connection connection, String cubeName, String expression) {
            this.connection = Objects.requireNonNull(connection, "connection");
            this.cubeName = Objects.requireNonNull(cubeName, "cubeName");
            this.expression = Objects.requireNonNull(expression, "expression");
        }

        /**
         * Runs {@code expression} as a calculated measure and fails unless its formatted value
         * is exactly {@code expected}; a null {@code expected} matches a null cell value, which
         * formats as the empty string.
         */
        public void returns(String expected) {
            String expectedOrEmpty = expected == null ? "" : expected;
            String mdx = queryFor(expression);
            String actual = Mdx.execute(connection, mdx, Optional.empty()).getCell(new int[] { 0 })
                    .getFormattedValue();
            if (!expectedOrEmpty.equals(actual)) {
                throw mismatch("MDX expression", mdx, expectedOrEmpty, actual);
            }
        }

        /** Fails unless {@code expression} - wrapped in {@code Iif(expression, "true", "false")} - evaluates to true. */
        public void isTrue() {
            checkBoolean(true);
        }

        /** Fails unless {@code expression} - wrapped in {@code Iif(expression, "true", "false")} - evaluates to false. */
        public void isFalse() {
            checkBoolean(false);
        }

        /**
         * Runs {@code expression} as a calculated measure and fails unless its raw numeric value is
         * within {@code delta} of {@code expected}; two {@code NaN} values are considered equal.
         */
        public void returns(double expected, double delta) {
            String mdx = queryFor(expression);
            Object value = Mdx.execute(connection, mdx, Optional.empty()).getCell(new int[] { 0 })
                    .getValue();
            double actual;
            try {
                actual = ((Number) value).doubleValue();
            } catch (ClassCastException ex) {
                throw new AssertionFailedError("Actual value \"" + value + "\" is not a number.",
                        Double.toString(expected), String.valueOf(value));
            }
            if (Double.isNaN(expected) && Double.isNaN(actual)) {
                return;
            }
            if (Math.abs(expected - actual) > delta) {
                throw mismatch("MDX expression", mdx, Double.toString(expected), Double.toString(actual));
            }
        }

        private void checkBoolean(boolean expected) {
            String mdx = queryFor("Iif (" + expression + ",\"true\",\"false\")");
            String actual = Mdx.execute(connection, mdx, Optional.empty()).getCell(new int[] { 0 })
                    .getFormattedValue();
            String expectedString = expected ? "true" : "false";
            if (!expectedString.equals(actual)) {
                throw mismatch("MDX boolean expression", mdx, expectedString, actual);
            }
        }

        /**
         * Runs {@code expression} as a calculated measure and fails unless it errors - either by
         * throwing during parse/execute, or by yielding an error cell - with a stack trace
         * containing {@code pattern}.
         */
        public void throwsMessage(String pattern) {
            Objects.requireNonNull(pattern, "pattern");
            String mdx = queryFor(expression);
            Throwable thrown = null;
            try {
                Cell cell = Mdx.execute(connection, mdx, Optional.empty()).getCell(new int[] { 0 });
                if (cell.isError()) {
                    thrown = (Throwable) cell.getValue();
                }
            } catch (Throwable t) {
                thrown = t;
            }
            checkThrows(mdx, thrown, pattern);
        }

        private String queryFor(String expr) {
            return "with member [Measures].[Foo] as " + Util.singleQuoteString(expr)
                    + " select {[Measures].[Foo]} on columns from " + quoteCubeName(cubeName);
        }
    }

    private static String quoteCubeName(String cubeName) {
        return cubeName.indexOf(' ') >= 0 ? Util.quoteMdxIdentifier(cubeName) : cubeName;
    }

    private static String renderGrid(Result result) {
        StringWriter sw = new StringWriter();
        result.print(new PrintWriter(sw));
        return sw.toString();
    }

    /** Same layout as legacy {@code TestUtil.toString(List<Position>)}: one line per position, tuples braced. */
    private static String renderPositions(List<Position> positions) {
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (Position position : positions) {
            if (!first) {
                buf.append(System.lineSeparator());
            }
            first = false;
            boolean tuple = position.size() != 1;
            if (tuple) {
                buf.append('{');
            }
            for (int j = 0; j < position.size(); j++) {
                if (j > 0) {
                    buf.append(", ");
                }
                buf.append(position.get(j).getUniqueName());
            }
            if (tuple) {
                buf.append('}');
            }
        }
        return buf.toString();
    }

    private static AssertionFailedError mismatch(String what, String mdx, String expected, String actual) {
        String diff = GridDiff.render(expected, actual);
        String message = what + " did not match" + System.lineSeparator() + "MDX:" + System.lineSeparator() + mdx
                + System.lineSeparator() + System.lineSeparator() + diff;
        return new AssertionFailedError(message, expected, actual);
    }

    private static void checkThrows(String mdx, Throwable thrown, String pattern) {
        if (thrown == null) {
            throw new AssertionFailedError("query did not throw; expected an error matching '" + pattern + "'"
                    + System.lineSeparator() + "MDX:" + System.lineSeparator() + mdx);
        }
        String stackTrace = stackTraceOf(thrown);
        if (!stackTrace.contains(pattern)) {
            throw new AssertionFailedError(
                    "query's error does not contain '" + pattern + "'" + System.lineSeparator() + "MDX:"
                            + System.lineSeparator() + mdx + System.lineSeparator() + "error:"
                            + System.lineSeparator() + stackTrace,
                    pattern, stackTrace, thrown);
        }
    }

    private static String stackTraceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}

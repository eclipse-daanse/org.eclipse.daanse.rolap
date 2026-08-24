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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.core.AbstractBasicContext;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.agg.AndPredicate;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.agg.GroupingSet;
import org.eclipse.daanse.rolap.common.agg.OrPredicate;
import org.eclipse.daanse.rolap.common.agg.Segment;
import org.eclipse.daanse.rolap.common.agg.SegmentWithData;
import org.eclipse.daanse.rolap.common.agg.ValueColumnPredicate;
import org.eclipse.daanse.rolap.common.result.BatchLoader;
import org.eclipse.daanse.rolap.common.result.FastBatchingCellReader;
import org.eclipse.daanse.rolap.common.result.GroupingSetsCollector;
import org.eclipse.daanse.rolap.common.sql.SqlQueryCapabilities;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.opentest4j.AssertionFailedError;

/**
 * Fluent fixture for building {@link CellRequest}s / {@link BatchLoader.Batch}es /
 * {@link GroupingSet}s against a plain {@link Connection}, and for asserting that a batch of
 * cell requests triggers (or doesn't trigger) a particular SQL statement.
 *
 * <p>
 * Replaces the {@code createRequest} / {@code createBatch} / {@code getGroupingSet} /
 * {@code assertRequestSql} family and the {@code CellRequestConstraint} type that used to live on
 * {@code mondrian.rolap.BatchTestCase}, built on three parallel {@code String[]} arrays
 * (table/column/value) with no field names to anchor a mistake against. Here each constrained
 * column is one {@link RequestBuilder#where(String, String, String)} call, and a compound
 * aggregate constraint is a named {@link Constraint} factory ({@link Constraint#yearQuarterMonth},
 * {@link Constraint#countryState}, {@link Constraint#productFamilyDepartment}) rather than a
 * positional {@code (tables, columns, values)} triple assembled by the caller.
 *
 * <p>
 * {@link #forRequests(CellRequest...)} replaces {@code assertRequestSql}'s core mechanic - run a
 * batch of requests through a real {@link FastBatchingCellReader}, and check whether the SQL
 * that would have executed matches (or doesn't) a given statement. Unlike the legacy version, a
 * mismatch or a missing/forbidden query renders every request in {@link #render(CellRequest[])}
 * form as part of the {@link AssertionFailedError}, so a failure says what was actually being
 * asked for rather than just the SQL that didn't show up. Per-dialect pattern selection (the
 * {@code mondrian.test.SqlPattern} / database-product bookkeeping) stays with the caller, since
 * that type lives above this module in the dependency graph; this fixture takes one resolved
 * {@code (sql, triggerSql)} pair per {@link RequestSqlAssert#verify()} call.
 */
public final class CellRequestFixture {

    private final Connection connection;

    private CellRequestFixture(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    /** Starts a fixture against {@code connection} directly. */
    public static CellRequestFixture of(Connection connection) {
        return new CellRequestFixture(connection);
    }

    /** Starts a fixture against {@code context}'s default-role connection. */
    public static CellRequestFixture of(Context<?> context) {
        Objects.requireNonNull(context, "context");
        return new CellRequestFixture(context.getConnectionWithDefaultRole());
    }

    /** Starts building a single {@link CellRequest}. */
    public RequestBuilder request() {
        return new RequestBuilder(connection);
    }

    /** Starts building a {@link BatchLoader.Batch} - the cross product of every {@code where}'s values. */
    public BatchBuilder batch(BatchLoader loader) {
        return new BatchBuilder(connection, loader);
    }

    /** Starts building a {@link GroupingSet} by running a batch straight through aggregation loading. */
    public GroupingSetBuilder groupingSet() {
        return new GroupingSetBuilder(connection);
    }

    /** Starts a SQL-generation assertion over {@code requests}. */
    public RequestSqlAssert forRequests(CellRequest... requests) {
        return new RequestSqlAssert(connection, requests);
    }

    // ------------------------------------------------------------------
    // RequestBuilder
    // ------------------------------------------------------------------

    /** Builds one {@link CellRequest}, one {@link #where} call per constrained column. */
    public static final class RequestBuilder {

        private final Connection connection;
        private String cube;
        private String measure;
        private final List<String[]> wheres = new ArrayList<>();
        private Constraint constraint;

        private RequestBuilder(Connection connection) {
            this.connection = connection;
        }

        /** The cube the request's measure lives on. */
        public RequestBuilder cube(String cubeName) {
            this.cube = cubeName;
            return this;
        }

        /** The measure's unique name, e.g. {@code "[Measures].[Unit Sales]"}. */
        public RequestBuilder measure(String measureName) {
            this.measure = measureName;
            return this;
        }

        /** Constrains {@code table.column} to {@code value}. Call once per constrained column. */
        public RequestBuilder where(String table, String column, String value) {
            wheres.add(new String[] { table, column, value });
            return this;
        }

        /** Attaches a compound (OR-of-AND) aggregate constraint built from a {@link Constraint} factory. */
        public RequestBuilder constrain(Constraint constraint) {
            this.constraint = constraint;
            return this;
        }

        /** Builds the request. */
        public CellRequest build() {
            Objects.requireNonNull(cube, "cube");
            Objects.requireNonNull(measure, "measure");
            RolapStar.Measure starMeasure = lookupMeasure(connection, cube, measure);
            RolapStar star = starMeasure.getStar();
            CellRequest request = new CellRequest(starMeasure, false, false);
            for (String[] where : wheres) {
                String table = where[0];
                String column = where[1];
                String value = where[2];
                if (table != null && !table.isEmpty()) {
                    RolapStar.Column starColumn = star.lookupColumn(table, column);
                    request.addConstrainedColumn(starColumn, new ValueColumnPredicate(starColumn, value));
                }
            }
            if (constraint != null) {
                request.addAggregateList(constraint.getBitKey(star), constraint.toPredicate(star));
            }
            return request;
        }
    }

    // ------------------------------------------------------------------
    // BatchBuilder
    // ------------------------------------------------------------------

    /**
     * Builds a {@link BatchLoader.Batch} holding the cross product of every {@link #where}'s
     * values - one {@link CellRequest} per combination, mirroring the legacy
     * {@code createBatch}/{@code addRequests} recursion.
     */
    public static final class BatchBuilder {

        private final Connection connection;
        private final BatchLoader loader;
        private String cube;
        private String measure;
        private final List<String> tables = new ArrayList<>();
        private final List<String> columns = new ArrayList<>();
        private final List<String[]> valuesPerColumn = new ArrayList<>();
        private Constraint constraint;

        private BatchBuilder(Connection connection, BatchLoader loader) {
            this.connection = connection;
            this.loader = Objects.requireNonNull(loader, "loader");
        }

        public BatchBuilder cube(String cubeName) {
            this.cube = cubeName;
            return this;
        }

        public BatchBuilder measure(String measureName) {
            this.measure = measureName;
            return this;
        }

        /** Constrains {@code table.column} to each of {@code values} - one request per value, crossed with every other {@code where}. */
        public BatchBuilder where(String table, String column, String... values) {
            tables.add(table);
            columns.add(column);
            valuesPerColumn.add(values);
            return this;
        }

        public BatchBuilder constrain(Constraint constraint) {
            this.constraint = constraint;
            return this;
        }

        public BatchLoader.Batch build() {
            Objects.requireNonNull(cube, "cube");
            Objects.requireNonNull(measure, "measure");
            String[] tableArr = tables.toArray(new String[0]);
            String[] columnArr = columns.toArray(new String[0]);

            List<String> firstValues = new ArrayList<>();
            for (String[] values : valuesPerColumn) {
                firstValues.add(values[0]);
            }
            BatchLoader.Batch batch = loader.new Batch(buildRequest(tableArr, columnArr, firstValues));

            addRequests(batch, tableArr, columnArr, new ArrayList<>(), 0);
            return batch;
        }

        private void addRequests(
            BatchLoader.Batch batch, String[] tableArr, String[] columnArr, List<String> selected, int pos)
        {
            if (pos < columnArr.length) {
                for (String value : valuesPerColumn.get(pos)) {
                    selected.add(value);
                    addRequests(batch, tableArr, columnArr, selected, pos + 1);
                    selected.remove(selected.size() - 1);
                }
            } else {
                batch.add(buildRequest(tableArr, columnArr, selected));
            }
        }

        private CellRequest buildRequest(String[] tableArr, String[] columnArr, List<String> values) {
            RequestBuilder rb = new RequestBuilder(connection).cube(cube).measure(measure);
            for (int i = 0; i < tableArr.length; i++) {
                rb.where(tableArr[i], columnArr[i], values.get(i));
            }
            if (constraint != null) {
                rb.constrain(constraint);
            }
            return rb.build();
        }
    }

    // ------------------------------------------------------------------
    // GroupingSetBuilder
    // ------------------------------------------------------------------

    /** Builds a {@link GroupingSet} by running a {@link BatchBuilder}'s batch through aggregation loading. */
    public static final class GroupingSetBuilder {

        private final Connection connection;
        private String cube;
        private String measure;
        private final List<String> tables = new ArrayList<>();
        private final List<String> columns = new ArrayList<>();
        private final List<String[]> valuesPerColumn = new ArrayList<>();

        private GroupingSetBuilder(Connection connection) {
            this.connection = connection;
        }

        public GroupingSetBuilder cube(String cubeName) {
            this.cube = cubeName;
            return this;
        }

        public GroupingSetBuilder measure(String measureName) {
            this.measure = measureName;
            return this;
        }

        public GroupingSetBuilder where(String table, String column, String... values) {
            tables.add(table);
            columns.add(column);
            valuesPerColumn.add(values);
            return this;
        }

        public GroupingSet build() {
            Objects.requireNonNull(cube, "cube");
            Objects.requireNonNull(measure, "measure");
            ExecutionImpl execution =
                new ExecutionImpl(connection.getInternalStatement(), Optional.of(Duration.ofMinutes(5)));
            ExecutionMetadata metadata =
                ExecutionMetadata.of("CellRequestFixture.groupingSet", "CellRequestFixture.groupingSet", null, 0);
            return ExecutionContext.where(
                execution.asContext().createChild(metadata, Optional.empty()),
                () -> {
                    RolapCube rolapCube = lookupCube(connection, cube);
                    AbstractBasicContext<?> abc = (AbstractBasicContext<?>) connection.getContext();
                    BatchLoader loader = new BatchLoader(
                        ExecutionContext.current(),
                        ((AggregationManager) abc.getAggregationManager()).getCacheMgr(),
                        SqlQueryCapabilities.of(rolapCube.getStar().getDialect()),
                        rolapCube);
                    BatchBuilder batchBuilder = new BatchBuilder(connection, loader).cube(cube).measure(measure);
                    for (int i = 0; i < tables.size(); i++) {
                        batchBuilder.where(tables.get(i), columns.get(i), valuesPerColumn.get(i));
                    }
                    BatchLoader.Batch batch = batchBuilder.build();
                    GroupingSetsCollector collector = new GroupingSetsCollector(true);
                    List<Future<Map<Segment, SegmentWithData>>> segmentFutures = new ArrayList<>();
                    batch.loadAggregation(collector, segmentFutures);
                    return collector.getGroupingSets().get(0);
                });
        }
    }

    // ------------------------------------------------------------------
    // RequestSqlAssert
    // ------------------------------------------------------------------

    /**
     * Fluent SQL-generation assertion over a fixed set of {@link CellRequest}s:
     * {@code fixture.forRequests(requests).expectSql(sql, trigger).verify()}.
     */
    public static final class RequestSqlAssert {

        private final Connection connection;
        private final CellRequest[] requests;
        private String expectedSql;
        private String triggerSql;
        private boolean negative;

        private RequestSqlAssert(Connection connection, CellRequest[] requests) {
            this.connection = connection;
            this.requests = Objects.requireNonNull(requests, "requests");
            if (requests.length == 0) {
                throw new IllegalArgumentException("requests must not be empty");
            }
        }

        /** Records that {@link #verify()} must see {@code sql} (also used as the trigger prefix) executed. */
        public RequestSqlAssert expectSql(String sql) {
            return expectSql(sql, sql);
        }

        /** Records that {@link #verify()} must see a statement starting with {@code triggerSql}, equal to {@code sql}. */
        public RequestSqlAssert expectSql(String sql, String triggerSql) {
            this.expectedSql = Objects.requireNonNull(sql, "sql");
            this.triggerSql = Objects.requireNonNull(triggerSql, "triggerSql");
            this.negative = false;
            return this;
        }

        /** Records that {@link #verify()} must NOT see a statement starting with {@code triggerSql}. */
        public RequestSqlAssert forbidSql(String triggerSql) {
            this.expectedSql = null;
            this.triggerSql = Objects.requireNonNull(triggerSql, "triggerSql");
            this.negative = true;
            return this;
        }

        /** Runs the requests through a real batch load and checks the recorded expectation. */
        public void verify() {
            if (triggerSql == null) {
                throw new IllegalStateException("call expectSql(...) or forbidSql(...) before verify()");
            }

            RolapCube cube = lookupCube(connection, requests[0].getMeasure().getCubeName());
            TriggerHook hook = new TriggerHook(triggerSql);
            RolapUtil.setHook(connection.getContext(), hook);
            Bomb bomb = null;
            ExecutionImpl execution =
                new ExecutionImpl(connection.getInternalStatement(), Optional.of(Duration.ofMillis(1000)));
            AbstractBasicContext<?> abc =
                (AbstractBasicContext<?>) execution.getDaanseStatement().getDaanseConnection().getContext();
            AggregationManager aggMgr = (AggregationManager) abc.getAggregationManager();
            ExecutionMetadata metadata = ExecutionMetadata.of("CellRequestFixture", "CellRequestFixture", null, 0);
            ExecutionContext executionContext = execution.asContext().createChild(metadata, Optional.empty());
            try {
                FastBatchingCellReader fbcr = new FastBatchingCellReader(execution, cube, aggMgr);
                for (CellRequest request : requests) {
                    fbcr.recordCellRequest(request);
                }
                ExecutionContext.where(executionContext, fbcr::loadAggregations);
            } catch (Bomb e) {
                bomb = e;
            } catch (RuntimeException e) {
                bomb = Util.getMatchingCause(e, Bomb.class);
                if (bomb == null) {
                    throw e;
                }
            } finally {
                RolapUtil.setHook(connection.getContext(), null);
            }

            if (negative) {
                if (bomb != null || hook.foundMatch()) {
                    throw new AssertionFailedError("forbidden query [" + triggerSql + "] detected"
                            + System.lineSeparator() + "Requests:" + System.lineSeparator() + render(requests));
                }
                return;
            }

            if (bomb == null) {
                throw new AssertionFailedError("expected query [" + expectedSql + "] did not occur"
                        + System.lineSeparator() + "Requests:" + System.lineSeparator() + render(requests));
            }
            String expected = replaceQuotes(expectedSql);
            String actual = replaceQuotes(bomb.sql);
            if (!expected.equals(actual)) {
                String diff = GridDiff.render(expected, actual);
                throw new AssertionFailedError(
                        "SQL did not match" + System.lineSeparator() + "Requests:" + System.lineSeparator()
                                + render(requests) + System.lineSeparator() + System.lineSeparator() + diff,
                        expected, actual);
            }
        }
    }

    // ------------------------------------------------------------------
    // Constraint
    // ------------------------------------------------------------------

    /**
     * A compound (OR-of-AND) aggregate constraint over one or more columns, attached to a request
     * via {@link RequestBuilder#constrain(Constraint)} / {@link BatchBuilder#constrain(Constraint)}.
     * Named factories cover the shapes the legacy {@code makeConstraintXxx} helpers built;
     * {@link #of(String[], String[], List)} remains for anything else.
     */
    public static final class Constraint {

        private final String[] tables;
        private final String[] columns;
        private final List<String[]> valueTuples;

        private Constraint(String[] tables, String[] columns, List<String[]> valueTuples) {
            this.tables = tables;
            this.columns = columns;
            this.valueTuples = valueTuples;
        }

        /** A constraint over {@code (time_by_day.the_year, time_by_day.quarter, time_by_day.month_of_year)}. */
        public static Constraint yearQuarterMonth(String[]... tuples) {
            return of(
                new String[] { "time_by_day", "time_by_day", "time_by_day" },
                new String[] { "the_year", "quarter", "month_of_year" },
                List.of(tuples));
        }

        /** A constraint over {@code (store.store_country, store.store_state)}. */
        public static Constraint countryState(String[]... tuples) {
            return of(
                new String[] { "store", "store" },
                new String[] { "store_country", "store_state" },
                List.of(tuples));
        }

        /** A constraint over {@code (product_class.product_family, product_class.product_department)}. */
        public static Constraint productFamilyDepartment(String[]... tuples) {
            return of(
                new String[] { "product_class", "product_class" },
                new String[] { "product_family", "product_department" },
                List.of(tuples));
        }

        /** A constraint over an arbitrary {@code tables}/{@code columns} pair; each entry of {@code valueTuples} is one OR-branch. */
        public static Constraint of(String[] tables, String[] columns, List<String[]> valueTuples) {
            Objects.requireNonNull(tables, "tables");
            Objects.requireNonNull(columns, "columns");
            Objects.requireNonNull(valueTuples, "valueTuples");
            if (tables.length != columns.length) {
                throw new IllegalArgumentException(
                        "tables and columns must be the same length (" + tables.length + " vs " + columns.length + ")");
            }
            for (String[] tuple : valueTuples) {
                if (tuple.length != tables.length) {
                    throw new IllegalArgumentException(
                            "value tuple length " + tuple.length + " does not match column count " + tables.length);
                }
            }
            return new Constraint(tables, columns, new ArrayList<>(valueTuples));
        }

        private BitKey getBitKey(RolapStar star) {
            return star.getBitKey(tables, columns);
        }

        private StarPredicate toPredicate(RolapStar star) {
            RolapStar.Column[] starColumns = new RolapStar.Column[tables.length];
            for (int i = 0; i < tables.length; i++) {
                starColumns[i] = star.lookupColumn(tables[i], columns[i]);
            }

            List<StarPredicate> orPredicates = new ArrayList<>();
            for (String[] values : valueTuples) {
                List<StarPredicate> andPredicates = new ArrayList<>();
                for (int i = 0; i < values.length; i++) {
                    andPredicates.add(new ValueColumnPredicate(starColumns[i], values[i]));
                }
                orPredicates.add(andPredicates.size() == 1 ? andPredicates.get(0) : new AndPredicate(andPredicates));
            }

            return orPredicates.size() == 1 ? orPredicates.get(0) : new OrPredicate(orPredicates);
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private static RolapStar.Measure lookupMeasure(Connection connection, String cube, String measureName) {
        Cube olapCube = connection.getCatalog().lookupCube(cube).orElseThrow();
        Member measure =
            olapCube.getCatalogReader(null).getMemberByUniqueName(Util.parseIdentifier(measureName), true);
        return RolapStar.getStarMeasure(measure);
    }

    private static RolapCube lookupCube(Connection connection, String cube) {
        return (RolapCube) connection.getCatalog().lookupCube(cube).orElseThrow();
    }

    private static String replaceQuotes(String s) {
        return s.replace('`', '"').replace('\'', '"');
    }

    /** Renders one request as {@code cube}/{@code measure} plus each constrained {@code table.column = value}. */
    static String render(CellRequest request) {
        StringBuilder sb = new StringBuilder();
        RolapStar.Measure measure = request.getMeasure();
        sb.append("CellRequest{cube=").append(measure.getCubeName())
                .append(", measure=").append(measure.getName());
        if (request.isUnsatisfiable()) {
            sb.append(", UNSATISFIABLE");
        } else {
            RolapStar.Column[] columns = request.getConstrainedColumns();
            Object[] values = request.getSingleValues();
            for (int i = 0; i < columns.length; i++) {
                sb.append(System.lineSeparator()).append("  ")
                        .append(columns[i].getTable().getAlias()).append('.').append(columns[i].getName())
                        .append(" = ").append(values[i]);
            }
        }
        for (String compound : request.getCompoundPredicateStrings()) {
            sb.append(System.lineSeparator()).append("  compound: ").append(compound);
        }
        sb.append(System.lineSeparator()).append('}');
        return sb.toString();
    }

    /** Renders every request, one {@link #render(CellRequest)} block per line group. */
    static String render(CellRequest[] requests) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < requests.length; i++) {
            if (i > 0) {
                sb.append(System.lineSeparator());
            }
            sb.append(render(requests[i]));
        }
        return sb.toString();
    }

    /** Fake exception used to interrupt execution the instant the trigger SQL is seen. */
    private static final class Bomb extends Error {
        final String sql;

        Bomb(String sql) {
            this.sql = sql;
        }
    }

    private static final class TriggerHook implements RolapUtil.ExecuteQueryHook {

        private final String trigger;
        private boolean foundMatch;

        TriggerHook(String trigger) {
            // Normalise whitespace once; matchTrigger normalises the captured SQL the same way so
            // a multi-line indented expectation matches a single-line captured query.
            this.trigger = trigger.replaceAll("\\s+", " ").trim();
        }

        private boolean matchTrigger(String sql) {
            String s = replaceQuotes(sql).replaceAll("\\s+", " ").trim();
            String t = replaceQuotes(trigger);
            if (s.startsWith(t) && !foundMatch) {
                foundMatch = true;
            }
            return s.startsWith(t);
        }

        @Override
        public void onExecuteQuery(String sql) {
            if (matchTrigger(sql)) {
                throw new Bomb(sql);
            }
        }

        boolean foundMatch() {
            return foundMatch;
        }
    }
}

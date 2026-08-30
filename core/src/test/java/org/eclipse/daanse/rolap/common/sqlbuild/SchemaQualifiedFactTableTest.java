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
package org.eclipse.daanse.rolap.common.sqlbuild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.RolapStar.Condition.JoinColumn;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Verifies that every fact-table FROM emitted by the builder fast paths carries the schema of the
 * star table's relation — the same qualification {@link RelationFromMapper#fromTable} (member/level
 * queries) and {@link AggJoinPlanner#aggTableFrom} (agg tables) already apply. Without it a catalog
 * whose fact table lives outside the connection's default search path fails on every cell/segment
 * request ({@code relation "fact" does not exist}) while the dimension queries — correctly
 * qualified — keep working.
 */
class SchemaQualifiedFactTableTest {

    private static final String SCHEMA = "theschema";

    /** A star fixture: fact + one joined dimension, one dimension column and one sum measure —
     *  each table's relation owned by {@code theschema} (or unowned when {@code schema} is null). */
    private record Fixture(RolapStar.Table fact, RolapStar.Table dim, RolapStar.Column idCol,
            RolapStar.Measure measure, Predicate filter) {
    }

    private static NamedColumnSet columnSet(String name, String schemaName) {
        NamedColumnSet ncs = mock(NamedColumnSet.class);
        when(ncs.getName()).thenReturn(name);
        if (schemaName != null) {
            Schema schema = mock(Schema.class);
            when(schema.getName()).thenReturn(schemaName);
            when(ncs.getNamespace()).thenReturn(schema);
        }
        return ncs;
    }

    private static RolapStar.Table table(String name, String schemaName, RolapStar.Table parent,
            RolapStar.Condition join) {
        NamedColumnSet ncs = columnSet(name, schemaName);
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getTableName()).thenReturn(name);
        when(t.getAlias()).thenReturn(name);
        when(t.getParentTable()).thenReturn(parent);
        when(t.getJoinCondition()).thenReturn(join);
        when(t.getTable()).thenReturn(ncs);
        return t;
    }

    private static Fixture fixture(String schemaName) {
        RolapStar.Table fact = table("fact_sales", schemaName, null, null);
        RolapStar.Condition join = mock(RolapStar.Condition.class);
        when(join.leftColumn()).thenReturn(Optional.of(new JoinColumn("fact_sales", "time_id")));
        when(join.rightColumn()).thenReturn(Optional.of(new JoinColumn("dim_time", "id")));
        RolapStar.Table dim = table("dim_time", schemaName, fact, join);

        RolapStar.Column idCol = mock(RolapStar.Column.class);
        when(idCol.getTable()).thenReturn(dim);
        when(idCol.getExpression()).thenReturn(new RolapColumn("dim_time", "id"));
        when(idCol.getInternalType()).thenReturn(BestFitColumnType.INT);

        Aggregator sum = mock(Aggregator.class);
        when(sum.getExpression(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> new StringBuilder("sum(" + inv.getArgument(0) + ")"));
        RolapStar.Measure measure = mock(RolapStar.Measure.class);
        when(measure.getTable()).thenReturn(fact);
        when(measure.getExpression()).thenReturn(new RolapColumn("fact_sales", "amount"));
        when(measure.getInternalType()).thenReturn(BestFitColumnType.DECIMAL);
        when(measure.getAggregator()).thenReturn(sum);

        Predicate filter = Predicates.comparison(Expressions.column(TableAlias.of("dim_time"), "id"),
                ComparisonOperator.EQ, Expressions.literal(4, Datatype.INTEGER));
        return new Fixture(fact, dim, idCol, measure, filter);
    }

    private static String aggregateSql(Fixture f) {
        Dialect ansi = new AnsiDialect();
        return new DialectSqlRenderer(ansi)
                .render(AggregateSqlMapper.aggregate(f.fact(), List.of(f.idCol()), List.of(f.filter()),
                        List.of(f.measure()), ansi))
                .sql();
    }

    /** Segment request: the fact table FROM and the joined dimension both spell the schema. */
    @Test
    void aggregateQualifiesFactAndJoinedTablesWithSchema() {
        assertThat(aggregateSql(fixture(SCHEMA))).isEqualTo(
                "select \"dim_time\".\"id\" as \"c0\", sum(\"fact_sales\".\"amount\") as \"m0\""
                + " from \"theschema\".\"fact_sales\" as \"fact_sales\""
                + " join \"theschema\".\"dim_time\" as \"dim_time\""
                + " on \"fact_sales\".\"time_id\" = \"dim_time\".\"id\""
                + " where \"dim_time\".\"id\" = 4"
                + " group by \"dim_time\".\"id\"");
    }

    /** A relation not owned by a schema keeps the bare spelling — no {@code "null".} prefix. */
    @Test
    void aggregateKeepsBareNamesWithoutSchema() {
        assertThat(aggregateSql(fixture(null))).isEqualTo(
                "select \"dim_time\".\"id\" as \"c0\", sum(\"fact_sales\".\"amount\") as \"m0\""
                + " from \"fact_sales\" as \"fact_sales\""
                + " join \"dim_time\" as \"dim_time\""
                + " on \"fact_sales\".\"time_id\" = \"dim_time\".\"id\""
                + " where \"dim_time\".\"id\" = 4"
                + " group by \"dim_time\".\"id\"");
    }

    /** Drill-through detail rows ride the same FROM builder — schema-qualified as well. */
    @Test
    void drillThroughQualifiesFactAndJoinedTablesWithSchema() {
        Fixture f = fixture(SCHEMA);
        Dialect ansi = new AnsiDialect();
        String sql = new DialectSqlRenderer(ansi)
                .render(AggregateSqlMapper.drillThrough(f.fact(),
                        List.of(new AggregateSqlMapper.DrillColumn(f.idCol(), null, true, "Id", false)),
                        List.of(), List.of(), false, 0, ansi))
                .sql();

        assertThat(sql)
                .contains(" from \"theschema\".\"fact_sales\" as \"fact_sales\"")
                .contains(" join \"theschema\".\"dim_time\" as \"dim_time\"");
    }

    /** {@link JoinPlanner#tableFromClause} — the member-query fact FROM — carries the schema in its
     *  {@link FromClause.FromTable} table reference. */
    @Test
    void joinPlannerTableFromClauseCarriesSchemaReference() {
        FromClause from = JoinPlanner.tableFromClause(fixture(SCHEMA).fact());

        assertThat(from).isInstanceOf(FromClause.FromTable.class);
        assertThat(((FromClause.FromTable) from).table().schema())
                .map(s -> s.name()).contains(SCHEMA);
    }

    /** ... and stays empty when the relation is unowned. */
    @Test
    void joinPlannerTableFromClauseWithoutSchemaStaysBare() {
        FromClause from = JoinPlanner.tableFromClause(fixture(null).fact());

        assertThat(from).isInstanceOf(FromClause.FromTable.class);
        assertThat(((FromClause.FromTable) from).table().schema()).isEmpty();
    }
}

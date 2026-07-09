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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.common.agg.ListColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.MemberTuplePredicate;
import org.eclipse.daanse.rolap.common.agg.MinusStarPredicate;
import org.eclipse.daanse.rolap.common.agg.RangeColumnPredicate;
import org.eclipse.daanse.rolap.common.agg.ValueColumnPredicate;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link StarPredicateTranslator} turns ROLAP star predicates
 * ({@code ValueColumnPredicate}, {@code ListColumnPredicate}, {@code RangeColumnPredicate},
 * {@code MemberTuplePredicate}, {@code MinusStarPredicate}, and the fake-column and resolver
 * variants) into builder {@link Predicate}s that render to the expected WHERE SQL.
 */
class StarPredicateTranslatorTest {

    private static RolapStar.Column column(String alias, String name, Datatype type) {
        RolapStar.Table table = mock(RolapStar.Table.class);
        when(table.getAlias()).thenReturn(alias);
        RolapStar.Column col = mock(RolapStar.Column.class);
        when(col.getTable()).thenReturn(table);
        when(col.getExpression()).thenReturn(new org.eclipse.daanse.rolap.element.RolapColumn(alias, name));
        when(col.getDatatype()).thenReturn(type);
        return col;
    }

    /** Render a predicate as the WHERE of a trivial query (so we exercise the real renderer). */
    private static String renderWhere(Predicate p) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(From.table("schul_jahr", TableAlias.of("schul_jahr")));
        q.where(p);
        String sql = new DialectSqlRenderer(new AnsiDialect()).render(q.build()).sql();
        return sql.substring(sql.indexOf(" where ") + " where ".length());
    }

    @Test
    void valueColumnPredicateBecomesEquals() {
        RolapStar.Column col = column("schul_jahr", "id", Datatype.INTEGER);
        ValueColumnPredicate value = mock(ValueColumnPredicate.class);
        when(value.getConstrainedColumn()).thenReturn(col);
        when(value.getValue()).thenReturn(4);

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(value)))
                .isEqualTo("\"schul_jahr\".\"id\" = 4");
    }

    @Test
    void listColumnPredicateBecomesIn() {
        RolapStar.Column col = column("schul_jahr", "id", Datatype.INTEGER);
        ValueColumnPredicate v1 = mock(ValueColumnPredicate.class);
        when(v1.getConstrainedColumn()).thenReturn(col);
        when(v1.getValue()).thenReturn(4);
        ValueColumnPredicate v2 = mock(ValueColumnPredicate.class);
        when(v2.getConstrainedColumn()).thenReturn(col);
        when(v2.getValue()).thenReturn(5);

        ListColumnPredicate list = mock(ListColumnPredicate.class);
        when(list.getConstrainedColumn()).thenReturn(col);
        when(list.getPredicates()).thenReturn(List.<StarColumnPredicate>of(v1, v2));

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(list)))
                .isEqualTo("\"schul_jahr\".\"id\" in (4, 5)");
    }

    /**
     * The fake-column case ({@code AggQuerySpec}'s segment predicate with no constrained column):
     * the WHERE column is the caller-supplied node and the literal type the caller-supplied
     * fallback datatype, rendered as an IN-list.
     */
    @Test
    void fakeColumnListPredicateRendersInListLikeCreateInExpr() {
        ValueColumnPredicate v1 = mock(ValueColumnPredicate.class);
        when(v1.getConstrainedColumn()).thenReturn(null);
        when(v1.getValue()).thenReturn(1997);
        ValueColumnPredicate v2 = mock(ValueColumnPredicate.class);
        when(v2.getConstrainedColumn()).thenReturn(null);
        when(v2.getValue()).thenReturn(1998);
        ListColumnPredicate list = mock(ListColumnPredicate.class);
        when(list.getPredicates()).thenReturn(List.<StarColumnPredicate>of(v1, v2));

        org.eclipse.daanse.sql.statement.api.expression.SqlExpression node =
                org.eclipse.daanse.sql.statement.api.Expressions.column(
                        TableAlias.of("agg"), "the_year");
        Predicate p = StarPredicateTranslator.toColumnPredicate(list, node, Datatype.INTEGER);
        assertThat(renderWhere(p)).isEqualTo("\"agg\".\"the_year\" in (1997, 1998)");
    }

    /**
     * The agg-slicer-tuple path: translate with a column resolver that substitutes an
     * aggregate-table node for the base column. The IN factoring is unchanged — only the column
     * expression differs from the default {@code JoinPlanner.expressionFor}.
     */
    @Test
    void resolverSubstitutesColumnNodeForListIn() {
        RolapStar.Column col = column("time_by_day", "the_year", Datatype.INTEGER);
        ValueColumnPredicate v1 = value(col, 1997);
        ValueColumnPredicate v2 = value(col, 1998);
        ListColumnPredicate list = mock(ListColumnPredicate.class);
        when(list.getConstrainedColumn()).thenReturn(col);
        when(list.getPredicates()).thenReturn(List.<StarColumnPredicate>of(v1, v2));

        java.util.function.Function<RolapStar.Column, org.eclipse.daanse.sql.statement.api.expression.SqlExpression>
            resolver = c -> org.eclipse.daanse.sql.statement.api.Expressions.column(
                TableAlias.of("agg_c_10"), "the_year");

        // default path renders the base column; the resolver substitutes the agg node.
        assertThat(renderWhere(StarPredicateTranslator.toPredicate(list)))
                .isEqualTo("\"time_by_day\".\"the_year\" in (1997, 1998)");
        assertThat(renderWhere(StarPredicateTranslator.toPredicate(list, resolver)))
                .isEqualTo("\"agg_c_10\".\"the_year\" in (1997, 1998)");
    }

    /** The resolver threads through the OrPredicate IN-list factoring too (single-column OR → IN). */
    @Test
    void resolverSubstitutesColumnNodeForOrFactoredIn() {
        RolapStar.Column col = column("time_by_day", "the_year", Datatype.INTEGER);
        ValueColumnPredicate v1 = value(col, 1997);
        ValueColumnPredicate v2 = value(col, 1998);
        org.eclipse.daanse.rolap.common.agg.OrPredicate or =
                mock(org.eclipse.daanse.rolap.common.agg.OrPredicate.class);
        when(or.getChildren()).thenReturn(
                List.<org.eclipse.daanse.rolap.common.star.StarPredicate>of(v1, v2));

        java.util.function.Function<RolapStar.Column, org.eclipse.daanse.sql.statement.api.expression.SqlExpression>
            resolver = c -> org.eclipse.daanse.sql.statement.api.Expressions.column(
                TableAlias.of("agg_c_10"), "the_year");

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(or, resolver)))
                .isEqualTo("((\"agg_c_10\".\"the_year\" in (1997, 1998)))");
    }

    private static ValueColumnPredicate value(RolapStar.Column col, Object v) {
        ValueColumnPredicate p = mock(ValueColumnPredicate.class);
        when(p.getConstrainedColumn()).thenReturn(col);
        when(p.getValue()).thenReturn(v);
        return p;
    }

    // ---- RangeColumnPredicate ------------------------------------------------

    @Test
    void rangeLowerInclusiveBecomesGreaterEqual() {
        RolapStar.Column col = column("schul_jahr", "id", Datatype.INTEGER);
        ValueColumnPredicate lower = value(col, 5);
        RangeColumnPredicate range = mock(RangeColumnPredicate.class);
        when(range.getConstrainedColumn()).thenReturn(col);
        when(range.getLowerBound()).thenReturn(lower);
        when(range.getLowerInclusive()).thenReturn(true);
        when(range.getUpperBound()).thenReturn(null);

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(range)))
                .isEqualTo("\"schul_jahr\".\"id\" >= 5");
    }

    @Test
    void rangeUpperExclusiveBecomesLessThan() {
        RolapStar.Column col = column("schul_jahr", "id", Datatype.INTEGER);
        ValueColumnPredicate upper = value(col, 10);
        RangeColumnPredicate range = mock(RangeColumnPredicate.class);
        when(range.getConstrainedColumn()).thenReturn(col);
        when(range.getLowerBound()).thenReturn(null);
        when(range.getUpperBound()).thenReturn(upper);
        when(range.getUpperInclusive()).thenReturn(false);

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(range)))
                .isEqualTo("\"schul_jahr\".\"id\" < 10");
    }

    @Test
    void rangeTwoSidedInclusiveLowerExclusiveUpperBecomesAnd() {
        RolapStar.Column col = column("schul_jahr", "id", Datatype.INTEGER);
        ValueColumnPredicate lower = value(col, 5);
        ValueColumnPredicate upper = value(col, 10);
        RangeColumnPredicate range = mock(RangeColumnPredicate.class);
        when(range.getConstrainedColumn()).thenReturn(col);
        when(range.getLowerBound()).thenReturn(lower);
        when(range.getLowerInclusive()).thenReturn(true);
        when(range.getUpperBound()).thenReturn(upper);
        when(range.getUpperInclusive()).thenReturn(false);

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(range)))
                .isEqualTo("(\"schul_jahr\".\"id\" >= 5 and \"schul_jahr\".\"id\" < 10)");
    }

    // ---- MemberTuplePredicate ------------------------------------------------

    @Test
    void memberTupleEqBecomesEqualityConjunction() {
        RolapStar.Column year = column("d", "year", Datatype.INTEGER);
        RolapStar.Column quarter = column("d", "quarter", Datatype.VARCHAR);
        MemberTuplePredicate tuple = mock(MemberTuplePredicate.class);
        when(tuple.getConstrainedColumnList()).thenReturn(List.of(year, quarter));
        when(tuple.getBoundSpecs()).thenReturn(List.of(
                new MemberTuplePredicate.BoundSpec(List.of(1997, "Q1"),
                        MemberTuplePredicate.BoundRelation.EQ)));

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(tuple)))
                .isEqualTo("(\"d\".\"year\" = 1997 and \"d\".\"quarter\" = 'Q1')");
    }

    @Test
    void memberTupleGtBecomesLexicographicOr() {
        RolapStar.Column year = column("d", "year", Datatype.INTEGER);
        RolapStar.Column quarter = column("d", "quarter", Datatype.VARCHAR);
        MemberTuplePredicate tuple = mock(MemberTuplePredicate.class);
        when(tuple.getConstrainedColumnList()).thenReturn(List.of(year, quarter));
        when(tuple.getBoundSpecs()).thenReturn(List.of(
                new MemberTuplePredicate.BoundSpec(List.of(1997, "Q1"),
                        MemberTuplePredicate.BoundRelation.GT)));

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(tuple)))
                .isEqualTo("(\"d\".\"year\" > 1997 or (\"d\".\"year\" = 1997 and \"d\".\"quarter\" > 'Q1'))");
    }

    @Test
    void memberTupleMultiBoundBecomesAnd() {
        RolapStar.Column year = column("d", "year", Datatype.INTEGER);
        MemberTuplePredicate tuple = mock(MemberTuplePredicate.class);
        when(tuple.getConstrainedColumnList()).thenReturn(List.of(year));
        when(tuple.getBoundSpecs()).thenReturn(List.of(
                new MemberTuplePredicate.BoundSpec(List.of(1990), MemberTuplePredicate.BoundRelation.GE),
                new MemberTuplePredicate.BoundSpec(List.of(2000), MemberTuplePredicate.BoundRelation.LT)));

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(tuple)))
                .isEqualTo("(\"d\".\"year\" >= 1990 and \"d\".\"year\" < 2000)");
    }

    @Test
    void memberTupleColumnValueMisalignmentDeclines() {
        RolapStar.Column year = column("d", "year", Datatype.INTEGER);
        MemberTuplePredicate tuple = mock(MemberTuplePredicate.class);
        when(tuple.getConstrainedColumnList()).thenReturn(List.of(year));
        // two values but only one column (a non-RolapCubeLevel level was skipped): must decline.
        when(tuple.getBoundSpecs()).thenReturn(List.of(
                new MemberTuplePredicate.BoundSpec(List.of(1997, "Q1"),
                        MemberTuplePredicate.BoundRelation.EQ)));

        assertThatThrownBy(() -> StarPredicateTranslator.toPredicate(tuple))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("misalignment");
    }

    // ---- MinusStarPredicate --------------------------------------------------

    @Test
    void minusWithNullCoverageBecomesAndNot() {
        RolapStar.Column col = column("schul_jahr", "id", Datatype.INTEGER);
        ValueColumnPredicate plusSide = value(col, 4);
        MinusStarPredicate minus = mock(MinusStarPredicate.class);
        when(minus.getConstrainedColumn()).thenReturn(col);
        when(minus.getPlus()).thenReturn(plusSide);
        // minus side excludes the null value → it already constrains null; no extra IS NULL widening.
        ValueColumnPredicate minusSide = value(col, Util.sqlNullValue);
        doAnswer(inv -> {
            ((Collection<Object>) inv.getArgument(0)).add(Util.sqlNullValue);
            return null;
        }).when(minusSide).values(org.mockito.ArgumentMatchers.any());
        when(minus.getMinus()).thenReturn(minusSide);

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(minus)))
                .isEqualTo("(\"schul_jahr\".\"id\" = 4 and not (\"schul_jahr\".\"id\" is null))");
    }

    @Test
    void minusWithoutNullCoverageWidensWithIsNull() {
        RolapStar.Column col = column("schul_jahr", "id", Datatype.INTEGER);
        ValueColumnPredicate plusSide = value(col, 4);
        ValueColumnPredicate minusSide = value(col, 7);
        MinusStarPredicate minus = mock(MinusStarPredicate.class);
        when(minus.getConstrainedColumn()).thenReturn(col);
        when(minus.getPlus()).thenReturn(plusSide);
        // minus side is a plain value (no null) → widen NOT(minus) with OR col IS NULL.
        when(minus.getMinus()).thenReturn(minusSide);

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(minus)))
                .isEqualTo("(\"schul_jahr\".\"id\" = 4 and (not (\"schul_jahr\".\"id\" = 7)"
                        + " or \"schul_jahr\".\"id\" is null))");
    }

    @Test
    void minusNestedInAnd() {
        RolapStar.Column col = column("schul_jahr", "id", Datatype.INTEGER);
        ValueColumnPredicate plusSide = value(col, 4);
        ValueColumnPredicate minusSide = value(col, 7);
        ValueColumnPredicate sibling = value(col, 9);
        MinusStarPredicate minus = mock(MinusStarPredicate.class);
        when(minus.getConstrainedColumn()).thenReturn(col);
        when(minus.getPlus()).thenReturn(plusSide);
        when(minus.getMinus()).thenReturn(minusSide);

        org.eclipse.daanse.rolap.common.agg.AndPredicate and =
                mock(org.eclipse.daanse.rolap.common.agg.AndPredicate.class);
        when(and.getChildren()).thenReturn(
                List.<org.eclipse.daanse.rolap.common.star.StarPredicate>of(minus, sibling));

        assertThat(renderWhere(StarPredicateTranslator.toPredicate(and)))
                .isEqualTo("((\"schul_jahr\".\"id\" = 4 and (not (\"schul_jahr\".\"id\" = 7)"
                        + " or \"schul_jahr\".\"id\" is null)) and \"schul_jahr\".\"id\" = 9)");
    }
}

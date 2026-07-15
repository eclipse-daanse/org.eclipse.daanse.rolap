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
package org.eclipse.daanse.rolap.common.constraint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.sql.model.type.Datatype;
import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.SqlRender;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.junit.jupiter.api.Test;

/**
 * Verifies the aggStar-threaded member-set producers. With a non-null {@code aggStar} every column
 * node in {@code MemberConstraintWriter.generateSingleValueInPredicatePure} and
 * {@code MemberConstraintWriter.memberConstraintContribution} is the aggregate substitution
 * ({@code aggStar.lookupColumn(bitPos).toSqlExpression()}); a null aggStar matches the base form;
 * agg-unresolvable shapes bail (empty). Provenance for the {@code AggPlan} is resolved via
 * {@link MemberConstraintWriter#aggMemberTable}.
 * <p>
 * Needs the Mockito inline mock maker ({@code src/test/resources/mockito-extensions}):
 * {@link RolapCubeLevel} finalizes hierarchy accessors.
 */
class MemberConstraintAggThreadingTest {

    private static final String AGG = "agg_c_14_sales_fact_1997";

    private final RolapStar.Table customer = table("customer");
    private final RolapStar.Column cityCol = column(customer, "customer", "city", 3);
    private final RolapStar.Column stateCol = column(customer, "customer", "state_province", 4);
    private final RolapCubeLevel stateLevel = level(stateCol, true);
    private final RolapCubeLevel cityLevel = level(cityCol, false);

    private final AggStar aggStar = mock(AggStar.class);
    private final AggStar.Table aggTable = aggTable(AGG);

    private final RolapMember ca = member("CA", stateLevel, null);
    private final RolapMember or = member("OR", stateLevel, null);

    MemberConstraintAggThreadingTest() {
        aggColumn(aggStar, aggTable, 3, "city");
        aggColumn(aggStar, aggTable, 4, "state_province");
    }

    private static RolapStar.Table table(String alias) {
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getAlias()).thenReturn(alias);
        return t;
    }

    private static RolapStar.Column column(RolapStar.Table table, String tableAlias, String name, int bitPos) {
        RolapStar.Column col = mock(RolapStar.Column.class);
        when(col.getTable()).thenReturn(table);
        when(col.getExpression()).thenReturn(new RolapColumn(tableAlias, name));
        when(col.getDatatype()).thenReturn(Datatype.VARCHAR);
        when(col.getBitPosition()).thenReturn(bitPos);
        return col;
    }

    private static AggStar.Table aggTable(String name) {
        AggStar.Table t = mock(AggStar.Table.class);
        when(t.getName()).thenReturn(name);
        return t;
    }

    private static AggStar.Table.Column aggColumn(AggStar aggStar, AggStar.Table table, int bitPos, String name) {
        AggStar.Table.Column c = mock(AggStar.Table.Column.class);
        when(c.toSqlExpression()).thenReturn(Expressions.column(TableAlias.of(AGG), name));
        when(c.getTable()).thenReturn(table);
        when(aggStar.lookupColumn(bitPos)).thenReturn(c);
        return c;
    }

    private static RolapCubeLevel level(RolapStar.Column starKeyColumn, boolean unique) {
        RolapCubeLevel l = mock(RolapCubeLevel.class);
        when(l.isUnique()).thenReturn(unique);
        when(l.getBaseStarKeyColumn(null)).thenReturn(starKeyColumn);
        when(l.getDatatype()).thenReturn(Datatype.VARCHAR);
        return l;
    }

    private static RolapMember member(String key, RolapCubeLevel level, RolapMember parent) {
        RolapMember m = mock(RolapMember.class);
        when(m.getKey()).thenReturn(key);
        when(m.getLevel()).thenReturn(level);
        when(m.getParentMember()).thenReturn(parent);
        when(m.getUniqueName()).thenReturn("[Customers].[" + key + "]");
        return m;
    }

    private RolapMember city(String key, RolapMember state) {
        return member(key, cityLevel, state);
    }

    private static String render(Predicate p) {
        return SqlRender.renderPredicate(p, new AnsiDialect());
    }

    /** A multi-valued slicer column ({@code includeParentLevels=false}): one factored IN part
     *  built on the agg column node. */
    @Test
    void multiValuedSlicerShapeSubstitutesTheAggColumn() {
        Optional<List<Predicate>> parts = MemberConstraintWriter.generateSingleValueInPredicatePure(
                null, aggStar, List.of(ca, or), stateLevel, false, false, false);

        assertThat(parts).isPresent();
        assertThat(parts.get()).hasSize(1);
        assertThat(render(parts.get().get(0)))
                .isEqualTo("\"" + AGG + "\".\"state_province\" in ('CA', 'OR')");
    }

    /** A null aggStar through the threaded overload matches the base form. */
    @Test
    void nullAggStarIsByteIdenticalToTheBaseForm() {
        Optional<List<Predicate>> threaded = MemberConstraintWriter.generateSingleValueInPredicatePure(
                null, null, List.of(ca, or), stateLevel, false, false, false);
        Optional<List<Predicate>> base = MemberConstraintWriter.generateSingleValueInPredicatePure(
                null, List.of(ca, or), stateLevel, false, false, false);

        assertThat(threaded).isPresent();
        assertThat(base).isPresent();
        assertThat(render(threaded.get().get(0))).isEqualTo(render(base.get().get(0)))
                .isEqualTo("\"customer\".\"state_province\" in ('CA', 'OR')");
    }

    /** A column the agg star does not carry bails (empty) — the "agg-missing-column" class. */
    @Test
    void missingAggColumnBails() {
        AggStar sparse = mock(AggStar.class); // lookupColumn -> null for every bit
        assertThat(MemberConstraintWriter.generateSingleValueInPredicatePure(
                null, sparse, List.of(ca, or), stateLevel, false, false, false)).isEmpty();
    }

    /** An agg column without a dialect-free expression bails too (the toSqlExpression()==null leg). */
    @Test
    void aggColumnWithoutExpressionBails() {
        AggStar broken = mock(AggStar.class);
        AggStar.Table.Column noExpr = mock(AggStar.Table.Column.class);
        when(broken.lookupColumn(4)).thenReturn(noExpr); // toSqlExpression() stays null

        assertThat(MemberConstraintWriter.generateSingleValueInPredicatePure(
                null, broken, List.of(ca, or), stateLevel, false, false, false)).isEmpty();
    }

    /** A shared / non-cube level (no star key) cannot be agg-substituted — bail, where the base
     *  form falls back to the level key expression. */
    @Test
    void noStarKeyLevelBailsUnderAgg() {
        RolapCubeLevel shared = mock(RolapCubeLevel.class);
        when(shared.isUnique()).thenReturn(true);
        when(shared.getKeyExp()).thenReturn(new RolapColumn("shared_dim", "key_col"));
        when(shared.getDatatype()).thenReturn(Datatype.VARCHAR);
        RolapMember m = member("X", shared, null);

        assertThat(MemberConstraintWriter.generateSingleValueInPredicatePure(
                null, aggStar, List.of(m), shared, false, false, true)).isEmpty();
        // the base form still expresses it via the key expression
        assertThat(MemberConstraintWriter.generateSingleValueInPredicatePure(
                null, null, List.of(m), shared, false, false, true)).isPresent();
    }

    /** The SetConstraint arg channel: the member-set contribution under agg keeps the BASE star
     *  table (the base join channel) while the predicate is fully agg-substituted. */
    @Test
    void memberConstraintContributionSubstitutesPredicateAndKeepsBaseTable() {
        List<RolapMember> members = List.of(city("Altadena", ca), city("Arcadia", ca));

        Optional<ColumnPredicate> cp = MemberConstraintWriter.memberConstraintContribution(
                null, aggStar, members, false, false);

        assertThat(cp).isPresent();
        assertThat(cp.get().table()).isSameAs(customer);
        assertThat(render(cp.get().predicate())).isEqualTo(
                "(\"" + AGG + "\".\"city\" in ('Altadena', 'Arcadia')"
                        + " and \"" + AGG + "\".\"state_province\" = 'CA')");
    }

    /** A non-rectangle multi-level member set agg-substitutes too — the tuple IN is built over the
     *  agg column nodes. */
    @Test
    void tupleInMemberSetSubstitutesTheAggColumns() {
        List<RolapMember> members =
                List.of(city("Altadena", ca), city("Arcadia", ca), city("Salem", or));

        Optional<ColumnPredicate> cp = MemberConstraintWriter.memberConstraintContribution(
                null, aggStar, members, false, false);

        assertThat(cp).isPresent();
        assertThat(cp.get().predicate()).isInstanceOf(Predicate.And.class);
        Predicate.And and = (Predicate.And) cp.get().predicate();
        assertThat(and.operands()).hasSize(1);
        assertThat(and.operands().get(0)).isInstanceOf(Predicate.InTuple.class);
        assertThat(render(cp.get().predicate()))
                .contains("\"" + AGG + "\".\"city\"")
                .contains("\"" + AGG + "\".\"state_province\"")
                .doesNotContain("\"customer\".");
    }

    /** The 4-arg (no aggStar) overload is untouched — base column nodes, base table. */
    @Test
    void baseContributionOverloadIsUntouched() {
        List<RolapMember> members = List.of(city("Altadena", ca), city("Arcadia", ca));

        Optional<ColumnPredicate> cp = MemberConstraintWriter.memberConstraintContribution(
                null, members, false, false);

        assertThat(cp).isPresent();
        assertThat(render(cp.get().predicate())).isEqualTo(
                "(\"customer\".\"city\" in ('Altadena', 'Arcadia')"
                        + " and \"customer\".\"state_province\" = 'CA')");
    }

    /** Provenance for the AggPlan: the agg table carrying the member level's substituted key. */
    @Test
    void aggMemberTableResolvesTheProvenanceTable() {
        assertThat(MemberConstraintWriter.aggMemberTable(null, aggStar, cityLevel))
                .contains(aggTable);
        // unresolvable: no agg column for the bit
        assertThat(MemberConstraintWriter.aggMemberTable(null, mock(AggStar.class), cityLevel))
                .isEmpty();
        // unresolvable: level without a star key
        RolapCubeLevel shared = mock(RolapCubeLevel.class);
        assertThat(MemberConstraintWriter.aggMemberTable(null, aggStar, shared)).isEmpty();
    }
}

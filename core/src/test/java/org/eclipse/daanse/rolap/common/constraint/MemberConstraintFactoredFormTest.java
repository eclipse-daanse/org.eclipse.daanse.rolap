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
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Verifies the factored member-restriction producer
 * ({@link MemberConstraintWriter#memberConstraintContributionFactored}): a non-crossjoin member set
 * emits the factored per-level IN
 * {@code ("customer"."city" in (…) and "customer"."state_province" in ('CA', 'OR', 'WA'))} — leaf
 * level first in member order, parent level in first-encounter order, stopping at the first unique
 * level. The default contribution producer instead emits the exact tuple IN for a non-rectangle
 * set; a rectangle is factored by both.
 * <p>
 * Needs the Mockito inline mock maker ({@code src/test/resources/mockito-extensions}):
 * {@link RolapCubeLevel} finalizes hierarchy accessors.
 */
class MemberConstraintFactoredFormTest {

    private final RolapStar.Table customer = table("customer");
    private final RolapStar.Column cityCol = column(customer, "customer", "city");
    private final RolapStar.Column stateCol = column(customer, "customer", "state_province");
    private final RolapCubeLevel stateLevel = level(stateCol, true);
    private final RolapCubeLevel cityLevel = level(cityCol, false);

    private final RolapMember ca = member("CA", stateLevel, null);
    private final RolapMember or = member("OR", stateLevel, null);

    private static RolapStar.Table table(String alias) {
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getAlias()).thenReturn(alias);
        return t;
    }

    private static RolapStar.Column column(RolapStar.Table table, String tableAlias, String name) {
        RolapStar.Column col = mock(RolapStar.Column.class);
        when(col.getTable()).thenReturn(table);
        when(col.getExpression()).thenReturn(new RolapColumn(tableAlias, name));
        when(col.getDatatype()).thenReturn(Datatype.VARCHAR);
        return col;
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

    /**
     * Cities across several states — not a rectangle. The factored form emits the bounding box:
     * leaf IN in member order, parent IN in first-encounter order, one outer paren pair, none
     * around the per-level INs.
     */
    @Test
    void factoredFormOnNonRectangleMatchesRecorder() {
        List<RolapMember> members =
                List.of(city("Altadena", ca), city("Arcadia", ca), city("Salem", or));

        Optional<ColumnPredicate> cp = MemberConstraintWriter
                .memberConstraintContributionFactored(null, members, false, false);

        assertThat(cp).isPresent();
        assertThat(cp.get().table()).isSameAs(customer);
        assertThat(render(cp.get().predicate())).isEqualTo(
                "(\"customer\".\"city\" in ('Altadena', 'Arcadia', 'Salem')"
                        + " and \"customer\".\"state_province\" in ('CA', 'OR'))");
    }

    /**
     * Parent IN keeps first-encounter order (not sorted): interleaving the members must not reorder
     * the states.
     */
    @Test
    void factoredFormKeepsFirstEncounterParentOrder() {
        List<RolapMember> members =
                List.of(city("Salem", or), city("Altadena", ca), city("Arcadia", ca));

        Optional<ColumnPredicate> cp = MemberConstraintWriter
                .memberConstraintContributionFactored(null, members, false, false);

        assertThat(cp).isPresent();
        assertThat(render(cp.get().predicate())).isEqualTo(
                "(\"customer\".\"city\" in ('Salem', 'Altadena', 'Arcadia')"
                        + " and \"customer\".\"state_province\" in ('OR', 'CA'))");
    }

    /**
     * The default producer keeps a non-rectangle multi-level set on the exact tuple IN —
     * single-operand And wrap around an InTuple.
     */
    @Test
    void defaultProducerKeepsTupleFormOnNonRectangle() {
        List<RolapMember> members =
                List.of(city("Altadena", ca), city("Arcadia", ca), city("Salem", or));

        Optional<ColumnPredicate> cp = MemberConstraintWriter
                .memberConstraintContribution(null, members, false, false);

        assertThat(cp).isPresent();
        assertThat(cp.get().predicate()).isInstanceOf(Predicate.And.class);
        Predicate.And and = (Predicate.And) cp.get().predicate();
        assertThat(and.operands()).hasSize(1);
        assertThat(and.operands().get(0)).isInstanceOf(Predicate.InTuple.class);
    }

    /** A rectangle (all cities in ONE state): both producers emit the identical factored form. */
    @Test
    void rectangleIsFactoredInBothProducers() {
        List<RolapMember> members = List.of(city("Altadena", ca), city("Arcadia", ca));

        Optional<ColumnPredicate> factored = MemberConstraintWriter
                .memberConstraintContributionFactored(null, members, false, false);
        Optional<ColumnPredicate> standard = MemberConstraintWriter
                .memberConstraintContribution(null, members, false, false);

        assertThat(factored).isPresent();
        assertThat(standard).isPresent();
        String expected = "(\"customer\".\"city\" in ('Altadena', 'Arcadia')"
                + " and \"customer\".\"state_province\" = 'CA')";
        assertThat(render(factored.get().predicate())).isEqualTo(expected);
        assertThat(render(standard.get().predicate())).isEqualTo(expected);
    }
}

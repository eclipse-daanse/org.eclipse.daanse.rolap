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
 * Verifies the factored-exclude producer
 * ({@link MemberConstraintWriter#memberConstraintContributionFactoredExclude}) for the
 * {@code MemberExcludeConstraint} reads:
 * <ul>
 * <li>the multi-level exclude (Store City under CA):
 *     {@code ((not (city in ('Los Angeles', 'San Francisco')) or (city is null)) or
 *     (not (state = 'CA') or (state is null)))} — one per-level NOT/null-reinclude pair per level
 *     from leaf to first-unique parent, OR-joined; the {@code Or} wrap supplies the outer parens;</li>
 * <li>the single-level exclude (Time Years):
 *     {@code ((not (year in (2003, 2004, 2005)) or (year is null)))} — the sole pair inside the
 *     single-operand OR wrap.</li>
 * </ul>
 * Needs the Mockito inline mock maker ({@code src/test/resources/mockito-extensions}):
 * {@link RolapCubeLevel} finalizes hierarchy accessors.
 */
class MemberConstraintFactoredExcludeFormTest {

    private final RolapStar.Table customer = table("customer");
    private final RolapStar.Column cityCol = column(customer, "customer", "city", Datatype.VARCHAR);
    private final RolapStar.Column stateCol =
            column(customer, "customer", "state_province", Datatype.VARCHAR);
    private final RolapCubeLevel stateLevel = level(stateCol, true, Datatype.VARCHAR);
    private final RolapCubeLevel cityLevel = level(cityCol, false, Datatype.VARCHAR);
    private final RolapMember ca = member("CA", stateLevel, null);

    private static RolapStar.Table table(String alias) {
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getAlias()).thenReturn(alias);
        return t;
    }

    private static RolapStar.Column column(RolapStar.Table table, String tableAlias, String name,
            Datatype datatype) {
        RolapStar.Column col = mock(RolapStar.Column.class);
        when(col.getTable()).thenReturn(table);
        when(col.getExpression()).thenReturn(new RolapColumn(tableAlias, name));
        when(col.getDatatype()).thenReturn(datatype);
        return col;
    }

    private static RolapCubeLevel level(RolapStar.Column starKeyColumn, boolean unique,
            Datatype datatype) {
        RolapCubeLevel l = mock(RolapCubeLevel.class);
        when(l.isUnique()).thenReturn(unique);
        when(l.getBaseStarKeyColumn(null)).thenReturn(starKeyColumn);
        when(l.getDatatype()).thenReturn(datatype);
        return l;
    }

    private static RolapMember member(Object key, RolapCubeLevel level, RolapMember parent) {
        RolapMember m = mock(RolapMember.class);
        when(m.getKey()).thenReturn(key);
        when(m.getLevel()).thenReturn(level);
        when(m.getParentMember()).thenReturn(parent);
        when(m.getUniqueName()).thenReturn("[" + key + "]");
        return m;
    }

    private static String render(Predicate p) {
        return SqlRender.renderPredicate(p, new AnsiDialect());
    }

    /** Multi-level exclude: per-level NOT/null pairs, OR-joined, leaf level first. */
    @Test
    void multiLevelExcludeMatchesRecorder() {
        List<RolapMember> excludes = List.of(
                member("Los Angeles", cityLevel, ca),
                member("San Francisco", cityLevel, ca));

        Optional<ColumnPredicate> cp = MemberConstraintWriter
                .memberConstraintContributionFactoredExclude(null, excludes, true);

        assertThat(cp).isPresent();
        assertThat(cp.get().table()).isSameAs(customer);
        assertThat(render(cp.get().predicate())).isEqualTo(
                "((not (\"customer\".\"city\" in ('Los Angeles', 'San Francisco'))"
                        + " or (\"customer\".\"city\" is null))"
                        + " or (not (\"customer\".\"state_province\" = 'CA')"
                        + " or (\"customer\".\"state_province\" is null)))");
    }

    /** Single-level exclude (numeric keys): the sole pair, double-wrapped. */
    @Test
    void singleLevelExcludeMatchesRecorder() {
        RolapStar.Table time = table("time");
        RolapStar.Column yearCol = column(time, "time", "YEAR_ID", Datatype.INTEGER);
        RolapCubeLevel yearLevel = level(yearCol, true, Datatype.INTEGER);
        List<RolapMember> excludes = List.of(
                member(2003, yearLevel, null),
                member(2004, yearLevel, null),
                member(2005, yearLevel, null));

        Optional<ColumnPredicate> cp = MemberConstraintWriter
                .memberConstraintContributionFactoredExclude(null, excludes, true);

        assertThat(cp).isPresent();
        assertThat(render(cp.get().predicate())).isEqualTo(
                "((not (\"time\".\"YEAR_ID\" in (2003, 2004, 2005))"
                        + " or (\"time\".\"YEAR_ID\" is null)))");
    }
}

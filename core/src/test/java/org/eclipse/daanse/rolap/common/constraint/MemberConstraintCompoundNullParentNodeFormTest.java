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

import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.jdbc.db.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.SqlRender;
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.junit.jupiter.api.Test;

/**
 * Verifies the compound-null-parent member constraint
 * ({@link MemberConstraintWriter#memberConstraintContributionCompoundNullParent}): a multi-level
 * member set whose ancestor key is the SQL null value (the Warehouse2 shape) renders as the tuple
 * IN over the non-null members OR-joined with one parenthesised group per null parent (each group
 * being the per-level lineage {@code IS NULL} piece AND the children tuple IN). Shapes with no null
 * ancestor bail.
 *
 * <p>Needs the Mockito inline mock maker ({@code src/test/resources/mockito-extensions}):
 * {@link RolapCubeLevel} finalizes hierarchy accessors.
 */
class MemberConstraintCompoundNullParentNodeFormTest {

    private final RolapStar.Table warehouse = table("warehouse");
    private final RolapStar.Column address2Col = column(warehouse, "warehouse", "wa_address2");
    private final RolapStar.Column address1Col = column(warehouse, "warehouse", "wa_address1");
    private final RolapStar.Column nameCol = column(warehouse, "warehouse", "warehouse_name");
    private final RolapCubeLevel address2Level = level(address2Col);
    private final RolapCubeLevel address1Level = level(address1Col);
    private final RolapCubeLevel nameLevel = level(nameCol);

    /** The shared null-keyed address2 parent of the corpus shape. */
    private final RolapMember a2Null = member(Util.sqlNullValue, address2Level, null);

    MemberConstraintCompoundNullParentNodeFormTest() {
        when(address2Level.getChildLevel()).thenReturn(address1Level);
        when(address1Level.getChildLevel()).thenReturn(nameLevel);
    }

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

    private static RolapCubeLevel level(RolapStar.Column starKeyColumn) {
        RolapCubeLevel l = mock(RolapCubeLevel.class);
        when(l.isUnique()).thenReturn(false);
        when(l.getBaseStarKeyColumn(null)).thenReturn(starKeyColumn);
        when(l.getDatatype()).thenReturn(Datatype.VARCHAR);
        return l;
    }

    private static RolapMember member(Object key, RolapCubeLevel level, RolapMember parent) {
        RolapMember m = mock(RolapMember.class);
        when(m.getKey()).thenReturn(key);
        when(m.getLevel()).thenReturn(level);
        when(m.getParentMember()).thenReturn(parent);
        when(m.getUniqueName()).thenReturn("[Warehouse2].[" + key + "]");
        return m;
    }

    private RolapMember warehouseName(String name, String address1, RolapMember address2) {
        RolapMember a1 = member(address1, address1Level, address2);
        return member(name, nameLevel, a1);
    }

    /** Renders under a dialect that supports multi-value IN; dialects without it degrade the same
     *  InTuple node to an OR-of-ANDs at render. */
    private static String render(org.eclipse.daanse.sql.statement.api.expression.Predicate p) {
        return SqlRender.renderPredicate(p, new AnsiDialect() {
            @Override
            public boolean supportsMultiValueInExpr() {
                return true;
            }
        });
    }

    /** Upcase and strip whitespace and parentheses, for spelling-insensitive comparison. */
    private static String normalized(String sql) {
        return sql.toUpperCase().replaceAll("[\\s()]", "");
    }

    /**
     * A mixed set: one member with a fully non-null lineage plus two members under the same
     * null-keyed address2 parent — the tuple IN over the non-null rows OR-joined with the
     * null-parent group (per-level lineage piece AND the children tuple IN).
     */
    @Test
    void mixedNullNonNullParentsRenderTheRecorderCompoundModuloIsNullSpelling() {
        RolapMember a2b = member("Suite 23", address2Level, null);
        RolapMember nonNull = warehouseName("Jorge Garcia", "7647 Bursary St", a2b);
        RolapMember underNull1 = warehouseName("Arnold and Sons", "5617 Saclan Terrace", a2Null);
        RolapMember underNull2 = warehouseName("Bachmann", "3377 Coachman Place", a2Null);

        Optional<ColumnPredicate> cp = MemberConstraintWriter
                .memberConstraintContributionCompoundNullParent(
                        null, List.of(nonNull, underNull1, underNull2), false);

        assertThat(cp).isPresent();
        assertThat(cp.get().table()).isSameAs(warehouse);
        String nodeSql = render(cp.get().predicate());
        assertThat(nodeSql).isEqualTo(
                "((\"warehouse\".\"warehouse_name\", \"warehouse\".\"wa_address1\","
                        + " \"warehouse\".\"wa_address2\") in"
                        + " (('Jorge Garcia', '7647 Bursary St', 'Suite 23'))"
                        + " or (\"warehouse\".\"wa_address2\" is null"
                        + " and (\"warehouse\".\"warehouse_name\", \"warehouse\".\"wa_address1\") in"
                        + " (('Arnold and Sons', '5617 Saclan Terrace'),"
                        + " ('Bachmann', '3377 Coachman Place'))))");

        // The equivalent "( col IS NULL )" spelling differs only in that fragment: replacing it
        // with the renderer's "is null" spelling yields exactly the node SQL, nothing else.
        String recorderSql =
                "((\"warehouse\".\"warehouse_name\", \"warehouse\".\"wa_address1\","
                        + " \"warehouse\".\"wa_address2\") in"
                        + " (('Jorge Garcia', '7647 Bursary St', 'Suite 23'))"
                        + " or (( \"warehouse\".\"wa_address2\" IS NULL )"
                        + " and (\"warehouse\".\"warehouse_name\", \"warehouse\".\"wa_address1\") in"
                        + " (('Arnold and Sons', '5617 Saclan Terrace'),"
                        + " ('Bachmann', '3377 Coachman Place'))))";
        assertThat(recorderSql.replace(
                "( \"warehouse\".\"wa_address2\" IS NULL )",
                "\"warehouse\".\"wa_address2\" is null"))
                .isEqualTo(nodeSql);
        assertThat(normalized(recorderSql)).isEqualTo(normalized(nodeSql));
    }

    /**
     * An all-null-parent set: no non-null row survives, so the sole null-parent group carries the
     * outer wrap as a single-operand And.
     */
    @Test
    void soleNullParentGroupCarriesTheOuterWrap() {
        RolapMember underNull1 = warehouseName("Arnold and Sons", "5617 Saclan Terrace", a2Null);
        RolapMember underNull2 = warehouseName("Bachmann", "3377 Coachman Place", a2Null);

        Optional<ColumnPredicate> cp = MemberConstraintWriter
                .memberConstraintContributionCompoundNullParent(
                        null, List.of(underNull1, underNull2), false);

        assertThat(cp).isPresent();
        assertThat(render(cp.get().predicate())).isEqualTo(
                "((\"warehouse\".\"wa_address2\" is null"
                        + " and (\"warehouse\".\"warehouse_name\", \"warehouse\".\"wa_address1\") in"
                        + " (('Arnold and Sons', '5617 Saclan Terrace'),"
                        + " ('Bachmann', '3377 Coachman Place'))))");
    }

    /** No null ancestor anywhere: the compound producer bails (the plain tuple-IN form owns the
     *  shape). */
    @Test
    void noNullParentGroupBails() {
        RolapMember a2b = member("Suite 23", address2Level, null);
        RolapMember a2c = member("Suite 5", address2Level, null);
        RolapMember m1 = warehouseName("Jorge Garcia", "7647 Bursary St", a2b);
        RolapMember m2 = warehouseName("Frank", "1 Main St", a2c);

        assertThat(MemberConstraintWriter.memberConstraintContributionCompoundNullParent(
                null, List.of(m1, m2), false)).isEmpty();
    }

    /** Unique-member-level and single-member cross-product shapes are the factored per-level INs,
     *  not the compound — bail. */
    @Test
    void nonCompoundShapesBail() {
        RolapCubeLevel uniqueLevel = level(nameCol);
        when(uniqueLevel.isUnique()).thenReturn(true);
        RolapMember unique = member("Arnold and Sons", uniqueLevel, null);
        assertThat(MemberConstraintWriter.memberConstraintContributionCompoundNullParent(
                null, List.of(unique), false)).isEmpty();

        // A single multi-level member is always a cross product of its per-level keys.
        RolapMember single = warehouseName("Arnold and Sons", "5617 Saclan Terrace", a2Null);
        assertThat(MemberConstraintWriter.memberConstraintContributionCompoundNullParent(
                null, List.of(single), false)).isEmpty();

        assertThat(MemberConstraintWriter.memberConstraintContributionCompoundNullParent(
                null, List.of(), false)).isEmpty();
    }
}

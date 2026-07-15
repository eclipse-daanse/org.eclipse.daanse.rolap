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

import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.RolapStar.Condition.JoinColumn;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapCubeHierarchy;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link TupleSqlMapper#t4SubToken} classifies a tuple/arm shape that is outside the
 * builder's scope into the right token — one test per token ({@code t4-arm-computed},
 * {@code t4-computed-residue}, {@code t4-no-star-key-survivor}, {@code t4-no-fact-adjacent:<condition>},
 * {@code t4-projection-scope}) plus the defensive {@code t4-none} for a supported shape.
 * <p>
 * Needs the Mockito inline mock maker ({@code src/test/resources/mockito-extensions}):
 * {@link RolapCubeLevel} finalizes {@code getHierarchy()}.
 */
class TupleSqlMapperT4CensusTest {

    private final RolapStar star = mock(RolapStar.class);
    private final RolapStar.Table fact = table("sales_fact_1997", "sales_fact_1997", null, null);
    private final RolapStar.Table customer = table("customer", "customer", fact,
            join("sales_fact_1997", "customer_id", "customer", "customer_id"));
    private final RolapStar.Table store = table("store", "store", fact,
            join("sales_fact_1997", "store_id", "store", "store_id"));

    TupleSqlMapperT4CensusTest() {
        when(star.getFactTable()).thenReturn(fact);
    }

    private RolapStar.Table table(String name, String alias, RolapStar.Table parent,
            RolapStar.Condition joinCondition) {
        RolapStar.Table t = mock(RolapStar.Table.class);
        when(t.getTableName()).thenReturn(name);
        when(t.getAlias()).thenReturn(alias);
        when(t.getParentTable()).thenReturn(parent);
        when(t.getJoinCondition()).thenReturn(joinCondition);
        when(t.getStar()).thenReturn(star);
        return t;
    }

    private static RolapStar.Condition join(String leftTable, String leftCol, String rightTable,
            String rightCol) {
        RolapStar.Condition c = mock(RolapStar.Condition.class);
        when(c.leftColumn()).thenReturn(Optional.of(new JoinColumn(leftTable, leftCol)));
        when(c.rightColumn()).thenReturn(Optional.of(new JoinColumn(rightTable, rightCol)));
        return c;
    }

    private static TableSource tableSource(String name) {
        TableSource source = mock(TableSource.class);
        when(source.getAlias()).thenReturn(name);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet.class);
        when(ncs.getName()).thenReturn(name);
        when(source.getTable()).thenReturn(ncs);
        return source;
    }

    /** A plain-column cube level on a single-table relation, star key on {@code starTable}. */
    private RolapCubeLevel level(String table, String column, RolapStar.Table starTable) {
        RolapCubeLevel l = mock(RolapCubeLevel.class);
        RolapCubeHierarchy h = mock(RolapCubeHierarchy.class);
        TableSource relation = tableSource(table); // built OUTSIDE the stubbing (no nested when())
        when(h.getRelation()).thenReturn(relation);
        org.mockito.Mockito.doReturn(List.of(l)).when(h).getLevels();
        org.mockito.Mockito.doReturn(h).when(l).getHierarchy();
        when(l.getDepth()).thenReturn(0);
        when(l.getUniqueName()).thenReturn("[" + table + "].[" + column + "]");
        when(l.getKeyExp()).thenReturn(new RolapColumn(table, column));
        when(l.getInternalType()).thenReturn(BestFitColumnType.STRING);
        when(l.getOrdinalExps()).thenReturn(List.of());
        when(l.getProperties()).thenReturn(new RolapProperty[0]);
        if (starTable != null) {
            RolapStar.Column starColumn = mock(RolapStar.Column.class);
            when(starColumn.getTable()).thenReturn(starTable);
            when(l.getBaseStarKeyColumn(null)).thenReturn(starColumn);
        }
        return l;
    }

    /** A computed ({@code <SQL>}) expression carrying ONE generic variant. */
    private static SqlExpression computed(String genericSql) {
        SqlExpression e = mock(SqlExpression.class);
        org.eclipse.daanse.olap.api.SqlStatement stmt =
                mock(org.eclipse.daanse.olap.api.SqlStatement.class);
        when(stmt.getDialects()).thenReturn(List.of("generic"));
        when(stmt.getSql()).thenReturn(genericSql);
        org.mockito.Mockito.doReturn(List.of(stmt)).when(e).getSqls();
        return e;
    }

    private String token(List<RolapLevel> targets, boolean unionArm) {
        return TupleSqlMapper.t4SubToken(targets, null, unionArm, List.of(), List.of());
    }

    /** A NOT_LAST union arm with a computed-expression level. */
    @Test
    void armWithComputedLevelIsArmComputed() {
        RolapCubeLevel name = level("customer", "customer_id", customer);
        SqlExpression fullName = computed("CONCAT(\"customer\".\"fname\", ' ', \"customer\".\"lname\")");
        org.mockito.Mockito.doReturn(List.of(fullName)).when(name).getOrdinalExps();

        assertThat(token(List.of(name), true)).isEqualTo("t4-arm-computed");
        // The same shape on the ONLY route classifies as computed-residue.
        assertThat(token(List.of(name), false)).isEqualTo("t4-computed-residue");
    }

    /** A first target whose star key does not resolve on the base cube. */
    @Test
    void firstTargetWithoutStarKeyIsNoStarKeySurvivor() {
        RolapCubeLevel noKey = level("customer", "customer_id", null);

        assertThat(token(List.of(noKey), false)).isEqualTo("t4-no-star-key-survivor:first");
    }

    /** A further target whose star key does not resolve on the base cube. */
    @Test
    void furtherTargetWithoutStarKeyIsNoStarKeySurvivorFurther() {
        RolapCubeLevel first = level("customer", "customer_id", customer);
        RolapCubeLevel further = level("store", "store_type", null);

        assertThat(token(List.of(first, further), false))
                .isEqualTo("t4-no-star-key-survivor:further");
    }

    /**
     * A first target whose chain never reaches the fact (degenerate dimension keyed on the fact
     * itself) is attributed with the failing same-table condition: a single target is served
     * elsewhere, so the same-table gate declines on target count.
     */
    @Test
    void degenerateFirstTargetIsNoFactAdjacentWithCondition() {
        // Star key directly on the fact: parent chain is null → no fact-adjacent table.
        RolapCubeLevel degenerate = level("sales_fact_1997", "promotion_id", fact);

        assertThat(token(List.of(degenerate), false))
                .startsWith("t4-no-fact-adjacent:single-target");
    }

    /** A further target projecting a column outside its chain / the first relation / the fact. */
    @Test
    void furtherTargetProjectionOutsideScopeIsProjectionScope() {
        RolapCubeLevel first = level("customer", "customer_id", customer);
        RolapCubeLevel further = level("store", "store_type", store);
        RolapProperty outside = property(new RolapColumn("warehouse", "warehouse_name"));
        when(further.getProperties()).thenReturn(new RolapProperty[] { outside });

        assertThat(token(List.of(first, further), false)).isEqualTo("t4-projection-scope");
    }

    /** Defensive: a shape the strict gate accepts is no T4 decline at all. */
    @Test
    void supportedShapeIsNone() {
        RolapCubeLevel first = level("customer", "customer_id", customer);
        RolapCubeLevel further = level("store", "store_type", store);

        assertThat(token(List.of(first, further), false)).isEqualTo("t4-none");
    }

    private static RolapProperty property(SqlExpression exp) {
        RolapProperty p = mock(RolapProperty.class);
        when(p.getName()).thenReturn("p");
        when(p.getExp()).thenReturn(exp);
        return p;
    }
}

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
package org.eclipse.daanse.rolap.common.nativize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.jdbc.db.api.type.Datatype;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.RolapAggregationManager;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.sql.ConstraintContribution;
import org.eclipse.daanse.rolap.common.sql.CrossJoinArg;
import org.eclipse.daanse.rolap.common.sql.MemberListCrossJoinArg;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapStoredMeasure;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Verifies the compound-null-parent retry in {@code RolapNativeSet.SetConstraint.toContribution}'s
 * arg loop: an arg whose member set {@code memberConstraintContribution} cannot carry because of a
 * null ancestor key (the Warehouse2 shape) is retried through the compound node form
 * ({@code memberConstraintContributionCompoundNullParent}) and carried as that arg's
 * {@code ColumnPredicate}. An all-member arg adds nothing (present, unrestricted).
 *
 * <p>Harness: an inline-mocked {@code NonEmptyCrossJoinConstraint} with reflectively injected
 * {@code evaluator}/{@code strict} state and a real {@code args} field; the base context leg's
 * static seams are stubbed to the empty (present, unrestricted) contribution while the arg channel
 * runs the real {@code MemberConstraintWriter} against the warehouse fixture.
 */
class SetConstraintCompoundNullParentRetryTest {

    private final RolapStar.Table warehouse = table("warehouse");
    private final RolapStar.Column address2Col = column(warehouse, "warehouse", "wa_address2");
    private final RolapStar.Column address1Col = column(warehouse, "warehouse", "wa_address1");
    private final RolapStar.Column nameCol = column(warehouse, "warehouse", "warehouse_name");
    private final RolapCubeLevel address2Level = level(address2Col);
    private final RolapCubeLevel address1Level = level(address1Col);
    private final RolapCubeLevel nameLevel = level(nameCol);
    private final RolapMember a2Null = member(Util.sqlNullValue, address2Level, null);

    SetConstraintCompoundNullParentRetryTest() {
        when(address2Level.getChildLevel()).thenReturn(address1Level);
        when(address1Level.getChildLevel()).thenReturn(nameLevel);
    }

    @Test
    void nullAncestorArgIsRetriedThroughTheCompoundNodeForm() throws Exception {
        RolapNativeSet.SetConstraint sc = setConstraintWith(
            memberListArg(List.of(
                warehouseName("Arnold and Sons", "5617 Saclan Terrace", a2Null),
                warehouseName("Bachmann", "3377 Coachman Place", a2Null))));

        try (MockedStatic<RolapAggregationManager> agg = mockStatic(RolapAggregationManager.class)) {
            stubEmptyContextSeams(agg);

            Optional<ConstraintContribution> candidate = sc.toContribution(null, null);

            assertThat(candidate)
                .as("the null-ancestor arg takes the compound node form")
                .isPresent();
            assertThat(candidate.get().orderedPredicates()).hasSize(1);
            assertThat(candidate.get().orderedPredicates().get(0).table()).isSameAs(warehouse);
            assertThat(candidate.get().joinTables()).containsExactly(warehouse);
            assertThat(candidate.get().where()).isPresent();
        }
    }

    @Test
    void allMemberArgAddsNothingAndStaysUnrestricted() throws Exception {
        // An all-member arg: the arg loop skips it. toContribution returns the present,
        // unrestricted base contribution.
        RolapMember all = member("All Warehouses", nameLevel, null);
        when(all.isAll()).thenReturn(true);
        RolapNativeSet.SetConstraint sc = setConstraintWith(memberListArg(List.of(all)));

        try (MockedStatic<RolapAggregationManager> agg = mockStatic(RolapAggregationManager.class)) {
            stubEmptyContextSeams(agg);

            Optional<ConstraintContribution> c = sc.toContribution(null, null);
            assertThat(c).isPresent();
            assertThat(c.get().orderedPredicates()).isEmpty();
            assertThat(c.get().where()).isEmpty();
        }
    }

    // ---------------------------------------------------------------------------------------
    // fixtures
    // ---------------------------------------------------------------------------------------

    private RolapNativeSet.SetConstraint setConstraintWith(CrossJoinArg arg) throws Exception {
        RolapEvaluator evaluator = mock(RolapEvaluator.class);
        when(evaluator.push()).thenReturn(evaluator);
        when(evaluator.getMembers()).thenReturn(new Member[] {mock(RolapStoredMeasure.class)});
        when(evaluator.getNonAllMembers()).thenReturn(new Member[0]);
        when(evaluator.getCube()).thenReturn(mock(RolapCube.class));
        when(evaluator.getCatalogReader())
            .thenReturn(mock(org.eclipse.daanse.olap.api.catalog.CatalogReader.class));

        RolapNativeCrossJoin.NonEmptyCrossJoinConstraint sc =
            mock(RolapNativeCrossJoin.NonEmptyCrossJoinConstraint.class);
        when(sc.toContribution(any(), any())).thenCallRealMethod();
        when(sc.toContribution(any(), any(),
            any(SqlContextConstraint.CalcLift.class))).thenCallRealMethod();
        when(sc.canApplyCrossJoinArgConstraint(any())).thenCallRealMethod();
        when(sc.executedCalcLift()).thenCallRealMethod();
        when(sc.getEvaluator()).thenReturn(evaluator);
        inject(sc, "evaluator", evaluator);
        inject(sc, "strict", false);
        sc.args = new CrossJoinArg[] {arg};
        return sc;
    }

    private static MemberListCrossJoinArg memberListArg(List<RolapMember> members) {
        MemberListCrossJoinArg arg = mock(MemberListCrossJoinArg.class);
        when(arg.getMembers()).thenReturn(members);
        when(arg.isRestrictMemberTypes()).thenReturn(false);
        when(arg.isExclude()).thenReturn(false);
        when(arg.hasCalcMembers()).thenReturn(false);
        when(arg.getLevel()).thenReturn(null);
        return arg;
    }

    /** The base context leg reduced to the EMPTY (present, unrestricted) contribution: the REAL
     *  writer expansion over the measure-only mock context (empty set, no roles) plus a
     *  zero-column cell request behind the one public seam. */
    private static void stubEmptyContextSeams(MockedStatic<RolapAggregationManager> agg) {
        CellRequest request = mock(CellRequest.class);
        when(request.getConstrainedColumns()).thenReturn(new RolapStar.Column[0]);
        when(request.getSingleValues()).thenReturn(new Object[0]);
        agg.when(() -> RolapAggregationManager.makeRequest(any(Member[].class)))
            .thenReturn(request);
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

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = SqlContextConstraint.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}

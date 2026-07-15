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
package org.eclipse.daanse.rolap.common.sqlbuild.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.sql.dialect.db.common.AnsiDialect;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.LevelType;
import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.element.OlapMetaDataBase;
import org.eclipse.daanse.rolap.api.element.RolapMember;
import org.eclipse.daanse.rolap.common.constraint.DefaultMemberChildrenConstraint;
import org.eclipse.daanse.rolap.common.sql.ContributionResult;
import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.element.RolapColumn;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.sql.statement.render.DialectSqlRenderer;
import org.junit.jupiter.api.Test;

/**
 * Pins the member-children routing decisions: a mapper-supported level with a translating,
 * dimension-only constraint routes to the authoritative build (whose rendered SQL is pinned); a
 * constraint that does not translate routes to the defensive {@link
 * MemberChildrenRouter.Route.Throw} carrying the reason.
 */
class MemberChildrenRouterTest {

    /** (all) &gt; Country &gt; State Province (unique) &gt; City on the single table customer. */
    private static List<RolapLevel> customerLevels() {
        org.eclipse.daanse.rolap.mapping.model.database.source.TableSource customerSource =
            mock(org.eclipse.daanse.rolap.mapping.model.database.source.TableSource.class);
        when(customerSource.getAlias()).thenReturn("customer");
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs =
            mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet.class);
        when(ncs.getName()).thenReturn("customer");
        when(customerSource.getTable()).thenReturn(ncs);

        RolapHierarchy hierarchy = mock(RolapHierarchy.class);
        when(hierarchy.getRelation()).thenReturn(customerSource);
        when(hierarchy.getUniqueName()).thenReturn("[Customer]");
        when(hierarchy.getDimension()).thenReturn(mock(Dimension.class));
        when(hierarchy.tableExists("customer")).thenReturn(true);
        List<RolapLevel> levels = new ArrayList<>();
        doReturn(levels).when(hierarchy).getLevels();
        levels.add(level(hierarchy, "(All)", 0, null,
            RolapLevel.FLAG_ALL | RolapLevel.FLAG_UNIQUE));
        levels.add(level(hierarchy, "Country", 1, new RolapColumn("customer", "country"),
            RolapLevel.FLAG_UNIQUE));
        levels.add(level(hierarchy, "State Province", 2,
            new RolapColumn("customer", "state_province"), RolapLevel.FLAG_UNIQUE));
        levels.add(level(hierarchy, "City", 3, new RolapColumn("customer", "city"), 0));
        return levels;
    }

    private static RolapLevel level(RolapHierarchy hierarchy, String name, int depth,
            SqlExpression keyExp, int flags) {
        return new RolapLevel(hierarchy, name, null, true, null, depth, keyExp, null, null,
            null, null, null, null, RolapProperty.emptyArray, flags,
            org.eclipse.daanse.sql.model.type.Datatype.VARCHAR, null,
            RolapLevel.HideMemberCondition.Never, LevelType.REGULAR, "",
            OlapMetaDataBase.empty());
    }

    /** A [State Province] parent whose children ([City]) are read. */
    private static RolapMember stateParent(List<RolapLevel> levels) {
        RolapMember parent = mock(RolapMember.class);
        doReturn(levels.get(2)).when(parent).getLevel();
        when(parent.getKey()).thenReturn("CA");
        when(parent.isAll()).thenReturn(false);
        when(parent.isNull()).thenReturn(false);
        when(parent.getParentMember()).thenReturn(null);
        return parent;
    }

    @Test
    void dimensionOnlyConstraintRoutesToAuthoritativeBuild() {
        List<RolapLevel> levels = customerLevels();
        RolapHierarchy hierarchy = levels.get(0).getHierarchy();
        MemberChildrenRouter.Route route = MemberChildrenRouter.route(
            hierarchy, stateParent(levels), null, DefaultMemberChildrenConstraint.instance());
        assertThat(route).isInstanceOf(MemberChildrenRouter.Route.Builder.class);
        String sql = new DialectSqlRenderer(new AnsiDialect())
            .render(((MemberChildrenRouter.Route.Builder) route).statement().get()).sql();
        assertThat(sql)
            .contains("select \"customer\".\"city\" as \"c0\"")
            .contains("from \"customer\" as \"customer\"")
            .contains("\"customer\".\"state_province\" =");
    }

    @Test
    void nonTranslatingConstraintRoutesToThrowWithReason() {
        List<RolapLevel> levels = customerLevels();
        RolapHierarchy hierarchy = levels.get(0).getHierarchy();
        MemberChildrenConstraint opaque = new MemberChildrenConstraint() {
            @Override
            public Object getCacheKey() {
                return this;
            }
        };
        MemberChildrenRouter.Route route =
            MemberChildrenRouter.route(hierarchy, stateParent(levels), null, opaque);
        assertThat(route).isInstanceOf(MemberChildrenRouter.Route.Throw.class);
        assertThat(((MemberChildrenRouter.Route.Throw) route).reason())
            .contains("member children read not modellable");
    }

    @Test
    void defaultToContributionCarriesItsReason() {
        MemberChildrenConstraint opaque = new MemberChildrenConstraint() {
            @Override
            public Object getCacheKey() {
                return this;
            }
        };
        ContributionResult r = opaque.toContribution(null, null, null);
        assertThat(r.isSupported()).isFalse();
        assertThat(r.reason()).contains("not expressible as a contribution");
    }
}

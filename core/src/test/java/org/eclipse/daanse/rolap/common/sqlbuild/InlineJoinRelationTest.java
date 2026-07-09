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
import java.util.Set;

import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.mapping.model.database.relational.RelationalFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.junit.jupiter.api.Test;

/**
 * Verifies the inline-join member-children shape — a level whose hierarchy relation is a JOIN
 * containing an INLINE-table leaf ({@code store JOIN inline-nation}):
 * <ol>
 * <li>{@link RelationFromMapper#tableAliases} collects the inline leaf's alias, so
 *     {@code memberFromTables} keeps the inline side of the subset;</li>
 * <li>{@link RelationFromMapper#fromReferenced} composes the subset as a {@code FromJoin} with a
 *     {@code FromInline} operand;</li>
 * <li>{@link MemberSqlMapper#supportsInlineJoinRelation} accepts exactly this shape.</li>
 * </ol>
 */
class InlineJoinRelationTest {

    /** A real (EMF) inline-table source — the {@code nation} leaf of the snowflake. */
    private static InlineTableSource inlineSource(String alias) {
        org.eclipse.daanse.rolap.mapping.model.database.relational.InlineTable table =
                RelationalFactory.eINSTANCE.createInlineTable();
        table.setName(alias);
        InlineTableSource source = SourceFactory.eINSTANCE.createInlineTableSource();
        source.setAlias(alias);
        source.setTable(table);
        return source;
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

    /** {@code left join right on left.leftKey = right.rightKey} over arbitrary leaf sources. */
    private static JoinSource joinSource(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource leftSource,
            String leftKeyName,
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource rightSource,
            String rightKeyName) {
        JoinSource joinSource = mock(JoinSource.class);
        JoinedQueryElement left = mock(JoinedQueryElement.class);
        JoinedQueryElement right = mock(JoinedQueryElement.class);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column leftKey =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column.class);
        when(leftKey.getName()).thenReturn(leftKeyName);
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column rightKey =
                mock(org.eclipse.daanse.cwm.model.cwm.resource.relational.Column.class);
        when(rightKey.getName()).thenReturn(rightKeyName);
        when(left.getSource()).thenReturn(leftSource);
        when(left.getKey()).thenReturn(leftKey);
        when(right.getSource()).thenReturn(rightSource);
        when(right.getKey()).thenReturn(rightKey);
        when(joinSource.getLeft()).thenReturn(left);
        when(joinSource.getRight()).thenReturn(right);
        return joinSource;
    }

    /** The InlineTableTest snowflake: {@code store join inline-nation on store_country = nation_name}. */
    private static JoinSource storeJoinInlineNation() {
        return joinSource(tableSource("store"), "store_country",
                inlineSource("nation"), "nation_name");
    }

    private static RolapLevel level(JoinSource relation, boolean parentChild) {
        RolapLevel level = mock(RolapLevel.class);
        RolapHierarchy hierarchy = mock(RolapHierarchy.class);
        when(hierarchy.getRelation()).thenReturn(relation);
        org.mockito.Mockito.doReturn(hierarchy).when(level).getHierarchy();
        when(level.isParentChild()).thenReturn(parentChild);
        return level;
    }

    /** The alias collection includes the inline leaf — memberFromTables keeps the inline side. */
    @Test
    void tableAliasesCollectInlineLeaves() {
        JoinSource relation = storeJoinInlineNation();

        assertThat(RelationFromMapper.tableAliases(relation))
                .containsExactly("store", "nation");
        assertThat(RelationFromMapper.memberFromTables(relation, Set.of("nation")))
                .containsExactly("store", "nation");
    }

    /**
     * The subset FROM composes {@code FromJoin(store, INNER, FromInline(nation), on)} — an inline
     * leaf is accepted as a join operand (the renderer renders {@code FromInline} like any derived
     * table inside a join).
     */
    @Test
    void fromReferencedComposesInlineLeafAsJoinOperand() {
        JoinSource relation = storeJoinInlineNation();

        FromClause from = RelationFromMapper.fromReferenced(relation, Set.of("store", "nation"));

        assertThat(from).isInstanceOf(FromClause.FromJoin.class);
        FromClause.FromJoin join = (FromClause.FromJoin) from;
        assertThat(join.left()).isInstanceOf(FromClause.FromTable.class);
        assertThat(join.right()).isInstanceOf(FromClause.FromInline.class);
        assertThat(((FromClause.FromInline) join.right()).alias().name()).isEqualTo("nation");
    }

    /** The gate accepts exactly the inline-join shape. */
    @Test
    void supportsInlineJoinRelationScopesTheProbe() {
        assertThat(MemberSqlMapper.supportsInlineJoinRelation(
                level(storeJoinInlineNation(), false))).isTrue();
        // A plain table join (no inline leaf) is the ordinary supported shape, not this one.
        assertThat(MemberSqlMapper.supportsInlineJoinRelation(
                level(joinSource(tableSource("store"), "region_id",
                        tableSource("region"), "region_id"), false))).isFalse();
        // A parent-child level stays outside (the parent-child channel is separate).
        assertThat(MemberSqlMapper.supportsInlineJoinRelation(
                level(storeJoinInlineNation(), true))).isFalse();
        // A lone inline relation is the exotic single-relation shape, not this one.
        RolapLevel loneInline = mock(RolapLevel.class);
        RolapHierarchy h = mock(RolapHierarchy.class);
        when(h.getRelation()).thenReturn(inlineSource("nation"));
        org.mockito.Mockito.doReturn(h).when(loneInline).getHierarchy();
        assertThat(MemberSqlMapper.supportsInlineJoinRelation(loneInline)).isFalse();
        // A join containing a VIEW leaf stays declined.
        org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource view =
                mock(org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource.class);
        when(view.getAlias()).thenReturn("v");
        assertThat(MemberSqlMapper.supportsInlineJoinRelation(
                level(joinSource(tableSource("store"), "k", view, "k"), false))).isFalse();
    }

    /** The strict/computed/exotic gates all decline the inline-join shape. */
    @Test
    void existingGatesStillDeclineInlineJoin() {
        RolapLevel level = level(storeJoinInlineNation(), false);
        when(level.getKeyExp()).thenReturn(
                new org.eclipse.daanse.rolap.element.RolapColumn("nation", "nation_name"));
        when(level.getOrdinalExps()).thenReturn(List.of());
        when(level.getProperties()).thenReturn(new org.eclipse.daanse.rolap.element.RolapProperty[0]);

        assertThat(MemberSqlMapper.supports(level)).isFalse();
        assertThat(MemberSqlMapper.supportsComputed(level)).isFalse();
        assertThat(MemberSqlMapper.supportsExoticSingleRelation(level)).isFalse();
    }
}

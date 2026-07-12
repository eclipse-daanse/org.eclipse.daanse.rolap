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

import java.util.List;

import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;

/**
 * The parent-child member-enumeration SELECTs (split out of {@link TupleSqlMapper}, pure code
 * motion): PC children, PC roots and PC children-via-intermediate-level — the builder shapes of
 * {@code SqlMemberSource.makeChildMemberSqlPC*}.
 */
public final class ParentChildQueries {

    private ParentChildQueries() {
    }

    /**
     * The children of a parent-child hierarchy member ({@code SqlMemberSource.makeChildMemberSqlPC}):
     * {@code SELECT key[, ordinals][, properties] FROM <rel> WHERE (parentExp = member.key)
     * GROUP BY … ORDER BY …}. Single (unique) PC level: key projected with internalType (SELECT+GROUP
     * BY), ordinals deduped by rendered SQL against the key (matching ordinal → ORDER BY key; otherwise
     * SELECT+GROUP BY+ORDER BY the ordinal; no extra key tiebreaker), or ORDER BY key when there is no
     * ordinal, then properties (SELECT; GROUP BY only when the property is NOT level-value-dependent —
     * functionally dependent on the level value). The parent predicate is a single-element AND so it
     * renders parenthesised: {@code WHERE (parent = key)}.
     */
    public static SelectStatement parentChildChildrenSql(
            org.eclipse.daanse.rolap.api.element.RolapMember member,
            boolean viewAware) {
        RolapLevel level = (RolapLevel) member.getLevel();
        return pcLevelSelect(level, level.getParentExp(),
                parentEqualsKey(level.getParentExp(), member.getKey(), level, viewAware), viewAware);
    }

    /**
     * Roots of a parent-child hierarchy ({@code SqlMemberSource.makeChildMemberSqlPCRoot}):
     * {@code WHERE parentExp IS NULL} (or {@code = nullParentValue}), projecting the single child PC
     * level. The condition is emitted bare (no AND wrap, no parens), rendering the canonical
     * lower-case {@code is null} or {@code = <value>} with numbers unquoted and other values
     * single-quoted.
     */
    public static SelectStatement parentChildRootSql(
            org.eclipse.daanse.rolap.api.element.RolapMember member,
            boolean viewAware) {
        RolapLevel level = (RolapLevel) member.getLevel().getChildLevel();
        SqlExpression parentExp = level.getParentExp();
        String npv = level.getNullParentValue();
        // The root predicate renders bare — no AND wrap, no parens (unlike PC/PCForLevel, whose
        // parent predicate is wrapped in a one-element AND so it renders parenthesised).
        org.eclipse.daanse.sql.statement.api.expression.Predicate where;
        if (npv == null || "NULL".equalsIgnoreCase(npv)) {
            // Structured is-null → the renderer's canonical lower-case " is null", consistent with every
            // other keyword the builder emits.
            where = org.eclipse.daanse.sql.statement.api.Predicates.isNull(
                    JoinPlanner.expressionFor(parentExp));
        } else {
            // = nullParentValue: a number is left unquoted, otherwise single-quoted. The parent column is a
            // node (so a COMPUTED parent expression renders as a RawVariant); the value is a raw literal
            // preserving the config string's formatting.
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression parentNode =
                    JoinPlanner.expressionFor(parentExp);
            String valueSql;
            try {
                Double.parseDouble(npv);
                valueSql = npv;
            } catch (NumberFormatException e) {
                StringBuilder b = new StringBuilder();
                org.eclipse.daanse.olap.common.Util.singleQuoteString(npv, b);
                valueSql = b.toString();
            }
            // residual: pc-null-parent literal formatting — a Literal node cannot guarantee the config
            // string's exact numeric formatting, so a raw literal preserves it.
            where = org.eclipse.daanse.sql.statement.api.Predicates.comparison(parentNode,
                    org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ,
                    org.eclipse.daanse.sql.statement.api.Expressions.raw(valueSql));
        }
        return pcLevelSelect(level, parentExp, where, viewAware);
    }

    /**
     * Children of a PC member queried via an intermediate parent-child level
     * ({@code SqlMemberSource.makeChildMemberSqlPCForLevel}): {@code WHERE parentChildLevel.parentExp =
     * member.key}, projecting the member's own level.
     */
    public static SelectStatement parentChildChildrenForLevelSql(
            org.eclipse.daanse.rolap.api.element.RolapMember member, RolapLevel parentChildLevel,
            boolean viewAware) {
        RolapLevel level = (RolapLevel) member.getLevel();
        return pcLevelSelect(level, parentChildLevel.getParentExp(),
                parentEqualsKey(parentChildLevel.getParentExp(), member.getKey(), level, viewAware), viewAware);
    }

    /** {@code (parentExp = key)} as a single-element AND so it renders parenthesised. */
    private static org.eclipse.daanse.sql.statement.api.expression.Predicate parentEqualsKey(
            SqlExpression parentExp, Object key, RolapLevel keyTypeLevel,
            boolean viewAware) {
        return org.eclipse.daanse.sql.statement.api.Predicates.and(java.util.List.of(
                org.eclipse.daanse.sql.statement.api.Predicates.comparison(
                        JoinPlanner.expressionFor(parentExp),
                        org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator.EQ,
                        org.eclipse.daanse.sql.statement.api.Expressions.literal(key, keyTypeLevel.getDatatype()))));
    }

    /** Shared parent-child level projection: FROM (parentExp + the projected level's columns), the given
     *  WHERE, then key (SELECT+GROUP BY, internalType), ordinals (deduped vs key), properties. */
    private static SelectStatement pcLevelSelect(RolapLevel level, SqlExpression parentExp,
            org.eclipse.daanse.sql.statement.api.expression.Predicate where,
            boolean viewAware) {
        RolapHierarchy hierarchy = level.getHierarchy();
        java.util.Set<String> keyTables = new java.util.LinkedHashSet<>();
        TupleSqlMapper.addAlias(keyTables, parentExp);
        TupleSqlMapper.addAlias(keyTables, level.getKeyExp());
        if (level.getOrdinalExps() != null) {
            for (SqlExpression oe : level.getOrdinalExps()) {
                TupleSqlMapper.addAlias(keyTables, oe);
            }
        }
        for (RolapProperty property : level.getProperties()) {
            TupleSqlMapper.addAlias(keyTables, property.getExp());
        }
        java.util.Set<String> fromTables = RelationFromMapper.memberFromTables(hierarchy.getRelation(), keyTables);
        org.eclipse.daanse.sql.statement.api.model.FromClause from =
                RelationFromMapper.fromReferenced(hierarchy.getRelation(), fromTables);
        if (from == null) {
            from = RelationFromMapper.from(hierarchy.getRelation());
        }

        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(from);
        q.where(where);

        SqlExpression keyExp = level.getKeyExp();
        ProjectionRef keyRef = q.project(JoinPlanner.expressionFor(keyExp), level.getInternalType());
        q.groupOn(keyRef);

        List<? extends SqlExpression> ordinals = level.getOrdinalExps();
        if (ordinals != null && !ordinals.isEmpty()) {
            for (SqlExpression ordinalExp : ordinals) {
                if (TupleSqlMapper.orderSig(ordinalExp).equals(TupleSqlMapper.orderSig(keyExp))) {
                    q.orderOn(keyRef, TupleSqlMapper.sortSpec(ordinalExp.getSortingDirection()));
                } else {
                    ProjectionRef ref = q.project(JoinPlanner.expressionFor(ordinalExp), null);
                    q.groupOn(ref);
                    q.orderOn(ref, TupleSqlMapper.sortSpec(ordinalExp.getSortingDirection()));
                }
            }
        } else {
            q.orderOn(keyRef, TupleSqlMapper.sortSpec(keyExp.getSortingDirection()));
        }

        for (RolapProperty property : level.getProperties()) {
            ProjectionRef ref = q.project(JoinPlanner.expressionFor(property.getExp()), null);
            if (!property.dependsOnLevelValue()) {
                q.groupOn(ref);
            }
        }
        q.completeNonAggregatesGroupBy();
        return q.build();
    }
}

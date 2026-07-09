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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;

/**
 * Engine-free helpers that translate {@link AggStar} structure into the generic
 * {@code org.eclipse.daanse.sql.statement} model — the level-derived half of the agg-join channel:
 * everything a mapper can compute deterministically from the target level plus the aggStar lives
 * here, so the producer-side {@code AggPlan} never has to carry it. The {@link JoinPlanner} sibling
 * for aggregate tables.
 * <p>
 * A consumer must call the same {@code isLevelCollapsed}/{@code levelContainsMultipleColumns}
 * predicates that select the SQL shape and then use these helpers for the SQL model, or its output
 * diverges.
 */
public final class AggJoinPlanner {

    private AggJoinPlanner() {
    }

    /**
     * The level-expression-to-target-expression map for a read against {@code aggStar}: starts from
     * the identity map (every level
     * expression maps to itself — including a {@code null -> null} entry when the level has no
     * caption, which {@link #requiresJoinToDim} skips) and substitutes agg expressions where the
     * aggregate table carries them.
     * <ul>
     * <li>{@code aggStar == null}: the identity map (no substitution).</li>
     * <li>No {@code AggStar.Table.Level} for the level's star key (the {@code aggLevel == null}
     *     branch): the key expression is re-mapped to the raw {@code AggStar.Table.Column}
     *     expression and NOTHING else — caption/ordinals/properties keep their identity entries
     *     (they are not on the agg table, so a consumer projects them from the dimension —
     *     "no extra columns").</li>
     * <li>An {@code AggStar.Table.Level} exists: the key maps to the agg level expression;
     *     ordinals pair up index-wise up to the SHORTER of the two lists (a level ordinal beyond
     *     the agg level's list keeps its identity entry); the caption maps only when the agg level
     *     carries one; each property maps by NAME when present on the agg level.</li>
     * </ul>
     * An identity entry remaining in the result is exactly the "not on the agg table" signal
     * {@link #requiresJoinToDim} tests.
     */
    public static Map<SqlExpression, SqlExpression> levelTargetExpMap(RolapLevel level, AggStar aggStar) {
        Map<SqlExpression, SqlExpression> map = initializeIdentityMap(level);
        if (aggStar == null) {
            return Collections.unmodifiableMap(map);
        }
        AggStar.Table.Level aggLevel = getAggLevel(aggStar, (RolapCubeLevel) level);
        if (aggLevel == null) {
            // If no AggStar Level is defined, then the key exp is
            // a raw AggStar Column.  No extra columns.
            AggStar.Table.Column aggStarColumn = getAggColumn(aggStar, (RolapCubeLevel) level);
            assert aggStarColumn.getExpression() != null;
            map.put(level.getKeyExp(), aggStarColumn.getExpression());
        } else {
            assert aggLevel.getExpression() != null;
            map.put(level.getKeyExp(), aggLevel.getExpression());
            // put in target map elements where indexes are same.
            if (aggLevel.getOrdinalExps() != null) {
                int size = Math.min(aggLevel.getOrdinalExps().size(), level.getOrdinalExps().size());
                for (int i = 0; i < size; i++) {
                    map.put(level.getOrdinalExps().get(i), aggLevel.getOrdinalExps().get(i));
                }
            }
            if (aggLevel.getCaptionExp() != null) {
                map.put(level.getCaptionExp(), aggLevel.getCaptionExp());
            }
            for (RolapProperty prop : level.getProperties()) {
                String propName = prop.getName();
                if (aggLevel.getProperties().containsKey(propName)) {
                    map.put(prop.getExp(), aggLevel.getProperties().get(propName));
                }
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * True when the {@link #levelTargetExpMap} result contains any IDENTITY entry (a non-null level
     * expression mapped to itself) — that expression has no counterpart on the aggregate table, so
     * the consumer must join back to the dimension table(s) to project it. The {@code null -> null}
     * no-caption entry does not count.
     */
    public static boolean requiresJoinToDim(Map<SqlExpression, SqlExpression> targetExp) {
        for (Map.Entry<SqlExpression, SqlExpression> entry : targetExp.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equals(entry.getValue())) {
                // this level expression does not have a corresponding
                // field on the aggregate table
                return true;
            }
        }
        return false;
    }

    /**
     * One FROM registration of the agg-table chain: the table's {@link FromClause}, its FROM alias
     * ({@code AggStar.Table.getName()}, which {@code addToFrom} passes as the {@code addFrom} alias)
     * and the {@code ON} predicate linking the table to its parent
     * ({@code null} for the chain root, the agg fact table, which has no join condition).
     * <p>
     * This is the aggregate-side parallel of {@link JoinPlanner.JoinStep} rather than a
     * generalization of it: {@code JoinStep} carries a {@code RolapStar.Table} consumed via
     * {@code JoinPlanner.tableFromClause} by the tuple and member mappers, and an
     * {@code AggStar.Table} is not a {@code RolapStar.Table}, so the agg channel uses its own edge
     * type ({@code AggJoinEdge}) consumed alongside it.
     */
    public record AggJoinEdge(FromClause from, String fromAlias, Predicate on) {
    }

    /**
     * The FROM registrations {@code AggStar.Table.addToFrom(query, failIfExists, joinToParent=true)}
     * performs for {@code table}, as edges: SELF FIRST, then the parents up to the agg fact table.
     * Each table's edge carries
     * its own {@link AggStar.Table.JoinCondition} as {@code left = right}
     * ({@link #joinPredicate}); the agg fact root carries {@code null}. NOTE the join CONDITIONS
     * belong parent-first (a table's condition follows its parents'), while the FROM registrations
     * are self-first; a consumer replays the conditions from this list in REVERSE.
     */
    public static List<AggJoinEdge> aggTableChain(AggStar.Table table) {
        List<AggJoinEdge> chain = new ArrayList<>();
        for (AggStar.Table t = table; t != null; t = t.hasParent() ? t.getParent() : null) {
            Predicate on = t.hasJoinCondition() ? joinPredicate(t.getJoinCondition()) : null;
            chain.add(new AggJoinEdge(aggTableFrom(t), t.getName(), on));
        }
        return chain;
    }

    /** The {@code left = right} equality of an agg-table join condition — both sides are level
     *  {@code SqlExpression}s, translated dialect-free via {@link JoinPlanner#expressionFor}. */
    public static Predicate joinPredicate(AggStar.Table.JoinCondition joinCondition) {
        return Predicates.comparison(JoinPlanner.expressionFor(joinCondition.getLeft()),
                ComparisonOperator.EQ, JoinPlanner.expressionFor(joinCondition.getRight()));
    }

    /**
     * The {@link FromClause} for one aggregate table:
     * the physical table (schema-qualified when the relation carries one, incl. the
     * {@code sqlWhereExpression} filter slot) aliased as {@code table.getName()} — for the agg FACT
     * table that is the agg table's own name (a DimTable's
     * name is its star alias, distinct from the physical name). A non-table agg relation
     * (view/inline — not produced by the agg matcher) falls back to
     * {@link RelationFromMapper#from}.
     */
    public static FromClause aggTableFrom(AggStar.Table table) {
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation = table.getRelation();
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource ts) {
            return From.table(From.tableRef(RelationFromMapper.schemaName(ts.getTable()), ts.getTable().getName()),
                    TableAlias.of(table.getName()), RelationFromMapper.tableFilter(ts), Map.of());
        }
        return RelationFromMapper.from(relation);
    }

    /**
     * The dimension-to-aggregate join edge for a COLLAPSED level projected with a dimension join:
     * {@code levelKey = aggColumn}. The consumer adds the dimension FROM leaf-first
     * ({@link RelationFromMapper#fromInverse}) and the agg chain ({@link #aggTableChain}), then
     * this edge ties them together.
     */
    public static Predicate dimToAggEdge(RolapCubeLevel level, AggStar aggStar) {
        RolapStar.Column starColumn = level.getStarKeyColumn();
        AggStar.Table.Column aggColumn = aggStar.lookupColumn(starColumn.getBitPosition());
        return Predicates.comparison(JoinPlanner.expressionFor(level.getKeyExp()),
                ComparisonOperator.EQ, JoinPlanner.expressionFor(aggColumn.getExpression()));
    }

    /** The agg level registered for the level's star key bit, or null when the agg table carries
     *  the key only as a raw column. */
    private static AggStar.Table.Level getAggLevel(AggStar aggStar, RolapCubeLevel level) {
        RolapStar.Column starColumn = level.getStarKeyColumn();
        return aggStar.lookupLevel(starColumn.getBitPosition());
    }

    /** The agg column for the level's star key bit. */
    private static AggStar.Table.Column getAggColumn(AggStar aggStar, RolapCubeLevel level) {
        RolapStar.Column starColumn = level.getStarKeyColumn();
        int bitPos = starColumn.getBitPosition();
        return aggStar.lookupColumn(bitPos);
    }

    /**
     * Every level expression (key, ordinals,
     * caption — possibly {@code null} —, properties) mapped to itself, the starting assumption
     * before agg substitution.
     */
    private static Map<SqlExpression, SqlExpression> initializeIdentityMap(RolapLevel level) {
        Map<SqlExpression, SqlExpression> map = new HashMap<>();
        map.put(level.getKeyExp(), level.getKeyExp());
        for (SqlExpression oe : level.getOrdinalExps()) {
            map.put(oe, oe);
        }
        map.put(level.getCaptionExp(), level.getCaptionExp());
        for (RolapProperty prop : level.getProperties()) {
            if (!map.containsKey(prop.getExp())) {
                map.put(prop.getExp(), prop.getExp());
            }
        }
        return map;
    }
}

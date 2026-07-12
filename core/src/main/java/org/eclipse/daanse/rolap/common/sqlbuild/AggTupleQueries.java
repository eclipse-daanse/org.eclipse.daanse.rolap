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
import org.eclipse.daanse.rolap.common.aggmatcher.AggStar;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.element.RolapCubeLevel;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.NullOrder;
import org.eclipse.daanse.sql.statement.api.model.SortDirection;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.rolap.common.RolapUtil;

/**
 * The aggregate-table tuple/level-members SELECTs (split out of {@link TupleSqlMapper}, pure
 * code motion): the collapsed single-column and collapsed tuple reads, the agg-star tuple read
 * with its chain registration/folding, and the agg-side level projections.
 */
public final class AggTupleQueries {

    private AggTupleQueries() {
    }

    /**
     * The collapsed single-column aggStar LEVEL-MEMBERS SELECT — the tuple-reader counterpart of
     * {@link MemberSqlMapper#collapsedSingleColumnSql}, the
     * {@code addAggColumnToSql} branch: every projected level (root..target) has a single key
     * column present IN the aggregate table, so no dimension join is needed. Projects each level's
     * AGGREGATE column ({@code aggStar.lookupColumn(bitPos).toSqlExpression()}) from the single agg
     * table, root down to target, each added to GROUP BY (every collapsed column is grouped
     * unconditionally) and ordered ASC nulls-last (the standalone
     * {@code WhichSelect.ONLY} read this path is restricted to). {@code where} (the agg-substituted
     * context — {@link org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint#levelMembersAggWhere}) is split into per-conjunct
     * WHERE clauses (a nested {@code And} stays grouped).
     * <p>
     * {@code nativeHaving} (a native Filter measure condition) and {@code nativeOrder} (a native
     * TopCount/BottomCount measure order) are carried the SAME way {@code TupleSqlMapper.buildLevelSelect} does — the
     * HAVING after the GROUP BY, the measure order projected and ORDERed BEFORE the level ordering — but
     * both already agg-substituted by the constraint's agg counterparts
     * ({@link org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint#levelMembersAggHaving} /
     * {@link org.eclipse.daanse.rolap.common.constraint.SqlContextConstraint#levelMembersAggOrder}),
     * since the aggStar read has no base-star
     * contribution to resolve them through. A plain (non-native) context constraint passes both empty and
     * the body is identical to the pure-projection shape.
     */
    public static SelectStatement collapsedSingleColumnSql(
            List<RolapCubeLevel> collapsedLevels,
            AggStar aggStar,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving) {
        return collapsedTupleLevelMembersSql(List.of(collapsedLevels), aggStar, where, nativeOrder,
                nativeHaving);
    }

    /**
     * The MULTI-TARGET generalization of {@link #collapsedSingleColumnSql}
     * (agg-only multi-target projection): every projected level of every target is COLLAPSED on
     * the aggregate table and needs no dimension join, so the whole read is a single-agg-table
     * SELECT. Two per-level branches ({@code addLevelMemberSql}):
     * <ul>
     * <li>single-column collapsed ({@code !levelContainsMultipleColumns}) — the
     *     {@code addAggColumnToSql} branch: the agg key column, SELECT + unconditional GROUP BY,
     *     ORDER BY key ASC;</li>
     * <li>multi-column collapsed with EVERY extra column substituted on the agg table
     *     ({@code !requiresJoinToDim}) — the {@code targetExp} branch: key/caption/ordinals/
     *     properties each projected via {@link AggJoinPlanner#levelTargetExpMap}, GROUP BY per the
     *     hierarchy's {@code isGroupByNeeded}, a non-key ordinal ORDERed BEFORE the key (e.g.
     *     {@code testmonthord} before {@code testmonthname}) with the key as
     *     tiebreaker — the {@code AggStar.Table.Level.getOrdinalExps()} channel.</li>
     * </ul>
     * A level outside those branches (not collapsed, or collapsed but requiring the dimension join)
     * is a caller bug here — it belongs to {@link #aggTupleLevelMembersSql} — and throws.
     * {@code where}/{@code nativeOrder}/{@code nativeHaving} are carried exactly like the
     * single-target form (already agg-substituted by the constraint's agg counterparts). The multi-target
     * path is currently unused by executed routes; the single-target delegation keeps the
     * {@code aggstar-collapsed-levelmembers} route unchanged.
     */
    public static SelectStatement collapsedTupleLevelMembersSql(
            List<List<RolapCubeLevel>> targetCollapsedLevels,
            AggStar aggStar,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving) {
        RolapCubeLevel firstLevel = targetCollapsedLevels.get(0).get(0);
        AggStar.Table.Column firstAgg =
                aggStar.lookupColumn(firstLevel.getStarKeyColumn().getBitPosition());
        SelectStatementBuilder q = SelectStatementBuilder.create();
        // FROM the single agg table (collapsed = every level's key column IS a column of the agg fact).
        q.from(org.eclipse.daanse.sql.statement.api.From.table(firstAgg.getTable().getName(),
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of(
                        org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.getTableAlias(
                                firstAgg.getExpression()))));
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        if (targetCollapsedLevels.size() > 1) {
            q.header("tuples " + String.join(" x ", targetCollapsedLevels.stream()
                    .map(t -> t.get(t.size() - 1).getUniqueName()).toList()));
            q.footerComment("tuple members (aggstar collapsed)");
        } else {
            List<RolapCubeLevel> only = targetCollapsedLevels.get(0);
            q.header("members " + only.get(only.size() - 1).getUniqueName());
            q.footerComment("level members (aggstar collapsed)");
        }
        // Each recorded context restriction is its own WHERE conjunct (a nested AND stays grouped) —
        // the same split MemberSqlMapper.collapsedSingleColumnSql applies.
        where.ifPresent(p -> {
            if (p instanceof org.eclipse.daanse.sql.statement.api.expression.Predicate.And and) {
                and.operands().forEach(op -> q.where(op, "context"));
            } else {
                q.where(p, "context");
            }
        });
        // Native Filter HAVING (a measure-comparison condition), emitted after the WHERE (it follows
        // GROUP BY in SQL). The predicate already carries its `((agg(measure) op value))` parenthesisation
        // AND is already agg-substituted (compiled through RolapNativeSql with this aggStar).
        nativeHaving.ifPresent(p -> q.having(p, "native filter"));
        // Project each target's collapsed levels root..target, in the loop order addLevelMemberSql
        // emits. Defer the level ORDER BY so a native measure order can be PREPENDED below.
        java.util.Set<String> orderedColumns = new java.util.LinkedHashSet<>();
        java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders = new java.util.ArrayList<>();
        for (List<RolapCubeLevel> target : targetCollapsedLevels) {
            // needsGroupBy is only consulted by the multi-column (targetExp) branch; computing it
            // lazily keeps the single-column route free of new hierarchy reads. The
            // null-tolerant pre-check (not the recorder predicate) decides the EMISSION branch
            // here because the caller has already routed with the real predicates — the
            // single-column branch performs no ordinal/caption/property reads, and the delegation
            // must not add any for single-column inputs.
            boolean anyMultiCol = target.stream()
                    .anyMatch(AggTupleQueries::collapsedLevelMultipleColumns);
            boolean needsGroupBy = true;
            if (anyMultiCol) {
                RolapCubeLevel targetLevel = target.get(target.size() - 1);
                RolapHierarchy hierarchy = targetLevel.getHierarchy();
                needsGroupBy = RolapUtil.isGroupByNeeded(hierarchy,
                        (List<RolapLevel>) hierarchy.getLevels(), targetLevel.getDepth());
            }
            for (RolapCubeLevel level : target) {
                if (collapsedLevelMultipleColumns(level)) {
                    java.util.Map<SqlExpression, SqlExpression> targetExp =
                            AggJoinPlanner.levelTargetExpMap(level, aggStar);
                    if (AggJoinPlanner.requiresJoinToDim(targetExp)) {
                        throw new IllegalStateException(
                                "collapsed-tuple read requires agg-substituted levels (dim join needed): "
                                        + level.getUniqueName());
                    }
                    projectAggSubstitutedLevel(q, level, aggStar, needsGroupBy, orderedColumns,
                            levelOrders, true);
                } else {
                    projectAggCollapsedColumn(q, level, aggStar, orderedColumns, levelOrders);
                }
            }
        }
        // Native TopCount/Order measure: project it (trailing alias) and order by it FIRST, then the
        // deferred level ORDER BY — the emission order buildLevelSelect uses. The measure expression is
        // already agg-substituted (compiled through RolapNativeSql with this aggStar).
        nativeOrder.ifPresent(no -> {
            ProjectionRef measureRef = q.project(no.measureExpr(), null, null, "measure (native order)");
            // The native order measure is an aggregate the renderer's structural check cannot see; exempt
            // it from GROUP-BY completion (the recorder never groups the order expression either).
            q.excludeFromGroupByCompletion(measureRef);
            SortDirection dir = no.direction() == SortingDirection.DESC ? SortDirection.DESC : SortDirection.ASC;
            q.orderOn(measureRef, new SortSpec(dir, no.nullable(), NullOrder.LAST, false));
        });
        levelOrders.forEach(e -> q.orderOn(e.getKey(), e.getValue()));
        return q.build();
    }

    // ---- the agg tuple mapper ---------------------------------------------

    /**
     * The AGGREGATE-TABLE level/tuple-members SELECT — the
     * {@code addLevelMemberSql} loop with a non-null {@code aggStar} (agg-fact-join and
     * agg-dim-join), emitted in the reference query's REGISTRATION order,
     * because the assembler folds the FROM left-deep from that order and demotes
     * cycle edges to WHERE. Per target, per non-all level root→depth:
     * <ol>
     * <li>{@code isLevelCollapsed && !levelContainsMultipleColumns} (the SAME
     *     {@code SqlMemberSource} predicates, including the ordinal quirk: ANY
     *     non-empty {@code ordinalExps} ⇒ multiple columns) — the {@code addAggColumnToSql} branch:
     *     project the agg column only and register {@link AggJoinPlanner#aggTableChain};</li>
     * <li>else substitute {@link AggJoinPlanner#levelTargetExpMap} at every projection point
     *     ({@link #projectAggLevel}); a NON-collapsed level registers its base dimension subset per
     *     mapped expression ({@code hierarchy.addToFrom});</li>
     * <li>{@code factJoinRequired}: the per-level existence join — the agg arm of
     *     {@code joinLevelTableToFactTable}: the chain of
     *     {@code aggStar.lookupColumn(starKey.getBitPosition()).getTable()};</li>
     * <li>({@code collapsed && requiresJoinToDim}): the INVERSE dimension subset
     *     ({@link RelationFromMapper#fromInverse} semantics), then the agg chain, then the
     *     {@link AggJoinPlanner#dimToAggEdge} edge;</li>
     * <li>after all targets: {@code havingJoins} — the native-HAVING compile FROM side
     *     effects ({@link HavingJoin}), replayed in tape position (the filter compile runs in the
     *     constraint fork after the level loop and BEFORE the inherited context ops);</li>
     * <li>then {@code orderedAggPredicates} in order — each predicate's agg-table
     *     chain registered on first appearance, then its WHERE conjunct (the {@code [join, wheres]}
     *     interleaving of the context constraint).</li>
     * </ol>
     * The FROM is PRE-FOLDED here with the assembler's exact algorithm ({@link #foldAggFrom}):
     * items in registration order, each pending item joined by its FIRST recorded edge into the
     * already-placed set (multi-pass, alias-tracked like {@code addChainAliasAware}), unused edges
     * appended as WHERE conjuncts AFTER the context conjuncts (dedup by predicate value), leftovers
     * comma-joined. {@link AggJoinPlanner.AggJoinEdge} is the agg-chain transport into the fold —
     * pre-folding was chosen over threading a parallel edge list through
     * {@code buildLevelSelect} because ON-vs-WHERE placement needs the GLOBAL registration order
     * (dim leaves, agg chains and cycle edges interleave), which a per-list join emission alongside
     * {@code JoinStep} cannot express; {@code buildLevelSelect} receives the finished tree.
     * <p>
     * {@code nativeHaving}/{@code nativeOrder} are carried exactly like {@code buildLevelSelect}
     * does (every reader path carries both); {@code emitOrderBy=false} is the
     * union arm. Currently unused by executed routes.
     */
    public static SelectStatement aggTupleLevelMembersSql(List<RolapLevel> targetLevels,
            AggStar aggStar,
            boolean viewAware,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where,
            java.util.List<org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate> orderedAggPredicates,
            java.util.List<HavingJoin> havingJoins,
            java.util.Optional<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.NativeOrder> nativeOrder,
            java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> nativeHaving,
            boolean factJoinRequired,
            org.eclipse.daanse.rolap.element.RolapCube baseCube,
            boolean emitOrderBy) {
        java.util.LinkedHashMap<String, org.eclipse.daanse.sql.statement.api.model.FromClause> items =
                new java.util.LinkedHashMap<>();
        java.util.List<FoldEdge> edges = new java.util.ArrayList<>();
        for (RolapLevel target : targetLevels) {
            RolapHierarchy hierarchy = target.getHierarchy();
            List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
            for (int i = 0; i <= target.getDepth(); i++) {
                RolapLevel lvl = levels.get(i);
                if (lvl.isAll()) {
                    continue;
                }
                RolapCubeLevel cubeLevel = (RolapCubeLevel) lvl;
                boolean collapsed = org.eclipse.daanse.rolap.common.member.SqlMemberSource
                        .isLevelCollapsed(aggStar, cubeLevel);
                boolean multipleCols = org.eclipse.daanse.rolap.common.member.SqlMemberSource
                        .levelContainsMultipleColumns(lvl);
                if (collapsed && !multipleCols) {
                    // addAggColumnToSql: agg column projection only (see projectAggLevel) + the agg
                    // chain FROM. The recorder CONTINUEs here — no per-level constraint join.
                    registerAggChain(items, edges, aggStar
                            .lookupColumn(cubeLevel.getStarKeyColumn().getBitPosition()).getTable());
                    continue;
                }
                java.util.Map<SqlExpression, SqlExpression> targetExp =
                        AggJoinPlanner.levelTargetExpMap(lvl, aggStar);
                SqlExpression keyExp = targetExp.get(lvl.getKeyExp());
                // The recorder's FROM-side ordinal list: filtered by targetExp membership, mapped.
                List<SqlExpression> ordinalExps = lvl.getOrdinalExps().stream()
                        .filter(targetExp::containsKey).map(targetExp::get).toList();
                SqlExpression captionExp = targetExp.get(lvl.getCaptionExp());
                SqlExpression parentExp = lvl.getParentExp();
                if (parentExp != null && !collapsed) {
                    registerLevelRelation(items, edges, hierarchy, parentExp);
                }
                if (!collapsed) {
                    registerLevelRelation(items, edges, hierarchy, keyExp);
                    for (SqlExpression oe : ordinalExps) {
                        registerLevelRelation(items, edges, hierarchy, oe);
                    }
                    if (captionExp != null) {
                        registerLevelRelation(items, edges, hierarchy, captionExp);
                    }
                }
                // Per-level constraint ops (addLevelConstraintOps): the existence join to the agg
                // fact — joinLevelTableToFactTable's agg arm, registered BETWEEN the level's own
                // FROM adds and the inverse arm.
                if (factJoinRequired) {
                    RolapStar.Column starKey = cubeLevel.getBaseStarKeyColumn(baseCube);
                    if (starKey != null) {
                        registerAggChain(items, edges,
                                aggStar.lookupColumn(starKey.getBitPosition()).getTable());
                    }
                }
                if (collapsed && AggJoinPlanner.requiresJoinToDim(targetExp)) {
                    // inverse dimension subset first (leaf-ward), then the agg chain, then the
                    // dimKey = aggColumn edge — the addToFromInverse/addToFrom/
                    // addJoinCondition order.
                    registerInverseRelation(items, edges, hierarchy, lvl.getKeyExp());
                    AggStar.Table.Column aggColumn =
                            aggStar.lookupColumn(cubeLevel.getStarKeyColumn().getBitPosition());
                    registerAggChain(items, edges, aggColumn.getTable());
                    edges.add(new FoldEdge(
                            org.eclipse.daanse.rolap.common.util.SqlExpressionResolver
                                    .getTableAlias(lvl.getKeyExp()),
                            org.eclipse.daanse.rolap.common.util.SqlExpressionResolver
                                    .getTableAlias(aggColumn.getExpression()),
                            AggJoinPlanner.dimToAggEdge(cubeLevel, aggStar)));
                } else if (collapsed) {
                    registerAggChain(items, edges, aggStar
                            .lookupColumn(cubeLevel.getStarKeyColumn().getBitPosition()).getTable());
                }
            }
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("agg tuple read produced no FROM items: " + targetLevels);
        }
        // The native-HAVING compile FROM side effects (FilterConstraint.addConstraintOps
        // -> RolapNativeSql MatchingSqlCompiler -> ctx.addToFrom), replayed in tape position: the
        // filter compile runs in the constraint fork AFTER the level loop and BEFORE the inherited
        // context ops, so its registrations land here — after all targets, before the context
        // predicate chains. Each is the hierarchy.addToFrom form (relationSubset — the WHOLE join
        // kept under FilterChildlessSnowflakeMembers), which is how the FROM gains the
        // filter-referenced table's full snowflake subtree (e.g.
        // cat→product_cat→product_csv). Registering an already-present subset is a no-op
        // (alias dedup + edge value dedup in the fold).
        for (HavingJoin hj : havingJoins) {
            registerLevelRelation(items, edges, hj.hierarchy(), hj.expression());
        }
        // Context constraint (after ALL targets — addConstraintOps order): per column, the table's
        // agg chain on first appearance, then its WHERE conjunct.
        java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> conjuncts =
                new java.util.ArrayList<>();
        if (!orderedAggPredicates.isEmpty()) {
            for (org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate cp : orderedAggPredicates) {
                if (cp.table() != null) {
                    registerAggChain(items, edges, cp.table());
                }
                conjuncts.add(cp.predicate());
            }
        } else {
            where.ifPresent(p -> {
                if (p instanceof org.eclipse.daanse.sql.statement.api.expression.Predicate.And and) {
                    conjuncts.addAll(and.operands());
                } else {
                    conjuncts.add(p);
                }
            });
        }
        // Assembler fold: builds the left-deep FROM tree and APPENDS the unused (cycle) edges
        // to the conjunct list — after the context conjuncts, deduped by predicate value.
        org.eclipse.daanse.sql.statement.api.model.FromClause from = foldAggFrom(items, edges, conjuncts);
        java.util.Optional<org.eclipse.daanse.sql.statement.api.expression.Predicate> where2 =
                conjuncts.isEmpty() ? java.util.Optional.empty()
                        : java.util.Optional.of(org.eclipse.daanse.sql.statement.api.Predicates.and(conjuncts));
        return TupleSqlMapper.buildLevelSelect(targetLevels, from, viewAware, java.util.List.of(), where2, nativeOrder,
                nativeHaving, emitOrderBy, true, aggStar);
    }

    /**
     * One recorded join EDGE of the agg FROM graph — the mapper-side form of
     * {@code QueryTape.JoinEdge}: both endpoint aliases (undirected; schema-traversal order) plus
     * the structured ON predicate. The fold consumes these exactly like the assembler consumes the
     * tape's edges.
     */
    private record FoldEdge(String leftAlias, String rightAlias,
            org.eclipse.daanse.sql.statement.api.expression.Predicate on) {
    }

    /**
     * One FROM registration the native-HAVING compile performs as a side effect
     * ({@code RolapNativeSql}'s Caption/Name MATCHES compiler under an aggStar calls
     * {@code ctx.addToFrom(hierarchy, expression)} whenever the filter source does NOT resolve to
     * an agg column — the referenced dimension table must be joinable): the hierarchy whose
     * relation subset ({@code hierarchy.addToFrom} semantics —
     * {@link RelationFromMapper#relationSubset}, whole join kept under
     * {@code FilterChildlessSnowflakeMembers}) joins the FROM. Collected by
     * {@code NativeSqlContext.scratchCollecting} during the constraint's scratch compile
     * ({@code SqlContextConstraint.levelMembersAggHavingWithJoins}) and replayed by
     * {@link #aggTupleLevelMembersSql} in tape position.
     */
    public record HavingJoin(RolapHierarchy hierarchy, SqlExpression expression) {
    }

    /**
     * The {@code AggStar.Table.addToFrom(query, failIfExists=false, joinToParent=true)} form:
     * FROM items SELF-FIRST up the chain (alias-deduped — {@code putIfAbsent} is the
     * {@code addFromTable} dedup), join CONDITIONS replayed in REVERSE (parent-first — the
     * recursion unwinds; see {@link AggJoinPlanner#aggTableChain}). Conditions are recorded on
     * EVERY call, exactly like the unconditional {@code addJoinCondition} — the fold's
     * value dedup absorbs the duplicates.
     */
    private static void registerAggChain(
            java.util.Map<String, org.eclipse.daanse.sql.statement.api.model.FromClause> items,
            java.util.List<FoldEdge> edges, AggStar.Table table) {
        List<AggJoinPlanner.AggJoinEdge> chain = AggJoinPlanner.aggTableChain(table);
        for (AggJoinPlanner.AggJoinEdge e : chain) {
            items.putIfAbsent(e.fromAlias(), e.from());
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            AggJoinPlanner.AggJoinEdge e = chain.get(i);
            if (e.on() != null) {
                edges.add(new FoldEdge(e.fromAlias(), chain.get(i + 1).fromAlias(), e.on()));
            }
        }
    }

    /**
     * The {@code RolapHierarchy.addToFrom(query, expression)} form: the smallest relation subset
     * reaching the expression's table ({@link RelationFromMapper#relationSubset}, whole relation
     * when unresolved), registered leaf-per-leaf via {@link #walkRelation}.
     */
    private static void registerLevelRelation(
            java.util.Map<String, org.eclipse.daanse.sql.statement.api.model.FromClause> items,
            java.util.List<FoldEdge> edges, RolapHierarchy hierarchy, SqlExpression exp) {
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation =
                hierarchy.getRelation();
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource sub = relation;
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource
                && exp != null) {
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource subset =
                    RelationFromMapper.relationSubset(relation,
                            org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.getTableAlias(exp));
            sub = subset != null ? subset : relation;
        }
        walkRelation(items, edges, sub);
    }

    /**
     * The {@code RolapHierarchy.addToFromInverse(query, expression)} form: the INVERSE
     * subset ({@link RelationFromMapper#relationSubsetInverse} — leaf-ward subtree kept, root-ward
     * ancestors dropped, no childless-filter consultation), registered leaf-per-leaf. An
     * unresolved subset is a caller bug, exactly as {@code addFrom(null)} would be.
     */
    private static void registerInverseRelation(
            java.util.Map<String, org.eclipse.daanse.sql.statement.api.model.FromClause> items,
            java.util.List<FoldEdge> edges, RolapHierarchy hierarchy, SqlExpression exp) {
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation =
                hierarchy.getRelation();
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource sub = relation;
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource
                && exp != null) {
            sub = RelationFromMapper.relationSubsetInverse(relation,
                    org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.getTableAlias(exp));
        }
        if (sub == null) {
            throw new IllegalStateException("inverse relation subset unresolved for " + exp);
        }
        walkRelation(items, edges, sub);
    }

    /**
     * The {@code QueryRecorder.addFrom(relation, alias, failIfExists=false)} form: leaves register
     * left-to-right (alias-deduped), a join records its edge only when a side was NEWLY added (the
     * {@code addJoin} rule), post-order per join node. Returns whether anything was
     * added.
     */
    private static boolean walkRelation(
            java.util.Map<String, org.eclipse.daanse.sql.statement.api.model.FromClause> items,
            java.util.List<FoldEdge> edges,
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            boolean addedLeft = walkRelation(items, edges, join.getLeft().getSource());
            boolean addedRight = walkRelation(items, edges, join.getRight().getSource());
            if (addedLeft || addedRight) {
                edges.add(new FoldEdge(
                        org.eclipse.daanse.rolap.common.util.JoinUtil.getLeftAlias(join),
                        org.eclipse.daanse.rolap.common.util.JoinUtil.getRightAlias(join),
                        RelationFromMapper.joinOn(join)));
            }
            return addedLeft || addedRight;
        }
        String alias = org.eclipse.daanse.rolap.common.util.RelationUtil.getAlias(relation);
        if (!items.containsKey(alias)) {
            items.put(alias, RelationFromMapper.fromReferenced(relation, java.util.Set.of(alias)));
            return true;
        }
        return false;
    }

    /**
     * The {@code ContributionAssembler.buildFromClause} form: folds the registered items into a
     * LEFT-DEEP join tree in insertion order — each pending item joined by the FIRST recorded edge
     * connecting it to an already-placed alias, multi-pass until no progress — then appends every
     * edge NOT used as a tree ON to {@code outConjuncts} (cycle edges → WHERE, dedup by predicate
     * value), and comma-joins any disconnected leftovers. A single item (or an edge-less
     * registration) short-circuits exactly like the assembler.
     */
    private static org.eclipse.daanse.sql.statement.api.model.FromClause foldAggFrom(
            java.util.LinkedHashMap<String, org.eclipse.daanse.sql.statement.api.model.FromClause> items,
            java.util.List<FoldEdge> edges,
            java.util.List<org.eclipse.daanse.sql.statement.api.expression.Predicate> outConjuncts) {
        java.util.List<String> aliases = List.copyOf(items.keySet());
        java.util.List<org.eclipse.daanse.sql.statement.api.model.FromClause> itemList =
                List.copyOf(items.values());
        if (itemList.size() == 1 || edges.isEmpty()) {
            return itemList.size() == 1 ? itemList.get(0)
                    : new org.eclipse.daanse.sql.statement.api.model.FromClause.FromProduct(itemList);
        }
        java.util.Map<String, java.util.List<FoldEdge>> byAlias = new java.util.HashMap<>();
        for (FoldEdge e : edges) {
            byAlias.computeIfAbsent(e.leftAlias(), k -> new java.util.ArrayList<>()).add(e);
            byAlias.computeIfAbsent(e.rightAlias(), k -> new java.util.ArrayList<>()).add(e);
        }
        org.eclipse.daanse.sql.statement.api.model.FromClause acc = itemList.get(0);
        java.util.Set<String> placed = new java.util.HashSet<>();
        placed.add(aliases.get(0));
        java.util.Set<org.eclipse.daanse.sql.statement.api.expression.Predicate> treeOns =
                new java.util.HashSet<>();
        java.util.List<Integer> pending = new java.util.ArrayList<>();
        for (int i = 1; i < itemList.size(); i++) {
            pending.add(i);
        }
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            java.util.List<Integer> stillPending = new java.util.ArrayList<>();
            for (Integer idx : pending) {
                String a = aliases.get(idx);
                org.eclipse.daanse.sql.statement.api.expression.Predicate on = null;
                for (FoldEdge e : byAlias.getOrDefault(a, List.of())) {
                    String other = e.leftAlias().equals(a) ? e.rightAlias() : e.leftAlias();
                    if (placed.contains(other)) {
                        on = e.on();
                        break;
                    }
                }
                if (on != null) {
                    acc = new org.eclipse.daanse.sql.statement.api.model.FromClause.FromJoin(
                            acc, org.eclipse.daanse.sql.statement.api.model.JoinKind.INNER,
                            itemList.get(idx), on);
                    placed.add(a);
                    treeOns.add(on);
                    progress = true;
                } else {
                    stillPending.add(idx);
                }
            }
            pending = stillPending;
        }
        for (FoldEdge e : edges) {
            if (treeOns.add(e.on())) {
                outConjuncts.add(e.on());
            }
        }
        if (pending.isEmpty()) {
            return acc;
        }
        java.util.List<org.eclipse.daanse.sql.statement.api.model.FromClause> product =
                new java.util.ArrayList<>();
        product.add(acc);
        for (Integer idx : pending) {
            product.add(itemList.get(idx));
        }
        return new org.eclipse.daanse.sql.statement.api.model.FromClause.FromProduct(product);
    }

    /**
     * The AGG counterpart of {@link #projectTargetLevels} — one target's projection in the
     * {@code addLevelMemberSql} agg branches, every non-all level root→depth through
     * {@link #projectAggLevel}. GROUP BY need is this hierarchy's own {@code isGroupByNeeded}; the
     * ORDER BY dedup set spans all targets; {@code projectParent=false} is the union arm (level
     * orders deferred and dropped by the caller).
     */
    static void projectAggTargetLevels(SelectStatementBuilder q, RolapLevel targetLevel,
            AggStar aggStar,
            java.util.Set<String> orderedColumns,
            java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders,
            boolean projectParent) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();
        int levelDepth = targetLevel.getDepth();
        boolean needsGroupBy = RolapUtil.isGroupByNeeded(hierarchy, levels, levelDepth);
        for (int i = 0; i <= levelDepth; i++) {
            RolapLevel level = levels.get(i);
            if (level.isAll()) {
                continue;
            }
            projectAggLevel(q, level, aggStar, needsGroupBy, orderedColumns, levelOrders, projectParent);
        }
    }

    /**
     * ONE level's agg projection — branch parity with the {@code addLevelMemberSql}
     * body (calls the SAME
     * {@code isLevelCollapsed}/{@code levelContainsMultipleColumns} predicates):
     * <ul>
     * <li>collapsed single-column — the {@code addAggColumnToSql} branch: the agg column
     *     ({@code toSqlExpression}, usage-prefix aware) with the star column's internal type,
     *     SELECT + UNCONDITIONAL GROUP BY ({@code addSelectGroupBy}), key ORDER BY deferred;</li>
     * <li>else the {@code targetExp} branch: key/caption projected from
     *     {@link AggJoinPlanner#levelTargetExpMap} (an identity entry projects the DIMENSION
     *     column — the {@code aggLevel == null} "no extra columns" drop rule leaves
     *     caption/ordinals/properties on the dimension), ordinals iterated on the LEVEL list with
     *     the recorder's key-equality check and mapped per ordinal (a non-key ordinal ORDERs
     *     before the key tiebreaker), properties per the recorder's RolapColumn-or-raw rule,
     *     GROUP BY gated on {@code needsGroupBy}.</li>
     * </ul>
     */
    private static void projectAggLevel(SelectStatementBuilder q, RolapLevel level, AggStar aggStar,
            boolean needsGroupBy,
            java.util.Set<String> orderedColumns,
            java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders,
            boolean projectParent) {
        RolapCubeLevel cubeLevel = (RolapCubeLevel) level;
        if (org.eclipse.daanse.rolap.common.member.SqlMemberSource.isLevelCollapsed(aggStar, cubeLevel)
                && !org.eclipse.daanse.rolap.common.member.SqlMemberSource
                        .levelContainsMultipleColumns(level)) {
            projectAggCollapsedColumn(q, cubeLevel, aggStar, orderedColumns, levelOrders);
            return;
        }
        projectAggSubstitutedLevel(q, level, aggStar, needsGroupBy, orderedColumns, levelOrders,
                projectParent);
    }

    /**
     * Null-tolerant emission-branch check for {@link #collapsedTupleLevelMembersSql} ONLY: a level
     * exposing NO ordinal/caption/property columns takes the single-column branch without any
     * further reads (the single-column branch reads nothing but the key/agg column, and the
     * delegation must stay behavior-preserving); a level with extra columns defers to the recorder
     * predicate for quirk parity (ANY non-empty ordinal list ⇒ multiple columns). The
     * recorder-loop counterpart {@link #projectAggLevel} always calls the recorder predicates directly.
     */
    private static boolean collapsedLevelMultipleColumns(RolapCubeLevel level) {
        List<? extends SqlExpression> ordinals = level.getOrdinalExps();
        SqlExpression captionExp = level.getCaptionExp();
        RolapProperty[] properties = level.getProperties();
        boolean anyExtra = (ordinals != null && !ordinals.isEmpty())
                || captionExp != null
                || (properties != null && properties.length > 0);
        return anyExtra
                && org.eclipse.daanse.rolap.common.member.SqlMemberSource.levelContainsMultipleColumns(level);
    }

    /**
     * The {@code addAggColumnToSql} branch: SELECT + GROUP BY the agg column (unconditionally —
     * {@code addSelectGroupBy}), ORDER BY key ASC (deferred; dropped on a union arm).
     */
    private static void projectAggCollapsedColumn(SelectStatementBuilder q, RolapCubeLevel level,
            AggStar aggStar,
            java.util.Set<String> orderedColumns,
            java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders) {
        RolapStar.Column starColumn = level.getStarKeyColumn();
        AggStar.Table.Column aggColumn = aggStar.lookupColumn(starColumn.getBitPosition());
        ProjectionRef ref = q.project(aggColumn.toSqlExpression(), starColumn.getInternalType(),
                null, "level key " + level.getUniqueName());
        q.groupOn(ref);
        TupleSqlMapper.orderOnce(levelOrders, orderedColumns, ref, aggColumn.getExpression(),
                TupleSqlMapper.sortSpec(SortingDirection.ASC));
    }

    /**
     * The {@code targetExp} branch of the recorder loop: every projection point substituted via
     * {@link AggJoinPlanner#levelTargetExpMap} (see {@link #projectAggLevel}'s javadoc for the
     * branch rules).
     */
    private static void projectAggSubstitutedLevel(SelectStatementBuilder q, RolapLevel level,
            AggStar aggStar, boolean needsGroupBy,
            java.util.Set<String> orderedColumns,
            java.util.List<java.util.Map.Entry<ProjectionRef, SortSpec>> levelOrders,
            boolean projectParent) {
        java.util.Map<SqlExpression, SqlExpression> targetExp =
                AggJoinPlanner.levelTargetExpMap(level, aggStar);
        SqlExpression keyExp = targetExp.get(level.getKeyExp());
        SqlExpression captionExp = targetExp.get(level.getCaptionExp());
        SqlExpression parentExp = level.getParentExp();
        if (parentExp != null && projectParent) {
            // Parent-child level (recorder: projected for LAST/ONLY regardless of collapsed —
            // only the FROM registration is collapse-gated). Raw parent expression, never mapped.
            ProjectionRef parentRef = q.project(JoinPlanner.expressionFor(parentExp),
                    level.getInternalType(), null, "parent key " + level.getUniqueName());
            if (needsGroupBy) {
                q.groupOn(parentRef);
            }
            SortSpec parentSort = TupleSqlMapper.sortSpecNullsFirst(SortingDirection.ASC);
            String npv = level.getNullParentValue();
            if (npv != null && !"null".equalsIgnoreCase(npv)) {
                parentSort = parentSort.withNullSortValue(npv, level.getDatatype());
            }
            levelOrders.add(java.util.Map.entry(parentRef, parentSort));
        }
        ProjectionRef keyRef = q.project(JoinPlanner.expressionFor(keyExp), level.getInternalType(),
                null, "level key " + level.getUniqueName());
        if (needsGroupBy) {
            q.groupOn(keyRef);
        }
        if (captionExp != null) {
            ProjectionRef captionRef = q.project(JoinPlanner.expressionFor(captionExp), null, null,
                    "level caption " + level.getUniqueName());
            if (needsGroupBy) {
                q.groupOn(captionRef);
            }
        }
        List<? extends SqlExpression> ordinals = level.getOrdinalExps();
        if (ordinals != null && !ordinals.isEmpty()) {
            for (SqlExpression ordinalExp : ordinals) {
                // The recorder compares the LEVEL expressions (keyExp.equals(ordinalExp)) and
                // projects/orders the MAPPED ones.
                if (level.getKeyExp().equals(ordinalExp)) {
                    TupleSqlMapper.orderOnce(levelOrders, orderedColumns, keyRef, keyExp,
                            TupleSqlMapper.sortSpec(ordinalExp.getSortingDirection()));
                } else {
                    SqlExpression oe = targetExp.get(ordinalExp);
                    ProjectionRef ref = q.project(JoinPlanner.expressionFor(oe), null, null,
                            "level ordinal " + level.getUniqueName());
                    if (needsGroupBy) {
                        q.groupOn(ref);
                    }
                    TupleSqlMapper.orderOnce(levelOrders, orderedColumns, ref, oe,
                            TupleSqlMapper.sortSpec(ordinalExp.getSortingDirection()));
                    // the level key is a tiebreaker after a non-key ordinal.
                    TupleSqlMapper.orderOnce(levelOrders, orderedColumns, keyRef, keyExp, TupleSqlMapper.sortSpec(SortingDirection.ASC));
                }
            }
        } else {
            TupleSqlMapper.orderOnce(levelOrders, orderedColumns, keyRef, keyExp, TupleSqlMapper.sortSpec(SortingDirection.ASC));
        }
        for (RolapProperty property : level.getProperties()) {
            SqlExpression propExp = targetExp.get(property.getExp());
            // The recorder's rule: a mapped plain column projects (with the agg/level table alias);
            // anything else falls back to the raw property expression.
            SqlExpression toProject = (propExp instanceof org.eclipse.daanse.rolap.element.RolapColumn)
                    ? propExp : property.getExp();
            ProjectionRef ref = q.project(JoinPlanner.expressionFor(toProject), null, null,
                    "member property " + property.getName());
            if (!property.dependsOnLevelValue()) {
                q.groupOn(ref);
            }
        }
        if (needsGroupBy) {
            q.completeNonAggregatesGroupBy();
        }
    }
}

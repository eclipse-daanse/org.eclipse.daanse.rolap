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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.util.SqlExpressionResolver;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapProperty;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.NullOrder;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.SortDirection;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;

/**
 * Builds the member-enumeration SELECT for a single {@link RolapLevel} with the generic, dialect-free
 * statement builder.
 * <p>
 * Scope ({@link #supports}): plain-column key/caption/ordinal/property expressions, no parent-child
 * level, no collapsed-aggregate join, no constraint. Emission order:
 * <ol>
 *   <li>FROM = {@link RelationFromMapper} over the hierarchy's relation;</li>
 *   <li>SELECT key, then the caption column (if any), then each ordinal expression that differs
 *       from the key, then properties;</li>
 *   <li>GROUP BY every selected column when {@code needsGroupBy};</li>
 *   <li>ORDER BY each ordinal (or the key when there are none / when an ordinal equals the key),
 *       nulls last, per the expression's sorting direction.</li>
 * </ol>
 */
public final class MemberSqlMapper {

    private MemberSqlMapper() {
    }

    /** True if {@link #childMemberSql} can build this level (see the class scope). */
    public static boolean supports(RolapLevel level) {
        if (level.isParentChild()) {
            return false;
        }
        org.eclipse.daanse.rolap.element.RolapHierarchy h =
                (org.eclipse.daanse.rolap.element.RolapHierarchy) level.getHierarchy();
        if (h.getRelation() == null || !RelationFromMapper.supports(h.getRelation())) {
            return false;
        }
        if (!isPlainColumn(level.getKeyExp())) {
            return false;
        }
        if (level.hasCaptionColumn() && !isPlainColumn(level.getCaptionExp())) {
            return false;
        }
        for (SqlExpression oe : level.getOrdinalExps()) {
            if (!isPlainColumn(oe)) {
                return false;
            }
        }
        for (RolapProperty p : level.getProperties()) {
            if (!isPlainColumn(p.getExp())) {
                return false;
            }
        }
        return true;
    }

    /**
     * A RELAXED {@link #supports}: as {@code supports}, but
     * the plain-column gates on key / caption / ordinal / property are dropped, so a level whose
     * caption / ordinal / property (or key) is a COMPUTED expression is accepted too. Still rejects a
     * parent-child level and a relation the {@link RelationFromMapper} cannot build — the emission
     * ({@link #projectLevel} via {@link JoinPlanner#expressionFor}) already carries a computed
     * expression as a {@code RawVariant}, so those are the only remaining hard limits. This is the
     * member-children counterpart of {@link TupleSqlMapper#supportsAllowingExpressions} (which already
     * routes the constraint-less computed level-members read onto the builder).
     * <p>
     * The route gate of the computed-level member-children read: {@code SqlMemberSource} routes on
     * this check and builds on the mapper.
     */
    public static boolean supportsComputed(RolapLevel level) {
        if (level.isParentChild()) {
            return false;
        }
        org.eclipse.daanse.rolap.element.RolapHierarchy h =
                (org.eclipse.daanse.rolap.element.RolapHierarchy) level.getHierarchy();
        return h.getRelation() != null && RelationFromMapper.supports(h.getRelation());
    }

    /**
     * The exotic-relation gate: a level whose hierarchy relation is a SINGLE
     * view ({@code SqlSelectSource}) or inline table ({@code InlineTableSource}) — no join. These render
     * the view/inline directly ({@code [Warehouse].[Warehouse ID]}, {@code [Store Type]},
     * {@code [Alternative Promotion]}, {@code [Shared Alternative Promotion]}), so the
     * dimension-only member-children enumeration is built on the mapper. A {@code JoinSource} that merely
     * CONTAINS a view/inline is EXCLUDED: its level key lives on the
     * joined side the per-level subset FROM drops (e.g.
     * {@code [Store].[Store Country]} = store JOIN inline-nation), so it stays on the recorder.
     */
    public static boolean supportsExoticSingleRelation(RolapLevel level) {
        if (level.isParentChild()) {
            return false;
        }
        org.eclipse.daanse.rolap.element.RolapHierarchy h =
                (org.eclipse.daanse.rolap.element.RolapHierarchy) level.getHierarchy();
        return h.getRelation() != null && RelationFromMapper.isSingleViewOrInline(h.getRelation());
    }

    /**
     * The inline-join gate: a non-parent-child level whose
     * hierarchy relation is a JOIN tree of plain tables with at least ONE inline-table leaf
     * ({@code InlineTableSource}) — the shape the exotic single-relation gate deliberately excludes
     * ({@code [Store].[Store Country]} = store JOIN inline-nation,
     * InlineTableTest#testInlineTableSnowflake). The FROM model composes a {@code FromInline} as a
     * join operand ({@code FromJoin(left, INNER, FromInline, on)}) and the renderer renders it like
     * any derived table, so the per-level subset FROM reaches the inline leaf now that
     * {@link RelationFromMapper}'s alias collection includes inline leaves. {@code SqlMemberSource}
     * routes the dimension-only member-children enumeration through it.
     */
    public static boolean supportsInlineJoinRelation(RolapLevel level) {
        if (level.isParentChild()) {
            return false;
        }
        org.eclipse.daanse.rolap.element.RolapHierarchy h =
                (org.eclipse.daanse.rolap.element.RolapHierarchy) level.getHierarchy();
        return h.getRelation()
                instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join
                && tableOrInlineLeaves(join) && containsInlineLeaf(join);
    }

    /** Every leaf of the relation tree is a plain table or an inline table (no view). */
    private static boolean tableOrInlineLeaves(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            return tableOrInlineLeaves(join.getLeft().getSource())
                    && tableOrInlineLeaves(join.getRight().getSource());
        }
        return relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource
                || relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource;
    }

    /** The relation tree contains at least one inline-table leaf. */
    private static boolean containsInlineLeaf(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            return containsInlineLeaf(join.getLeft().getSource())
                    || containsInlineLeaf(join.getRight().getSource());
        }
        return relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource;
    }

    /**
     * The member-enumeration SELECT for {@code level} (see {@link #supports}), optionally
     * constrained to a parent member's children. {@code memberConstraint} (see
     * {@link JoinPlanner#memberKeyConstraint}) is added to {@code WHERE} before the join predicates
     * (the {@code [constraint, joins]} order).
     */
    public static SelectStatement childMemberSql(RolapLevel level, boolean needsGroupBy,
            Optional<Predicate> memberConstraint) {
        return childMemberSql(level, needsGroupBy, memberConstraint, false);
    }

    /**
     * As {@link #childMemberSql(RolapLevel, boolean, Optional)} but, when {@code splitConjuncts} is set,
     * the {@code memberConstraint}'s TOP-LEVEL {@code AND} operands are emitted as SEPARATE WHERE clauses
     * (a nested {@code AND} — the parenthesised multi-column parent-key group — stays one grouped
     * conjunct). This reproduces a {@link org.eclipse.daanse.rolap.common.constraint.ChildByNameConstraint}
     * recorder form, whose parent-key group ({@code addMemberConstraint}) and by-name filter
     * ({@code addMemberLevelConstraintOps}) are two separate {@code WHERE} conjuncts
     * ({@code (parent) and UPPER(name) = UPPER('x')}). The default ({@code false}) keeps the whole
     * predicate as one conjunct — the form the plain parent-key
     * {@link org.eclipse.daanse.rolap.common.constraint.DefaultMemberChildrenConstraint} enumeration needs
     * (a bare parent-key {@code AND} renders as one grouped {@code (a and b and c)}).
     */
    public static SelectStatement childMemberSql(RolapLevel level, boolean needsGroupBy,
            Optional<Predicate> memberConstraint, boolean splitConjuncts) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        // FROM the per-level snowflake SUBSET (the same subset TupleSqlMapper uses) — NOT the whole
        // relation. The whole relation over-joins a near-table level to the far table, dropping childless
        // snowflake members; the subset honors FilterChildlessSnowflakeMembers and falls back to the whole
        // relation for computed-column levels (no table alias to subset by).
        // Keep the snowflake as the renderer's ANSI JOIN…ON tree (the same form the level-members path
        // emits) instead of flattening to comma + a join predicate in WHERE — the SQL-assert tests
        // (EffectiveMemberCacheTest) baseline the ANSI form; a single-table subset is just one table
        // either way, so non-snowflake dimensions are unaffected.
        FromClause levelFrom = TupleSqlMapper.fromForLevels(level, false);
        // Base-FROM provenance: the dimension + the child level whose relation anchors the FROM
        // (rendered only when comments are on; never part of the executed SQL).
        q.from(From.commentBase(levelFrom,
                TupleSqlMapper.baseTableComment(level, From.baseAlias(levelFrom))));
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        q.header("children at " + level.getUniqueName());
        q.footerComment("member children (dimension only)");
        memberConstraint.ifPresent(p -> {
            // A ChildByNameConstraint carries [parent-key group, by-name filter] as a top-level AND; the
            // recorder emits them as two separate conjuncts (addMemberConstraint + addMemberLevelConstraint),
            // so split here to match. A plain parent-key enumeration's bare AND stays one grouped conjunct.
            if (splitConjuncts && p instanceof Predicate.And and) {
                and.operands().forEach(op -> q.where(op, "member constraint"));
            } else {
                q.where(p, "parent member key");
            }
        });
        projectLevel(q, level, needsGroupBy);
        return q.build();
    }

    /**
     * The context-constrained variant: the level members joined to the fact table (and any context
     * dimension tables) so a NON-EMPTY / context restriction can be applied — the builder counterpart
     * of {@code addLevelMemberSql} + {@code SqlContextConstraint.addContextConstraint}. The reference
     * shape is DIMENSION-rooted ANSI: FROM is the hierarchy relation (its snowflake JOIN…ON tree
     * intact), the fact joins INTO it on the dimension's own fact edge ({@code fact.fk = dim.key}),
     * then each context table joins to the fact; WHERE is {@code where} (the translated context
     * predicate, AND-combined with the parent-key constraint), each top-level conjunct on its own.
     * The fact table is derived from the contributed {@code joinTables} (each star table knows its
     * star). A shape with no resolvable dimension→fact edge falls back to the comma-join form
     * (guarded — a divergence falls back to the reference query).
     */
    public static SelectStatement childMemberSql(RolapLevel level, boolean needsGroupBy,
            Optional<Predicate> where, List<RolapStar.Table> joinTables) {
        org.eclipse.daanse.rolap.element.RolapHierarchy hierarchy =
                (org.eclipse.daanse.rolap.element.RolapHierarchy) level.getHierarchy();
        RolapStar.Table fact = factFor(level, joinTables);
        Set<String> dimAliases = RelationFromMapper.tableAliases(hierarchy.getRelation());
        FromClause relationFrom = RelationFromMapper.from(hierarchy.getRelation());
        // The dimension's fact-adjacent table (the fact's FK references it): from the cube level's
        // star key; a plain (shared) level derives it from a contributed join table that is part of
        // the dimension relation.
        RolapStar.Table factAdjacent = null;
        if (level instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel cl
                && cl.getStarKeyColumn() != null) {
            factAdjacent = TupleSqlMapper.factAdjacentTable(cl.getStarKeyColumn().getTable(), fact);
        }
        if (factAdjacent == null) {
            for (RolapStar.Table t : joinTables) {
                if (dimAliases.contains(t.getAlias())) {
                    factAdjacent = TupleSqlMapper.factAdjacentTable(t, fact);
                    if (factAdjacent != null) {
                        break;
                    }
                }
            }
        }
        // A degenerate dimension whose relation IS the fact needs no fact join at all.
        boolean needFactJoin = !dimAliases.contains(fact.getAlias());
        if (needFactJoin && factAdjacent == null) {
            return commaJoinChildMemberSql(level, needsGroupBy, where, joinTables, fact, dimAliases,
                    relationFrom);
        }

        SelectStatementBuilder q = SelectStatementBuilder.create();
        // Base-FROM provenance: the dimension relation is the FROM root (the fact joins into it).
        q.from(From.commentBase(relationFrom,
                TupleSqlMapper.baseTableComment(level, From.baseAlias(relationFrom))));
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        q.header("children at " + level.getUniqueName());
        q.footerComment("member children (fact join)");
        Set<RolapStar.Table> emitted = new LinkedHashSet<>();
        if (needFactJoin) {
            // The fact joins INTO the dimension root on the dimension's own edge — the fact is the
            // joined table, the dimension stays the FROM root (the reference add order).
            for (Predicate on : JoinPlanner.joinChainFor(factAdjacent, fact, emitted)) {
                q.join(org.eclipse.daanse.sql.statement.api.model.JoinKind.INNER,
                        JoinPlanner.tableFromClause(fact), on, "fact join (context)");
            }
        }
        // Context tables join to the fact (parent-first, deduped); a table already part of the
        // dimension relation contributes no extra join. The fact itself yields no step.
        for (RolapStar.Table t : joinTables) {
            if (!dimAliases.contains(t.getAlias())) {
                for (JoinPlanner.JoinStep s : JoinPlanner.joinStepsFor(t, fact, emitted)) {
                    q.join(org.eclipse.daanse.sql.statement.api.model.JoinKind.INNER,
                            JoinPlanner.tableFromClause(s.table()), s.on(), "fact join (context)");
                }
            }
        }
        // Each context restriction is its own WHERE conjunct, so split a top-level
        // AND into separate conjuncts; a nested AND (e.g. the multi-part parent key) stays grouped.
        where.ifPresent(p -> {
            if (p instanceof Predicate.And and) {
                and.operands().forEach(op -> q.where(op, "context"));
            } else {
                q.where(p, "context");
            }
        });
        projectLevel(q, level, needsGroupBy);
        return q.build();
    }

    /**
     * Comma-join fallback of the fact-join {@link #childMemberSql(RolapLevel, boolean, Optional, List,
     * boolean)} overload for shapes with no resolvable dimension→fact edge: FROM lists the relation,
     * the fact and the context tables as comma items, every star join is a WHERE conjunct. Guarded
     * use only — the ANSI form is the primary shape, so this is reached only via the guard's fallback.
     */
    private static SelectStatement commaJoinChildMemberSql(RolapLevel level, boolean needsGroupBy,
            Optional<Predicate> where, List<RolapStar.Table> joinTables, RolapStar.Table fact,
            Set<String> dimAliases, FromClause relationFrom) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        List<FromClause> items = new ArrayList<>();
        // Base-FROM provenance: the dimension relation is the FROM base here (the fact is a comma item).
        items.add(From.commentBase(relationFrom,
                TupleSqlMapper.baseTableComment(level, From.baseAlias(relationFrom))));
        items.add(JoinPlanner.tableFromClause(fact));
        Set<RolapStar.Table> referenced = new LinkedHashSet<>();
        Set<String> addedAliases = new java.util.HashSet<>();
        addedAliases.add(fact.getAlias());
        for (RolapStar.Table t : joinTables) {
            referenced.add(t);
            if (!dimAliases.contains(t.getAlias()) && addedAliases.add(t.getAlias())) {
                items.add(From.table(t.getTableName(), TableAlias.of(t.getAlias())));
            }
        }
        q.from(new FromClause.FromProduct(items));
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        q.header("children at " + level.getUniqueName());
        q.footerComment("member children (fact join)");

        // Star join predicates as comma-join WHERE conjuncts (parent-first, deduped) — each referenced
        // table's chain to the fact.
        JoinPlanner.joinPredicates(fact, referenced).forEach(p -> q.where(p, "fact join (context)"));
        where.ifPresent(p -> {
            if (p instanceof Predicate.And and) {
                and.operands().forEach(op -> q.where(op, "context"));
            } else {
                q.where(p, "context");
            }
        });

        projectLevel(q, level, needsGroupBy);
        return q.build();
    }

    /**
     * As the fact-join {@link #childMemberSql(RolapLevel, boolean, Optional, List, boolean)} overload but
     * with the context constraint's per-column {@code (table, predicate)} pairs so the WHERE is
     * interleaved exactly like the {@code addMemberConstraint + addLevelConstraint} sequence:
     * <ol>
     *   <li>the queried dimension relation's own (snowflake) join(s);</li>
     *   <li>per context column in contribution order: its join chain to the fact (registered once)
     *       and {@code [its value-constraint(s)]} — these already include the parent's level key, since
     *       the parent key is added as a context column (after {@code setContext(parent)}) and the
     *       explicit {@code addMemberConstraint} duplicate dedupes away;</li>
     *   <li>finally the queried child level -> fact join ({@code addLevelConstraint}), registered last
     *       and deduped against the joins already registered (a no-op for single-table dimensions).</li>
     * </ol>
     * The rendered JOIN order is the breadth-attach fold over that registration order (see
     * {@link #foldJoinSteps}), matching how the reference assembler folds its FROM items.
     * Empty {@code orderedPredicates} delegates to the grouped overload (guarded, non-authoritative).
     */
    public static SelectStatement childMemberSql(RolapLevel level, boolean needsGroupBy,
            Optional<Predicate> where, List<RolapStar.Table> joinTables,
            List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate> orderedPredicates,
            Optional<Predicate> memberKeyGroup) {
        if (orderedPredicates.isEmpty()) {
            return childMemberSql(level, needsGroupBy, where, joinTables);
        }
        org.eclipse.daanse.rolap.element.RolapHierarchy hierarchy =
                (org.eclipse.daanse.rolap.element.RolapHierarchy) level.getHierarchy();
        RolapStar.Table fact = factFor(level, joinTables);

        // Separate the star joins (→ ANSI JOIN…ON) from the value constraints (→ WHERE). The context
        // dimension / queried-dimension star tables arrive as innerJoin steps, not FROM comma items.
        // Join REGISTRATION order (the reference's FROM add order): per context column its table then
        // its snowflake ancestors; the parent's level key is already among these context columns; the
        // queried child level's own chain (addLevelConstraint) last, deduped (no-op single-table).
        LinkedHashSet<RolapStar.Table> pendingJoins = new LinkedHashSet<>();
        List<Predicate> wheres = new ArrayList<>();
        // WHERE: per context column [its value constraint]; then the parenthesised parent-key group
        // (addMemberConstraint) as a trailing conjunct.
        for (org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate cp
                : orderedPredicates) {
            addChainSelfFirst(pendingJoins, cp.table(), fact);
            wheres.add(cp.predicate());
        }
        memberKeyGroup.ifPresent(wheres::add);
        if (level instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel cubeLevel
                && cubeLevel.getStarKeyColumn() != null) {
            addChainSelfFirst(pendingJoins, cubeLevel.getStarKeyColumn().getTable(), fact);
        }
        // The rendered join order is the reference assembler's fold: breadth-attach from the fact —
        // repeatedly scan the registered tables in order, joining each whose parent is already in the
        // tree. A fact-adjacent table attaches on the first pass; a deeper snowflake table (e.g.
        // product_class) waits until its parent is placed, so it lands AFTER later-registered
        // fact-adjacent tables — exactly how the reference renders a cross-dimension snowflake mix.
        List<JoinPlanner.JoinStep> joinSteps = foldJoinSteps(pendingJoins, fact);

        // FROM = the fact (the join root) as a comma item, plus any queried-dimension relation leaf
        // tables needed only for the SELECT projection that are NOT already brought in as a join step.
        Set<String> joinedAliases = new java.util.HashSet<>();
        for (JoinPlanner.JoinStep s : joinSteps) {
            joinedAliases.add(s.table().getAlias());
        }
        List<FromClause> items = new ArrayList<>();
        Set<String> addedAliases = new java.util.HashSet<>();
        // Base-FROM provenance: the FACT anchors this FROM (the dimension tables arrive as join steps).
        String factComment = (level instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel cl
                && cl.getCube() != null)
                        ? "fact table (cube " + cl.getCube().getName() + ")"
                        : "fact table " + fact.getTableName();
        // tableFromClause handles a view-backed fact (no table name): it renders the view relation
        // (`(select …) as "alias"`) instead of an empty table reference.
        items.add(From.commentBase(JoinPlanner.tableFromClause(fact), factComment));
        addedAliases.add(fact.getAlias());
        List<FromClause> relLeaves = new ArrayList<>();
        flattenRelation(RelationFromMapper.from(hierarchy.getRelation()), relLeaves);
        for (FromClause leaf : relLeaves) {
            String a = leafAlias(leaf);
            if (a != null && !joinedAliases.contains(a) && addedAliases.add(a)) {
                items.add(leaf);
            }
        }
        // Single-table dimensions bring every table in as a join step, leaving only the fact here —
        // a one-item FROM is that item itself (FromProduct requires two).
        FromClause from = items.size() == 1 ? items.get(0) : new FromClause.FromProduct(items);

        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(from);
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        q.header("children at " + level.getUniqueName());
        q.footerComment("member children (context constrained)");
        for (JoinPlanner.JoinStep s : joinSteps) {
            q.join(org.eclipse.daanse.sql.statement.api.model.JoinKind.INNER,
                    JoinPlanner.tableFromClause(s.table()), s.on(), "fact join (context)");
        }
        wheres.forEach(p -> q.where(p, "context"));
        projectLevel(q, level, needsGroupBy);
        return q.build();
    }

    /**
     * The collapsed single-column aggStar member-children SELECT — the builder counterpart of the
     * {@code makeChildMemberSql} collapsed branch (a level whose key is a single column present IN an
     * aggregate table, so no dimension join is needed). Projects the AGGREGATE column
     * ({@code aggStar.lookupColumn(bitPos).toSqlExpression()}) from the single agg table, optionally
     * grouped, ordered ASC nulls-last; {@code where} (the agg-substituted context + parent-key
     * predicate) is split into per-conjunct WHERE clauses exactly like the other overloads (a nested
     * {@code And} — the parenthesised parent-key group — stays grouped).
     * <p>
     * Builds the collapsed single-column aggStar member-children read directly.
     */
    public static SelectStatement collapsedSingleColumnSql(RolapLevel level,
            org.eclipse.daanse.rolap.common.aggmatcher.AggStar aggStar, boolean needsGroupBy,
            Optional<Predicate> where) {
        RolapStar.Column starColumn =
                ((org.eclipse.daanse.rolap.element.RolapCubeLevel) level).getStarKeyColumn();
        int bitPos = starColumn.getBitPosition();
        org.eclipse.daanse.rolap.common.aggmatcher.AggStar.Table.Column aggColumn =
                aggStar.lookupColumn(bitPos);
        // The dialect-free agg column node ("aggAlias"."aggCol") — the SELECT / GROUP BY / ORDER BY
        // expression AND the FROM alias source.
        org.eclipse.daanse.sql.statement.api.expression.SqlExpression node = aggColumn.toSqlExpression();
        SelectStatementBuilder q = SelectStatementBuilder.create();
        // FROM the single agg table (collapsed = the agg column's table IS the agg fact table).
        q.from(From.table(aggColumn.getTable().getName(),
                TableAlias.of(SqlExpressionResolver.getTableAlias(aggColumn.getExpression()))));
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        q.header("children at " + level.getUniqueName());
        q.footerComment("member children (aggstar collapsed)");
        // Each recorded context restriction / the parent-key group is its own WHERE conjunct, so split
        // a top-level And; a nested And (the parenthesised parent key) stays grouped.
        where.ifPresent(p -> {
            if (p instanceof Predicate.And and) {
                and.operands().forEach(op -> q.where(op, "context"));
            } else {
                q.where(p, "context");
            }
        });
        ProjectionRef ref = q.project(node, starColumn.getInternalType(), null,
                "level key " + level.getUniqueName());
        if (needsGroupBy) {
            q.groupOn(ref);
        }
        // ASC, nulls-last (SortSpec(ASC, nullable=true, NullOrder.LAST)).
        q.orderOn(ref, sortSpec(SortingDirection.ASC));
        if (needsGroupBy) {
            q.completeNonAggregatesGroupBy();
        }
        return q.build();
    }

    /**
     * The AGGREGATE-star member-children SELECT — for the
     * {@code SqlMemberSource.makeChildMemberSql} aggStar branches that survive the collapsed
     * single-column early return (that one is {@link #collapsedSingleColumnSql}): the child level is
     * either NOT collapsed on {@code aggStar} (the fact-join shapes — dimension FROM, agg fact
     * joined in via the level's agg-column table chain) or collapsed WITH dimension extras
     * (caption/ordinal/property columns on the dimension, key tied to the agg column,
     * parent levels walked for uniqueness).
     * <p>
     * The emission follows the registration sequence:
     * FROM items in registration order, join edges breadth-attached left-deep by the assembler,
     * an edge whose two tables are both already placed folds to a WHERE conjunct — see
     * {@code ContributionAssembler.buildFromClause}:
     * <ol>
     *   <li>per {@code orderedAggPredicates} entry: its agg table chain
     *       ({@link AggJoinPlanner#aggTableChain} — FROM self-first, conditions replayed in reverse,
     *       a {@code null} table skipped per the {@code AggPlan} contract) and its predicate as a
     *       WHERE conjunct ({@code addMemberConstraint}/context order);</li>
     *   <li>the queried child level's key-expression relation subset
     *       ({@code RolapHierarchy.addToFrom} semantics: {@link RelationFromMapper#relationSubset},
     *       whole-relation fallback for a computed key — a computed key projects through the
     *       {@code RawVariant} channel of {@link JoinPlanner#expressionFor}, which is why the
     *       route gate is {@link #supportsComputed}, not the strict {@link #supports});</li>
     *   <li>the level's own agg join ({@code addLevelConstraint} →
     *       {@code joinLevelTableToFactTable} agg arm): the agg table chain of the level's star-key
     *       agg column — for a collapsed level that is the agg fact itself (usually a FROM no-op);</li>
     *   <li>collapsed only: the key's INVERSE relation subset
     *       ({@code RolapHierarchy.addToFromInverse} / {@link RelationFromMapper#relationSubsetInverse}),
     *       the {@code levelKey = aggColumn} edge ({@link AggJoinPlanner#dimToAggEdge}), then the
     *       PARENT-WALK: while the parent level is neither {@code all} nor the CHILD level unique,
     *       the parent's inverse subset + its own {@code parentKey = aggColumn} edge — a walk edge
     *       whose tables are both already placed is a duplicate edge that folds to
     *       WHERE (deduped by predicate value against the tree ONs);</li>
     *   <li>non-collapsed only: the caption / ordinal / property relation subsets (the recorder
     *       adds them to FROM only when the level is not collapsed);</li>
     *   <li>WHERE order: the context conjuncts of step 1, then {@code where} (the grouped channel —
     *       a top-level {@code AND} split into separate conjuncts, a nested {@code AND} kept
     *       grouped), then {@code memberKeyGroup} (the parenthesised parent-key form, appended after
     *       the context like the authoritative collapsed path), then the folded duplicate edges.</li>
     * </ol>
     * An EMPTY {@code orderedAggPredicates} + empty {@code where} + empty {@code memberKeyGroup} is
     * a VALID unconstrained read and emits NO WHERE at all. {@code needsGroupBy} follows the star variant exactly
     * (every projected level column grouped, {@code completeNonAggregatesGroupBy} for restrictive
     * dialects). NO {@code Dialect} parameter: every node built here is dialect-free (computed
     * expressions travel as {@code RawVariant} maps) — the dialect enters only at the render
     * seam ({@code SqlBuildGuard.build}), per the confinement invariant.
     * <p>
     * Called by the children router ({@code SqlMemberSource.makeChildMemberSql}); the caller
     * must only route reads that actually join the fact (an
     * {@code isJoinRequired()=false} shape never reaches the agg branches).
     */
    public static SelectStatement aggChildMemberSql(RolapLevel level,
            org.eclipse.daanse.rolap.common.aggmatcher.AggStar aggStar,
            boolean needsGroupBy, Optional<Predicate> where,
            List<org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate> orderedAggPredicates,
            Optional<Predicate> memberKeyGroup) {
        org.eclipse.daanse.rolap.element.RolapHierarchy hierarchy =
                (org.eclipse.daanse.rolap.element.RolapHierarchy) level.getHierarchy();
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation =
                hierarchy.getRelation();

        AggFromState st = new AggFromState();
        List<Predicate> wheres = new ArrayList<>();
        // 1. Context: per agg-substituted column its table chain, its restriction a WHERE conjunct.
        for (org.eclipse.daanse.rolap.common.sql.AggPlan.AggColumnPredicate cp : orderedAggPredicates) {
            if (cp.table() != null) {
                registerAggChain(st, cp.table());
            }
            wheres.add(cp.predicate());
        }
        // 2. The child level's key relation subset (whole relation for a computed key).
        registerRelation(st, relationSubsetFor(relation, level.getKeyExp()));
        // The same collapsed test SqlMemberSource.isLevelCollapsed performs (not called directly:
        // member -> sqlbuild imports this class, the reverse call would cycle the packages).
        org.eclipse.daanse.rolap.common.aggmatcher.AggStar.Table.Column levelAggColumn =
                aggStar.lookupColumn(((org.eclipse.daanse.rolap.element.RolapCubeLevel) level)
                        .getStarKeyColumn().getBitPosition());
        boolean levelCollapsed = levelAggColumn.getTable()
                instanceof org.eclipse.daanse.rolap.common.aggmatcher.AggStar.FactTable;
        // 3. The level's own agg join (joinLevelTableToFactTable agg arm) — for a collapsed level
        // the chain is the agg fact alone (a FROM no-op when the context already registered it).
        registerAggChain(st, levelAggColumn.getTable());
        if (levelCollapsed) {
            // 4. Collapsed: inverse subset, the dimKey = aggColumn edge, then the parent walk.
            registerRelation(st, relationSubsetInverseFor(relation, level.getKeyExp()));
            addAggEdge(st, level.getKeyExp(), levelAggColumn,
                    AggJoinPlanner.dimToAggEdge((org.eclipse.daanse.rolap.element.RolapCubeLevel) level, aggStar));
            // The CHILD level's uniqueness is evaluated once; the walk stops at the first all level
            // (or immediately for a unique child).
            boolean isUnique = level.isUnique();
            org.eclipse.daanse.rolap.element.RolapCubeLevel parentLevel =
                    (org.eclipse.daanse.rolap.element.RolapCubeLevel) level.getParentLevel();
            while (parentLevel != null && !parentLevel.isAll() && !isUnique) {
                registerRelation(st, relationSubsetInverseFor(relation, parentLevel.getKeyExp()));
                org.eclipse.daanse.rolap.common.aggmatcher.AggStar.Table.Column parentAggColumn =
                        aggStar.lookupColumn(parentLevel.getStarKeyColumn().getBitPosition());
                addAggEdge(st, parentLevel.getKeyExp(), parentAggColumn,
                        AggJoinPlanner.dimToAggEdge(parentLevel, aggStar));
                parentLevel = parentLevel.getParentLevel();
            }
        } else {
            // 5. Non-collapsed: caption / ordinal / property subsets join the dimension side in.
            if (level.hasCaptionColumn()) {
                registerRelation(st, relationSubsetFor(relation, level.getCaptionExp()));
            }
            for (SqlExpression ordinalExp : level.getOrdinalExps()) {
                registerRelation(st, relationSubsetFor(relation, ordinalExp));
            }
            for (RolapProperty property : level.getProperties()) {
                registerRelation(st, relationSubsetFor(relation, property.getExp()));
            }
        }
        // 6. The grouped where channel (split like every other overload) and the parent-key group.
        where.ifPresent(p -> {
            if (p instanceof Predicate.And and) {
                wheres.addAll(and.operands());
            } else {
                wheres.add(p);
            }
        });
        memberKeyGroup.ifPresent(wheres::add);

        SelectStatementBuilder q = SelectStatementBuilder.create();
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        q.header("children at " + level.getUniqueName());
        q.footerComment("member children (aggstar join)");
        foldAggFrom(st, q, level);
        wheres.forEach(p -> q.where(p, "context"));
        // Duplicate edges — both tables already placed when the edge arrived — fold to WHERE, in
        // edge order, deduped by predicate value against the tree ONs.
        for (AggFromState.Edge e : st.edges) {
            if (st.treeOns.add(e.on())) {
                q.where(e.on(), "agg join");
            }
        }
        projectLevel(q, level, needsGroupBy);
        return q.build();
    }

    /** Registration + fold state of {@link #aggChildMemberSql}: the FROM items / {@code JoinEdge}s
     *  (see {@code ContributionAssembler}). */
    private static final class AggFromState {
        /** One undirected join edge between two FROM aliases (schema-traversal order). */
        record Edge(String a, String b, Predicate on) {
        }

        final java.util.LinkedHashMap<String, FromClause> fromItems = new java.util.LinkedHashMap<>();
        final List<Edge> edges = new ArrayList<>();
        /** ONs used by the FROM tree (and WHERE-folded edges) — the duplicate-edge dedup set. */
        final Set<Predicate> treeOns = new LinkedHashSet<>();

        boolean registerFrom(String alias, FromClause item) {
            return fromItems.putIfAbsent(alias, item) == null;
        }
    }

    /**
     * The agg-table chain registration (as {@code AggStar.Table.addToFrom(query, false, true)}):
     * FROM items self-first, join conditions replayed in REVERSE (parent-first) per the
     * {@link AggJoinPlanner#aggTableChain} contract — each condition links a chain table to its
     * parent (the next chain element).
     */
    private static void registerAggChain(AggFromState st,
            org.eclipse.daanse.rolap.common.aggmatcher.AggStar.Table table) {
        List<AggJoinPlanner.AggJoinEdge> chain = AggJoinPlanner.aggTableChain(table);
        for (AggJoinPlanner.AggJoinEdge e : chain) {
            st.registerFrom(e.fromAlias(), e.from());
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            if (chain.get(i).on() != null) {
                st.edges.add(new AggFromState.Edge(chain.get(i).fromAlias(),
                        chain.get(i + 1).fromAlias(), chain.get(i).on()));
            }
        }
    }

    /**
     * A relation (subset) flattened the way {@code addFrom} does: leaves
     * left-to-right as separate FROM items, each join's {@code ON} tracked as an edge — inner joins
     * before outer (the {@code addJoin} recursion order) and only when a side is NEW (a re-registered
     * subset adds no duplicate edge, mirroring {@code addJoin}'s {@code addLeft || addRight} guard).
     */
    private static void registerRelation(AggFromState st,
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            int before = st.fromItems.size();
            registerRelation(st, join.getLeft().getSource());
            registerRelation(st, join.getRight().getSource());
            if (st.fromItems.size() > before) {
                st.edges.add(new AggFromState.Edge(
                        org.eclipse.daanse.rolap.common.util.JoinUtil.getLeftAlias(join),
                        org.eclipse.daanse.rolap.common.util.JoinUtil.getRightAlias(join),
                        RelationFromMapper.joinOn(join)));
            }
        } else {
            st.registerFrom(
                    org.eclipse.daanse.rolap.common.util.RelationUtil.getAlias(relation),
                    RelationFromMapper.from(relation));
        }
    }

    /** {@code RolapHierarchy.addToFrom(query, expression)} subset semantics: the Filter-aware
     *  smallest subset for the expression's table, whole relation for a computed expression (no
     *  table alias) or when no subset resolves. */
    private static org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relationSubsetFor(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
            SqlExpression expression) {
        String alias = SqlExpressionResolver.getTableAlias(expression);
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource
                && alias != null) {
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource sub =
                    RelationFromMapper.relationSubset(relation, alias);
            return sub != null ? sub : relation;
        }
        return relation;
    }

    /** {@code RolapHierarchy.addToFromInverse(query, expression)} subset semantics: the INVERSE
     *  (leaf-ward) subset for the expression's table, whole relation for a computed expression or
     *  when no subset resolves. */
    private static org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relationSubsetInverseFor(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
            SqlExpression expression) {
        String alias = SqlExpressionResolver.getTableAlias(expression);
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource
                && alias != null) {
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource sub =
                    RelationFromMapper.relationSubsetInverse(relation, alias);
            return sub != null ? sub : relation;
        }
        return relation;
    }

    /**
     * The {@code dimKey = aggColumn} edge, added only when BOTH tables are already registered (a
     * computed key has no table alias, so its edge vanishes).
     */
    private static void addAggEdge(AggFromState st, SqlExpression dimKeyExp,
            org.eclipse.daanse.rolap.common.aggmatcher.AggStar.Table.Column aggColumn, Predicate on) {
        String dimAlias = SqlExpressionResolver.getTableAlias(dimKeyExp);
        String aggAlias = SqlExpressionResolver.getTableAlias(aggColumn.getExpression());
        if (dimAlias != null && aggAlias != null
                && st.fromItems.containsKey(dimAlias) && st.fromItems.containsKey(aggAlias)) {
            st.edges.add(new AggFromState.Edge(dimAlias, aggAlias, on));
        }
    }

    /**
     * The breadth-attach left-deep FROM fold (as
     * {@code ContributionAssembler.buildFromClause}): base = the first registered item, every pass
     * attaches each pending item by the FIRST edge (in edge order) whose other endpoint is already
     * placed; a disconnected leftover becomes a comma-product item. Edges used as tree ONs land in
     * {@code st.treeOns} so the caller can WHERE-fold the duplicates.
     */
    private static void foldAggFrom(AggFromState st, SelectStatementBuilder q, RolapLevel level) {
        List<java.util.Map.Entry<String, FromClause>> items = new ArrayList<>(st.fromItems.entrySet());
        // Base-FROM provenance (render-only): the dimension's level-table label when the base is a
        // dimension table, the agg table's name when the context put the agg fact first.
        FromClause base = items.get(0).getValue();
        String baseAlias = items.get(0).getKey();
        String baseComment = RelationFromMapper.tableAliases(
                ((org.eclipse.daanse.rolap.element.RolapHierarchy) level.getHierarchy()).getRelation())
                .contains(baseAlias)
                        ? TupleSqlMapper.baseTableComment(level, baseAlias)
                        : "agg table " + baseAlias;
        FromClause acc = From.commentBase(base, baseComment);
        Set<String> placed = new java.util.HashSet<>();
        placed.add(baseAlias);
        List<java.util.Map.Entry<String, FromClause>> pending =
                new ArrayList<>(items.subList(1, items.size()));
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            List<java.util.Map.Entry<String, FromClause>> stillPending = new ArrayList<>();
            for (java.util.Map.Entry<String, FromClause> item : pending) {
                Predicate on = null;
                for (AggFromState.Edge e : st.edges) {
                    if (e.a().equals(item.getKey()) && placed.contains(e.b())
                            || e.b().equals(item.getKey()) && placed.contains(e.a())) {
                        on = e.on();
                        break;
                    }
                }
                if (on != null) {
                    acc = new FromClause.FromJoin(acc,
                            org.eclipse.daanse.sql.statement.api.model.JoinKind.INNER, item.getValue(),
                            on, java.util.Optional.empty());
                    placed.add(item.getKey());
                    st.treeOns.add(on);
                    progress = true;
                } else {
                    stillPending.add(item);
                }
            }
            pending = stillPending;
        }
        if (pending.isEmpty()) {
            q.from(acc);
            return;
        }
        // Disconnected leftovers: comma product (the assembler's fallback) — silently dropping a
        // table would corrupt the read.
        List<FromClause> product = new ArrayList<>();
        product.add(acc);
        pending.forEach(e -> product.add(e.getValue()));
        q.from(new FromClause.FromProduct(product));
    }

    /**
     * The fact table for a fact-join children SELECT: from the first contributed join table; with
     * none (a fact-join contribution whose predicates all sit on the queried dimension itself),
     * from the queried level's own star key — same derivation as {@code TupleSqlMapper}.
     */
    private static RolapStar.Table factFor(RolapLevel level, List<RolapStar.Table> joinTables) {
        if (!joinTables.isEmpty()) {
            return joinTables.get(0).getStar().getFactTable();
        }
        if (level instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel cl
                && cl.getStarKeyColumn() != null) {
            return cl.getStarKeyColumn().getTable().getStar().getFactTable();
        }
        throw new IllegalArgumentException("no fact table derivable for " + level.getUniqueName());
    }

    /** See {@link JoinPlanner#addChainSelfFirst} — shared with the tuple mapper. */
    private static void addChainSelfFirst(Set<RolapStar.Table> pending, RolapStar.Table table,
            RolapStar.Table fact) {
        JoinPlanner.addChainSelfFirst(pending, table, fact);
    }

    /** See {@link JoinPlanner#foldJoinSteps} — shared with the tuple mapper. */
    private static List<JoinPlanner.JoinStep> foldJoinSteps(LinkedHashSet<RolapStar.Table> pending,
            RolapStar.Table fact) {
        return JoinPlanner.foldJoinSteps(pending, fact);
    }

    /** Collect the leaf tables of a (possibly snowflake) relation FROM clause, in left-to-right order. */
    private static void flattenRelation(FromClause fc, List<FromClause> leaves) {
        if (fc instanceof FromClause.FromJoin j) {
            flattenRelation(j.left(), leaves);
            flattenRelation(j.right(), leaves);
        } else if (fc instanceof FromClause.FromProduct p) {
            for (FromClause item : p.items()) {
                flattenRelation(item, leaves);
            }
        } else {
            leaves.add(fc);
        }
    }

    /** The query-local alias of a leaf FROM table, or {@code null} if it is not a plain table. */
    private static String leafAlias(FromClause fc) {
        return (fc instanceof FromClause.FromTable ft) ? ft.alias().name() : null;
    }

    /** SELECT key, caption, differing ordinals, properties; GROUP BY each when {@code needsGroupBy};
     *  ORDER BY ordinals (or the key), nulls last — shared by both {@link #childMemberSql} overloads. */
    private static void projectLevel(SelectStatementBuilder q, RolapLevel level, boolean needsGroupBy) {
        SqlExpression keyExp = level.getKeyExp();
        ProjectionRef keyRef = q.project(JoinPlanner.expressionFor(keyExp), level.getInternalType(),
                null, "level key " + level.getUniqueName());
        if (needsGroupBy) {
            q.groupOn(keyRef);
        }

        // Caption is selected right after the key (matching makeChildMemberSql's emission order).
        if (level.hasCaptionColumn()) {
            ProjectionRef captionRef = q.project(JoinPlanner.expressionFor(level.getCaptionExp()), null,
                    null, "level caption " + level.getUniqueName());
            if (needsGroupBy) {
                q.groupOn(captionRef);
            }
        }

        List<? extends SqlExpression> ordinals = level.getOrdinalExps();
        if (ordinals != null && !ordinals.isEmpty()) {
            for (SqlExpression ordinalExp : ordinals) {
                if (!sameColumn(ordinalExp, keyExp)) {
                    ProjectionRef ref = q.project(JoinPlanner.expressionFor(ordinalExp), null,
                            null, "level ordinal " + level.getUniqueName());
                    if (needsGroupBy) {
                        q.groupOn(ref);
                    }
                    // Order on the projection ref so the renderer spells it as the SELECT alias when
                    // the dialect requiresOrderByAlias, and as the expression otherwise.
                    q.orderOn(ref, sortSpec(ordinalExp.getSortingDirection()));
                } else {
                    q.orderOn(keyRef, sortSpec(ordinalExp.getSortingDirection()));
                }
            }
        } else {
            q.orderOn(keyRef, sortSpec(keyExp.getSortingDirection()));
        }

        for (RolapProperty property : level.getProperties()) {
            ProjectionRef ref = q.project(JoinPlanner.expressionFor(property.getExp()), null,
                    null, "member property " + property.getName());
            // Canonical (permissive-dialect) form: group a property only when it is NOT functionally
            // dependent on the level value (a domain fact). A dialect that cannot select non-grouped
            // columns has the renderer complete the group by (completeNonAggregatesGroupBy below).
            if (!property.dependsOnLevelValue()) {
                q.groupOn(ref);
            }
        }
        // A genuinely grouping read (keys/ordinals placed as group keys) needs every non-aggregate
        // column grouped on restrictive dialects — the renderer appends the rest per dialect.
        if (needsGroupBy) {
            q.completeNonAggregatesGroupBy();
        }
    }

    // ---- helpers ---------------------------------------------------------------

    private static boolean isPlainColumn(SqlExpression e) {
        return e instanceof org.eclipse.daanse.rolap.element.RolapColumn;
    }

    /** Two expressions reference the same plain column (same table alias, same column name). */
    private static boolean sameColumn(SqlExpression a, SqlExpression b) {
        return a instanceof org.eclipse.daanse.rolap.element.RolapColumn
                && b instanceof org.eclipse.daanse.rolap.element.RolapColumn
                && SqlExpressionResolver.getTableAlias(a) != null
                && SqlExpressionResolver.getTableAlias(a).equals(SqlExpressionResolver.getTableAlias(b))
                && ((org.eclipse.daanse.rolap.element.RolapColumn) a).getName()
                        .equals(((org.eclipse.daanse.rolap.element.RolapColumn) b).getName());
    }

    /** ROLAP sort direction → builder {@link SortSpec} (nullable, nulls-last). */
    private static SortSpec sortSpec(SortingDirection direction) {
        SortDirection dir = direction == SortingDirection.DESC ? SortDirection.DESC : SortDirection.ASC;
        return new SortSpec(dir, true, NullOrder.LAST, false);
    }
}

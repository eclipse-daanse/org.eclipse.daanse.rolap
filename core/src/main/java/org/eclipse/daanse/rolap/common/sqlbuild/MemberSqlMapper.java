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
     * The member-enumeration SELECT for {@code level} (see {@link #supports}), optionally
     * constrained to a parent member's children. {@code memberConstraint} (see
     * {@link JoinPlanner#memberKeyConstraint}) is added to {@code WHERE} before the join predicates
     * (the {@code [constraint, joins]} order).
     */
    public static SelectStatement childMemberSql(RolapLevel level, boolean needsGroupBy,
            Optional<Predicate> memberConstraint, boolean allowsSelectNotInGroupBy) {
        SelectStatementBuilder q = SelectStatementBuilder.create();
        // FROM the per-level snowflake SUBSET (the same subset TupleSqlMapper uses) — NOT the whole
        // relation. The whole relation over-joins a near-table level to the far table, dropping childless
        // snowflake members; the subset honors FilterChildlessSnowflakeMembers and falls back to the whole
        // relation for computed-column levels (no table alias to subset by).
        // Keep the snowflake as the renderer's ANSI JOIN…ON tree (the same form the level-members path
        // emits) instead of flattening to comma + a join predicate in WHERE — the SQL-assert tests
        // (EffectiveMemberCacheTest) baseline the ANSI form; a single-table subset is just one table
        // either way, so non-snowflake dimensions are unaffected.
        FromClause levelFrom = TupleSqlMapper.fromForLevels(level, null);
        // Base-FROM provenance: the dimension + the child level whose relation anchors the FROM
        // (rendered only when comments are on; never part of the executed SQL).
        q.from(From.commentBase(levelFrom,
                TupleSqlMapper.baseTableComment(level, From.baseAlias(levelFrom))));
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        q.header("children at " + level.getUniqueName());
        q.footerComment("member children (dimension only)");
        memberConstraint.ifPresent(p -> q.where(p, "parent member key"));
        projectLevel(q, level, needsGroupBy, allowsSelectNotInGroupBy);
        return q.build();
    }

    /**
     * The context-constrained variant: the level members joined to the fact table (and any context
     * dimension tables) so a NON-EMPTY / context restriction can be applied — the builder counterpart
     * of {@code addLevelMemberSql} + {@code SqlContextConstraint.addContextConstraint}. FROM is the
     * hierarchy relation, then the fact, then the context tables (the reference path's add order); WHERE is the
     * star join predicates ({@link JoinPlanner#joinPredicates}) followed by {@code where} (the
     * translated context predicate, AND-combined with the parent-key constraint). The fact table is
     * derived from the contributed {@code joinTables} (each star table knows its star).
     */
    public static SelectStatement childMemberSql(RolapLevel level, boolean needsGroupBy,
            Optional<Predicate> where, List<RolapStar.Table> joinTables, boolean allowsSelectNotInGroupBy) {
        org.eclipse.daanse.rolap.element.RolapHierarchy hierarchy =
                (org.eclipse.daanse.rolap.element.RolapHierarchy) level.getHierarchy();
        SelectStatementBuilder q = SelectStatementBuilder.create();

        RolapStar.Table fact = joinTables.get(0).getStar().getFactTable();
        List<FromClause> items = new ArrayList<>();
        // FROM = the queried dimension relation + the fact + the context dimension tables, all as comma
        // items; every star join (queried dimension↔fact and each context table↔fact) is a WHERE conjunct
        // (comma-join form, join conditions pushed into WHERE). The queried dimension's
        // own tables come from its relation, so they are not re-added as comma items (which would duplicate
        // their alias) — only context tables not already in the relation are added.
        Set<String> dimAliases = RelationFromMapper.tableAliases(hierarchy.getRelation());
        FromClause relationFrom = RelationFromMapper.from(hierarchy.getRelation());
        // Base-FROM provenance: the dimension relation is the FROM base here (the fact is a comma item).
        items.add(From.commentBase(relationFrom,
                TupleSqlMapper.baseTableComment(level, From.baseAlias(relationFrom))));
        items.add(From.table(fact.getTableName(), TableAlias.of(fact.getAlias())));
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
        // table's chain to the fact, including the queried dimension's own fact join.
        JoinPlanner.joinPredicates(fact, referenced).forEach(p -> q.where(p, "fact join (context)"));
        // Each context restriction is its own WHERE conjunct, so split a top-level
        // AND into separate conjuncts; a nested AND (e.g. the multi-part parent key) stays grouped.
        where.ifPresent(p -> {
            if (p instanceof Predicate.And and) {
                and.operands().forEach(op -> q.where(op, "context"));
            } else {
                q.where(p, "context");
            }
        });

        projectLevel(q, level, needsGroupBy, allowsSelectNotInGroupBy);
        return q.build();
    }

    /**
     * As the fact-join {@link #childMemberSql(RolapLevel, boolean, Optional, List, boolean)} overload but
     * with the context constraint's per-column {@code (table, predicate)} pairs so the WHERE is
     * interleaved exactly like the {@code addMemberConstraint + addLevelConstraint} sequence:
     * <ol>
     *   <li>the queried dimension relation's own (snowflake) join(s);</li>
     *   <li>per context column in contribution order: {@code [its join chain to fact (once)]} then
     *       {@code [its value-constraint(s)]} — these already include the parent's level key, since
     *       the parent key is added as a context column (after {@code setContext(parent)}) and the
     *       explicit {@code addMemberConstraint} duplicate dedupes away;</li>
     *   <li>finally the queried child level -> fact join ({@code addLevelConstraint}), appended last and
     *       deduped against the joins already emitted (a no-op for single-table dimensions).</li>
     * </ol>
     * Empty {@code orderedPredicates} delegates to the grouped overload (guarded, non-authoritative).
     */
    public static SelectStatement childMemberSql(RolapLevel level, boolean needsGroupBy,
            Optional<Predicate> where, List<RolapStar.Table> joinTables,
            List<org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate> orderedPredicates,
            Optional<Predicate> memberKeyGroup,
            boolean allowsSelectNotInGroupBy) {
        if (orderedPredicates.isEmpty()) {
            return childMemberSql(level, needsGroupBy, where, joinTables, allowsSelectNotInGroupBy);
        }
        org.eclipse.daanse.rolap.element.RolapHierarchy hierarchy =
                (org.eclipse.daanse.rolap.element.RolapHierarchy) level.getHierarchy();
        RolapStar.Table fact = joinTables.get(0).getStar().getFactTable();

        // Separate the star joins (→ ANSI JOIN…ON) from the value constraints (→ WHERE), preserving
        // the parent-first, deduped join order via a shared `emitted` set. The context dimension /
        // queried-dimension star tables arrive as innerJoin steps, not FROM comma items.
        Set<RolapStar.Table> emitted = new LinkedHashSet<>();
        List<JoinPlanner.JoinStep> joinSteps = new ArrayList<>();
        List<Predicate> wheres = new ArrayList<>();
        // WHERE: per context column [its value constraint] (the star join chain becomes a JOIN…ON step);
        // the parent's level key is already among these context columns. Then the parenthesised
        // parent-key group (addMemberConstraint) as a trailing conjunct. The child level -> fact join
        // (addLevelConstraint) becomes a trailing join step, deduped via `emitted` (no-op single-table).
        for (org.eclipse.daanse.rolap.common.sql.ConstraintContribution.ColumnPredicate cp
                : orderedPredicates) {
            joinSteps.addAll(JoinPlanner.joinStepsFor(cp.table(), fact, emitted));
            wheres.add(cp.predicate());
        }
        memberKeyGroup.ifPresent(wheres::add);
        if (level instanceof org.eclipse.daanse.rolap.element.RolapCubeLevel cubeLevel
                && cubeLevel.getStarKeyColumn() != null) {
            joinSteps.addAll(JoinPlanner.joinStepsFor(cubeLevel.getStarKeyColumn().getTable(), fact, emitted));
        }

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
        items.add(From.commentBase(From.table(fact.getTableName(), TableAlias.of(fact.getAlias())),
                factComment));
        addedAliases.add(fact.getAlias());
        List<FromClause> relLeaves = new ArrayList<>();
        flattenRelation(RelationFromMapper.from(hierarchy.getRelation()), relLeaves);
        for (FromClause leaf : relLeaves) {
            String a = leafAlias(leaf);
            if (a != null && !joinedAliases.contains(a) && addedAliases.add(a)) {
                items.add(leaf);
            }
        }
        FromClause from = new FromClause.FromProduct(items);

        SelectStatementBuilder q = SelectStatementBuilder.create();
        q.from(from);
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        q.header("children at " + level.getUniqueName());
        q.footerComment("member children (context constrained)");
        for (JoinPlanner.JoinStep s : joinSteps) {
            q.join(org.eclipse.daanse.sql.statement.api.model.JoinKind.INNER,
                    JoinPlanner.tableFromClause(s.table(), null), s.on(), "fact join (context)");
        }
        wheres.forEach(p -> q.where(p, "context"));
        projectLevel(q, level, needsGroupBy, allowsSelectNotInGroupBy);
        return q.build();
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
    private static void projectLevel(SelectStatementBuilder q, RolapLevel level, boolean needsGroupBy,
            boolean allowsSelectNotInGroupBy) {
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
            // makeChildMemberSql rule: group the property unless the dialect allows
            // select-not-in-group-by AND the property is functionally dependent on the level value.
            if ((needsGroupBy && !allowsSelectNotInGroupBy) || !property.dependsOnLevelValue()) {
                q.groupOn(ref);
            }
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

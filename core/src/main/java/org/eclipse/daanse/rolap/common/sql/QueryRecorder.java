/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2002-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * Copyright (C) 2021 Sergei Semenkov
 * All Rights Reserved.
 *
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */

package org.eclipse.daanse.rolap.common.sql;

import static org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.getTableAlias;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.getLeftAlias;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.getRightAlias;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.left;
import static org.eclipse.daanse.rolap.common.util.JoinUtil.right;
import static org.eclipse.daanse.rolap.common.util.TableUtil.getHintMap;
import static org.eclipse.daanse.rolap.common.util.ViewUtil.getCodeSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.eclipse.daanse.jdbc.db.api.type.BestFitColumnType;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.Utils;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.util.RelationUtil;

/**
 * QueryRecorder is the record-time side of SQL construction: it holds
 * the mutation bookkeeping (FROM-alias dedup, snowflake relation maps, SELECT alias counter and
 * node→alias map, query-level flags) and the full mutator surface, each mutator validating,
 * updating the bookkeeping, and appending one immutable {@link QueryTape.QueryOp} to the tape. The
 * tape ({@link #ops()}) is the SOLE render source — the assembler
 * ({@code ContributionAssembler}) replays it against a fresh statement builder.
 *
 * <p>{@link #fork()} / {@link #append(QueryTape)} let a producer record a contribution on a fork
 * that is later appended to the parent.
 *
 * <p>NOTE: Instances of this class are NOT thread safe so the user must make sure this is
 * accessed by only one thread at a time.
 */
public class QueryRecorder {
    /** Controls the formatting of the sql string. */
    private final boolean generateFormattedSql;

    private boolean distinct;

    /** Controls whether table optimization hints are used */
    private boolean allowHints;

    /**
     * This list is used to keep track of what aliases have been  used in the
     * FROM clause. One might think that a java.util.Set would be a more
     * appropriate Collection type, but if you only have a couple of "from
     * aliases", then iterating over a list is faster than doing a hash lookup
     * (as is used in java.util.HashSet).
     */
    private final List<String> fromAliases;

    private final Set<org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource> relations =
        new HashSet<>();

    private final Map<org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource, org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource>
        mapRelationToRoot =
        new HashMap<>();

    private final Map<org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource, List<RelInfo>>
        mapRootToRelations =
        new HashMap<>();

    /**
     * SELECT alias keyed by the dialect-free builder NODE of the projected expression (not its dialect-rendered
     * string). Node {@code equals} is render-equality, so this resolves an alias for a lookup expression that
     * produces the same node — without the live dialect. Used by the native-filter HAVING-alias path
     * ({@link #getAlias(org.eclipse.daanse.olap.api.sql.SqlExpression)}).
     */
    private final Map<SqlExpression, String> aliasByNode =
        new HashMap<>();

    /** Number of SELECT items added — the source of {@code c0..cN} column aliases. Kept live at record
     *  time because the assigned alias is RETURNED to callers ({@link #addSelect}); each recorded
     *  {@code SelectItem} op carries the RESOLVED alias, so replay never needs the counter. */
    private int aliasCounter = 0;

    /**
     * The ordered mutation tape: one immutable {@link QueryTape.QueryOp} per {@link #track} step. This tape
     * is the ONLY render source — {@link #buildStatement()} hands it to {@code ContributionAssembler},
     * which replays it against a
     * fresh {@code SelectStatementBuilder} (including dedup and FROM/JOIN folding) and builds the statement.
     */
    private final List<QueryTape.QueryOp> tape = new ArrayList<>();

    /** Records a mutation step's immutable op on the {@link #tape} — the sole render source.
     *  Kept as a method so every mutation call-site stays greppable as {@code track(}. */
    private void track(QueryTape.QueryOp op) {
        tape.add(op);
    }

    /** The immutable mutation tape recorded so far, plus the query-level flags (see {@link QueryTape}). */
    public QueryTape ops() {
        return new QueryTape(tape, distinct, generateFormattedSql);
    }

    private static final String ALIAS_EXISTS_ERROR = "query already contains alias '%s'";
    private static final String COLUMN_ALIAS_PREFIX = "c";
    private static final String EXPRESSION_NULL_OR_BLANK_ERROR = "expression must not be null or blank";
    private static final String ALIAS_NULL_OR_BLANK_ERROR = "alias must not be null or blank";

    /**
     * Validates that an expression is not null or blank.
     *
     * @param expression the expression to validate
     * @throws IllegalArgumentException if expression is null or blank
     */
    private static void requireNonBlankExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException(EXPRESSION_NULL_OR_BLANK_ERROR);
        }
    }

    /**
     * Validates that an alias is not null or blank.
     *
     * @param alias the alias to validate
     * @throws IllegalArgumentException if alias is null or blank
     */
    private static void requireNonBlankAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException(ALIAS_NULL_OR_BLANK_ERROR);
        }
    }

    /**
     * Base constructor used by all other constructors to create an empty
     * instance.
     *
     * @param formatted Whether to generate SQL formatted on multiple lines
     */
    public QueryRecorder(boolean formatted) {
        this.generateFormattedSql = formatted;

        this.fromAliases = new ArrayList<>();


        // REVIEW emcdermid 10-Jul-2009: It might be okay to allow
        // hints in all cases, but for initial implementation this
        // allows us to them on selectively in specific situations.
        // Usage will likely expand with experimentation.
        this.allowHints = false;
    }

    /**
     * Snapshot copy constructor backing {@link #fork()}: copies the parent's record-time READ state
     * (FROM aliases, snowflake relation maps, alias-by-node map, alias counter, flags) so the fork's
     * mutators make the same dedup / alias decisions the parent would have made — with an EMPTY tape,
     * so the fork's {@link #ops()} carries only the contribution recorded on the fork.
     */
    private QueryRecorder(QueryRecorder parent) {
        this.generateFormattedSql = parent.generateFormattedSql;
        this.fromAliases = new ArrayList<>(parent.fromAliases);
        this.relations.addAll(parent.relations);
        this.mapRelationToRoot.putAll(parent.mapRelationToRoot);
        // The RelInfo lists are put once by registerRootRelation and never mutated afterwards, so a
        // shallow map copy is a faithful snapshot.
        this.mapRootToRelations.putAll(parent.mapRootToRelations);
        this.aliasByNode.putAll(parent.aliasByNode);
        this.aliasCounter = parent.aliasCounter;
        this.distinct = parent.distinct;
        this.allowHints = parent.allowHints;
    }

    public static QueryRecorder newQuery(Context<?> context, String err) {
        return new QueryRecorder(
            context.getConfigValue(ConfigConstants.GENERATE_FORMATTED_SQL, ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE, Boolean.class));
    }

    /**
     * The dialect-free {@link org.eclipse.daanse.sql.statement.api.model.SelectStatement} this recorder has
     * accumulated — assembled by replaying the recorded {@link QueryTape} tape (the sole render source; the
     * assembler applies the dedup, resolves the deferred sub-query FROMs and folds the JOIN edges into the
     * FROM tree). Dialect-free: the {@code DialectSqlRenderer} applies the dialect once when this statement
     * is rendered (via {@code SqlRender.render(buildStatement(), dialect, renderOptions())}).
     */
    public org.eclipse.daanse.sql.statement.api.model.SelectStatement buildStatement() {
        return org.eclipse.daanse.rolap.common.sqlbuild.ContributionAssembler.assemble(ops());
    }

    /**
     * The render options this recorder was configured with — multi-line when {@link #isFormatted()}, else
     * compact. Dialect-free, so a consumer renders the assembled statement without this class referencing
     * the {@code Dialect} type: {@code SqlRender.render(recorder.buildStatement(), dialect, recorder.renderOptions())}.
     * The central {@link org.eclipse.daanse.rolap.common.SqlRender} seam carries the diagnostic commented-SQL
     * file log ({@code CommentedSqlLog}) and the commented double-render.
     */
    public org.eclipse.daanse.sql.statement.api.render.RenderOptions renderOptions() {
        return isFormatted()
                ? org.eclipse.daanse.sql.statement.api.render.RenderOptions.multiLine()
                : org.eclipse.daanse.sql.statement.api.render.RenderOptions.compact();
    }

    /**
     * A recorder forked off a parent {@link QueryRecorder} (see {@link #fork()}): pre-seeded with a
     * snapshot of the parent's read state and an empty tape. A Fork IS a {@code QueryRecorder}, so a
     * producer records on it exactly as on the parent; the parent later merges the contribution via
     * {@code parent.append(fork.ops())}. Deliberately minimal — it adds no state of its own.
     */
    public static final class Fork extends QueryRecorder {
        private Fork(QueryRecorder parent) {
            super(parent);
        }
    }

    /**
     * A new {@link Fork} seeded with a SNAPSHOT of this recorder's read state (FROM aliases, relation
     * maps, alias-by-node map, alias counter, dedup sets, flags) and an EMPTY tape. Mutations on the
     * fork do not touch this recorder; merge them back with {@link #append(QueryTape)}.
     */
    public Fork fork() {
        return new Fork(this);
    }

    /**
     * Replays a fork's recorded ops onto THIS recorder, dispatching each op through the same internal
     * bookkeeping the public mutators use — NOT a naive tape {@code addAll} — so the parent's
     * bookkeeping (FROM-alias dedup list, alias counter, alias-by-node map, distinct / supported
     * flags) ends up exactly as if the fork's mutator calls had been made on the parent directly:
     * <ul>
     *   <li>FROM ops ({@code FromRendered} / {@code FromDeferred} / {@code FromTable}) re-check the
     *       alias dedup against the parent's live {@code fromAliases} (the {@code failIfExists=false}
     *       behaviour of the mutators) and register the alias when added;</li>
     *   <li>{@code SelectItem} re-applies the counter increment and the {@code aliasByNode} put with
     *       the op's RESOLVED alias (identical to every SELECT mutator's bookkeeping, since the op
     *       captured the exact node + alias the mutator computed against the forked snapshot);</li>
     *   <li>{@code Distinct} re-applies the flag mutation;</li>
     *   <li>every other op is bookkeeping-free at record time (pure {@code track(op)}), so appending
     *       the op verbatim is exactly what its mutator did; duplicate {@code JoinEdge}s are deduped
     *       by the assembler's FROM/JOIN fold, as they already are for the star-join overlap.</li>
     * </ul>
     * The snowflake relation maps are NOT merged — the tape does not carry {@code RelationalSource}
     * registrations; a fork records contribution ops (WHERE / JOIN / SELECT), not new snowflake
     * root registrations.
     */
    public void append(QueryTape forkOps) {
        for (QueryTape.QueryOp op : forkOps.ops()) {
            switch (op) {
                case QueryTape.Distinct d -> {
                    this.distinct = d.value();
                    track(d);
                }
                case QueryTape.FromRendered f -> appendFrom(f.alias(), f);
                case QueryTape.FromDeferred f -> appendFrom(f.alias(), f);
                case QueryTape.FromTable f -> appendFrom(f.alias(), f);
                case QueryTape.SelectItem s -> {
                    aliasCounter++;
                    aliasByNode.put(s.node(), s.mapAlias());
                    track(s);
                }
                default -> track(op);
            }
        }
    }

    /**
     * Fork merge: {@link #append(QueryTape)} the given ops AND adopt the fork's snowflake
     * relation bookkeeping ({@code relations} set + {@code mapRelationToRoot} /
     * {@code mapRootToRelations}), which the ops tape does not carry (constraints mutate it via
     * {@code hierarchy.addToFrom} → {@code registerRootRelation} / {@code addFrom(RelationalSource)},
     * pure map mutations with no op). Because the fork was seeded with an EXACT snapshot of this
     * recorder's state and this recorder is not mutated between {@code fork()} and this call, the
     * fork's post-run bookkeeping is exactly what an un-forked run of the
     * constraint would have left here — so adopting it wholesale (fork wins, reproducing even
     * registerRootRelation's overwrite semantics) keeps LATER {@code addFrom} join-between decisions
     * (the {@code FilterChildlessSnowflakeMembers=false} path, which reads these maps) identical.
     * For an implementor that returns pure ops without touching the fork, the adoption
     * is a no-op (the fork still equals the snapshot).
     */
    public void append(Fork fork, QueryTape forkOps) {
        append(forkOps);
        // Upcast: the private fields are declared on QueryRecorder (not inherited into Fork's type).
        final QueryRecorder f = fork;
        this.relations.addAll(f.relations);
        this.mapRelationToRoot.putAll(f.mapRelationToRoot);
        this.mapRootToRelations.putAll(f.mapRootToRelations);
    }

    /** FROM-op replay for {@link #append(QueryTape)}: the mutators' alias dedup ({@code failIfExists=false})
     *  against the parent's live alias list, registering the alias when the op is kept. */
    private void appendFrom(String alias, QueryTape.QueryOp op) {
        if (alias != null) {
            if (fromAliases.contains(alias)) {
                return;
            }
            fromAliases.add(alias);
        }
        track(op);
    }

    public void setDistinct(final boolean distinct) {
        this.distinct = distinct;
        track(new QueryTape.Distinct(distinct));
    }

    /**
     * Chooses whether table optimization hints may be used
     * (assuming the dialect supports it).
     *
     * @param t True to allow hints to be used, false otherwise
     */
    public void setAllowHints(boolean t) {
        this.allowHints = t;
    }

    /**
     * Core of the rendered-subquery FROM: records the supplied {@code builderNode} (a
     * {@code FromRaw} for an already-rendered string, or a {@code FromSubquery} carrying a
     * sub-query's dialect-free statement) under the FROM-alias dedup.
     */
    private boolean addFromRendered(
        final String query,
        final String alias,
        final boolean failIfExists,
        final FromClause builderNode)
    {
        requireNonBlankAlias(alias);

        if (fromAliases.contains(alias)) {
            if (failIfExists) {
                throw Util.newInternal(ALIAS_EXISTS_ERROR.formatted(alias));
            } else {
                return false;
            }
        }

        fromAliases.add(alias);
        track(new QueryTape.FromRendered(alias, builderNode));
        return true;
    }

    /** Records a deferred view/inline-table FROM: the op carries a resolver; at assembly a placeholder with the
     *  correct alias (for join-folding) is replaced at its recorded index with the dialect-resolved FROM.
     *  Keeps the producer dialect-free. */
    private boolean addDeferredFrom(String alias, boolean failIfExists,
            java.util.function.Supplier<FromClause> resolver) {
        requireNonBlankAlias(alias);
        if (fromAliases.contains(alias)) {
            if (failIfExists) {
                throw Util.newInternal(ALIAS_EXISTS_ERROR.formatted(alias));
            }
            return false;
        }
        fromAliases.add(alias);
        track(new QueryTape.FromDeferred(alias, resolver));
        return true;
    }

    /**
     * Adds [schema.]table AS alias to the FROM clause.
     *
     * @param schema schema name; may be null
     * @param name table name
     * @param alias table alias, may not be null
     *              (if not null, must not be zero length).
     * @param filter Extra filter condition, or null
     * @param hints table optimization hints, if any
     * @param failIfExists Whether to throw a RuntimeException if from clause
     *   already contains this alias
     *
     *  alias != null
     * @return true if table was added
     */
    public boolean addFromTable(
        final String schema,
        final String name,
        final String alias,
        final String filter,
        final Map<String, String> hints,
        final boolean failIfExists)
    {
        if (fromAliases.contains(alias)) {
            if (failIfExists) {
                throw Util.newInternal(ALIAS_EXISTS_ERROR.formatted(alias));
            } else {
                return false;
            }
        }

        if (alias != null) {
            if (alias.isBlank()) {
                throw new IllegalArgumentException(ALIAS_NULL_OR_BLANK_ERROR);
            }
            fromAliases.add(alias);
        }

        if (filter != null) {
            // append filter condition to where clause
            addWhere("(", filter, ")");
        }
        // Capture table optimizer hints (gated on allowHints) so the assembled render keeps them; the
        // per-table filter is already fed via addWhere above, so it is not repeated here. The op carries
        // the EFFECTIVE hint map rather than the live allowHints field.
        final Map<String, String> tableHints = (this.allowHints && hints != null) ? hints : Map.of();
        track(new QueryTape.FromTable(schema, name, alias, tableHints));
        return true;
    }

    public void addFrom(
        final QueryRecorder sqlQuery,
        final String alias,
        final boolean failIfExists)
    {
        // Defer the sub-query's statement build to assembly time (so its FROM index is known for
        // join-folding), then embed it as a FromSubquery. Dialect-free: the nested assemble produces a
        // dialect-free SelectStatement; the renderer applies the dialect once at render.
        addDeferredFrom(alias, failIfExists,
                () -> new FromClause.FromSubquery(
                        org.eclipse.daanse.rolap.common.sqlbuild.ContributionAssembler.assemble(sqlQuery.ops()),
                        TableAlias.of(alias)));
    }

    /**
     * Adds a relation to a query, adding appropriate join conditions, unless
     * it is already present.
     *
     * Returns whether the relation was added to the query.
     *
     * @param relation Relation to add
     * @param alias Alias of relation. If null, uses relation's alias.
     * @param failIfExists Whether to fail if relation is already present
     * @return true, if relation *was* added to query
     */
    public boolean addFrom(
        final org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
        final String alias,
        final boolean failIfExists)
    {
        registerRootRelation(relation);

        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation1) {
            if (relations.add(relation1)
                && !SystemWideProperties.instance()
                .FilterChildlessSnowflakeMembers)
            {
                // This relation is new to this query. Add a join to any other
                // relation in the same dimension.
                //
                // (If FilterChildlessSnowflakeMembers were false,
                // this would be unnecessary. Adding a relation automatically
                // adds all relations between it and the fact table.)
                org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource root =
                    mapRelationToRoot.get(relation1);
                List<org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource> relationsCopy =
                    new ArrayList<>(relations);
                for (org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation2 : relationsCopy) {
                    if (relation2 != relation1
                        && mapRelationToRoot.get(relation2) == root)
                    {
                        addJoinBetween(root, relation1, relation2);
                    }
                }
            }
        }

        return switch (relation) {
            case org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource view -> {
                final String viewAlias = alias != null ? alias : RelationUtil.getAlias(view);
                // Dialect-free: hand the renderer the whole per-dialect view map (it picks the live dialect's
                // entry at render) instead of resolving a dialect-specific string while building.
                yield addFromRendered("view", viewAlias, false,
                        new FromClause.FromVariant(getCodeSet(view).asMap(), TableAlias.of(viewAlias)));
            }
            case org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource inlineTable -> {
                final String inlineAlias = alias != null ? alias : RelationUtil.getAlias(inlineTable);
                // Dialect-free: carry the inline-table data as a FromInline node; the renderer generates the
                // dialect-specific VALUES SQL at render time (no Dialect needed while building).
                RolapUtil.InlineTableData data = RolapUtil.inlineTableData(inlineTable);
                yield addFromRendered("inline", inlineAlias, failIfExists,
                        From.inline(data.columnNames(), data.columnTypes(), data.rows(), TableAlias.of(inlineAlias)));
            }
            case org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table -> {
                final String tableAlias = alias != null ? alias : RelationUtil.getAlias(table);
                yield addFromTable(
                    getSchemaName((org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema) table.getTable().getNamespace()),
                    table.getTable().getName(),
                    tableAlias,
                    Optional.ofNullable(table.getSqlWhereExpression())
                        .map(org.eclipse.daanse.rolap.mapping.model.database.source.SqlStatement::getSql)
                        .orElse(null),
                    getHintMap(table),
                    failIfExists);
            }
            case org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join -> addJoin(
                left(join),
                getLeftAlias(join),
                join.getLeft().getKey(),
                right(join),
                getRightAlias(join),
                join.getRight().getKey(),
                failIfExists);
            default -> throw Util.newInternal("bad relation type " + relation);
        };
    }

    private String getSchemaName(org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema schema) {
        return schema != null ? schema.getName() : null;
    }

	private boolean addJoin(
	    org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource left,
        String leftAlias,
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column leftKey,
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource right,
        String rightAlias,
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column rightKey,
        boolean failIfExists)
    {
        boolean addLeft = addFrom(left, leftAlias, failIfExists);
        boolean addRight = addFrom(right, rightAlias, failIfExists);

        boolean added = addLeft || addRight;
        if (added) {
            // Always record a dialect-free FromJoin edge; the assembler folds the edges into a left-deep
            // JOIN…ON tree (or comma-join + pushed WHERE for allowsJoinOn=false dialects). The structured
            // comparison needs no dialect; the fold dedups an edge the star-join path (addWhere(Condition))
            // also added. Structured (dialect-free) ON predicate; a degenerate key-less side renders as just
            // the table alias (renderer guards a null-name Column).
            final Predicate on = Predicates.comparison(
                    Expressions.column(TableAlias.of(leftAlias), leftKey != null ? leftKey.getName() : null),
                    ComparisonOperator.EQ,
                    Expressions.column(TableAlias.of(rightAlias), rightKey != null ? rightKey.getName() : null));
            track(new QueryTape.JoinEdge(leftAlias, rightAlias, on));
        }
        return added;
    }

    private void addJoinBetween(
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource root,
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation1,
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation2)
    {
        List<RelInfo> relations = mapRootToRelations.get(root);
        int index1 = find(relations, relation1);
        int index2 = find(relations, relation2);
        assert index1 != -1;
        assert index2 != -1;
        int min = Math.min(index1, index2);
        int max = Math.max(index1, index2);
        for (int i = max - 1; i >= min; i--) {
            RelInfo relInfo = relations.get(i);
                addJoin(
                    relInfo.relation(),
                    relInfo.leftAlias() != null
                        ? relInfo.leftAlias()
                        : RelationUtil.getAlias(relInfo.relation()),
                    relInfo.leftKey(),
                    relations.get(i + 1).relation(),
                    relInfo.rightAlias() != null
                        ? relInfo.rightAlias()
                        : RelationUtil.getAlias(relations.get(i + 1).relation()),
                    relInfo.rightKey(),
                    false);
        }
    }

    private int find(List<RelInfo> relations, org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        return IntStream.range(0, relations.size())
            .filter(i -> Utils.equalsQuery(relations.get(i).relation(), relation))
            .findFirst()
            .orElse(-1);
    }

    /**
     * Adds an expression to the select clause, automatically creating a
     * column alias.
     */
    public String addSelect(final CharSequence expression, BestFitColumnType type) {
        // Auto column alias: the projection is recorded WITHOUT an explicit ColumnAlias and the renderer
        // synthesizes c{ordinal} per the dialect's allowsFieldAlias() (DialectSqlRenderer.effectiveAlias). The
        // c{ordinal} the renderer emits equals nextColumnAlias() (aliasCounter == projection ordinal), so the
        // SELECT is identical; the op's mapAlias feeds the assembler's GROUP BY / ORDER BY ref resolution.
        final String alias = nextColumnAlias();
        final String exprStr = expression.toString();
        aliasCounter++;
        aliasByNode.put(Expressions.raw(exprStr), alias);
        track(new QueryTape.SelectItem(Expressions.raw(exprStr), type, null, alias, null));
        return alias;
    }

    /**
     * Adds an expression to the SELECT and GROUP BY clauses. Uses the alias in
     * the GROUP BY clause, if the dialect requires it.
     *
     * @param expression Expression
     * @return Alias of expression
     */
    public String addSelectGroupBy(
        final String expression,
        BestFitColumnType type)
    {
        final String alias = addSelect(expression, type);
        addGroupBy(expression, alias);
        return alias;
    }

    private String nextColumnAlias() {
        return COLUMN_ALIAS_PREFIX + aliasCounter;
    }

    /**
     * Adds an expression to the select clause, with a specified type and
     * column alias.
     *
     * @param expression Expression
     * @param type Java type to be used to hold cursor column
     * @param alias Column alias (or null for no alias)
     * @return Column alias
     */
    public String addSelect(
        final CharSequence expression,
        final BestFitColumnType type,
        String alias)
    {
        aliasCounter++;
        final String exprStr = expression.toString();
        aliasByNode.put(Expressions.raw(exprStr), alias);
        track(new QueryTape.SelectItem(Expressions.raw(exprStr), type, alias, alias, null));
        return alias;
    }

    /**
     * Dialect-free SELECT: the caller passes the ROLAP {@code SqlExpression} (e.g. a level key) instead of a
     * pre-rendered string. A plain {@code RolapColumn} feeds the builder a dialect-free {@code Column} node;
     * a computed expression a {@code RawVariant} carrying its per-dialect SQL map. Alias assignment and the
     * alias maps match {@link #addSelect(CharSequence, BestFitColumnType)}.
     */
    public String addSelectExpr(org.eclipse.daanse.olap.api.sql.SqlExpression expression, BestFitColumnType type) {
        // Auto column alias: project without an explicit ColumnAlias; the renderer synthesizes c{ordinal} per
        // allowsFieldAlias() (identical, since c{ordinal} == nextColumnAlias()); the op's mapAlias feeds
        // the assembler's ref resolution.
        final String alias = nextColumnAlias();
        final SqlExpression node = nodeOf(expression, null);
        aliasCounter++;
        aliasByNode.put(node, alias);
        track(new QueryTape.SelectItem(node, type, null, alias, null));
        return alias;
    }

    public String addSelectExpr(org.eclipse.daanse.olap.api.sql.SqlExpression expression, BestFitColumnType type,
            String alias) {
        SqlExpression node = nodeOf(expression, null);
        aliasCounter++;
        aliasByNode.put(node, alias);
        track(new QueryTape.SelectItem(node, type, alias, alias, null));
        return alias;
    }

    /**
     * As {@link #addSelectExpr(org.eclipse.daanse.olap.api.sql.SqlExpression, BestFitColumnType)} (auto
     * column alias, identical SQL) but carrying an explanatory rollup comment. The comment is emitted
     * only by the diagnostic comment render (see {@link #renderOptions()}); the executed
     * SQL is unaffected.
     */
    public String addSelectExprCommented(org.eclipse.daanse.olap.api.sql.SqlExpression expression,
            BestFitColumnType type, String comment) {
        if (comment == null) {
            // No comment → take the exact original path, so behaviour is unchanged when comments are off.
            return addSelectExpr(expression, type);
        }
        final String alias = nextColumnAlias();
        final SqlExpression node = nodeOf(expression, null);
        aliasCounter++;
        aliasByNode.put(node, alias);
        track(new QueryTape.SelectItem(node, type, null, alias, comment));
        return alias;
    }

    /**
     * As {@link #addSelectNode(SqlExpression, BestFitColumnType, String)} (explicit alias, e.g. a measure
     * {@code m0}) but carrying a comment. A null comment delegates to the exact original method, so the
     * alias and SQL are identical when off; when on, the same alias is kept and only the comment is
     * added (rendered solely by the diagnostic comment copy).
     */
    public String addSelectNodeCommented(SqlExpression node, BestFitColumnType type, String alias, String comment) {
        if (comment == null) {
            return addSelectNode(node, type, alias);
        }
        aliasCounter++;
        aliasByNode.put(node, alias);
        track(new QueryTape.SelectItem(node, type, alias, alias, comment));
        return alias;
    }

    /**
     * Sets an optional statement-level explanatory comment (e.g. the originating MDX request / cube).
     * Emitted only by the diagnostic comment render; the executed SQL is unaffected.
     */
    public void setHeaderComment(String comment) {
        track(new QueryTape.Header(comment));
    }

    /**
     * Sets an optional trailing explanatory comment (one line naming the request type + constraint
     * class), appended at the very end of the statement. Emitted only by the diagnostic comment
     * render; the executed SQL is unaffected.
     */
    public void setFooterComment(String comment) {
        track(new QueryTape.Footer(comment));
    }

    /**
     * Records an explanatory comment for the FROM-item with the given alias (the joined table's "why").
     * Emitted only by the diagnostic comment render, on the {@code FromJoin} edge bringing the table in.
     */
    public void commentFrom(String alias, String comment) {
        if (alias != null && comment != null) {
            // Accumulate: the SAME join can be requested/justified by several producers (a snowflake table
            // whose columns serve several levels, or a dimension table needed both for a selected level and
            // for a member constraint, or a join added by both the snowflake and the star-condition paths)
            // yet the FROM fold renders it as ONE join. The assembler dedups the reasons (in order) so the
            // single rendered join carries every comment, not just the last one.
            track(new QueryTape.FromComment(alias, comment));
        }
    }

    /** Dialect-free GROUP BY counterpart of {@link #addGroupBy(String, String)}. */
    public void addGroupByExpr(org.eclipse.daanse.olap.api.sql.SqlExpression expression, String alias) {
        // Feed the projection ref (when the column is projected) so the renderer renders GROUP BY as the alias or
        // the expression per requiresGroupByAlias() (renderGroupKey already does this); fall back to the node for
        // a group-by-only column. The producer consults no dialect.
        SqlExpression node = nodeOf(expression, null);
        track(new QueryTape.GroupByExprNode(node, alias));
    }

    /** A plain {@code RolapColumn} → dialect-free {@code Column} node; any computed expression → a
     *  {@code RawVariant} node carrying its per-dialect SQL map (resolved at render). */
    private SqlExpression nodeOf(org.eclipse.daanse.olap.api.sql.SqlExpression expression, String legacyStr) {
        if (expression instanceof org.eclipse.daanse.rolap.element.RolapColumn c) {
            return Expressions.column(TableAlias.of(c.getTable()), c.getName());
        }
        // Computed expression: hand the renderer the whole per-dialect map (RawVariant) so the
        // builder node carries no dialect choice.
        return Expressions.rawVariant(
                org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.sqlVariants(expression));
    }

    /**
     * Builder-authoritative node SELECT — for callers that already hold a dialect-free builder
     * {@link SqlExpression} (e.g. {@code AggStar.Table.Column.toSqlExpression()}). Like
     * {@link #addWhere(org.eclipse.daanse.sql.statement.api.expression.Predicate)}, the node goes straight to
     * the tape; GROUP BY / ORDER BY resolve the projection by the returned alias.
     */
    public String addSelectNode(SqlExpression node, BestFitColumnType type, String alias) {
        aliasCounter++;
        aliasByNode.put(node, alias);
        track(new QueryTape.SelectItem(node, type, alias, alias, null));
        return alias;
    }

    public String addSelectNode(SqlExpression node, BestFitColumnType type) {
        // Auto column alias: project without an explicit ColumnAlias; the renderer synthesizes c{ordinal} per
        // allowsFieldAlias() (identical); the op's mapAlias feeds the assembler's ref resolution.
        final String alias = nextColumnAlias();
        aliasCounter++;
        aliasByNode.put(node, alias);
        track(new QueryTape.SelectItem(node, type, null, alias, null));
        return alias;
    }

    /** Builder-authoritative node GROUP BY counterpart of {@link #addGroupBy(String, String)}. */
    public void addGroupByNode(SqlExpression node, String alias) {
        track(new QueryTape.GroupByNode(node, alias));
    }

    /** ORDER BY over a pre-built statement node: the same {@code OrderByNode} op the
     *  olap-expression form records, without the expression→node conversion. */
    public void addOrderByNode(SqlExpression node, String alias, SortingDirection sortingDirection,
            boolean prepend, boolean nullable, boolean collateNullsLast) {
        if (SortingDirection.NONE.equals(sortingDirection)) {
            return;
        }
        track(new QueryTape.OrderByNode(node, alias, sortingDirection, prepend, nullable, collateNullsLast));
    }

    /**
     * The SELECT alias previously assigned to {@code expression}, resolved by the dialect-free builder node
     * (render-equality) rather than a dialect-rendered string, or {@code null}. Used by the native-filter
     * HAVING-alias path.
     */
    public String getAlias(org.eclipse.daanse.olap.api.sql.SqlExpression expression) {
        return aliasByNode.get(nodeOf(expression, null));
    }

    public void addWhere(
        final String exprLeft,
        final String exprMid,
        final String exprRight)
    {
        int len = exprLeft.length() + exprMid.length() + exprRight.length();
        StringBuilder buf = new StringBuilder(len);

        buf.append(exprLeft);
        buf.append(exprMid);
        buf.append(exprRight);

        addWhere(buf.toString());
    }

    public void addWhere(RolapStar.Condition joinCondition) {
        addJoinCondition(joinCondition.getLeft(), joinCondition.getRight());
    }

    /**
     * Feed a star/agg join condition {@code left = right} (normally plain columns) as a dialect-free
     * {@code FromJoin} edge — the statement assembly folds it into the ANSI {@code JOIN…ON} tree and the
     * union-find dedups any overlap (a join the {@code addJoin} snowflake path also added). The renderer pushes
     * it to WHERE for {@code allowsJoinOn=false} dialects (the comma-join fallback). No-op unless BOTH tables are
     * already in the FROM, so callers must {@code addToFrom} both tables BEFORE calling this.
     *
     * <p>Two special cases:
     * <ol>
     *   <li>A side resolves to a table alias that is NOT in the FROM — the parent-walk guard
     *       (e.g. {@code RolapHierarchy.addToFrom} walking join conditions past the added relation
     *       subset). Legitimate, silent no-op — also when the OTHER side is computed.</li>
     *   <li>A side is a computed {@code <SQL>} expression ({@code getTableAlias} returns null), the
     *       resolvable counterpart (if any) being in the FROM. The condition cannot enter the JOIN
     *       tree, but silently dropping it leaves a cross join with wrong aggregates — so it is
     *       emitted as a WHERE predicate (the legacy SqlQuery behavior), the computed side as a
     *       per-dialect RawVariant node. Logged (debug) with the grep-stable marker {@code agg-join-dropped}
     *       for attribution; the emission is gate-certified.</li>
     * </ol>
     */
    public void addJoinCondition(org.eclipse.daanse.olap.api.sql.SqlExpression left,
            org.eclipse.daanse.olap.api.sql.SqlExpression right) {
        String leftAlias = getTableAlias(left);
        String rightAlias = getTableAlias(right);
        if (leftAlias == null || rightAlias == null) {
            // Case (2): a computed <SQL> join-expression side — no resolvable table alias, so the
            // condition cannot enter the JOIN tree. A resolvable counterpart OUTSIDE the FROM is the
            // parent-walk guard (case 1): silent no-op. Otherwise the condition is emitted as a
            // WHERE predicate — the legacy SqlQuery behavior; dropping it produced a cross join with
            // wrong aggregates. Marker logged at debug — the emission is gate-certified.
            String resolvable = leftAlias != null ? leftAlias : rightAlias;
            if (resolvable != null && !fromAliases.contains(resolvable)) {
                return;
            }
            RolapUtil.SQL_GEN_LOGGER.debug(
                "agg-join-dropped: join condition has a computed <SQL> side with no resolvable table "
                    + "alias (leftAlias={}, rightAlias={}, left={}, right={}); emitted as a WHERE "
                    + "predicate (legacy SqlQuery behavior) instead of being dropped.",
                leftAlias, rightAlias, left, right);
            addWhere(Predicates.comparison(nodeOf(left, null), ComparisonOperator.EQ,
                nodeOf(right, null)));
            return;
        }
        if (fromAliases.contains(leftAlias) && fromAliases.contains(rightAlias)) {
            final SqlExpression leftNode = nodeOf(left, null);
            final SqlExpression rightNode = nodeOf(right, null);
            final Predicate on = Predicates.comparison(leftNode, ComparisonOperator.EQ, rightNode);
            track(new QueryTape.JoinEdge(leftAlias, rightAlias, on));
        }
        // else: case (1) — table never registered in FROM (parent-walk guard); legitimate silent no-op.
    }

    public void addWhere(final String expression)
    {
        requireNonBlankExpression(expression);
        track(new QueryTape.WhereRaw(expression));
    }

    /**
     * Dialect-free hook: add a dialect-free {@link org.eclipse.daanse.sql.statement.api.expression.Predicate}
     * straight to the statement builder, instead of a pre-rendered
     * string. Constraint / aggregate value-constraint feeders translate their {@code StarPredicate} via
     * {@code StarPredicateTranslator.toPredicate}
     * and call this, so the producer needs no {@code Dialect}
     * at construction time — the {@code DialectSqlRenderer} applies it once at render.
     */
    public void addWhere(final org.eclipse.daanse.sql.statement.api.expression.Predicate predicate) {
        track(new QueryTape.WherePredicate(predicate));
    }

    /**
     * As {@link #addWhere(org.eclipse.daanse.sql.statement.api.expression.Predicate)} with an explanatory
     * comment (member path / slicer range / role-access / exclude). Emitted only by the diagnostic comment
     * render; the executed SQL is unaffected. A null comment behaves exactly like the plain overload.
     */
    public void addWhere(final org.eclipse.daanse.sql.statement.api.expression.Predicate predicate, String comment) {
        track(new QueryTape.WherePredicateCommented(predicate, comment));
    }

    /**
     * Adds an expression to the GROUP BY clause, using the alias if the dialect requires it.
     *
     * @param expression the expression to group by
     * @param alias the column alias to use if the dialect requires GROUP BY aliases
     */
    public void addGroupBy(final String expression, final String alias) {
        // The assembler feeds the projection ref (when projected) so the renderer renders the alias or the
        // expression per requiresGroupByAlias(); it falls back to the raw expression otherwise.
        track(new QueryTape.GroupByAliased(expression, alias));
    }

    /**
     * A dialect-free HAVING conjunct {@link Predicate} with a provenance comment (emitted
     * only when comments are on), replayed via the builder's {@code having(Predicate[, comment])} and
     * deduped at assembly by node value — node equality is render-equality, so two conjuncts that
     * would have deduped as identical strings dedup identically as nodes.
     */
    public void addHaving(final Predicate predicate, final String comment) {
        java.util.Objects.requireNonNull(predicate, "predicate must not be null");
        track(new QueryTape.HavingNode(predicate, comment));
    }

    /**
     * Adds an item to the ORDER BY clause.
     *
     * @param expr the expr to order by
     * @param sortingDirection sort direction
     * @param prepend whether to prepend to the current list of items
     * @param nullable whether the expression might be null
     */
    public void addOrderBy(
        CharSequence expr,
        SortingDirection sortingDirection,
        boolean prepend,
        boolean nullable)
    {
        this.addOrderBy(expr, expr, sortingDirection, prepend, nullable, true);
    }

    /**
     * Adds an item to the ORDER BY clause.
     *
     * @param expr the expr to order by
     * @param alias the alias of the column, as returned by addSelect
     * @param sortingDirection sort direction
     * @param prepend whether to prepend to the current list of items
     * @param nullable whether the expression might be null
     * @param collateNullsLast whether null values should appear first or last.
     */
    public void addOrderBy(
        CharSequence expr,
        CharSequence alias,
        SortingDirection sortingDirection,
        boolean prepend,
        boolean nullable,
        boolean collateNullsLast)
    {
        if (SortingDirection.NONE.equals(sortingDirection)) {
            return;
        }
        track(new QueryTape.OrderBy(expr.toString(), alias == null ? null : alias.toString(),
                sortingDirection, prepend, nullable, collateNullsLast));
    }

    /**
     * Dialect-free ORDER BY counterpart of {@link #addOrderBy(CharSequence, CharSequence, SortingDirection,
     * boolean, boolean, boolean)}: the caller passes the ROLAP expression; the builder gets the
     * alias {@code ProjectionRef} (or a dialect-free node for the non-alias case).
     */
    public void addOrderByExpr(
        org.eclipse.daanse.olap.api.sql.SqlExpression expression,
        CharSequence alias,
        SortingDirection sortingDirection,
        boolean prepend,
        boolean nullable,
        boolean collateNullsLast)
    {
        SqlExpression node = nodeOf(expression, null);
        if (SortingDirection.NONE.equals(sortingDirection)) {
            return;
        }
        track(new QueryTape.OrderByNode(node, alias == null ? null : alias.toString(),
                sortingDirection, prepend, nullable, collateNullsLast));
    }

    /** Dialect-free parent-value (nullParentValue) ORDER BY: nulls sort as if they held
     *  {@code nullParentValue} of the given {@code type}. */
    public void addOrderByExpr(org.eclipse.daanse.olap.api.sql.SqlExpression expression, String alias,
            SortingDirection sortingDirection, boolean prepend, String nullParentValue,
            org.eclipse.daanse.jdbc.db.api.type.Datatype type, boolean collateNullsLast) {
        SqlExpression node = nodeOf(expression, null);
        if (SortingDirection.NONE.equals(sortingDirection)) {
            return;
        }
        track(new QueryTape.OrderByValueNode(node, alias, sortingDirection, prepend, nullParentValue,
                type, collateNullsLast));
    }

    /** Whether this query renders multi-line/indented SQL (the {@code generateFormattedSql} flag). */
    public boolean isFormatted() {
        return generateFormattedSql;
    }

    public void addGroupingSet(List<String> groupingColumnsExpr) {
        track(new QueryTape.GroupingSet(groupingColumnsExpr));
    }

    public void addGroupingFunction(String columnExpr) {
        track(new QueryTape.GroupingFunction(columnExpr));
    }

    public void registerRootRelation(org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource root) {
        // REVIEW: These data structures describe the star schema's shape; they belong in the
        // schema model rather than being rebuilt for each query.
        if (mapRelationToRoot.containsKey(root)) {
            return;
        }
        if (mapRootToRelations.containsKey(root)) {
            return;
        }
        List<RelInfo> relations = new ArrayList<>();
        flatten(relations, root, null, null, null, null);
        for (RelInfo relation : relations) {
            mapRelationToRoot.put(relation.relation(), root);
        }
        mapRootToRelations.put(root, relations);
    }

    private void flatten(
        List<RelInfo> relations,
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource root,
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column leftKey,
        String leftAlias,
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column rightKey,
        String rightAlias)
    {
        if (root instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            flatten(
                relations, left(join), join.getLeft().getKey(), getLeftAlias(join),
                join.getRight().getKey(), getRightAlias(join));
            flatten(
                relations, right(join), leftKey, leftAlias, rightKey,
                rightAlias);
        } else {
            relations.add(
                new RelInfo(
                    (org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource) root,
                    leftKey,
                    leftAlias,
                    rightKey,
                    rightAlias));
        }
    }

    /**
     * Holds information about a relation in a hierarchical query structure,
     * including join keys and table aliases for building complex FROM clauses.
     *
     * @param relation the relational query (table or subquery)
     * @param leftKey the column used as the left join key
     * @param leftAlias the alias for the left table
     * @param rightKey the column used as the right join key
     * @param rightAlias the alias for the right table
     */
    private record RelInfo(
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column leftKey,
        String leftAlias,
        org.eclipse.daanse.cwm.model.cwm.resource.relational.Column rightKey,
        String rightAlias) {
    }

}

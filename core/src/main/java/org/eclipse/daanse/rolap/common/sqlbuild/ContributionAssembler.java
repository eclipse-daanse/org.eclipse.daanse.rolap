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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.rolap.common.sql.QueryTape;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.ColumnAlias;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.JoinKind;
import org.eclipse.daanse.sql.statement.api.model.NullOrder;
import org.eclipse.daanse.sql.statement.api.model.ProjectionRef;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import org.eclipse.daanse.sql.statement.api.model.SortDirection;
import org.eclipse.daanse.sql.statement.api.model.SortSpec;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;

/**
 * Replays a recorded {@link QueryTape} against a fresh builder to assemble the dialect-free
 * {@link SelectStatement} (dedup, FROM/JOIN folding, deferred sub-query FROM resolution).
 * {@code QueryRecorder.buildStatement()} delegates here.
 *
 * <p>Each {@link QueryTape.QueryOp} replays with the semantics the recorder's mutators promise
 * (same dedup sets, same {@code projByAlias} bookkeeping, same builder overloads), so a tape
 * assembles to the same SQL no matter when — or how often — it is rendered.
 */
public final class ContributionAssembler {

    private ContributionAssembler() {
    }

    /** Replays the tape against fresh state and assembles the dialect-free statement. */
    public static SelectStatement assemble(QueryTape ops) {
        return buildStatement(replay(ops));
    }

    /**
     * The replayed per-query bookkeeping: the builder plus the dedup sets and alias→projection
     * map the op replays share. Fresh per assembly.
     */
    private static final class State {
        final SelectStatementBuilder builder = SelectStatementBuilder.create();
        final List<FromClause> fromItems = new ArrayList<>();
        final List<QueryTape.JoinEdge> builderJoins = new ArrayList<>();
        final Map<Integer, Supplier<FromClause>> deferredFroms = new LinkedHashMap<>();
        final Set<String> builderFromAliases = new LinkedHashSet<>();
        final Set<String> whereSeen = new LinkedHashSet<>();
        final Set<String> groupSeen = new LinkedHashSet<>();
        final Set<String> havingSeen = new LinkedHashSet<>();
        final Set<String> orderSeen = new LinkedHashSet<>();
        final Map<String, ProjectionRef> projByAlias = new HashMap<>();
        final Map<String, LinkedHashSet<String>> fromItemComments = new HashMap<>();
    }

    /**
     * Replays every op in recorded order — each case applies the op payload with the
     * dedup/aliasing semantics the recorder's corresponding {@code track()} method promises.
     */
    private static State replay(QueryTape ops) {
        State st = new State();
        for (QueryTape.QueryOp op : ops.ops()) {
            switch (op) {
                case QueryTape.Distinct o -> st.builder.distinct(o.value());
                case QueryTape.FromRendered o -> {
                    if (st.builderFromAliases.add(o.alias())) {
                        st.fromItems.add(o.node());
                    }
                }
                case QueryTape.FromDeferred o -> {
                    if (st.builderFromAliases.add(o.alias())) {
                        st.deferredFroms.put(st.fromItems.size(), o.resolver());
                        st.fromItems.add(new FromClause.FromRaw("", TableAlias.of(o.alias())));
                    }
                }
                case QueryTape.FromTable o -> {
                    if (st.builderFromAliases.add(o.alias())) {
                        st.fromItems.add(
                                From.table(o.schema(), o.name(), TableAlias.of(o.alias()), null, o.hints()));
                    }
                }
                case QueryTape.JoinEdge o -> st.builderJoins.add(o);
                case QueryTape.SelectItem o -> {
                    ColumnAlias columnAlias = o.explicitAlias() != null ? ColumnAlias.of(o.explicitAlias()) : null;
                    ProjectionRef ref;
                    if (o.comment() != null) {
                        ref = st.builder.project(o.node(), o.type(), columnAlias, o.comment());
                    } else if (columnAlias != null) {
                        ref = st.builder.project(o.node(), o.type(), columnAlias);
                    } else {
                        ref = st.builder.project(o.node(), o.type());
                    }
                    if (o.mapAlias() != null) {
                        st.projByAlias.put(o.mapAlias(), ref);
                    }
                }
                case QueryTape.Header o -> st.builder.header(o.comment());
                case QueryTape.Footer o -> st.builder.footerComment(o.comment());
                case QueryTape.FromComment o -> st.fromItemComments
                        .computeIfAbsent(o.alias(), k -> new LinkedHashSet<>()).add(o.comment());
                case QueryTape.GroupByAliased o -> {
                    if (st.groupSeen.add(o.expression())) {
                        ProjectionRef ref = st.projByAlias.get(o.alias());
                        if (ref != null) {
                            st.builder.groupOn(ref);
                        } else {
                            st.builder.groupOn(Expressions.raw(o.expression()));
                        }
                    }
                }
                case QueryTape.GroupByExprNode o -> {
                    if (st.groupSeen.add("e:" + o.node())) {
                        ProjectionRef ref = st.projByAlias.get(o.alias());
                        if (ref != null) {
                            st.builder.groupOn(ref);
                        } else {
                            st.builder.groupOn(o.node());
                        }
                    }
                }
                case QueryTape.GroupByNode o -> {
                    if (st.groupSeen.add("node:" + o.alias())) {
                        ProjectionRef ref = st.projByAlias.get(o.alias());
                        if (ref != null) {
                            st.builder.groupOn(ref);
                        } else {
                            st.builder.groupOn(o.node());
                        }
                    }
                }
                case QueryTape.WhereRaw o -> {
                    if (st.whereSeen.add(o.expression())) {
                        st.builder.where(Predicates.raw(o.expression()));
                    }
                }
                case QueryTape.WherePredicate o -> st.builder.where(o.predicate());
                case QueryTape.WherePredicateCommented o -> st.builder.where(o.predicate(), o.comment());
                case QueryTape.HavingNode o -> {
                    // Dedup by node value (prefixed, mirroring GroupByExprNode's "e:" pattern) —
                    // node equality is render-equality, so identical conjuncts dedup exactly as
                    // their identical strings did under the raw Having op.
                    if (st.havingSeen.add("node:" + o.predicate())) {
                        if (o.comment() != null) {
                            st.builder.having(o.predicate(), o.comment());
                        } else {
                            st.builder.having(o.predicate());
                        }
                    }
                }
                case QueryTape.OrderBy o -> {
                    boolean useAlias = o.alias() != null;
                    ProjectionRef ref = useAlias ? st.projByAlias.get(o.alias()) : null;
                    SortDirection dir = SortingDirection.DESC.equals(o.direction())
                            ? SortDirection.DESC : SortDirection.ASC;
                    SortSpec spec = new SortSpec(dir, o.nullable(),
                            o.collateNullsLast() ? NullOrder.LAST : NullOrder.FIRST, o.prepend());
                    // Dedup by the ORDER-BY EXPRESSION (not the alias): two aliases projecting the
                    // same expression are one sort key — dialects that render the expression form
                    // would emit a duplicate, which e.g. MSSQL rejects ("column specified more than
                    // once in the order by list"). A redundant duplicate sort key is a semantic no-op.
                    String key = "e:" + o.expr()
                            + "|" + dir + "|" + o.nullable() + "|" + o.collateNullsLast();
                    if (st.orderSeen.add(key)) {
                        if (ref != null) {
                            st.builder.orderOn(ref, spec);
                        } else {
                            st.builder.orderOn(Expressions.raw(o.expr()), spec);
                        }
                    }
                }
                case QueryTape.OrderByNode o -> {
                    boolean useAlias = o.alias() != null;
                    ProjectionRef ref = useAlias ? st.projByAlias.get(o.alias()) : null;
                    SortDirection dir = SortingDirection.DESC.equals(o.direction())
                            ? SortDirection.DESC : SortDirection.ASC;
                    SortSpec spec = new SortSpec(dir, o.nullable(),
                            o.collateNullsLast() ? NullOrder.LAST : NullOrder.FIRST, o.prepend());
                    // Node-value dedup (see the OrderBy case above): node equality is
                    // render-equality, mirroring the GroupByExprNode "e:" pattern.
                    String key = "e:" + o.node()
                            + "|" + dir + "|" + o.nullable() + "|" + o.collateNullsLast();
                    if (st.orderSeen.add(key)) {
                        if (ref != null) {
                            st.builder.orderOn(ref, spec);
                        } else {
                            st.builder.orderOn(o.node(), spec);
                        }
                    }
                }
                case QueryTape.OrderByValueNode o -> {
                    boolean useAlias = o.alias() != null;
                    ProjectionRef ref = useAlias ? st.projByAlias.get(o.alias()) : null;
                    SortDirection dir = SortingDirection.DESC.equals(o.direction())
                            ? SortDirection.DESC : SortDirection.ASC;
                    SortSpec spec = new SortSpec(dir, true,
                            o.collateNullsLast() ? NullOrder.LAST : NullOrder.FIRST, o.prepend())
                            .withNullSortValue(o.nullParentValue(), o.type());
                    String key = "ov|e:" + o.node() + "|" + dir + "|"
                            + o.nullParentValue() + "|" + o.collateNullsLast();
                    if (st.orderSeen.add(key)) {
                        if (ref != null) {
                            st.builder.orderOn(ref, spec);
                        } else {
                            st.builder.orderOn(o.node(), spec);
                        }
                    }
                }
                case QueryTape.GroupingSet o -> {
                    List<SqlExpression> keys = new ArrayList<>();
                    for (String c : o.columns()) {
                        keys.add(Expressions.raw(c));
                    }
                    st.builder.addGroupingSet(keys);
                }
                case QueryTape.GroupingFunction o ->
                        st.builder.addGroupingFunction(Expressions.raw(o.columnExpr()));
            }
        }
        return st;
    }

    /**
     * Resolves the deferred FROMs, folds the FROM items and builds. The state is fresh per
     * assembly, so the FROM is applied exactly once.
     */
    private static SelectStatement buildStatement(State st) {
        // Resolve the deferred sub-query FROMs — dialect-free, recursively (a nested sub-query's own
        // deferred FROMs resolve via its buildStatement()); the renderer applies the dialect at render.
        st.deferredFroms.forEach((idx, resolver) -> st.fromItems.set(idx, resolver.get()));
        FromClause fromClause = buildFromClause(st);
        if (fromClause != null) {
            st.builder.from(fromClause);
        }
        return st.builder.build();
    }

    /**
     * The collected FROM items folded into a {@code FromJoin} tree by the recorded ANSI-join
     * edges (left-deep), or a bare {@code FromProduct} (comma join) when there are no ANSI-join
     * edges.
     */
    private static FromClause buildFromClause(State st) {
        if (st.fromItems.isEmpty()) {
            return null;
        }
        // Base-FROM provenance: reasons recorded (commentFrom) against the BASE item's alias are
        // attached to the item itself — the fold below only reaches JOINED items (their reasons merge
        // into the FromJoin comment), so without this the base table renders comment-less. Applies to
        // every FROM shape (single item, join-tree root, product first item); render-only (no effect
        // on the executed SQL when comments are off).
        org.eclipse.daanse.sql.statement.api.model.TableAlias baseAlias =
                org.eclipse.daanse.sql.statement.api.From.baseAlias(st.fromItems.get(0));
        if (baseAlias != null) {
            Set<String> baseReasons = st.fromItemComments.get(baseAlias.name());
            if (baseReasons != null && !baseReasons.isEmpty()) {
                st.fromItems.set(0, org.eclipse.daanse.sql.statement.api.From.commentBase(
                        st.fromItems.get(0), String.join(" · ", baseReasons)));
            }
        }
        if (st.fromItems.size() == 1 || st.builderJoins.isEmpty()) {
            return st.fromItems.size() == 1 ? st.fromItems.get(0) : new FromClause.FromProduct(st.fromItems);
        }
        // Index the join edges by each endpoint alias (undirected: an edge's left/right is schema-traversal
        // order, not FROM order).
        Map<String, List<QueryTape.JoinEdge>> edges = new HashMap<>();
        for (QueryTape.JoinEdge j : st.builderJoins) {
            edges.computeIfAbsent(j.leftAlias(), k -> new ArrayList<>()).add(j);
            edges.computeIfAbsent(j.rightAlias(), k -> new ArrayList<>()).add(j);
        }
        // Fold fromItems into a LEFT-DEEP FromJoin tree in insertion order: each item is joined to the
        // accumulated tree by the first edge connecting it to an already-placed table. (The renderer requires
        // left-deep — JOIN is left-associative and it adds no parentheses.) The single-edge-per-item lookup
        // naturally dedups a join added by both the snowflake (addJoin) and star (addWhere(Condition)) paths.
        // Items with no connecting edge stay as a comma-product (disconnected component / cross join).
        FromClause acc = st.fromItems.get(0);
        Set<String> placed = new HashSet<>();
        placed.add(aliasOf(st.fromItems.get(0)));
        Set<Predicate> treeOns = new HashSet<>();
        List<FromClause> pending = new ArrayList<>(st.fromItems.subList(1, st.fromItems.size()));
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            List<FromClause> stillPending = new ArrayList<>();
            for (FromClause item : pending) {
                String a = aliasOf(item);
                Predicate on = null;
                for (QueryTape.JoinEdge j : edges.getOrDefault(a, List.of())) {
                    String other = j.leftAlias().equals(a) ? j.rightAlias() : j.leftAlias();
                    if (placed.contains(other)) {
                        on = j.on();
                        break;
                    }
                }
                if (on != null) {
                    // Merge all accumulated reasons for this (deduped) join into one comment.
                    Set<String> reasons = st.fromItemComments.get(a);
                    String mergedComment = (reasons == null || reasons.isEmpty())
                            ? null : String.join(" · ", reasons);
                    acc = new FromClause.FromJoin(acc, JoinKind.INNER, item, on,
                            Optional.ofNullable(mergedComment));
                    placed.add(a);
                    treeOns.add(on);
                    progress = true;
                } else {
                    stillPending.add(item);
                }
            }
            pending = stillPending;
        }
        // Safety: a join condition the single-edge-per-table tree could NOT carry (a cyclic/diamond graph — the
        // edge's two tables were both already placed via other edges) would otherwise be silently lost. Append
        // each such unused edge to WHERE, deduped by predicate value so a join already used as a tree ON (or
        // added twice by the addJoin + addWhere(Condition) paths) is not re-emitted. No-op for tree-shaped joins.
        for (QueryTape.JoinEdge j : st.builderJoins) {
            if (treeOns.add(j.on())) {
                st.builder.where(j.on());
            }
        }
        if (pending.isEmpty()) {
            return acc;
        }
        List<FromClause> product = new ArrayList<>();
        product.add(acc);
        product.addAll(pending);
        return new FromClause.FromProduct(product);
    }

    /** The alias of an aliased FROM item, else {@code null}. */
    private static String aliasOf(FromClause fc) {
        return fc instanceof FromClause.Aliased a ? a.alias().name() : null;
    }
}

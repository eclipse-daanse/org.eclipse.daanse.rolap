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

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.common.util.JoinUtil;
import org.eclipse.daanse.rolap.common.util.RelationUtil;
import org.eclipse.daanse.rolap.common.util.ViewUtil;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.From;
import org.eclipse.daanse.sql.statement.api.Predicates;
import org.eclipse.daanse.sql.statement.api.expression.ComparisonOperator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.model.FromClause;
import org.eclipse.daanse.sql.statement.api.model.JoinKind;
import org.eclipse.daanse.sql.statement.api.model.TableAlias;

/**
 * Maps a ROLAP mapping-level {@code RelationalSource} (the relation tree behind a hierarchy) to
 * the generic builder's {@link FromClause}, dialect-free and as an immutable tree.
 * <p>
 * Supported shapes ({@link #supports}): {@code TableSource} → a single
 * {@link FromClause.FromTable}; {@code JoinSource} (snowflake, any nesting) → a nested
 * {@link FromClause.FromJoin} ({@code INNER}) whose {@code ON} is the {@code left.key = right.key}
 * equality, each key qualified by the alias of the leftmost table of its side (the
 * {@code JoinUtil} resolution). The renderer turns the {@code FromJoin} into either an ANSI
 * {@code JOIN…ON} or — when {@code allowsJoinOn()=false} — a comma product with the conditions in
 * {@code WHERE}. {@link #from} additionally builds a lone {@code SqlSelectSource} (view) as a
 * {@link FromClause.FromVariant} and a lone {@code InlineTableSource} as a
 * {@link FromClause.FromInline} — executed routes gate on {@link #supports} (tables/joins only) or
 * {@link #isSingleViewOrInline} (the exotic single-relation shapes); everything else stays on the
 * recorder.
 */
public final class RelationFromMapper {

    private RelationFromMapper() {
    }

    /** True if {@link #from} can map this relation: a {@code TableSource} or a {@code JoinSource}
     * (of any nesting depth) of supported sides. */
    public static boolean supports(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource) {
            return true;
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            return supports(join.getLeft().getSource()) && supports(join.getRight().getSource());
        }
        return false;
    }

    /**
     * True if this relation is a SINGLE view ({@code SqlSelectSource}) or inline table
     * ({@code InlineTableSource}) — the exotic shapes {@link #from} maps to a lone
     * {@code FromVariant}/{@code FromInline} with no join. A {@code JoinSource} whose sides include
     * a view/inline is deliberately NOT accepted: such a join places the level key on a
     * joined side that the per-level subset FROM ({@code fromReferenced}) drops, diverging from the
     * recorder (e.g. {@code [Store].[Store Country]} = store JOIN inline-nation). Gates the
     * exotic-level member-children route so ONLY the single-relation view/inline shapes are built
     * authoritatively.
     */
    public static boolean isSingleViewOrInline(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        return relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource
                || relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource;
    }

    /**
     * True when a SINGLE view/inline relation (see {@link #isSingleViewOrInline}) actually resolves
     * to a non-empty FROM body — as opposed to a DEGENERATE relation whose {@link #from} would emit
     * broken SQL. The writeback {@code [Scenario]} dimension carries a {@code SqlSelectSource} with
     * an EMPTY body: it renders as {@code from () as "foo"} and even the recorder emits a
     * {@code SQLSyntaxError} (caught upstream). Guards the count widening so ONLY the resolvable
     * exotic single-relation shapes ({@code [Alternative Promotion]},
     * {@code [Shared Alternative Promotion]}) route to the builder count; the degenerate scenario
     * relation stays on the recorder (its broken read is unchanged). A view resolves when at least
     * one dialect variant carries a non-blank SQL body; an inline table resolves when it declares
     * at least one column.
     */
    public static boolean resolvesSingleViewOrInline(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource view) {
            return ViewUtil.getCodeSet(view).asMap().values().stream()
                    .anyMatch(sql -> sql != null && !sql.isBlank());
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource inline) {
            return !RolapUtil.inlineTableData(inline).columnNames().isEmpty();
        }
        return false;
    }

    /** The {@link FromClause} tree for a supported relation (see {@link #supports}). */
    public static FromClause from(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table) {
            return fromTable(table);
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            return new FromClause.FromJoin(from(join.getLeft().getSource()), JoinKind.INNER,
                    from(join.getRight().getSource()), joinOn(join));
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource view) {
            // Dialect-free: hand the renderer the whole per-dialect view map (DialectSqlRenderer.chooseVariant
            // picks the live dialect's entry at render).
            return new FromClause.FromVariant(ViewUtil.getCodeSet(view).asMap(),
                    TableAlias.of(RelationUtil.getAlias(view)));
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource inline) {
            // Dialect-free: carry the inline data as a FromInline node; the renderer generates the
            // dialect-specific VALUES SQL at render time.
            RolapUtil.InlineTableData d = RolapUtil.inlineTableData(inline);
            return From.inline(d.columnNames(), d.columnTypes(), d.rows(),
                    TableAlias.of(RelationUtil.getAlias(inline)));
        }
        throw new IllegalArgumentException("unsupported relation: " + relation);
    }

    /**
     * Like {@link #from} but restricted to the tables whose alias is in {@code included}: a join
     * side with no included table is dropped (its other side stands in for it). Combine with
     * {@link #memberFromTables} to produce the member-enumeration FROM, which omits
     * relation tables a query does not reach. Returns {@code null} when no table is included.
     */
    public static FromClause fromReferenced(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
            Set<String> included) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table) {
            return included.contains(RelationUtil.getAlias(table)) ? fromTable(table) : null;
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            FromClause left = fromReferenced(join.getLeft().getSource(), included);
            FromClause right = fromReferenced(join.getRight().getSource(), included);
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return new FromClause.FromJoin(left, JoinKind.INNER, right, joinOn(join));
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource view) {
            return included.contains(RelationUtil.getAlias(view))
                    ? new FromClause.FromVariant(ViewUtil.getCodeSet(view).asMap(),
                            TableAlias.of(RelationUtil.getAlias(view)))
                    : null;
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource inline) {
            if (!included.contains(RelationUtil.getAlias(inline))) {
                return null;
            }
            RolapUtil.InlineTableData d = RolapUtil.inlineTableData(inline);
            return From.inline(d.columnNames(), d.columnTypes(), d.rows(),
                    TableAlias.of(RelationUtil.getAlias(inline)));
        }
        throw new IllegalArgumentException("unsupported relation: " + relation);
    }

    /**
     * The table aliases a member-enumeration query joins for the given level tables: for each
     * alias, the tables of the smallest join subset that contains it — under
     * {@code FilterChildlessSnowflakeMembers} that subset reaches back to the join root (so a level
     * whose table is in a join's right branch pulls in its ancestor tables for childless-member
     * filtering), while a table at the root contributes only itself. This is the dialect-free
     * counterpart of {@code RolapHierarchy.addToFrom}'s {@code relationSubset}.
     */
    public static Set<String> memberFromTables(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
            Set<String> levelTableAliases) {
        Set<String> out = new LinkedHashSet<>();
        for (String alias : levelTableAliases) {
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource subset =
                    relationSubset(relation, alias);
            collectTableAliases(subset == null ? relation : subset, out);
        }
        return out;
    }

    /** The {@code left.key = right.key} equality for a join, qualified via {@link JoinUtil}.
     *  Package-visible: {@code TupleSqlMapper.productTupleLevelMembersSql} re-emits the same
     *  equality as a WHERE conjunct for the comma-product (no-fact-join) tuple shape. */
    static Predicate joinOn(
            org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
        return Predicates.comparison(
                Expressions.column(TableAlias.of(JoinUtil.getLeftAlias(join)), join.getLeft().getKey().getName()),
                ComparisonOperator.EQ,
                Expressions.column(TableAlias.of(JoinUtil.getRightAlias(join)), join.getRight().getKey().getName()));
    }

    /**
     * The smallest subset of {@code relation} containing the table {@code alias}, honouring
     * {@code FilterChildlessSnowflakeMembers} — the single home of the algorithm
     * ({@code RolapHierarchy.addToFrom} delegates here). A null relation node is a model error.
     */
    public static org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relationSubset(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation, String alias) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource right =
                    relationSubset(join.getRight().getSource(), alias);
            if (right == null) {
                return relationSubset(join.getLeft().getSource(), alias);
            }
            return SystemWideProperties.instance().FilterChildlessSnowflakeMembers ? join : right;
        }
        if (relation == null) {
            throw org.eclipse.daanse.olap.common.Util.newInternal("bad relation type " + relation);
        }
        return RelationUtil.getAlias(relation).equals(alias) ? relation : null;
    }

    /**
     * The INVERSE-subset FROM used with aggregate tables: the {@link FromClause} of
     * {@link #relationSubsetInverse}, i.e. the level-key table together with its LEAF-WARD join
     * subtree (when the alias sits in a join's LEFT branch the whole join is kept — the tables
     * between the level key and the hierarchy's leaf side — while root-ward ancestors are
     * dropped). The recorder path adds this subset first and then joins the agg table to it
     * "starting at the lowest granularity and working towards the fact table". A non-join relation,
     * or a {@code null} alias, maps whole — the same guard {@code addToFromInverse} applies.
     */
    public static FromClause fromInverse(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation, String alias) {
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource subRelation = relation;
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource
                && alias != null) {
            subRelation = relationSubsetInverse(relation, alias);
        }
        return from(subRelation);
    }

    /**
     * The smallest subset of {@code relation} containing the table {@code alias} in INVERSE join
     * order (used with agg tables): a
     * join is kept WHOLE when the alias is found in its LEFT branch (the leaf-ward subtree rides
     * along), and recursed into when the alias is only in the right branch. Unlike
     * {@link #relationSubset} this does NOT consult {@code FilterChildlessSnowflakeMembers}.
     * Returns {@code null} when no table matches; a null relation node is a model error.
     */
    public static org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relationSubsetInverse(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation, String alias) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource left =
                    relationSubsetInverse(join.getLeft().getSource(), alias);
            return left == null ? relationSubsetInverse(join.getRight().getSource(), alias) : join;
        }
        if (relation == null) {
            throw org.eclipse.daanse.olap.common.Util.newInternal("bad relation type " + relation);
        }
        return RelationUtil.getAlias(relation).equals(alias) ? relation : null;
    }

    /**
     * The alias of the table a FROM emits FIRST when adding the relation
     * subset for {@code tableAlias} — the leftmost leaf of {@link #relationSubset} (or of the whole
     * relation when {@code tableAlias} is null/unresolvable — the whole-relation
     * fallback for computed columns). This is the query's BASE FROM table, the anchor a base-table
     * provenance comment ({@code commentFrom}) must be keyed to.
     */
    public static String baseAliasFor(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
            String tableAlias) {
        if (relation == null) {
            return null;
        }
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource subset =
                tableAlias == null ? null : relationSubset(relation, tableAlias);
        return leftmostAlias(subset != null ? subset : relation);
    }

    /** The alias of the leftmost leaf of a relation tree (the first table a FROM emits). */
    private static String leftmostAlias(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            return leftmostAlias(join.getLeft().getSource());
        }
        return RelationUtil.getAlias(relation);
    }

    /** The table aliases a relation contributes to a FROM clause (its own table(s)/view(s)). */
    public static Set<String> tableAliases(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        Set<String> out = new java.util.LinkedHashSet<>();
        collectTableAliases(relation, out);
        return out;
    }

    private static void collectTableAliases(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation, Set<String> out) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource join) {
            collectTableAliases(join.getLeft().getSource(), out);
            collectTableAliases(join.getRight().getSource(), out);
        } else if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource
                || relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource
                || relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.InlineTableSource) {
            // views (SqlSelectSource) and inline tables (InlineTableSource) are leaf relations too —
            // collect their alias so a view/inline-backed level resolves into the snowflake subset
            // (the FROM renders them via FromVariant/FromInline; the recorder registers inline
            // aliases the same way). Without the inline alias, memberFromTables would drop the
            // inline side of a store JOIN inline-nation relation.
            out.add(RelationUtil.getAlias(relation));
        }
    }

    /**
     * The table's schema-defined {@code sqlWhereExpression} as a parenthesised raw predicate for
     * the {@code FromTable.filter} slot, or {@code null} when the relation carries none. The
     * recorder emits the same filter in {@code QueryRecorder.addFromTable} (the filter joins the
     * WHERE as its own conjunct when the table enters the FROM); the renderer pushes a
     * {@code FromTable.filter} into WHERE the same way ({@code DialectSqlRenderer.renderFrom}),
     * after the statement's explicit conjuncts. Shared by every reader mapper that puts a
     * star/mapping table into a FROM, so a table-level filter is never silently dropped.
     */
    public static Predicate tableFilter(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation) {
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource ts
                && ts.getSqlWhereExpression() != null) {
            String sql = ts.getSqlWhereExpression().getSql();
            if (sql != null && !sql.isBlank()) {
                return Predicates.raw("(" + sql + ")");
            }
        }
        return null;
    }

    /**
     * Collects every {@code FromTable.filter} of the tree into {@code sink} — left-deep, in
     * FROM-entry order, the order the recorder appends each table's schema SQL filter to WHERE as
     * the table enters the FROM ({@code QueryRecorder.addFromTable}) — and returns the tree with
     * the slots CLEARED. The level/tuple assembly ({@code TupleSqlMapper.buildLevelSelect}) emits
     * the collected filters as LEADING explicit WHERE conjuncts, BEFORE the constraint conjuncts:
     * leaving them in the slot renders them AFTER the explicit conjuncts instead
     * ({@code DialectSqlRenderer} pushes slot filters into WHERE last), giving the wrong conjunct
     * order. Clearing the slot prevents the double emission.
     */
    public static FromClause liftTableFilters(FromClause from, java.util.List<Predicate> sink) {
        if (from instanceof FromClause.FromTable t) {
            if (t.filter().isPresent()) {
                sink.add(t.filter().get());
                return new FromClause.FromTable(t.table(), t.alias(), java.util.Optional.empty(),
                        t.hints(), t.comment());
            }
            return t;
        }
        if (from instanceof FromClause.FromJoin j) {
            FromClause left = liftTableFilters(j.left(), sink);
            FromClause right = liftTableFilters(j.right(), sink);
            return (left == j.left() && right == j.right()) ? j
                    : new FromClause.FromJoin(left, j.kind(), right, j.on(), j.comment());
        }
        if (from instanceof FromClause.FromProduct p) {
            java.util.List<FromClause> items = new java.util.ArrayList<>();
            boolean changed = false;
            for (FromClause item : p.items()) {
                FromClause lifted = liftTableFilters(item, sink);
                changed |= lifted != item;
                items.add(lifted);
            }
            return changed ? new FromClause.FromProduct(items) : p;
        }
        return from;
    }

    private static FromClause.FromTable fromTable(
            org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table) {
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs = table.getTable();
        String name = ncs.getName();
        String alias = RelationUtil.getAlias(table);
        String schema = schemaName(ncs);
        // Carry the table's schema SQL filter in the FromTable.filter slot (the renderer emits
        // it as a WHERE conjunct), reproducing the recorder's addFromTable filter semantics.
        return From.table(From.tableRef(schema, name), TableAlias.of(alias), tableFilter(table),
                java.util.Map.of());
    }

    /** The schema qualifying a table, or null. Package-visible: {@code AggJoinPlanner.aggTableFrom}
     *  builds the same schema-qualified table reference for an agg-chain table. */
    static String schemaName(
            org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs) {
        if (ncs.getNamespace() instanceof org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema schema) {
            return schema.getName();
        }
        return null;
    }
}

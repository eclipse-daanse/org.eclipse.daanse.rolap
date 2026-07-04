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
 * {@code WHERE}. {@code SqlSelectSource} (view) and
 * {@code InlineTableSource} are not handled. Callers gate on {@link #supports} and fall back to the
 * reference SQL for everything else.
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

    /** The {@code left.key = right.key} equality for a join, qualified via {@link JoinUtil}. */
    private static Predicate joinOn(
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
                || relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.SqlSelectSource) {
            // views (SqlSelectSource) are leaf relations too — collect their alias so a view-backed
            // level resolves into the snowflake subset (dialect-aware FROM renders it via From.raw).
            out.add(RelationUtil.getAlias(relation));
        }
    }

    private static FromClause.FromTable fromTable(
            org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table) {
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs = table.getTable();
        String name = ncs.getName();
        String alias = RelationUtil.getAlias(table);
        String schema = schemaName(ncs);
        return schema != null ? From.table(schema, name, TableAlias.of(alias))
                : From.table(name, TableAlias.of(alias));
    }

    private static String schemaName(
            org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet ncs) {
        if (ncs.getNamespace() instanceof org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema schema) {
            return schema.getName();
        }
        return null;
    }
}

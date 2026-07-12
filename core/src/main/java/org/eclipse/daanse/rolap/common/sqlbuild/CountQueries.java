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

import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;

/**
 * The cardinality/count SELECTs (split out of {@link TupleSqlMapper}, pure code motion): the
 * distinct-keys level count, the leaf-level row count and the aggregate-table row count — the
 * builder shapes of {@code SqlMemberSource.makeLevelMemberCountSql} and
 * {@code AggStar.Table.makeNumberOfRows}.
 */
public final class CountQueries {

    private CountQueries() {
    }

    /**
     * True if {@link #levelMemberCountSql} can build the count query for {@code targetLevel}: a
     * supported relation. The {@code SELECT … FROM (SELECT DISTINCT …)} subquery shape additionally
     * requires {@code dialect.allowsFromQuery()} — check that at the call site.
     * <p>
     * The per-level parent-child and plain-column gates do not apply here: the count projects each
     * counted level's key via {@link JoinPlanner#expressionFor} (a computed key travels as a
     * {@code RawVariant}), and a parent-child level projects its key like any other — the parent
     * column is ignored. The only remaining limit is a relation {@link RelationFromMapper} cannot
     * build (a view/inline shape), which keeps the recorder residue at the call site.
     */
    public static boolean countSupports(RolapLevel targetLevel) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation =
                hierarchy.getRelation();
        if (relation == null) {
            return false;
        }
        if (RelationFromMapper.supports(relation)) {
            return true;
        }
        // A SINGLE view/inline relation is mapped by levelMemberCountSql via
        // RelationFromMapper.from (SqlSelectSource -> FromVariant, InlineTableSource -> FromInline),
        // so its "select count(*) from (select distinct <keys>) init" builds. GUARD: the writeback
        // [Scenario] dimension's DEGENERATE view has an empty body (renders "from () as foo", broken
        // even on the recorder), so resolvesSingleViewOrInline keeps it declined. Parent-child on a
        // single view/inline is deliberately excluded (matches supportsExoticSingleRelation).
        return MemberSqlMapper.supportsExoticSingleRelation(targetLevel)
                && RelationFromMapper.resolvesSingleViewOrInline(relation);
    }

    /**
     * The {@code SELECT count(*) FROM (SELECT DISTINCT <keys>) "init"} cardinality query for
     * {@code targetLevel} — the {@code allowsFromQuery} shape of
     * {@code SqlMemberSource.makeLevelMemberCountSql}. The inner SELECT projects each non-all level's
     * key from the target depth up to (and including) the first {@code unique} level, in that order.
     */
    public static SelectStatement levelMemberCountSql(RolapLevel targetLevel) {
        RolapHierarchy hierarchy = targetLevel.getHierarchy();
        List<RolapLevel> levels = (List<RolapLevel>) hierarchy.getLevels();

        List<RolapLevel> keyLevels = new java.util.ArrayList<>();
        java.util.Set<String> keyTables = new java.util.LinkedHashSet<>();
        for (int i = targetLevel.getDepth(); i >= 0; i--) {
            RolapLevel level = levels.get(i);
            if (level.isAll()) {
                continue;
            }
            keyLevels.add(level);
            TupleSqlMapper.addAlias(keyTables, level.getKeyExp());
            if (level.isUnique()) {
                break;
            }
        }

        java.util.Set<String> fromTables = RelationFromMapper.memberFromTables(hierarchy.getRelation(), keyTables);
        org.eclipse.daanse.sql.statement.api.model.FromClause from =
                RelationFromMapper.fromReferenced(hierarchy.getRelation(), fromTables);
        if (from == null) {
            from = RelationFromMapper.from(hierarchy.getRelation());
        }

        SelectStatementBuilder inner = SelectStatementBuilder.create();
        // Diagnostic provenance (rendered only when comments are on — the derived table renders its
        // comments only in the formatted diagnostic mode; the executed SQL is unchanged).
        inner.header("distinct level keys");
        inner.distinct(true);
        inner.from(org.eclipse.daanse.sql.statement.api.From.commentBase(from,
                TupleSqlMapper.baseTableComment(targetLevel,
                        org.eclipse.daanse.sql.statement.api.From.baseAlias(from))));
        for (RolapLevel level : keyLevels) {
            inner.project(JoinPlanner.expressionFor(level.getKeyExp()), null, null,
                    "level key " + level.getUniqueName());
        }

        SelectStatementBuilder outer = SelectStatementBuilder.create();
        outer.header("level cardinality " + targetLevel.getUniqueName());
        outer.footerComment("cardinality probe (count distinct keys)");
        // Structured node that renders exactly as lower-case "count(*)": the renderer's Call
        // branch emits the aggregate NAME verbatim (no dialect consulted), and Raw("*") verbatim.
        outer.project(org.eclipse.daanse.sql.statement.api.Expressions.aggregate(
                "count", org.eclipse.daanse.sql.statement.api.Expressions.star()), null);
        outer.from(org.eclipse.daanse.sql.statement.api.From.subquery(inner.build(),
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of("init")));
        return outer.build();
    }

    /**
     * True when the leaf-level {@code count(*) FROM <table>} cardinality shape (the
     * {@code levelDepth == levels.size()} branch of {@code SqlMemberSource.makeLevelMemberCountSql})
     * can be rendered by the builder: renderable relation and a plain-column key (so the FROM table
     * can be derived the same way the generic count does).
     */
    public static boolean leafCountSupports(RolapLevel level) {
        RolapHierarchy hierarchy = level.getHierarchy();
        return hierarchy.getRelation() != null
                && RelationFromMapper.supports(hierarchy.getRelation())
                && TupleSqlMapper.isPlainColumn(level.getKeyExp());
    }

    /**
     * The leaf-level {@code SELECT count(*) FROM <table>} cardinality query — the full-depth branch of
     * {@code SqlMemberSource.makeLevelMemberCountSql} (no DISTINCT, no sub-query). FROM is derived from
     * the key's table exactly as the generic {@link #levelMemberCountSql} derives its inner FROM.
     */
    public static SelectStatement leafCountSql(RolapLevel level) {
        RolapHierarchy hierarchy = level.getHierarchy();
        java.util.Set<String> keyTables = new java.util.LinkedHashSet<>();
        TupleSqlMapper.addAlias(keyTables, level.getKeyExp());
        java.util.Set<String> fromTables = RelationFromMapper.memberFromTables(hierarchy.getRelation(), keyTables);
        org.eclipse.daanse.sql.statement.api.model.FromClause from =
                RelationFromMapper.fromReferenced(hierarchy.getRelation(), fromTables);
        if (from == null) {
            from = RelationFromMapper.from(hierarchy.getRelation());
        }
        SelectStatementBuilder b = SelectStatementBuilder.create();
        // Diagnostic provenance (rendered only when comments are on; never part of the executed SQL).
        b.header("level cardinality " + level.getUniqueName());
        b.footerComment("cardinality probe (count rows)");
        // Structured node that renders exactly as lower-case "count(*)" (see levelMemberCountSql).
        b.project(org.eclipse.daanse.sql.statement.api.Expressions.aggregate(
                "count", org.eclipse.daanse.sql.statement.api.Expressions.star()), null);
        b.from(org.eclipse.daanse.sql.statement.api.From.commentBase(from,
                TupleSqlMapper.baseTableComment(level, org.eclipse.daanse.sql.statement.api.From.baseAlias(from))));
        return b.build();
    }

    /**
     * The aggregate-table row-count cardinality probe — {@code SELECT count(*) FROM <table> AS
     * <alias>} — {@code AggStar.Table.makeNumberOfRows} (
     * {@code addSelect("count(*)")} + {@code addFrom(relation, name)}). {@code alias} is the AggStar
     * table's name (always equal to the physical table name for an aggregate table), so the rendered
     * FROM is {@code "agg_x" as "agg_x"} — the {@code select count(*) from "agg_x"
     * as "agg_x"} shape.
     * <p>
     * Returns {@code null} — the caller keeps the recorder — when {@code relation} is not a named
     * {@code TableSource} (a view / inline aggregate fact is outside this trivial builder's scope).
     */
    public static SelectStatement aggTableCountSql(
            org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
            String alias) {
        if (!(relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource ts)
                || ts.getTable() == null
                || ts.getTable().getName() == null || ts.getTable().getName().isBlank()) {
            return null;
        }
        String schema = ts.getTable().getNamespace()
                instanceof org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema s ? s.getName() : null;
        org.eclipse.daanse.sql.statement.api.model.TableAlias tableAlias =
                org.eclipse.daanse.sql.statement.api.model.TableAlias.of(alias);
        org.eclipse.daanse.sql.statement.api.model.FromClause from = schema == null
                ? org.eclipse.daanse.sql.statement.api.From.table(ts.getTable().getName(), tableAlias)
                : org.eclipse.daanse.sql.statement.api.From.table(schema, ts.getTable().getName(), tableAlias);
        SelectStatementBuilder b = SelectStatementBuilder.create();
        // Structured node that renders exactly as lower-case "count(*)" (see levelMemberCountSql):
        // the aggregate NAME is emitted verbatim and Raw("*") verbatim, no dialect consulted.
        b.project(org.eclipse.daanse.sql.statement.api.Expressions.aggregate(
                "count", org.eclipse.daanse.sql.statement.api.Expressions.star()), null);
        b.from(from);
        return b.build();
    }
}

/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
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

package org.eclipse.daanse.rolap.common.agg;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.common.sqlbuild.AggregateSqlMapper;
import org.eclipse.daanse.rolap.common.sqlbuild.QueryBuildContext;
import org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
import org.eclipse.daanse.sql.statement.api.expression.SqlExpression;
import org.eclipse.daanse.sql.statement.api.model.SelectStatement;
import java.util.ArrayList;

/**
 * Base class for {@link QuerySpec} implementations.
 *
 * @author jhyde
 * @author Richard M. Emberson
 */
public abstract class AbstractQuerySpec implements QuerySpec {
    private final RolapStar star;
    protected final boolean countOnly;

    /**
     * Creates an AbstractQuerySpec.
     *
     * @param star Star which defines columns of interest and their
     * relationships
     *
     * @param countOnly If true, generate no GROUP BY clause, so the query
     * returns a single row containing a grand total
     */
    protected AbstractQuerySpec(final RolapStar star, boolean countOnly) {
        this.star = star;
        this.countOnly = countOnly;
    }

    /**
     * Creates a query object.
     *
     * @return a new query object
     */
    protected QueryRecorder newQuery() {
        return getStar().newQueryRecorder();
    }

    @Override
	public RolapStar getStar() {
        return star;
    }

    /**
     * Adds a measure to a query.
     *
     * @param i Ordinal of measure
     * @param query Query object
     */
    protected void addMeasure(final int i, final QueryRecorder query) {
        RolapStar.Measure measure = getMeasure(i);
        if (!isPartOfSelect(measure)) {
            return;
        }
        Util.assertTrue(measure.getTable() == getStar().getFactTable());
        measure.getTable().addToFrom(query, false, true);

        // Dialect-free path: the measure column as a builder node — a plain Column for a RolapColumn,
        // else a computed RawVariant the renderer resolves per dialect at render; null for a measure
        // without a column expression (SqlNodeAggregator.toNode contract: simple aggregators render it
        // as count(*)-style star, PERCENTILE-style aggregators take their own column). The aggregator
        // wraps it into its node form (Aggregate / ExtraAggregate); only aggregators without a node
        // form (not a SqlNodeAggregator, e.g. custom) fall back to the rendered-string path below.
        org.eclipse.daanse.sql.statement.api.expression.SqlExpression operandNode =
            measure.getExpression() == null ? null
                : (measure.getExpression() instanceof org.eclipse.daanse.rolap.element.RolapColumn
                    ? org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(measure.getExpression())
                    : org.eclipse.daanse.sql.statement.api.Expressions.rawVariant(
                        org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.sqlVariants(
                            measure.getExpression())));
        org.eclipse.daanse.sql.statement.api.expression.SqlExpression outerNode =
            org.eclipse.daanse.rolap.aggregator.SqlNodeAggregator.toNodeOrNull(
                measure.getAggregator(), operandNode);
        if (outerNode != null) {
            // Keep the explicit measure alias (m{i}) in both cases; the comment is added only when on and
            // shows solely in the diagnostic copy (executed SQL unchanged).
            query.addSelectNodeCommented(outerNode, measure.getInternalType(), getMeasureAlias(i),
                "measure " + measure.getName() + " (" + measure.getAggregator().getName() + ")");
        } else {
            // Render the operand (count(*)-star / plain column / computed RawVariant) to its dialect
            // string; a computed column renders via its RawVariant instead of being rejected.
            String exprInner = org.eclipse.daanse.rolap.common.SqlRender.renderExpression(
                operandNode == null
                    ? org.eclipse.daanse.sql.statement.api.Expressions.star() : operandNode,
                getStar().getDialect());
            StringBuilder exprOuter = measure.getAggregator().getExpression(exprInner);
            query.addSelect(
                exprOuter,
                measure.getInternalType(),
                getMeasureAlias(i));
        }
    }

    protected abstract boolean isAggregate();

    protected Map<RolapStar.Column, String> nonDistinctGenerateSql(QueryRecorder query)
    {
        //First add fact table to From.
        getStar().getFactTable().addToFrom(query, false, false);
        if (getMeasureCount() > 0) {
            // Coarse aggregate context: a base (real fact) read; name the cube the measures belong to.
            query.setHeaderComment("segment cube " + getMeasure(0).getCubeName());
            query.setFooterComment("segment request");
        }
        // add constraining dimensions
        RolapStar.Column[] columns = getColumns();
        int arity = columns.length;
        if (countOnly) {
            query.addSelect("count(*)", BestFitColumnType.INT);
        }
        for (int i = 0; i < arity; i++) {
            RolapStar.Column column = columns[i];
            RolapStar.Table table = column.getTable();
            if (table.isFunky()) {
                // this is a funky dimension -- ignore for now
                continue;
            }
            table.addToFrom(query, false, true);

            StarColumnPredicate predicate = getColumnPredicate(i);
            if (predicate.getConstrainedColumn() != null) {
                // Dialect-free path: the predicate carries its own column (renders to the same expr),
                // so translate it to a builder Predicate instead of a raw dialect-rendered string.
                // always-true predicate adds no restriction: skip it.
                if (!org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.isAlwaysTrue(predicate)) {
                    query.addWhere(
                        org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toPredicate(predicate));
                }
            } else {
                // Fake-column case (predicate has no constrained column): substitute the star
                // column's node and translate to the value-constraint predicate node (same
                // IN/=/IS NULL forms).
                if (!org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.isAlwaysTrue(predicate)) {
                    query.addWhere(
                        org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toColumnPredicate(
                            predicate,
                            org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(column),
                            column.getDatatype()));
                }
            }

            if (countOnly || !isPartOfSelect(column)) {
                continue;
            }

            // some DB2 (AS400) versions throw an error, if a column alias is
            // there and *not* used in a subsequent order by/group by
            // Dialect-free: pass the column expression for SELECT / GROUP BY / ORDER BY.
            final Dialect dialect = getStar().getDialect();
            final String alias =
                    query.addSelectExpr(column.getExpression(), column.getInternalType(),
                        caps().fieldAlias() ? getColumnAlias(i) : null);

            if (isAggregate()) {
                query.addGroupByExpr(column.getExpression(), alias);
            }

            // Add ORDER BY clause to make the results deterministic.
            // Derby has a bug with ORDER BY, so ignore it.
            if (isOrdered()) {
                query.addOrderByExpr(
                    column.getExpression(),
                    alias,
                    SortingDirection.ASC, false, false, true);
            }
        }

        // Add compound member predicates
        extraPredicates(query);

        // add measures
        for (int i = 0, count = getMeasureCount(); i < count; i++) {
            addMeasure(i, query);
        }

        return Collections.emptyMap();
    }

    /**
     * Allows subclasses to specify if a given column must
     * be returned as part of the result set, in the select clause.
     */
    @SuppressWarnings("java:S1172")
    protected boolean isPartOfSelect(RolapStar.Column col) {
        return true;
    }

    /**
     * Allows subclasses to specify if a given column must
     * be returned as part of the result set, in the select clause.
     */
    @SuppressWarnings("java:S1172")
    protected boolean isPartOfSelect(RolapStar.Measure measure) {
        return true;
    }

    /**
     * Whether to add an ORDER BY clause to make results deterministic.
     * Necessary if query returns more than one row and results are for
     * human consumption.
     *
     * @return whether to sort query
     */
    protected boolean isOrdered() {
        return false;
    }

    /** The narrow capability view for the planner probes of this spec. */
    protected final org.eclipse.daanse.rolap.common.sql.SqlQueryCapabilities caps() {
        return org.eclipse.daanse.rolap.common.sql.SqlQueryCapabilities.of(getStar().getDialect());
    }

    @Override
	public RenderedSql generateSql() {
        int k = getDistinctMeasureCount();
        final Dialect dialect = getStar().getDialect();

        // AUTHORITATIVE builder for ALL nonDistinct aggregate shapes via AggregateSqlMapper: simple;
        // compound slicer predicates; a distinct measure rendered count(distinct col) inline; and the
        // countOnly / ordered / grouping-sets variants (carried as a AggregateSqlMapper.Shape). The
        // only structural decline left is the distinct-SUBQUERY rewrite (plus non-aggregate specs —
        // unreachable today, DrillThroughQuerySpec overrides generateSql — and mapper out-of-scope
        // columns/measures). For every builder shape the QueryRecorder is NEVER constructed.
        // Capability reads (allowsCountDistinct / allowsMultipleCountDistinct): the capability boolean is
        // gated HERE (choose the distinct-subquery rewrite vs the inline count(distinct)), while the
        // SPELLING stays in the Dialect/renderer. supportsGroupingSets is deliberately NOT gated here:
        // the statement model carries both the plain group keys and the grouping sets, and the renderer
        // picks the spelling per dialect — identical for recorder and builder.
        final org.eclipse.daanse.rolap.common.sql.SqlQueryCapabilities caps = caps();
        boolean distinctRewrite = (!caps.countDistinct() && k > 0)
            || (!caps.multipleCountDistinct() && k > 1);
        boolean usesNonDistinct = !distinctRewrite;
        // The count-distinct SUBQUERY rewrite is the RENDERER's job — DialectSqlRenderer degrades
        // the flat count(distinct) into the nested dummyname subquery for a dialect that cannot execute it
        // inline. Route the reproducible slice through the SAME builder as the non-distinct shapes: the
        // builder emits the canonical flat count(distinct) and the renderer wraps it. The residual (a
        // distinct rewrite combined with countOnly / ordered / grouping sets, or a measure that is not a
        // simple single-column aggregate node — count(*) / percentile / a string-fallback aggregator) stays
        // on the recorder's distinctGenerateSql below, whose output already IS the nested form. The
        // capability boolean still gates HERE; the SPELLING stays in the renderer.
        // countOnly is builder-eligible: the distinct-countOnly shape emits the flat distinct
        // measures WITHOUT count(*) (legacy distinctGenerateSql column semantics); the renderer's
        // subquery rewrite handles the dims-empty form. isOrdered() is vacuous on this path (only
        // DrillThroughQuerySpec overrides it, and that spec overrides generateSql entirely).
        // grouping sets stay on the recorder residual — BatchLoader splits distinct measures out
        // of grouping-set batches on restrictive dialects, so the combo is defensively rare.
        boolean distinctBuilderEligible = distinctRewrite
            && getGroupingSetsColumns().isEmpty() && measuresAreSimpleAggregates(dialect);
        if (isAggregate() && (usesNonDistinct || distinctBuilderEligible)) {
            RenderedSql built = tryAggregateBuilderAuthoritative(dialect, distinctRewrite);
            if (built != null) {
                return built;
            }
            // A mapper decline was already logged with its reason inside tryAggregateBuilderAuthoritative.
        } else if (org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.isDebugEnabled()) {
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                "aggregate segment: path=recorder reason={} distinctMeasures={} allowsCountDistinct={} allowsMultipleCountDistinct={}",
                isAggregate() ? "distinct-subquery-rewrite" : "not-aggregate",
                k, caps.countDistinct(), caps.multipleCountDistinct());
        }

        QueryRecorder query = newQuery();

        final Map<RolapStar.Column, String> groupingSetsAliases;
        // Same capability reads as above — gate by capability boolean at the decision point, spell at
        // the renderer.
        if (!caps.countDistinct() && k > 0
            || !caps.multipleCountDistinct() && k > 1)
        {
            groupingSetsAliases =
                distinctGenerateSql(query, countOnly);
        } else {
            groupingSetsAliases =
                nonDistinctGenerateSql(query);
        }
        if (!countOnly) {
            addGroupingFunction(query);
            addGroupingSets(query, groupingSetsAliases);
        }
        // Only the declined shapes reach here (the distinct-subquery rewrite — alone or combined with
        // countOnly/grouping sets —, a non-aggregate spec, or a mapper out-of-scope decline) — the
        // QueryRecorder is authoritative for them.
        return org.eclipse.daanse.rolap.common.SqlRender.render(query.buildStatement(), dialect, query.renderOptions());
    }

    /**
     * AUTHORITATIVE builder for the nonDistinct aggregate shapes: the group-by columns + measures PLUS
     * the {@link #getPredicateList()} compound (slicer) predicates as
     * {@link AggregateSqlMapper.ExtraConjunct}s, via {@link AggregateSqlMapper#aggregate} — with the
     * countOnly / ordered / grouping-sets variants carried as an {@link AggregateSqlMapper.Shape}. The
     * CALLER gates only the distinct-subquery rewrite (and isAggregate); this returns the builder SQL
     * directly (no reference compare), or {@code null}
     * when a column/measure is out of mapper scope (funky table / not part of SELECT) so the caller falls
     * back to the {@link QueryRecorder} path (each decline debug-logged as
     * {@code path=recorder reason=...}).
     */
    private RenderedSql tryAggregateBuilderAuthoritative(Dialect dialect, boolean distinctRewriteShape) {
        try {
            RolapStar.Table fact = getStar().getFactTable();
            RolapStar.Column[] columns = getColumns();
            List<RolapStar.Column> groupBy = new ArrayList<>();
            List<Predicate> columnFilters = new ArrayList<>();
            for (int i = 0; i < columns.length; i++) {
                RolapStar.Column column = columns[i];
                if (column.getTable().isFunky() || !isPartOfSelect(column)) {
                    // Out of mapper scope -> the QueryRecorder path builds this segment.
                    org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                        "aggregate segment: path=recorder reason=column-out-of-scope column={}",
                        column.getName());
                    return null;
                }
                groupBy.add(column);
                StarColumnPredicate predicate = getColumnPredicate(i);
                columnFilters.add(StarPredicateTranslator.isAlwaysTrue(predicate)
                    ? null : StarPredicateTranslator.toPredicate(predicate));
            }
            List<RolapStar.Measure> measures = new ArrayList<>();
            for (int i = 0, count = getMeasureCount(); i < count; i++) {
                RolapStar.Measure measure = getMeasure(i);
                if (!isPartOfSelect(measure)) {
                    // Out of mapper scope -> the QueryRecorder path builds this segment.
                    org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                        "aggregate segment: path=recorder reason=measure-out-of-scope measure={}",
                        measure.getName());
                    return null;
                }
                measures.add(measure);
            }
            // The compound (slicer) predicates the simple fast path skips -> translated WHERE + the tables
            // their constrained columns require joined (mirrors extraPredicates).
            List<AggregateSqlMapper.ExtraConjunct> extras = new ArrayList<>();
            for (StarPredicate sp : getPredicateList()) {
                if (StarPredicateTranslator.isAlwaysTrue(sp)) {
                    continue;
                }
                List<RolapStar.Table> tables = new ArrayList<>();
                for (RolapStar.Column c : sp.getConstrainedColumnList()) {
                    tables.add(c.getTable());
                }
                extras.add(new AggregateSqlMapper.ExtraConjunct(
                    StarPredicateTranslator.toPredicate(sp), tables));
            }
            // The countOnly (grand-total row), ordered (deterministic ORDER BY over the group-by
            // projection) and grouping sets (+ the grouping(x) rollup columns) shapes carried to the
            // mapper. All empty/false for the plain aggregate.
            List<List<RolapStar.Column>> groupingSets = new ArrayList<>();
            for (RolapStar.Column[] set : getGroupingSetsColumns()) {
                groupingSets.add(List.of(set));
            }
            AggregateSqlMapper.Shape shape = new AggregateSqlMapper.Shape(
                countOnly, isOrdered(), groupingSets, getRollupColumns(),
                countOnly && distinctRewriteShape);
            return QueryBuildContext.of(dialect, getStar().getContext())
                .build(() -> AggregateSqlMapper.aggregate(fact, groupBy, columnFilters, measures, dialect, extras,
                    shape))
                .render();
        } catch (RuntimeException e) {
            // A translator/mapper shape outside builder scope -> the QueryRecorder path builds this segment.
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                "aggregate segment: path=recorder reason=mapper-exception detail={}", e.toString());
            return null;
        }
    }

    /**
     * Whether EVERY measure of this spec renders as a SIMPLE single-column aggregate node
     * ({@code count(distinct col)} / {@code sum(col)} …) — the shape the renderer's count-distinct
     * subquery rewrite can unwrap (project the argument as the inner {@code m{i}}, re-aggregate the
     * outer column without {@code DISTINCT}). A {@code count(*)} (star argument), a multi-argument
     * aggregate, or a dialect-generated / string-fallback measure (percentile, LISTAGG, a custom
     * aggregator with no node form) is NOT reproducible from the flat statement, so the spec keeps the
     * recorder's {@link #distinctGenerateSql}. Gates the distinct-rewrite builder route in
     * {@link #generateSql()} so it stays aligned with what the renderer can wrap.
     */
    private boolean measuresAreSimpleAggregates(Dialect dialect) {
        for (int i = 0, count = getMeasureCount(); i < count; i++) {
            SqlExpression node =
                org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(getMeasure(i), dialect);
            if (!(node instanceof SqlExpression.Aggregate a) || a.arguments().size() != 1
                    || a.arguments().get(0) instanceof SqlExpression.Star) {
                return false;
            }
        }
        return true;
    }

    /**
     * The {@code GROUP BY GROUPING SETS} column lists of the batched multi-rollup segment load —
     * empty when the query has none (the default; also what {@link GroupingSetsList} returns when
     * grouping sets are not in use). Feeds the builder {@link AggregateSqlMapper.Shape}; the recorder
     * path keeps using {@link #addGroupingSets} for the declined distinct-rewrite combination.
     * {@link SegmentArrayQuerySpec} overrides to consult its grouping-sets list.
     */
    protected List<RolapStar.Column[]> getGroupingSetsColumns() {
        return Collections.emptyList();
    }

    /**
     * The rollup columns surfaced as {@code grouping(x)} SELECT-tail functions alongside grouping
     * sets — empty when the query has none (the default). Same feeding contract as
     * {@link #getGroupingSetsColumns()}.
     */
    protected List<RolapStar.Column> getRollupColumns() {
        return Collections.emptyList();
    }

    protected void addGroupingFunction(QueryRecorder query) {
        throw new UnsupportedOperationException();
    }

    protected void addGroupingSets(
        QueryRecorder query,
        Map<RolapStar.Column, String> groupingSetsAliases)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the number of measures whose aggregation function is
     * distinct-count.
     *
     * @return Number of distinct-count measures
     */
    protected int getDistinctMeasureCount() {
        int k = 0;
        for (int i = 0, count = getMeasureCount(); i < count; i++) {
            RolapStar.Measure measure = getMeasure(i);
            if (measure.getAggregator().isDistinct()) {
                ++k;
            }
        }
        return k;
    }

    /**
     * Generates a SQL query to retrieve the values in this segment using
     * an algorithm which converts distinct-aggregates to non-distinct
     * aggregates over subqueries.
     *
     * @param outerQuery Query to modify
     * @param countOnly If true, only generate a single row: no need to
     *   generate a GROUP BY clause or put any constraining columns in the
     *   SELECT clause
     * @return A map of aliases used in the inner query if grouping sets
     * were enabled.
     */
    protected Map<RolapStar.Column, String> distinctGenerateSql(
        final QueryRecorder outerQuery,
        boolean countOnly)
    {
        final Dialect dialect = getStar().getDialect();
        final Map<RolapStar.Column, String> groupingSetsAliases =
            new HashMap<>();
        // Generate something like
        //
        //  select d0, d1, count(m0)
        //  from (
        //    select distinct dim1.x as d0, dim2.y as d1, f.z as m0
        //    from f, dim1, dim2
        //    where dim1.k = f.k1
        //    and dim2.k = f.k2) as dummyname
        //  group by d0, d1
        //
        // or, if countOnly=true
        //
        //  select count(m0)
        //  from (
        //    select distinct f.z as m0
        //    from f, dim1, dim2
        //    where dim1.k = f.k1
        //    and dim2.k = f.k2) as dummyname

        // GREENPLUM not support InnerDistinct
        final QueryRecorder innerQuery = newQuery();
        innerQuery.setDistinct(caps().innerDistinct());

        // Register the fact table first (as nonDistinctGenerateSql does), so the inner subquery's FROM
        // roots at the fact and reads fact-first — matching the builder's flat form the renderer
        // wraps. Without this the inner FROM would root at the first constraining dimension's table
        // (dim JOIN fact), diverging from every other builder segment. Restrictive-dialect only (this
        // path is not exercised by the mainstream dialects), so the executed SQL is unchanged.
        getStar().getFactTable().addToFrom(innerQuery, false, false);

        // add constraining dimensions
        RolapStar.Column[] columns = getColumns();
        int arity = columns.length;
        for (int i = 0; i < arity; i++) {
            RolapStar.Column column = columns[i];
            RolapStar.Table table = column.getTable();
            if (table.isFunky()) {
                // this is a funky dimension -- ignore for now
                continue;
            }
            table.addToFrom(innerQuery, false, true);
            StarColumnPredicate predicate = getColumnPredicate(i);
            if (predicate.getConstrainedColumn() != null) {
                // Dialect-free path (see nonDistinctGenerateSql): translate to a builder Predicate.
                if (!org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.isAlwaysTrue(predicate)) {
                    innerQuery.addWhere(
                        org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toPredicate(predicate));
                }
            } else {
                // Fake-column case — the value-constraint predicate node (see nonDistinctGenerateSql).
                if (!org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.isAlwaysTrue(predicate)) {
                    innerQuery.addWhere(
                        org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toColumnPredicate(
                            predicate,
                            org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(column),
                            column.getDatatype()));
                }
            }
            if (countOnly) {
                continue;
            }
            // Dialect-free: pass the column expression for SELECT / GROUP BY.
            String alias = "d" + i;
            alias = innerQuery.addSelectExpr(column.getExpression(), null, alias);
            if (!caps().innerDistinct()) {
                innerQuery.addGroupByExpr(column.getExpression(), alias);
            }
            // Node channel: the outer SELECT/GROUP BY reference the inner alias as a column node —
            // the renderer quotes it per dialect.
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression aliasNode =
                org.eclipse.daanse.sql.statement.api.Expressions.column(alias);
            String outerAlias = outerQuery.addSelectNode(aliasNode, null);
            outerQuery.addGroupByNode(aliasNode, outerAlias);
            // Grouping-sets alias map (defensive distinct+grouping-sets residual): the consumer emits
            // these strings into GROUPING SETS, so they stay dialect-quoted here.
            groupingSetsAliases.put(
                column,
                dialect.quoteIdentifier(
                    new StringBuilder("dummyname.").append(alias)).toString());
        }

        // add predicates not associated with columns
        extraPredicates(innerQuery);

        // add measures
        for (int i = 0, count = getMeasureCount(); i < count; i++) {
            RolapStar.Measure measure = getMeasure(i);

            Util.assertTrue(measure.getTable() == getStar().getFactTable());
            measure.getTable().addToFrom(innerQuery, false, true);

            String alias = getMeasureAlias(i);
            if (measure.getExpression() != null) {
                // Dialect-free: project the raw measure column as a node (like the dimension SELECT above);
                // the outer query applies the aggregator. Plain column -> Column node, computed
                // -> RawVariant the renderer resolves per dialect. Bail to the string for a column-less measure.
                innerQuery.addSelectExpr(measure.getExpression(), measure.getInternalType(), alias);
                if (!caps().innerDistinct()) {
                    innerQuery.addGroupByExpr(measure.getExpression(), alias);
                }
            } else {
                String expr = measure.generateExprString(dialect);
                innerQuery.addSelect(expr, measure.getInternalType(), alias);
                if (!caps().innerDistinct()) {
                    innerQuery.addGroupBy(expr, alias);
                }
            }
            // Node channel: re-aggregate the inner alias with the non-distinct sibling as a node
            // (count(distinct x) -> count("m0")); an aggregator without a node form keeps the
            // rendered-string fallback.
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression reAgg =
                org.eclipse.daanse.rolap.aggregator.SqlNodeAggregator.toNodeOrNull(
                    measure.getAggregator().getNonDistinctAggregator(),
                    org.eclipse.daanse.sql.statement.api.Expressions.column(alias));
            if (reAgg != null) {
                outerQuery.addSelectNode(reAgg, measure.getInternalType());
            } else {
                outerQuery.addSelect(
                    measure.getAggregator().getNonDistinctAggregator()
                        .getExpression(dialect.quoteIdentifier(alias)),
                    measure.getInternalType());
            }
        }
        outerQuery.addFrom(innerQuery, "dummyname", true);
        return groupingSetsAliases;
    }

    /**
     * Adds predicates not associated with columns.
     *
     * @param query Query
     */
    protected void extraPredicates(QueryRecorder query) {
        List<StarPredicate> predicateList = getPredicateList();
        for (StarPredicate predicate : predicateList) {
            for (RolapStar.Column column
                : predicate.getConstrainedColumnList())
            {
                final RolapStar.Table table = column.getTable();
                table.addToFrom(query, false, true);
            }
            // Dialect-free path: translate the compound (slicer) predicate to a builder Predicate.
            // These are the SAME getPredicateList() predicates DrillThroughQuerySpec.buildDrillThrough
            // and tryAggregateBuilderAuthoritative already feed through StarPredicateTranslator.
            // Always-true adds no restriction and is skipped.
            if (StarPredicateTranslator.isAlwaysTrue(predicate)) {
                continue;
            }
            // Dialect-free: the translator is total for every shape getPredicateList() produces
            // (Value/List/And/Or/Literal plus Range / MemberTuple / Minus). An unmodeled shape throws
            // IllegalArgumentException (defensively impossible — surfaced, never a silently dropped
            // constraint).
            query.addWhere(StarPredicateTranslator.toPredicate(predicate));
        }
    }

    /**
     * Returns a list of predicates not associated with a particular column.
     *
     * @return list of non-column predicates
     */
    protected List<StarPredicate> getPredicateList() {
        return Collections.emptyList();
    }
}


// End AbstractQuerySpec.java

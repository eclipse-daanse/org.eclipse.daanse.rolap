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

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.type.BestFitColumnType;
import org.eclipse.daanse.olap.api.sql.SortingDirection;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.sql.statement.api.render.RenderedSql;
import org.eclipse.daanse.rolap.common.sql.QueryRecorder;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.common.sqlbuild.AggregateSqlMapper;
import org.eclipse.daanse.rolap.common.sqlbuild.SqlBuildGuard;
import org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator;
import org.eclipse.daanse.sql.statement.api.expression.Predicate;
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

        // Dialect-generator aggregators (PERCENTILE / LISTAGG / bitwise / NTH_VALUE) build a dialect-free
        // ExtraAggregate node the renderer turns into dialect SQL at render (the same
        // dialect.aggregationGenerator() call). The operand is the measure
        // column node (plain Column / computed RawVariant), or null for aggregators that take their own column.
        if (measure.getAggregator() instanceof org.eclipse.daanse.rolap.aggregator.NodeAggregate na) {
            org.eclipse.daanse.sql.statement.api.expression.SqlExpression operandNode =
                measure.getExpression() == null ? null
                    : (measure.getExpression() instanceof org.eclipse.daanse.rolap.element.RolapColumn
                        ? org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(measure.getExpression())
                        : org.eclipse.daanse.sql.statement.api.Expressions.rawVariant(
                            org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.sqlVariants(
                                measure.getExpression())));
            query.addSelectNodeCommented(na.toNode(operandNode), measure.getInternalType(), getMeasureAlias(i),
                "measure " + measure.getName() + " (" + measure.getAggregator().getName() + ")");
            return;
        }

        // Dialect-free path: the measure column as a builder node — count(*) for a null expression, a plain
        // Column for a RolapColumn, else a computed RawVariant the renderer resolves per dialect at
        // render — wrapped by a simple aggregator (sum/count/avg/min/
        // max/distinct-count) as an Aggregate node. Only composite / non-node aggregators (getExpression(node)
        // == null) fall back to the rendered-string path below; a computed expression always takes the
        // RawVariant node (generateExprString rejects computed columns).
        org.eclipse.daanse.sql.statement.api.expression.SqlExpression innerNode =
            measure.getExpression() == null
                ? org.eclipse.daanse.sql.statement.api.Expressions.star()
                : (measure.getExpression() instanceof org.eclipse.daanse.rolap.element.RolapColumn
                    ? org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner.expressionFor(measure.getExpression())
                    : org.eclipse.daanse.sql.statement.api.Expressions.rawVariant(
                        org.eclipse.daanse.rolap.common.util.SqlExpressionResolver.sqlVariants(
                            measure.getExpression())));
        org.eclipse.daanse.sql.statement.api.expression.SqlExpression outerNode =
            (innerNode != null
                && measure.getAggregator() instanceof org.eclipse.daanse.rolap.aggregator.AbstractAggregator agg)
                ? agg.getExpression(innerNode)
                : null;
        if (outerNode != null) {
            // Keep the explicit measure alias (m{i}) in both cases; the comment is added only when on and
            // shows solely in the diagnostic copy (executed SQL byte-identical).
            query.addSelectNodeCommented(outerNode, measure.getInternalType(), getMeasureAlias(i),
                "measure " + measure.getName() + " (" + measure.getAggregator().getName() + ")");
        } else {
            // Render the inner node (count(*) / plain column / computed RawVariant) to its dialect string;
            // a computed column renders via its RawVariant instead of being rejected.
            String exprInner = org.eclipse.daanse.rolap.common.SqlRender.renderExpression(
                innerNode, getStar().getDialect());
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
                // always-true (createInExpr would have returned "true") adds no restriction: skip it.
                if (!org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.isAlwaysTrue(predicate)) {
                    query.addWhere(
                        org.eclipse.daanse.rolap.common.sqlbuild.StarPredicateTranslator.toPredicate(predicate));
                }
            } else {
                // Fake-column case (predicate has no constrained column): keep the raw string rendering,
                // which substitutes the caller-supplied expr.
                // residual: fake-column createInExpr (dialect-rendered, no constrained column to
                // translate) — see 07-fallback-policy
                final String where = RolapStar.Column.createInExpr(
                    column.generateExprString(getStar().getDialect()),
                    predicate,
                    column.getDatatype(),
                    getStar().getDialect());
                if (!where.equals("true")) {
                    query.addWhere(where);
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
                        dialect.allowsFieldAlias() ? getColumnAlias(i) : null);

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

    @Override
	public RenderedSql generateSql() {
        int k = getDistinctMeasureCount();
        final Dialect dialect = getStar().getDialect();

        // AUTHORITATIVE builder for ALL nonDistinct aggregate shapes (simple; compound slicer predicates;
        // and/or a distinct measure rendered count(distinct col) inline) via AggregateSqlMapper.
        // Declines grouping-sets / distinct-SUBQUERY / ordered / countOnly so they fall to the
        // QueryRecorder path below — for the common aggregate the QueryRecorder is NEVER constructed. The
        // hasGroupingSets() check is essential: without it a k==0 grouping-sets segment would hit the
        // builder and silently drop its GROUPING SETS.
        // Capability reads (allowsCountDistinct / allowsMultipleCountDistinct): the capability boolean is
        // gated HERE (choose the distinct-subquery rewrite vs the inline count(distinct)), while the
        // SPELLING stays in the Dialect/renderer.
        boolean usesNonDistinct = !((!dialect.allowsCountDistinct() && k > 0)
            || (!dialect.allowsMultipleCountDistinct() && k > 1));
        if (!countOnly && !isOrdered() && isAggregate() && !hasGroupingSets() && usesNonDistinct) {
            RenderedSql built = tryAggregateBuilderAuthoritative(dialect);
            if (built != null) {
                return built;
            }
        }

        QueryRecorder query = newQuery();

        final Map<RolapStar.Column, String> groupingSetsAliases;
        // Same capability reads as above — gate by capability boolean at the decision point, spell at
        // the renderer.
        if (!dialect.allowsCountDistinct() && k > 0
            || !dialect.allowsMultipleCountDistinct() && k > 1)
        {
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                    "aggregate: distinctMeasures={} allowsCountDistinct={} allowsMultipleCountDistinct={} -> distinct-subquery rewrite",
                    k, dialect.allowsCountDistinct(), dialect.allowsMultipleCountDistinct());
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
        // Only the declined shapes reach here (countOnly, ordered, grouping-sets, distinct-subquery, or a
        // builder out-of-scope decline) — the QueryRecorder is authoritative for them.
        return query.toSqlAndTypes(dialect);
    }

    /**
     * AUTHORITATIVE builder for the nonDistinct aggregate shapes the simple gate skips: the group-by
     * columns + measures PLUS the {@link #getPredicateList()} compound (slicer) predicates as
     * {@link AggregateSqlMapper.ExtraConjunct}s, via {@link AggregateSqlMapper#aggregate}. The CALLER gates
     * the shape (not countOnly/ordered/grouping-sets/distinct-subquery); this returns the builder SQL
     * directly (no reference compare), or {@code null}
     * when a column/measure is out of mapper scope (funky table / not part of SELECT) so the caller falls
     * back to the {@link QueryRecorder} path.
     */
    private RenderedSql tryAggregateBuilderAuthoritative(Dialect dialect) {
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
                        "aggregate builder decline: column {} funky/not-part-of-select", column.getName());
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
                        "aggregate builder decline: measure {} not part of SELECT", measure.getName());
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
            return SqlBuildGuard.build(dialect,
                getStar().getContext().getConfigValue(
                    org.eclipse.daanse.olap.common.ConfigConstants.GENERATE_FORMATTED_SQL,
                    org.eclipse.daanse.olap.common.ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE,
                    Boolean.class),
                () -> AggregateSqlMapper.aggregate(fact, groupBy, columnFilters, measures, dialect, extras))
                .render();
        } catch (RuntimeException e) {
            // A translator/mapper shape outside builder scope -> the QueryRecorder path builds this segment.
            org.eclipse.daanse.rolap.common.RolapUtil.SQL_GEN_LOGGER.debug(
                "aggregate builder decline: {}", e.toString());
            return null;
        }
    }

    /**
     * Whether this query emits {@code GROUP BY GROUPING SETS} (the batched multi-rollup segment load). The
     * authoritative aggregate builder declines when true (the mapper does not model grouping sets). Default
     * false; {@link SegmentArrayQuerySpec} overrides to consult its grouping-sets list.
     */
    protected boolean hasGroupingSets() {
        return false;
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
        innerQuery.setDistinct(dialect.allowsInnerDistinct());

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
                // residual: fake-column createInExpr (dialect-rendered, no constrained column to
                // translate) — see 07-fallback-policy
                final String where = RolapStar.Column.createInExpr(
                    column.generateExprString(dialect),
                    predicate,
                    column.getDatatype(),
                    dialect);
                if (!where.equals("true")) {
                    innerQuery.addWhere(where);
                }
            }
            if (countOnly) {
                continue;
            }
            // Dialect-free: pass the column expression for SELECT / GROUP BY.
            String alias = "d" + i;
            alias = innerQuery.addSelectExpr(column.getExpression(), null, alias);
            if (!dialect.allowsInnerDistinct()) {
                innerQuery.addGroupByExpr(column.getExpression(), alias);
            }
            final String quotedAlias = dialect.quoteIdentifier(alias);
            outerQuery.addSelectGroupBy(quotedAlias, null);
            // Add this alias to the map of grouping sets aliases, keyed by the column (not the rendered
            // string) so the consumer needn't re-render it via the dialect.
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
                // the outer query applies the aggregator. Byte-equal — plain column -> Column node, computed
                // -> RawVariant the renderer resolves per dialect. Bail to the string for a column-less measure.
                innerQuery.addSelectExpr(measure.getExpression(), measure.getInternalType(), alias);
                if (!dialect.allowsInnerDistinct()) {
                    innerQuery.addGroupByExpr(measure.getExpression(), alias);
                }
            } else {
                String expr = measure.generateExprString(dialect);
                innerQuery.addSelect(expr, measure.getInternalType(), alias);
                if (!dialect.allowsInnerDistinct()) {
                    innerQuery.addGroupBy(expr, alias);
                }
            }
            outerQuery.addSelect(
                measure.getAggregator().getNonDistinctAggregator()
                    .getExpression(dialect.quoteIdentifier(alias)),
                measure.getInternalType());
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
            // Dialect-free path: translate the compound (slicer) predicate to a builder Predicate
            // instead of the dialect-rendered predicate.toSql(dialect, buf) string. These are the SAME
            // getPredicateList() predicates DrillThroughQuerySpec.buildDrillThrough and
            // tryAggregateBuilderAuthoritative already feed through StarPredicateTranslator.
            // Always-true adds no restriction and is skipped.
            if (StarPredicateTranslator.isAlwaysTrue(predicate)) {
                continue;
            }
            Predicate translated;
            try {
                translated = StarPredicateTranslator.toPredicate(predicate);
            } catch (IllegalArgumentException outOfTranslatorScope) {
                // Shape the translator does not model (Range / MemberTuple / Minus star predicate,
                // possibly nested inside an And/Or): keep the raw string rendering for this WHOLE
                // predicate — top-level per predicate, never a node/string hybrid within one predicate.
                translated = null;
            }
            if (translated != null) {
                query.addWhere(translated);
            } else {
                // residual: untranslatable StarPredicate shape (dialect-rendered toSql) — see 07-fallback-policy
                StringBuilder buf = new StringBuilder();
                predicate.toSql(getStar().getDialect(), buf);
                final String where = buf.toString();
                if (!where.equals("true")) {
                    query.addWhere(where);
                }
            }
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

/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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

package org.eclipse.daanse.rolap.common;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


import org.eclipse.daanse.olap.api.sql.SqlExpression;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.rolap.common.sqlbuild.JoinPlanner;
import org.eclipse.daanse.rolap.common.sqlbuild.RelationFromMapper;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.sql.statement.api.Expressions;
import org.eclipse.daanse.sql.statement.api.SelectStatementBuilder;
import org.eclipse.daanse.rolap.sql.SqlStatisticsProviderNew;


/**
 * Provides and caches statistics.
 *
 * Wrapper around SqlStatisticsProviderNew (the SQL estimates of a table, query or column),
 * followed by a cache to store the results.
 */
public class RolapStatisticsCache {
    private static final org.slf4j.Logger LOGGER =
        org.slf4j.LoggerFactory.getLogger(RolapStatisticsCache.class);
    private final RolapStar star;
    // probes run OUTSIDE the map: SQL inside computeIfAbsent held the CHM
    // bin lock for the probe's duration, coupling every same-bin reader -
    // the cache manager's actor included - to a foreign probe's latency
    // (one half of a real deadlock cycle with the query-limit semaphore).
    // putIfAbsent keeps the first answer; duplicate concurrent probes are
    // accepted. -1 is cached so failed probes are not retried.
    private final Map<List<String>, Long> columnMap = new ConcurrentHashMap<>();
    private final Map<List<String>, Long> tableMap = new ConcurrentHashMap<>();
    // in-flight probes per map: single-flight without a bin lock -
    // concurrent cold callers of the SAME key wait on the probe's future,
    // readers of other keys never wait at all
    private final Map<List<String>, java.util.concurrent.CompletableFuture<Long>> columnInflight =
        new ConcurrentHashMap<>();
    private final Map<List<String>, java.util.concurrent.CompletableFuture<Long>> tableInflight =
        new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.CompletableFuture<Long>> queryInflight =
        new ConcurrentHashMap<>();

    /** The single fallback provider; probes are cold-only, no per-call list. */
    private static final List<SqlStatisticsProviderNew> FALLBACK_PROVIDERS =
        List.of(new SqlStatisticsProviderNew());
    private final Map<String, Long> queryMap = new ConcurrentHashMap<>();

    public RolapStatisticsCache(RolapStar star) {
        this.star = star;
    }

    public void clear() {
        columnMap.clear();
        tableMap.clear();
        queryMap.clear();
    }

    /**
     * Cached probe: read, probe OUTSIDE the map, single-flight via the
     * in-flight future. Never runs SQL under a ConcurrentHashMap bin lock,
     * never fires the same probe twice concurrently (a cold level load
     * runs its count-distinct once) - and NEVER waits or probes on the
     * cache manager's thread: the probe path acquires the query-limit
     * semaphore whose holders block on that thread, so joining a foreign
     * probe there closes a real deadlock cycle. A cold miss on the actor
     * returns -1 (the established unknown sentinel) WITHOUT caching;
     * consumers degrade conservatively and the next query-thread call
     * probes normally. Note: a THROWING probe caches nothing either -
     * only a provider-returned -1 is cached (a dead target heals).
     */
    public static <K> long cached(Map<K, Long> map,
            Map<K, java.util.concurrent.CompletableFuture<Long>> inflight, K key,
            java.util.function.LongSupplier probe) {
        final Long existing = map.get(key);
        if (existing != null) {
            return existing;
        }
        if (warnIfOnCacheManagerThread(key)) {
            return -1;
        }
        final java.util.concurrent.CompletableFuture<Long> mine =
            new java.util.concurrent.CompletableFuture<>();
        final java.util.concurrent.CompletableFuture<Long> running =
            inflight.putIfAbsent(key, mine);
        if (running != null) {
            try {
                return running.join();
            } catch (java.util.concurrent.CompletionException e) {
                // hand waiters the same exception type the prober saw
                final Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw e;
            }
        }
        // a loser of an earlier race may have published between our first
        // read and the slot claim - re-check before firing SQL
        final Long published = map.get(key);
        if (published != null) {
            mine.complete(published);
            inflight.remove(key, mine);
            return published;
        }
        try {
            final long rowCount = probe.getAsLong();
            map.put(key, rowCount);
            mine.complete(rowCount);
            return rowCount;
        } catch (RuntimeException | Error e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inflight.remove(key, mine);
        }
    }

    /**
     * Guard AND detector: a cold cardinality request on the cache
     * manager's thread means the preload in BatchingCellReader missed a
     * request. The caller returns the unknown sentinel instead of probing
     * or joining - the WARN names the gap so the preload can be fixed.
     */
    public static boolean warnIfOnCacheManagerThread(Object key) {
        if (Thread.currentThread().getName()
                .startsWith("daanse.rolap.agg.SegmentCacheManager$ACTOR")) {
            LOGGER.warn("cold cardinality probe on the cache manager thread - preload gap? key={}", key);
            return true;
        }
        return false;
    }

    public long getRelationCardinality(
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
        String alias,
        long approxRowCount)
    {
        if (approxRowCount >= 0) {
            return approxRowCount;
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table) {
            return getTableCardinality(table.getTable());
        } else {
            SelectStatementBuilder q = SelectStatementBuilder.create();
            org.eclipse.daanse.sql.statement.api.model.FromClause from = RelationFromMapper.from(relation);
            // Diagnostic provenance (rendered only when comments are on; never part of the executed
            // SQL): this whole-relation read is the statistics cache's row-count probe input — the
            // provider wraps it as `select count(*) from (<this>)`.
            q.header("table cardinality " + relationName(alias, from));
            q.footerComment("cardinality probe (count rows)");
            q.from(from);
            q.project(Expressions.star(), null);
            return getQueryCardinality(SqlRender.render(q.build(), star.getDialect()).sql());
        }
    }

    private long getTableCardinality(
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet table)
    {
    	String schema = table.getNamespace() != null ? table.getNamespace().getName() : null;
        final List<String> key = Arrays.asList(schema, table.getName());
        return cached(tableMap, tableInflight, key, () -> {
            long rowCount = -1;
            for (SqlStatisticsProviderNew statisticsProvider
                    : FALLBACK_PROVIDERS) {
                rowCount = statisticsProvider.getTableCardinality(
                    star.getContext(),
                    schema,
                    table.getName(),
                    newExecution());
                if (rowCount >= 0) {
                    break;
                }
            }
            return rowCount;
        });
    }

    private ExecutionImpl newExecution() {
        return new ExecutionImpl(
            star.getCatalog().getInternalConnection().getInternalStatement(),
            ExecuteDurationUtil.executeDurationValue(
                star.getCatalog().getInternalConnection().getContext()));
    }

    private long getQueryCardinality(String sql) {
        return cached(queryMap, queryInflight, sql, () -> {
            long rowCount = -1;
            for (SqlStatisticsProviderNew statisticsProvider
                    : FALLBACK_PROVIDERS) {
                rowCount = statisticsProvider.getQueryCardinality(star.getContext(), sql, newExecution());
                if (rowCount >= 0) {
                    break;
                }
            }
            return rowCount;
        });
    }

    public long getColumnCardinality(
        org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource relation,
        SqlExpression expression,
        long approxCardinality)
    {
        if (approxCardinality >= 0) {
            return approxCardinality;
        }
        if (relation instanceof org.eclipse.daanse.rolap.mapping.model.database.source.TableSource table
            && expression instanceof org.eclipse.daanse.rolap.element.RolapColumn column)
        {
            return getColumnCardinality(
                table.getTable(),
                column.getName());
        } else {
            SelectStatementBuilder q = SelectStatementBuilder.create();
            org.eclipse.daanse.sql.statement.api.model.FromClause from = RelationFromMapper.from(relation);
            // Diagnostic provenance: the distinct-values read the statistics cache wraps as
            // `select count(*) from (<this>)` — a count-distinct probe for one column/expression.
            q.header("column cardinality " + columnName(expression, from));
            q.footerComment("cardinality probe (count distinct values)");
            q.distinct(true);
            q.from(from);
            q.project(JoinPlanner.expressionFor(expression), null);
            return getQueryCardinality(SqlRender.render(q.build(), star.getDialect()).sql());
        }
    }

    private long getColumnCardinality(
        org.eclipse.daanse.cwm.model.cwm.resource.relational.NamedColumnSet table,
        String column)
    {
    	String schema = table.getNamespace() != null ? table.getNamespace().getName() : null;
        final List<String> key = Arrays.asList(schema, table.getName(), column);
        return cached(columnMap, columnInflight, key, () -> {
            long rowCount = -1;
            for (SqlStatisticsProviderNew statisticsProvider
                    : FALLBACK_PROVIDERS) {
                rowCount = statisticsProvider.getColumnCardinality(
                    star.getContext(),
                    schema,
                    table.getName(),
                    column,
                    newExecution());
                if (rowCount >= 0) {
                    break;
                }
            }
            return rowCount;
        });
    }

    /** The provenance name for the row-count probe: the caller's alias, else the FROM base alias. */
    private static String relationName(String alias,
            org.eclipse.daanse.sql.statement.api.model.FromClause from) {
        if (alias != null && !alias.isBlank()) {
            return alias;
        }
        org.eclipse.daanse.sql.statement.api.model.TableAlias base =
                org.eclipse.daanse.sql.statement.api.From.baseAlias(from);
        return base != null ? base.name() : "relation";
    }

    /** The provenance name for the count-distinct probe: table.column for a plain column, else the base alias. */
    private static String columnName(SqlExpression expression,
            org.eclipse.daanse.sql.statement.api.model.FromClause from) {
        if (expression instanceof org.eclipse.daanse.rolap.element.RolapColumn column) {
            return column.getTable() != null
                    ? column.getTable() + "." + column.getName() : column.getName();
        }
        return "expression on " + relationName(null, from);
    }

}

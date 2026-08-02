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
package org.eclipse.daanse.rolap.core.api;

import org.eclipse.daanse.olap.common.ConfigConstants;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Option;

/**
 * The OSGi metatype of a context's tuning and behaviour settings.
 *
 * <p>
 * One attribute per key in {@code ConfigConstants}, so Configuration Admin and
 * any configuration UI know what may be set on a context, of which type, with
 * which default, and what each setting is for. {@link BasicContextOCD} extends
 * this interface, which is what attaches these attributes to the context
 * component's configuration.
 * </p>
 *
 * <p>
 * <b>Nothing reads this interface at runtime.</b> It is a description; the
 * values themselves are read through {@code Context.getConfig()}. Every key,
 * every default and every method name here has a counterpart there, and the
 * three must agree - the method name <em>is</em> the configuration key, so
 * renaming a method silently orphans the setting.
 * </p>
 *
 * <p>
 * Names and descriptions live in
 * {@code OSGI-INF/l10n/org.eclipse.daanse.rolap.core.ocd.properties} and are
 * referenced by the {@code %key.name} and {@code %key.description} convention.
 * Defaults are taken from {@code ConfigConstants} as constant expressions rather
 * than repeated as literals, so there is still one place where a default is
 * written down.
 * </p>
 */
public interface ContextConfigOCD {

    // ------------------------------------------------------------------
    // Native evaluation
    //
    // "Native" means the set operation is pushed down into SQL instead of
    // being built in memory and filtered there. Native evaluation is usually
    // far faster on large dimensions, because the database does the filtering
    // next to the data. It is not always possible, and these switches exist
    // mainly to turn it off when a native path misbehaves.
    // ------------------------------------------------------------------

    /** @see org.eclipse.daanse.olap.api.ContextConfig#enableNativeFilter() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_NATIVE_FILTER + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_NATIVE_FILTER + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ENABLE_NATIVE_FILTER_DEFAULT_VALUE,
            required = false)
    boolean enableNativeFilter();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#enableNativeCrossJoin() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_NATIVE_CROSS_JOIN + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_NATIVE_CROSS_JOIN + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ENABLE_NATIVE_CROSS_JOIN_DEFAULT_VALUE,
            required = false)
    boolean enableNativeCrossJoin();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#enableNativeNonEmpty() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_NATIVE_NON_EMPTY + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_NATIVE_NON_EMPTY + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ENABLE_NATIVE_NON_EMPTY_DEFAULT_VALUE,
            required = false)
    boolean enableNativeNonEmpty();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#enableNativeTopCount() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_NATIVE_TOP_COUNT + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_NATIVE_TOP_COUNT + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ENABLE_NATIVE_TOP_COUNT_DEFAULT_VALUE,
            required = false)
    boolean enableNativeTopCount();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#expandNonNative() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXPAND_NON_NATIVE + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXPAND_NON_NATIVE + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.EXPAND_NON_NATIVE_DEFAULT_VALUE,
            required = false)
    boolean expandNonNative();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#nativizeMinThreshold() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.NATIVIZE_MIN_THRESHOLD + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.NATIVIZE_MIN_THRESHOLD + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.NATIVIZE_MIN_THRESHOLD_DEFAULT_VALUE,
            required = false)
    int nativizeMinThreshold();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#nativizeMaxResults() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.NATIVIZE_MAX_RESULTS + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.NATIVIZE_MAX_RESULTS + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.NATIVIZE_MAX_RESULTS_DEFAULT_VALUE,
            required = false)
    int nativizeMaxResults();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#alertNativeEvaluationUnsupported() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED_DEFAULT_VALUE,
            options = {
                    @Option(value = "OFF", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".OFF" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "WARN", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".WARN" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "ERROR", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.ALERT_NATIVE_EVALUATION_UNSUPPORTED
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".ERROR" + BasicContextOCD.L10N_POSTFIX_LABEL)
            },
            required = false)
    String alertNativeEvaluationUnsupported();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#levelPreCacheThreshold() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD_DEFAULT_VALUE,
            required = false)
    int levelPreCacheThreshold();

    // ------------------------------------------------------------------
    // Cell cache and segments
    //
    // Cell values are held in segments: rectangular blocks of the cube keyed
    // by the columns that were constrained. These settings govern how those
    // blocks are stored, shared and discarded.
    // ------------------------------------------------------------------

    /** @see org.eclipse.daanse.olap.api.ContextConfig#segmentCache() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.SEGMENT_CACHE + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.SEGMENT_CACHE + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            required = false)
    String segmentCache();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#disableCaching() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.DISABLE_CACHING + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.DISABLE_CACHING + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.DISABLE_CACHING_DEFAULT_VALUE,
            required = false)
    boolean disableCaching();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#disableLocalSegmentCache() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.DISABLE_LOCAL_SEGMENT_CACHE + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.DISABLE_LOCAL_SEGMENT_CACHE + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.DISABLE_LOCAL_SEGMENT_CACHE_DEFAULT_VALUE,
            required = false)
    boolean disableLocalSegmentCache();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#enableSessionCaching() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_SESSION_CACHING + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_SESSION_CACHING + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ENABLE_SESSION_CACHING_DEFAULT_VALUE,
            required = false)
    boolean enableSessionCaching();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#cellBatchSize() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.CELL_BATCH_SIZE + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.CELL_BATCH_SIZE + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.CELL_BATCH_SIZE_DEFAULT_VALUE,
            required = false)
    int cellBatchSize();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#sparseSegmentCountThreshold() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.SPARSE_SEGMENT_COUNT_THRESHOLD + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.SPARSE_SEGMENT_COUNT_THRESHOLD + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.SPARSE_SEGMENT_COUNT_THRESHOLD_DEFAULT_VALUE,
            required = false)
    int sparseSegmentCountThreshold();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#sparseSegmentDensityThreshold() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.SPARSE_SEGMENT_DENSITY_THRESHOLD + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.SPARSE_SEGMENT_DENSITY_THRESHOLD + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.SPARSE_SEGMENT_DENSITY_THRESHOLD_DEFAULT_VALUE,
            required = false)
    double sparseSegmentDensityThreshold();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#enableInMemoryRollup() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_IN_MEMORY_ROLLUP + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_IN_MEMORY_ROLLUP + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ENABLE_IN_MEMORY_ROLLUP_DEFAULT_VALUE,
            required = false)
    boolean enableInMemoryRollup();

    // ------------------------------------------------------------------
    // Evaluation and MDX semantics
    //
    // These change what a query means, not just how fast it is answered.
    // Changing one of them can change reported numbers.
    // ------------------------------------------------------------------

    /** @see org.eclipse.daanse.olap.api.ContextConfig#solveOrderMode() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.SOLVE_ORDER_MODE + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.SOLVE_ORDER_MODE + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = ConfigConstants.SOLVE_ORDER_MODE_DEFAULT_VALUE,
            options = {
                    @Option(value = "ABSOLUTE", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.SOLVE_ORDER_MODE
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".ABSOLUTE" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "SCOPED", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.SOLVE_ORDER_MODE
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".SCOPED" + BasicContextOCD.L10N_POSTFIX_LABEL)
            },
            required = false)
    String solveOrderMode();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#compoundSlicerMemberSolveOrder() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.COMPOUND_SLICER_MEMBER_SOLVE_ORDER + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.COMPOUND_SLICER_MEMBER_SOLVE_ORDER + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.COMPOUND_SLICER_MEMBER_SOLVE_ORDER_DEFAULT_VALUE,
            required = false)
    int compoundSlicerMemberSolveOrder();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#currentMemberWithCompoundSlicerAlert() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.CURRENT_MEMBER_WITH_COMPOUND_SLICER_ALERT + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.CURRENT_MEMBER_WITH_COMPOUND_SLICER_ALERT + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = ConfigConstants.CURRENT_MEMBER_WITH_COMPOUND_SLICER_ALERT_DEFAULT_VALUE,
            options = {
                    @Option(value = "OFF", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.CURRENT_MEMBER_WITH_COMPOUND_SLICER_ALERT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".OFF" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "WARN", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.CURRENT_MEMBER_WITH_COMPOUND_SLICER_ALERT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".WARN" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "ERROR", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.CURRENT_MEMBER_WITH_COMPOUND_SLICER_ALERT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".ERROR" + BasicContextOCD.L10N_POSTFIX_LABEL)
            },
            required = false)
    String currentMemberWithCompoundSlicerAlert();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#ignoreMeasureForNonJoiningDimension() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.IGNORE_MEASURE_FOR_NON_JOINING_DIMENSION + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.IGNORE_MEASURE_FOR_NON_JOINING_DIMENSION + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.IGNORE_MEASURE_FOR_NON_JOINING_DIMENSION_DEFAULT_VALUE,
            required = false)
    boolean ignoreMeasureForNonJoiningDimension();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#nullDenominatorProducesNull() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.NULL_DENOMINATOR_PRODUCES_NULL + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.NULL_DENOMINATOR_PRODUCES_NULL + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.NULL_DENOMINATOR_PRODUCES_NULL_DEFAULT_VALUE,
            required = false)
    boolean nullDenominatorProducesNull();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#needDimensionPrefix() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.NEED_DIMENSION_PREFIX + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.NEED_DIMENSION_PREFIX + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.NEED_DIMENSION_PREFIX_DEFAULT_VALUE,
            required = false)
    boolean needDimensionPrefix();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#ignoreInvalidMembers() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.IGNORE_INVALID_MEMBERS + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.IGNORE_INVALID_MEMBERS + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.IGNORE_INVALID_MEMBERS_DEFAULT_VALUE,
            required = false)
    boolean ignoreInvalidMembers();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#ignoreInvalidMembersDuringQuery() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.IGNORE_INVALID_MEMBERS_DURING_QUERY + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.IGNORE_INVALID_MEMBERS_DURING_QUERY + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.IGNORE_INVALID_MEMBERS_DURING_QUERY_DEFAULT_VALUE,
            required = false)
    boolean ignoreInvalidMembersDuringQuery();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#maxEvalDepth() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.MAX_EVAL_DEPTH + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.MAX_EVAL_DEPTH + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.MAX_EVAL_DEPTH_DEFAULT_VALUE,
            required = false)
    int maxEvalDepth();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#iterationLimit() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ITERATION_LIMIT + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ITERATION_LIMIT + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ITERATION_LIMIT_DEFAULT_VALUE,
            required = false)
    int iterationLimit();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#crossJoinOptimizerSize() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.CROSS_JOIN_OPTIMIZER_SIZE + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.CROSS_JOIN_OPTIMIZER_SIZE + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.CROSS_JOIN_OPTIMIZER_SIZE_DEFAULT_VALUE,
            required = false)
    int crossJoinOptimizerSize();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#optimizePredicates() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.OPTIMIZE_PREDICATES + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.OPTIMIZE_PREDICATES + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.OPTIMIZE_PREDICATES_DEFAULT_VALUE,
            required = false)
    boolean optimizePredicates();

    // ------------------------------------------------------------------
    // Aggregate tables
    //
    // Pre-aggregated tables answer coarse questions without touching the fact
    // table. Reading them and using them are separate switches, and both must
    // be on for aggregates to take effect.
    // ------------------------------------------------------------------

    /** @see org.eclipse.daanse.olap.api.ContextConfig#useAggregates() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.USE_AGGREGATES + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.USE_AGGREGATES + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.USE_AGGREGATES_DEFAULT_VALUE,
            required = false)
    boolean useAggregates();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#readAggregates() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.READ_AGGREGATES + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.READ_AGGREGATES + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.READ_AGGREGATES_DEFAULT_VALUE,
            required = false)
    boolean readAggregates();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#chooseAggregateByVolume() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.CHOOSE_AGGREGATE_BY_VOLUME + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.CHOOSE_AGGREGATE_BY_VOLUME + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.CHOOSE_AGGREGATE_BY_VOLUME_DEFAULT_VALUE,
            required = false)
    boolean chooseAggregateByVolume();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#generateAggregateSql() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.GENERATE_AGGREGATE_SQL + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.GENERATE_AGGREGATE_SQL + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.GENERATE_AGGREGATE_SQL_DEFAULT_VALUE,
            required = false)
    boolean generateAggregateSql();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#enableGroupingSets() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_GROUPING_SETS + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_GROUPING_SETS + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ENABLE_GROUPING_SETS_DEFAULT_VALUE,
            required = false)
    boolean enableGroupingSets();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#enableTotalCount() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_TOTAL_COUNT + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_TOTAL_COUNT + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ENABLE_TOTAL_COUNT_DEFAULT_VALUE,
            required = false)
    boolean enableTotalCount();

    // ------------------------------------------------------------------
    // Concurrency
    // ------------------------------------------------------------------

    /** @see org.eclipse.daanse.olap.api.ContextConfig#queryLimit() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.QUERY_LIMIT + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.QUERY_LIMIT + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.QUERY_LIMIT_DEFAULT_VALUE,
            required = false)
    int queryLimit();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#rolapConnectionShepherdNbThreads() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_NB_THREADS + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_NB_THREADS + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_NB_THREADS_DEFAULT_VALUE,
            required = false)
    int rolapConnectionShepherdNbThreads();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#rolapConnectionShepherdThreadPollingInterval() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_DEFAULT_VALUE,
            required = false)
    long rolapConnectionShepherdThreadPollingInterval();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#rolapConnectionShepherdThreadPollingIntervalUnit() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_UNIT + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_UNIT + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_UNIT_DEFAULT_VALUE,
            options = {
                    @Option(value = "NANOSECONDS", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".NANOSECONDS" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "MICROSECONDS", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".MICROSECONDS" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "MILLISECONDS", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".MILLISECONDS" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "SECONDS", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".SECONDS" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "MINUTES", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".MINUTES" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "HOURS", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".HOURS" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "DAYS", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".DAYS" + BasicContextOCD.L10N_POSTFIX_LABEL)
            },
            required = false)
    String rolapConnectionShepherdThreadPollingIntervalUnit();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#segmentCacheManagerNumberSqlThreads() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.SEGMENT_CACHE_MANAGER_NUMBER_SQL_THREADS + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.SEGMENT_CACHE_MANAGER_NUMBER_SQL_THREADS + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.SEGMENT_CACHE_MANAGER_NUMBER_SQL_THREADS_DEFAULT_VALUE,
            required = false)
    int segmentCacheManagerNumberSqlThreads();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#segmentCacheManagerNumberCacheThreads() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.SEGMENT_CACHE_MANAGER_NUMBER_CACHE_THREADS + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.SEGMENT_CACHE_MANAGER_NUMBER_CACHE_THREADS + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.SEGMENT_CACHE_MANAGER_NUMBER_CACHE_THREADS_DEFAULT_VALUE,
            required = false)
    int segmentCacheManagerNumberCacheThreads();

    // ------------------------------------------------------------------
    // Execution, cancellation and limits
    // ------------------------------------------------------------------

    /** @see org.eclipse.daanse.olap.api.ContextConfig#queryTimeout() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.QUERY_TIMEOUT + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.QUERY_TIMEOUT + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.QUERY_TIMEOUT_DEFAULT_VALUE,
            required = false)
    int queryTimeout();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#executeDuration() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXECUTE_DURATION + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXECUTE_DURATION + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.EXECUTE_DURATION_DEFAULT_VALUE,
            required = false)
    long executeDuration();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#executeDurationUnit() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXECUTE_DURATION_UNIT + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXECUTE_DURATION_UNIT + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = ConfigConstants.EXECUTE_DURATION_UNIT_DEFAULT_VALUE,
            options = {
                    @Option(value = "NANOSECONDS", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXECUTE_DURATION_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".NANOSECONDS" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "MICROSECONDS", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXECUTE_DURATION_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".MICROSECONDS" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "MILLISECONDS", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXECUTE_DURATION_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".MILLISECONDS" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "SECONDS", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXECUTE_DURATION_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".SECONDS" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "MINUTES", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXECUTE_DURATION_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".MINUTES" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "HOURS", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXECUTE_DURATION_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".HOURS" + BasicContextOCD.L10N_POSTFIX_LABEL),
                    @Option(value = "DAYS", label = BasicContextOCD.L10N_PREFIX + ConfigConstants.EXECUTE_DURATION_UNIT
                            + BasicContextOCD.L10N_POSTFIX_OPTION + ".DAYS" + BasicContextOCD.L10N_POSTFIX_LABEL)
            },
            required = false)
    String executeDurationUnit();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#checkCancelOrTimeoutInterval() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.CHECK_CANCEL_OR_TIMEOUT_INTERVAL + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.CHECK_CANCEL_OR_TIMEOUT_INTERVAL + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.CHECK_CANCEL_OR_TIMEOUT_INTERVAL_DEFAULT_VALUE,
            required = false)
    int checkCancelOrTimeoutInterval();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#enableDrillThrough() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_DRILL_THROUGH + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_DRILL_THROUGH + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ENABLE_DRILL_THROUGH_DEFAULT_VALUE,
            required = false)
    boolean enableDrillThrough();

    // ------------------------------------------------------------------
    // Memory monitor
    // ------------------------------------------------------------------

    /** @see org.eclipse.daanse.olap.api.ContextConfig#memoryMonitor() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.MEMORY_MONITOR + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.MEMORY_MONITOR + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.MEMORY_MONITOR_DEFAULT_VALUE,
            required = false)
    boolean memoryMonitor();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#memoryMonitorThreshold() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.MEMORY_MONITOR_THRESHOLD + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.MEMORY_MONITOR_THRESHOLD + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.MEMORY_MONITOR_THRESHOLD_DEFAULT_VALUE,
            required = false)
    int memoryMonitorThreshold();

    // ------------------------------------------------------------------
    // SQL and diagnostics
    // ------------------------------------------------------------------

    /** @see org.eclipse.daanse.olap.api.ContextConfig#generateFormattedSql() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.GENERATE_FORMATTED_SQL + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.GENERATE_FORMATTED_SQL + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.GENERATE_FORMATTED_SQL_DEFAULT_VALUE,
            required = false)
    boolean generateFormattedSql();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#warnIfNoPatternForDialect() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.WARN_IF_NO_PATTERN_FOR_DIALECT + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.WARN_IF_NO_PATTERN_FOR_DIALECT + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = ConfigConstants.WARN_IF_NO_PATTERN_FOR_DIALECT_DEFAULT_VALUE,
            required = false)
    String warnIfNoPatternForDialect();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#testExpDependencies() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.TEST_EXP_DEPENDENCIES + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.TEST_EXP_DEPENDENCIES + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.TEST_EXP_DEPENDENCIES_DEFAULT_VALUE,
            required = false)
    int testExpDependencies();

    // ------------------------------------------------------------------
    // Naming, ordering and result shape
    //
    // These were the JVM-wide switches until they became per-context. They
    // are grouped here because they share that history, not because they are
    // otherwise related.
    // ------------------------------------------------------------------

    /** @see org.eclipse.daanse.olap.api.ContextConfig#caseSensitive() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.CASE_SENSITIVE + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.CASE_SENSITIVE + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.CASE_SENSITIVE_DEFAULT_VALUE,
            required = false)
    boolean caseSensitive();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#caseSensitiveMdxInstr() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.CASE_SENSITIVE_MDX_INSTR + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.CASE_SENSITIVE_MDX_INSTR + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.CASE_SENSITIVE_MDX_INSTR_DEFAULT_VALUE,
            required = false)
    boolean caseSensitiveMdxInstr();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#compareSiblingsByOrderKey() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.COMPARE_SIBLINGS_BY_ORDER_KEY + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.COMPARE_SIBLINGS_BY_ORDER_KEY + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.COMPARE_SIBLINGS_BY_ORDER_KEY_DEFAULT_VALUE,
            required = false)
    boolean compareSiblingsByOrderKey();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#enableExpCache() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_EXP_CACHE + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_EXP_CACHE + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ENABLE_EXP_CACHE_DEFAULT_VALUE,
            required = false)
    boolean enableExpCache();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#enableNonEmptyOnAllAxis() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ENABLE_NON_EMPTY_ON_ALL_AXIS_DEFAULT_VALUE,
            required = false)
    boolean enableNonEmptyOnAllAxis();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#enableRolapCubeMemberCache() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_ROLAP_CUBE_MEMBER_CACHE + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.ENABLE_ROLAP_CUBE_MEMBER_CACHE + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.ENABLE_ROLAP_CUBE_MEMBER_CACHE_DEFAULT_VALUE,
            required = false)
    boolean enableRolapCubeMemberCache();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#filterChildlessSnowflakeMembers() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.FILTER_CHILDLESS_SNOWFLAKE_MEMBERS_DEFAULT_VALUE,
            required = false)
    boolean filterChildlessSnowflakeMembers();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#maxConstraints() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.MAX_CONSTRAINTS + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.MAX_CONSTRAINTS + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.MAX_CONSTRAINTS_DEFAULT_VALUE,
            required = false)
    int maxConstraints();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#nullMemberRepresentation() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.NULL_MEMBER_REPRESENTATION + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.NULL_MEMBER_REPRESENTATION + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = ConfigConstants.NULL_MEMBER_REPRESENTATION_DEFAULT_VALUE,
            required = false)
    String nullMemberRepresentation();

    /** @see org.eclipse.daanse.olap.api.ContextConfig#resultLimit() */
    @AttributeDefinition(
            name = BasicContextOCD.L10N_PREFIX + ConfigConstants.RESULT_LIMIT + BasicContextOCD.L10N_POSTFIX_NAME,
            description = BasicContextOCD.L10N_PREFIX + ConfigConstants.RESULT_LIMIT + BasicContextOCD.L10N_POSTFIX_DESCRIPTION,
            defaultValue = "" + ConfigConstants.RESULT_LIMIT_DEFAULT_VALUE,
            required = false)
    int resultLimit();
}

/*
* Copyright (c) 2022 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.core.internal;



import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_PID;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_AGG_MATCH_RULES_SUPPLIER;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_CATALOG_MAPPING_SUPPLIER;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_CUSTOM_AGGREGATOR;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_CONNECTION_POOL;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_DIALECT_FACTORY;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_EXPRESSION_COMPILER_FACTORY;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_FUNCTION_SERVICE;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_DMV_PARSER_PROVIDER;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_MDX_PARSER_PROVIDER;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_SQL_GUARD_FACTORY;
import static org.osgi.namespace.unresolvable.UnresolvableNamespace.UNRESOLVABLE_FILTER;
import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ServiceScope.SINGLETON;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;

import javax.sql.DataSource;

import org.eclipse.daanse.jdbc.datasource.pools.api.ConnectionPool;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.DialectFactory;
import org.eclipse.daanse.dmv.parser.api.DmvParserProvider;
import org.eclipse.daanse.mdx.parser.api.MdxParserProvider;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompiler;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.agg.AggregationFactory;
import org.eclipse.daanse.olap.api.aggregator.CustomAggregatorFactory;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompilerFactory;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.function.FunctionService;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.core.LoggingEventBus;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.api.aggmatch.AggregationMatchRulesSupplier;
import org.eclipse.daanse.rolap.common.AbstractRolapContext;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.aggregator.AggregationFactoryImpl;
import org.eclipse.daanse.rolap.common.catalog.ContentIdentity;
import org.eclipse.daanse.rolap.common.catalog.RolapCatalogCache;
import org.eclipse.daanse.rolap.common.connection.ExternalRolapConnection;
import org.eclipse.daanse.rolap.common.connection.InternalRolapConnection;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluatorRoot;
import org.eclipse.daanse.rolap.common.result.RolapResult;
import org.eclipse.daanse.olap.result.ResultShepherdImpl;
import org.eclipse.daanse.rolap.core.api.BasicContextOCD;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.sql.guard.api.SqlGuardFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.daanse.rolap.mapping.model.access.common.AccessRole;
import org.eclipse.daanse.rolap.mapping.model.provider.util.CwmHelper;
import org.eclipse.daanse.cwm.model.cwm.foundation.businessinformation.util.Descriptions;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
@Designate(ocd = BasicContextOCD.class, factory = true)
@Component(service = Context.class, scope = SINGLETON, configurationPid = BASIC_CONTEXT_PID)
public class BasicContext extends AbstractRolapContext implements RolapContext {

    private static final String ERR_MSG_DIALECT_INIT = "Could not activate context. Error on initialisation of Dialect";

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicContext.class);

    private ConnectionPool connectionPool;

    private DialectFactory dialectFactory;

    private CatalogMappingSupplier catalogMappingSupplier;

    private volatile org.eclipse.daanse.rolap.mapping.model.catalog.Catalog cachedCatalogMapping;

    private volatile byte[] cachedContentIdentitySha256;

    private ExpressionCompilerFactory expressionCompilerFactory;

    private MdxParserProvider mdxParserProvider;

    private DmvParserProvider dmvParserProvider;

    private FunctionService functionService;

    private SqlGuardFactory sqlGuardFactory;

    private AggregationMatchRulesSupplier aggMatchRulesSupplier;

    private Dialect dialect = null;

    private AggregationFactory aggregationFactory = null;

    private Semaphore queryLimitSemaphore;

    private List<CustomAggregatorFactory> customAggregators = new ArrayList<CustomAggregatorFactory>();

    public BasicContext() {
    }

    @Activate
    public void activate(Map<String, Object> configuration) throws Exception {
        updateConfiguration(configuration);
        activate1();
        startCacheStatsLogger(configuration);
    }

    /**
     * Optional periodic cache observability: with
     * {@code cacheStatsLogSeconds > 0} in the component configuration a
     * daemon thread logs {@code CacheStatsReport.capture(this)} at INFO on
     * that period. Default off - the report stays a pull-only seam.
     */
    private java.util.concurrent.ScheduledExecutorService cacheStatsLogger;

    /** Component-config key for the optional periodic stats log. */
    static final String CACHE_STATS_LOG_SECONDS = "cacheStatsLogSeconds";

    private void startCacheStatsLogger(Map<String, Object> configuration) {
        long seconds = 0;
        Object raw = configuration == null ? null : configuration.get(CACHE_STATS_LOG_SECONDS);
        if (raw != null) {
            try {
                seconds = Long.parseLong(String.valueOf(raw));
            } catch (NumberFormatException e) {
                LOGGER.warn(CACHE_STATS_LOG_SECONDS + " carries '{}' (expected a number); stats logging stays off", raw);
            }
        }
        if (seconds <= 0) {
            return;
        }
        cacheStatsLogger = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "daanse.rolap.cacheStatsLogger");
            thread.setDaemon(true);
            return thread;
        });
        cacheStatsLogger.scheduleAtFixedRate(() -> {
            try {
                LOGGER.info(org.eclipse.daanse.rolap.common.CacheStatsReport.capture(this).formatted());
            } catch (RuntimeException e) {
                LOGGER.debug("cache stats capture failed", e);
            }
        }, seconds, seconds, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * The context binds a pool, not a raw DataSource. Binding the pool type is what
     * makes it impossible to end up on an unpooled connection by configuration
     * mistake - rolap opens one physical connection per statement, so an unpooled
     * context exhausts ports, server processes or table locks depending on the
     * database.
     */
    // O1: monitoring event buses bind as whiteboard services (same shape
    // as the segment caches below); with none bound the logging default
    // keeps the engine observable. Dispatch reads the live list, so a bus
    // arriving or leaving after activation takes effect immediately.
    private final List<org.eclipse.daanse.olap.api.monitor.EventBus> eventBuses =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    @Reference(cardinality = MULTIPLE, policy = DYNAMIC, policyOption = org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY)
    protected void bindEventBus(org.eclipse.daanse.olap.api.monitor.EventBus bus) {
        eventBuses.add(bus);
    }

    protected void unbindEventBus(org.eclipse.daanse.olap.api.monitor.EventBus bus) {
        eventBuses.remove(bus);
    }

    // external segment caches bind as whiteboard services; the provider owns
    // their lifecycle, unbind never tears a cache down
    private final List<org.eclipse.daanse.olap.spi.SegmentCache> segmentCaches =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    // synchronized with the activate replay loop: a bind racing activate must
    // either land in the pre-activation buffer or see the manager — never
    // neither. Attach order is bind order; service ranking is not consulted.
    // Services marked daanse.segmentcache.hidden=true stay unbound; they
    // feed a decorating cache that republishes itself without the marker.
    @Reference(cardinality = MULTIPLE, policy = DYNAMIC, policyOption = org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY, target = "(!(daanse.segmentcache.hidden=true))")
    protected void bindSegmentCache(org.eclipse.daanse.olap.spi.SegmentCache segmentCache) {
        synchronized (segmentCaches) {
            segmentCaches.add(segmentCache);
            if (aggMgr != null) {
                sharedCacheManager().addExternalCache(segmentCache);
            }
        }
    }

    protected void unbindSegmentCache(org.eclipse.daanse.olap.spi.SegmentCache segmentCache) {
        synchronized (segmentCaches) {
            segmentCaches.remove(segmentCache);
            if (aggMgr != null) {
                sharedCacheManager().removeExternalCache(segmentCache);
            }
        }
    }

    private org.eclipse.daanse.rolap.common.agg.SegmentCacheManager sharedCacheManager() {
        return (org.eclipse.daanse.rolap.common.agg.SegmentCacheManager) aggMgr.getSegmentCacheManager();
    }

    @Reference(name = BASIC_CONTEXT_REF_NAME_CONNECTION_POOL, target = UNRESOLVABLE_FILTER)
    protected void setConnectionPool(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
        this.cachedContentIdentitySha256 = null;
    }

    protected void unsetConnectionPool(ConnectionPool connectionPool) {
        if (this.connectionPool == connectionPool) {
            this.connectionPool = null;
            this.cachedContentIdentitySha256 = null;
        }
    }

    @Reference(name = BASIC_CONTEXT_REF_NAME_DIALECT_FACTORY, target = UNRESOLVABLE_FILTER)
    protected void setDialectFactory(DialectFactory dialectFactory) {
        this.dialectFactory = dialectFactory;
    }

    protected void unsetDialectFactory(DialectFactory dialectFactory) {
        if (this.dialectFactory == dialectFactory) {
            this.dialectFactory = null;
        }
    }

    @Reference(name = BASIC_CONTEXT_REF_NAME_CATALOG_MAPPING_SUPPLIER, target = UNRESOLVABLE_FILTER)
    protected void setCatalogMappingSupplier(CatalogMappingSupplier catalogMappingSupplier) {
        this.catalogMappingSupplier = catalogMappingSupplier;
        this.cachedCatalogMapping = null;
        this.cachedContentIdentitySha256 = null;
    }

    protected void unsetCatalogMappingSupplier(CatalogMappingSupplier catalogMappingSupplier) {
        if (this.catalogMappingSupplier == catalogMappingSupplier) {
            this.catalogMappingSupplier = null;
            this.cachedCatalogMapping = null;
            this.cachedContentIdentitySha256 = null;
        }
    }

    @Reference(name = BASIC_CONTEXT_REF_NAME_EXPRESSION_COMPILER_FACTORY)
    protected void setExpressionCompilerFactory(ExpressionCompilerFactory expressionCompilerFactory) {
        this.expressionCompilerFactory = expressionCompilerFactory;
    }

    protected void unsetExpressionCompilerFactory(ExpressionCompilerFactory expressionCompilerFactory) {
        if (this.expressionCompilerFactory == expressionCompilerFactory) {
            this.expressionCompilerFactory = null;
        }
    }

    @Reference(name = BASIC_CONTEXT_REF_NAME_MDX_PARSER_PROVIDER)
    protected void setMdxParserProvider(MdxParserProvider mdxParserProvider) {
        this.mdxParserProvider = mdxParserProvider;
    }

    protected void unsetMdxParserProvider(MdxParserProvider mdxParserProvider) {
        if (this.mdxParserProvider == mdxParserProvider) {
            this.mdxParserProvider = null;
        }
    }

    @Reference(name = BASIC_CONTEXT_REF_NAME_FUNCTION_SERVICE)
    protected void setFunctionService(FunctionService functionService) {
        this.functionService = functionService;
    }

    protected void unsetFunctionService(FunctionService functionService) {
        if (this.functionService == functionService) {
            this.functionService = null;
        }
    }

    @Reference(name = BASIC_CONTEXT_REF_NAME_DMV_PARSER_PROVIDER, cardinality = OPTIONAL)
    protected void setDmvParserProvider(DmvParserProvider dmvParserProvider) {
        this.dmvParserProvider = dmvParserProvider;
    }

    protected void unsetDmvParserProvider(DmvParserProvider dmvParserProvider) {
        if (this.dmvParserProvider == dmvParserProvider) {
            this.dmvParserProvider = null;
        }
    }

    @Reference(name = BASIC_CONTEXT_REF_NAME_SQL_GUARD_FACTORY, cardinality = OPTIONAL)
    protected void setSqlGuardFactory(SqlGuardFactory sqlGuardFactory) {
        this.sqlGuardFactory = sqlGuardFactory;
    }

    protected void unsetSqlGuardFactory(SqlGuardFactory sqlGuardFactory) {
        if (this.sqlGuardFactory == sqlGuardFactory) {
            this.sqlGuardFactory = null;
        }
    }

    @Reference(name = BASIC_CONTEXT_REF_NAME_AGG_MATCH_RULES_SUPPLIER, cardinality = OPTIONAL)
    protected void setAggMatchRulesSupplier(AggregationMatchRulesSupplier aggMatchRulesSupplier) {
        this.aggMatchRulesSupplier = aggMatchRulesSupplier;
    }

    protected void unsetAggMatchRulesSupplier(AggregationMatchRulesSupplier aggMatchRulesSupplier) {
        if (this.aggMatchRulesSupplier == aggMatchRulesSupplier) {
            this.aggMatchRulesSupplier = null;
        }
    }

    @Reference(name = BASIC_CONTEXT_REF_NAME_CUSTOM_AGGREGATOR, cardinality = MULTIPLE, policy = DYNAMIC)
    public void bindCustomAgregators(CustomAggregatorFactory aggregator) {
        customAggregators.add(aggregator);
    }

    public void unbindCustomAgregators(CustomAggregatorFactory aggregator) {
        customAggregators.remove(aggregator);
    }

    private void activate1() throws Exception {

        final org.eclipse.daanse.olap.api.monitor.EventBus loggingFallback = new LoggingEventBus();
        this.eventBus = event -> {
            if (eventBuses.isEmpty()) {
                loggingFallback.accept(event);
                return;
            }
            for (org.eclipse.daanse.olap.api.monitor.EventBus bus : eventBuses) {
                // per-bus isolation: emission sites sit on hot paths
                // (statement execution, the cache actor) - a buggy
                // third-party bus must neither break the query nor
                // suppress the remaining buses
                try {
                    bus.accept(event);
                } catch (RuntimeException | Error e) {
                    LOGGER.warn("event bus {} failed; continuing with the others",
                            bus.getClass().getName(), e);
                }
            }
        };

        catalogCache = new RolapCatalogCache(this);
        queryLimitSemaphore = new Semaphore(
                getConfig().queryLimit());

        try (Connection connection = connectionPool.getConnection()) {
            dialect = dialectFactory.createDialect(connection);
            aggregationFactory = new AggregationFactoryImpl(this.getCustomAggregators());
        } catch (SQLException e) {
            LOGGER.error(ERR_MSG_DIALECT_INIT, e);
        }

        shepherd = new ResultShepherdImpl(
                getConfig().rolapConnectionShepherdThreadPollingInterval(),
                getConfig().rolapConnectionShepherdThreadPollingIntervalUnit(),
                getConfig().rolapConnectionShepherdNbThreads());
        synchronized (segmentCaches) {
            aggMgr = new AggregationManager(this);
            for (org.eclipse.daanse.olap.spi.SegmentCache segmentCache : segmentCaches) {
                sharedCacheManager().addExternalCache(segmentCache);
            }
        }

        // arm the orphaned-context safety net now that every resource the
        // cleanup needs exists
        registerCleanup();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("new DaanseServer: id=" + getId());
        }
    }

    @Deactivate
    public void deactivate(Map<String, Object> configuration) throws Exception {
        if (cacheStatsLogger != null) {
            cacheStatsLogger.shutdownNow();
            cacheStatsLogger = null;
        }
        shutdown();
        updateConfiguration(null);
    }

    /**
     * The pool's own DataSource, so every caller that goes through this getter -
     * SqlStatement, RolapStar, the writeback emitter, the documentation provider -
     * draws from the pool without knowing about it.
     */
    @Override
    public DataSource getDataSource() {
        return connectionPool.dataSource();
    }

    @Override
    public ConnectionPool getConnectionPool() {
        return connectionPool;
    }

    @Override
    public Dialect getDialect() {
        return dialect;
    }

    @Override
    public String getName() {
        return getCatalogMapping().getName();
    }

    @Override
    public Optional<String> getDescription() {
        return Optional.ofNullable(Descriptions.localizedBody(getCatalogMapping(), CwmHelper.TYPE_DOCUMENTATION, null).orElse(null));
    }

    @Override
    public org.eclipse.daanse.rolap.mapping.model.catalog.Catalog getCatalogMapping() {
        if (cachedCatalogMapping == null) {
            cachedCatalogMapping = catalogMappingSupplier.get();
        }
        return cachedCatalogMapping;
    }

    @Override
    public byte[] getContentIdentitySha256() {
        byte[] identity = cachedContentIdentitySha256;
        if (identity == null) {
            // one JDBC round-trip, not one per racing first connection
            synchronized (this) {
                identity = cachedContentIdentitySha256;
                if (identity == null) {
                    try (Connection connection = getDataSource().getConnection()) {
                        DatabaseMetaData metaData = connection.getMetaData();
                        identity = ContentIdentity.sha256(catalogMappingSupplier.sha256(),
                                metaData.getURL(), metaData.getUserName(), connection.getCatalog(),
                                schemaOf(connection));
                        cachedContentIdentitySha256 = identity;
                    } catch (SQLException e) {
                        throw new IllegalStateException("content identity unavailable", e);
                    }
                }
            }
        }
        return identity.clone();
    }

    private static String schemaOf(Connection connection) {
        try {
            return connection.getSchema();
        } catch (SQLException e) {
            return "";
        }
    }

    @Override
    public ExpressionCompilerFactory getExpressionCompilerFactory() {
        return expressionCompilerFactory;
    }

    @Override
    public org.eclipse.daanse.olap.api.connection.Connection getConnectionWithDefaultRole() {
        return new InternalRolapConnection(this, null, new ConnectionProps());
    }

    @Override
    public org.eclipse.daanse.olap.api.connection.Connection getConnection(ConnectionProps props) {
        return new ExternalRolapConnection(this, props);
    }

    @Override
    public Semaphore getQueryLimitSemaphore() {
        return queryLimitSemaphore;
    }

    @Override
    public Optional<Map<Object, Object>> getSqlMemberSourceValuePool() {
        return Optional.empty(); // Caffein Cache is an option
    }

    @Override
    public FunctionService getFunctionService() {
        return functionService;
    }

    @Override
    public MdxParserProvider getMdxParserProvider() {
        return mdxParserProvider;
    }

    @Override
    public Optional<DmvParserProvider> getDmvParserProvider() {
        return Optional.ofNullable(dmvParserProvider);
    }

    @Override
    public List<String> getAccessRoles() {
    	org.eclipse.daanse.rolap.mapping.model.catalog.Catalog catalogMapping = getCatalogMapping();
        if (catalogMapping != null) {
            return Packages.available(catalogMapping, AccessRole.class).stream().map(org.eclipse.daanse.rolap.mapping.model.access.common.AccessRole::getName).toList();
        }
        return List.of();// may take from mapping
    }

    @Override
    public Optional<SqlGuardFactory> getSqlGuardFactory() {
        return Optional.ofNullable(sqlGuardFactory);
    }

    @Override
    public Optional<AggregationMatchRulesSupplier> getAggMatchRulesSupplier() {
        return Optional.ofNullable(aggMatchRulesSupplier);
    }

    @Override
    public AggregationFactory getAggregationFactory() {
        return this.aggregationFactory;
    }

    @Override
    public Evaluator createEvaluator(Statement statement) {
        final RolapEvaluatorRoot root = new RolapEvaluatorRoot(statement);
        return new RolapEvaluator(root);
    };

    @Override
    public Evaluator createDummyEvaluator(Statement statement) {
        ExecutionImpl dummyExecution = new ExecutionImpl(statement,
                ExecuteDurationUtil.executeDurationValue(statement.getConnection().getContext()));
        final RolapResult result = new RolapResult(dummyExecution, false);
        return result.getRootEvaluator();
    };

    @Override
    public List<CustomAggregatorFactory> getCustomAggregators() {
        return List.copyOf(this.customAggregators);
    };
}

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
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_CATALOG_MAPPING_SUPPLIER;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_CUSTOM_AGGREGATOR;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_DATA_SOURCE;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_DIALECT_FACTORY;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_EXPRESSION_COMPILER_FACTORY;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_FUNCTION_SERVICE;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_MDX_PARSER_PROVIDER;
import static org.osgi.namespace.unresolvable.UnresolvableNamespace.UNRESOLVABLE_FILTER;
import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ServiceScope.SINGLETON;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.DialectFactory;
import org.eclipse.daanse.mdx.parser.api.MdxParserProvider;
import org.eclipse.daanse.olap.api.AggregationFactory;
import org.eclipse.daanse.olap.api.ConfigConstants;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.Evaluator;
import org.eclipse.daanse.olap.api.Statement;
import org.eclipse.daanse.olap.api.aggregator.CustomAggregatorFactory;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompiler;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompilerFactory;
import org.eclipse.daanse.olap.api.connection.ConnectionProps;
import org.eclipse.daanse.olap.api.function.FunctionService;
import org.eclipse.daanse.olap.common.ExecuteDurationUtil;
import org.eclipse.daanse.olap.core.LoggingEventBus;
import org.eclipse.daanse.olap.server.ExecutionImpl;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.common.AbstractRolapContext;
import org.eclipse.daanse.rolap.common.RolapCatalogCache;
import org.eclipse.daanse.rolap.common.RolapConnection;
import org.eclipse.daanse.rolap.common.RolapConnectionPropsR;
import org.eclipse.daanse.rolap.common.RolapDependencyTestingEvaluator;
import org.eclipse.daanse.rolap.common.RolapEvaluator;
import org.eclipse.daanse.rolap.common.RolapEvaluatorRoot;
import org.eclipse.daanse.rolap.common.RolapProfilingEvaluator;
import org.eclipse.daanse.rolap.common.RolapResult;
import org.eclipse.daanse.rolap.common.RolapResultShepherd;
import org.eclipse.daanse.rolap.common.agg.AggregationManager;
import org.eclipse.daanse.rolap.common.aggregator.AggregationFactoryImpl;
import org.eclipse.daanse.rolap.core.api.BasicContextOCD;
import org.eclipse.daanse.rolap.mapping.api.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.mapping.api.model.AccessRoleMapping;
import org.eclipse.daanse.rolap.mapping.api.model.CatalogMapping;
import org.eclipse.daanse.sql.guard.api.SqlGuardFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Designate(ocd = BasicContextOCD.class, factory = true)
@Component(service = Context.class, scope = SINGLETON, configurationPid = BASIC_CONTEXT_PID)
public class BasicContext extends AbstractRolapContext implements RolapContext {

    private static final String ERR_MSG_DIALECT_INIT = "Could not activate context. Error on initialisation of Dialect";

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicContext.class);

    @Reference(name = BASIC_CONTEXT_REF_NAME_DATA_SOURCE, target = UNRESOLVABLE_FILTER)
    private DataSource dataSource;

    @Reference(name = BASIC_CONTEXT_REF_NAME_DIALECT_FACTORY, target = UNRESOLVABLE_FILTER)
    private DialectFactory dialectFactory;

    @Reference(name = BASIC_CONTEXT_REF_NAME_CATALOG_MAPPING_SUPPLIER, target = UNRESOLVABLE_FILTER)
    private CatalogMappingSupplier catalogMappingSupplier;

    @Reference(name = BASIC_CONTEXT_REF_NAME_EXPRESSION_COMPILER_FACTORY)
    private ExpressionCompilerFactory expressionCompilerFactory;

    @Reference(name = BASIC_CONTEXT_REF_NAME_MDX_PARSER_PROVIDER)
    private MdxParserProvider mdxParserProvider;

    @Reference(name = BASIC_CONTEXT_REF_NAME_FUNCTION_SERVICE)
    private FunctionService functionService;

    @Reference(cardinality = OPTIONAL)
    private SqlGuardFactory sqlGuardFactory;

    private Dialect dialect = null;

    private AggregationFactory aggregationFactory = null;

    private Semaphore queryLimitSemaphore;

    private List<CustomAggregatorFactory> customAggregators = new ArrayList<CustomAggregatorFactory>();

    @Activate
    public void activate(Map<String, Object> configuration) throws Exception {
        updateConfiguration(configuration);
        activate1();
    }

    @Reference(name = BASIC_CONTEXT_REF_NAME_CUSTOM_AGGREGATOR, cardinality = MULTIPLE, policy = DYNAMIC)
    public void bindCustomAgregators(CustomAggregatorFactory aggregator) {
        customAggregators.add(aggregator);
    }

    public void unbindCustomAgregators(CustomAggregatorFactory aggregator) {
        customAggregators.remove(aggregator);
    }

    public void activate1() throws Exception {

        this.eventBus = new LoggingEventBus();

        schemaCache = new RolapCatalogCache(this);
        queryLimitSemaphore = new Semaphore(
                getConfigValue(ConfigConstants.QUERY_LIMIT, ConfigConstants.QUERY_LIMIT_DEFAULT_VALUE, Integer.class));

        try (Connection connection = dataSource.getConnection()) {
            dialect = dialectFactory.createDialect(connection);
            aggregationFactory = new AggregationFactoryImpl(dialect, this.getCustomAggregators());
        } catch (Exception e) {
            LOGGER.error(ERR_MSG_DIALECT_INIT, e);
        }

        shepherd = new RolapResultShepherd(
                getConfigValue(ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL,
                        ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_DEFAULT_VALUE, Long.class),
                getConfigValue(ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_UNIT,
                        ConfigConstants.ROLAP_CONNECTION_SHEPHERD_THREAD_POLLING_INTERVAL_UNIT_DEFAULT_VALUE,
                        TimeUnit.class),
                getConfigValue(ConfigConstants.ROLAP_CONNECTION_SHEPHERD_NB_THREADS,
                        ConfigConstants.ROLAP_CONNECTION_SHEPHERD_NB_THREADS_DEFAULT_VALUE, Integer.class));
        aggMgr = new AggregationManager(this);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("new MondrianServer: id=" + getId());
        }
    }

    @Deactivate
    public void deactivate(Map<String, Object> configuration) throws Exception {
        shutdown();
        updateConfiguration(null);
    }

    @Override
    public DataSource getDataSource() {
        return dataSource;
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
        return Optional.ofNullable(getCatalogMapping().getDescription());
    }

    @Override
    public CatalogMapping getCatalogMapping() {
        return catalogMappingSupplier.get();
    }

//
//	@Override
//	public QueryProvider getQueryProvider() {
//		return queryProvider;
//	}

    @Override
    public ExpressionCompilerFactory getExpressionCompilerFactory() {
        return expressionCompilerFactory;
    }

    @Override
    public RolapConnection getConnectionWithDefaultRole() {
        return getConnection(new RolapConnectionPropsR());
    }

    @Override
    public RolapConnection getConnection(ConnectionProps props) {
        return new RolapConnection(this, props);
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
    public List<String> getAccessRoles() {
        CatalogMapping catalogMapping = getCatalogMapping();
        if (catalogMapping != null && catalogMapping.getAccessRoles() != null) {
            return catalogMapping.getAccessRoles().stream().map(AccessRoleMapping::getName).toList();
        }
        return List.of();// may take from mapping
    }

    @Override
    public Optional<SqlGuardFactory> getSqlGuardFactory() {
        return Optional.ofNullable(sqlGuardFactory);
    }

    @Override
    public AggregationFactory getAggragationFactory() {
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
        return this.customAggregators;
    };

    @Override
    public ExpressionCompiler createProfilingCompiler(ExpressionCompiler compiler) {
        return new RolapProfilingEvaluator.ProfilingEvaluatorCompiler(compiler);
    }

    /**
     * Creates a compiler which will generate programs which will test whether the dependencies declared
     * via mondrian.calc.Calc#dependsOn(MappingHierarchy) are accurate.
     */
    @Override
    public ExpressionCompiler createDependencyTestingCompiler(ExpressionCompiler compiler) {
        return new RolapDependencyTestingEvaluator.DteCompiler(compiler);
    }

}

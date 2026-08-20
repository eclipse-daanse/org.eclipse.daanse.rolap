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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.eclipse.daanse.jdbc.datasource.pools.api.ConnectionPool;
import org.eclipse.daanse.jdbc.datasource.pools.hikari.api.HikariConnectionPools;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.DialectFactory;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.mdx.parser.ccc.CCCMdxParserProvider;
import org.eclipse.daanse.olap.api.aggregator.CustomAggregatorFactory;
import org.eclipse.daanse.olap.api.monitor.EventBus;
import org.eclipse.daanse.olap.api.monitor.event.Event;
import org.eclipse.daanse.olap.calc.base.compiler.BaseExpressionCompilerFactory;
import org.eclipse.daanse.rolap.core.internal.BasicContext;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

/**
 * Plain-Java {@link BasicContext} that can be wired up without OSGi DS.
 * Construct with a ready-made {@link DataSource} + {@link Dialect} and a
 * {@link CatalogMappingSupplier}; the other collaborators (expression compiler,
 * MDX parser, function service) are instantiated with their standard
 * implementations.
 *
 * <p>
 * Designed to replace legacy.xmla's 800-line TestContextImpl: the resolver
 * registration is delegated to {@link FunctionServices#standard()}.
 */
public class TestContext extends BasicContext {

    /** The pool this context created itself, and therefore has to close. */
    private final ConnectionPool ownedPool;

    /** Additional sinks tapped onto {@link #eventBus}; see {@link #addEventListener}. */
    private final List<Consumer<Event>> eventListeners = new CopyOnWriteArrayList<>();
    private boolean eventTapInstalled;

    public TestContext(DataSource dataSource, Dialect dialect, CatalogMappingSupplier catalogMappingSupplier) {
        this(dataSource, dialect, catalogMappingSupplier, List.of());
    }

    public TestContext(DataSource dataSource, Dialect dialect, CatalogMappingSupplier catalogMappingSupplier,
            List<CustomAggregatorFactory> customAggregators) {
        this(HikariConnectionPools.create(dataSource), true, dialect, catalogMappingSupplier, customAggregators);
    }

    /**
     * Uses a pool the caller already has - for instance
     * {@link org.eclipse.daanse.jdbc.datasource.testkit.api.ActiveDatabase#connectionPool()}.
     * The pool is not closed with this context.
     */
    public TestContext(ConnectionPool connectionPool, Dialect dialect,
            CatalogMappingSupplier catalogMappingSupplier) {
        this(connectionPool, false, dialect, catalogMappingSupplier, List.of());
    }

    private TestContext(ConnectionPool connectionPool, boolean owned, Dialect dialect,
            CatalogMappingSupplier catalogMappingSupplier, List<CustomAggregatorFactory> customAggregators) {
        this.ownedPool = owned ? connectionPool : null;
        setConnectionPool(connectionPool);
        setDialectFactory(new FixedDialectFactory(dialect));
        setCatalogMappingSupplier(catalogMappingSupplier);
        setExpressionCompilerFactory(new BaseExpressionCompilerFactory());
        setMdxParserProvider(new CCCMdxParserProvider());
        setFunctionService(FunctionServices.standard());
        for (CustomAggregatorFactory aggregator : customAggregators) {
            bindCustomAgregators(aggregator);
        }
        try {
            activate(Map.of());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to activate TestContext", e);
        }
    }

    /**
     * Additively taps this context's {@link EventBus}: {@code listener} receives
     * every event alongside whatever the context's own bus already does with it
     * (nothing is replaced or lost). Safe to call more than once - each call adds
     * one more listener, the underlying bus is wrapped at most once.
     *
     * <p>Returns a handle to deregister just this listener - unlike a static,
     * process-wide tap, this stays scoped to the one {@code TestContext} instance
     * and cleans up completely when closed.
     */
    public synchronized AutoCloseable addEventListener(Consumer<Event> listener) {
        if (!eventTapInstalled) {
            EventBus original = getMonitor();
            setEventBusForTap(new EventBus() {
                @Override
                public void accept(Event event) {
                    original.accept(event);
                    for (Consumer<Event> l : eventListeners) {
                        l.accept(event);
                    }
                }
            });
            eventTapInstalled = true;
        }
        eventListeners.add(listener);
        return () -> eventListeners.remove(listener);
    }

    /** {@code eventBus} is protected on {@link org.eclipse.daanse.olap.core.AbstractBasicContext}; only reachable from here. */
    private void setEventBusForTap(EventBus bus) {
        this.eventBus = bus;
    }

    @Override
    public void deactivate(Map<String, Object> configuration) throws Exception {
        try {
            super.deactivate(configuration);
        } finally {
            if (ownedPool != null) {
                ownedPool.close();
            }
        }
    }

    private record FixedDialectFactory(Dialect dialect) implements DialectFactory {
        @Override
        public Dialect createDialect(DialectInitData init) {
            return dialect;
        }
    }
}

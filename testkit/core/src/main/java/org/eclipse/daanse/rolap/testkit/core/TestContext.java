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

import javax.sql.DataSource;

import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.eclipse.daanse.sql.dialect.api.DialectFactory;
import org.eclipse.daanse.sql.dialect.api.DialectInitData;
import org.eclipse.daanse.mdx.parser.ccc.CCCMdxParserProvider;
import org.eclipse.daanse.olap.api.aggregator.CustomAggregatorFactory;
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

    public TestContext(DataSource dataSource, Dialect dialect, CatalogMappingSupplier catalogMappingSupplier) {
        this(dataSource, dialect, catalogMappingSupplier, List.of());
    }

    public TestContext(DataSource dataSource, Dialect dialect, CatalogMappingSupplier catalogMappingSupplier,
            List<CustomAggregatorFactory> customAggregators) {
        setDataSource(dataSource);
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

    private record FixedDialectFactory(Dialect dialect) implements DialectFactory {
        @Override
        public Dialect createDialect(DialectInitData init) {
            return dialect;
        }
    }
}

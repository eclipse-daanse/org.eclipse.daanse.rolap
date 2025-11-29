/*
* Copyright (c) 2025 Contributors to the Eclipse Foundation.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_PID;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_CATALOG_MAPPING_SUPPLIER;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_DATA_SOURCE;
import static org.eclipse.daanse.rolap.core.api.Constants.BASIC_CONTEXT_REF_NAME_DIALECT_FACTORY;
import static org.mockito.Mockito.when;
import static org.osgi.test.common.dictionary.Dictionaries.dictionaryOf;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.sql.DataSource;

import org.eclipse.daanse.jdbc.db.dialect.api.Dialect;
import org.eclipse.daanse.jdbc.db.dialect.api.DialectFactory;
import org.eclipse.daanse.mdx.parser.api.MdxParserProvider;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.calc.compiler.ExpressionCompilerFactory;
import org.eclipse.daanse.olap.api.function.FunctionService;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.core.api.Constants;
import org.eclipse.daanse.rolap.mapping.model.RolapMappingFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.sql.guard.api.SqlGuardFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.test.assertj.servicereference.ServiceReferenceAssert;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.annotation.config.InjectConfiguration;
import org.osgi.test.common.annotation.config.WithFactoryConfiguration;
import org.osgi.test.common.service.ServiceAware;
import org.osgi.test.junit5.cm.ConfigurationExtension;
import org.osgi.test.junit5.context.BundleContextExtension;

import aQute.bnd.annotation.spi.ServiceProvider;

@ExtendWith(BundleContextExtension.class)
@ExtendWith(ConfigurationExtension.class)
@ExtendWith(MockitoExtension.class)

@ServiceProvider(value = javax.sql.DataSource.class)
@ServiceProvider(value = DialectFactory.class)
@ServiceProvider(value = MdxParserProvider.class)
@ServiceProvider(value = CatalogMappingSupplier.class)

class BasicContextServiceTest {
    private static final String TARGET_EXT = ".target";
    @InjectBundleContext
    BundleContext bc;

    @Mock
    Dialect dialect;

    @Mock
    DialectFactory dialectFactory;

    @Mock
    DataSource dataSource;

    @Mock
    Connection connection;

    @Mock
    CatalogMappingSupplier catalogMappingSupplier;

    @Mock
    ExpressionCompilerFactory expressionCompilerFactory;

    @Mock
    MdxParserProvider mdxParserProvider;

    org.eclipse.daanse.rolap.mapping.model.Catalog catalogMapping=RolapMappingFactory.eINSTANCE.createCatalog();

    @Mock
    FunctionService functionService;

    @Mock
    SqlGuardFactory sqlGuardFactory;

    @BeforeEach void setup() throws SQLException {

    }

    @Test void serviceExists(
        @InjectConfiguration(withFactoryConfig = @WithFactoryConfiguration(factoryPid = BASIC_CONTEXT_PID, name = "name1")) Configuration c,
        @InjectService(cardinality = 0) ServiceAware<Context> saContext) throws Exception {

        when(dataSource.getConnection()).thenReturn(connection);
        when(dialectFactory.createDialect(connection)).thenReturn(dialect);
        when(catalogMappingSupplier.get()).thenReturn(catalogMapping);
        catalogMapping.setName("schemaName");

        assertThat(saContext).isNotNull().extracting(ServiceAware::size).isEqualTo(0);

        ServiceReferenceAssert.assertThat(saContext.getServiceReference()).isNull();

        bc.registerService(DataSource.class, dataSource, dictionaryOf("ds", "1"));
        bc.registerService(DialectFactory.class, dialectFactory, dictionaryOf("df", "1"));
        bc.registerService(CatalogMappingSupplier.class, catalogMappingSupplier, dictionaryOf("cms", "1"));

        bc.registerService(ExpressionCompilerFactory.class, expressionCompilerFactory, dictionaryOf("ecf", "1"));
        bc.registerService(MdxParserProvider.class, mdxParserProvider, dictionaryOf("parser", "1"));
        bc.registerService(FunctionService.class, functionService, dictionaryOf("fs", "1"));
        bc.registerService(SqlGuardFactory.class, sqlGuardFactory, dictionaryOf("sg", "1"));

        Dictionary<String, Object> props = new Hashtable<>();

        props.put(BASIC_CONTEXT_REF_NAME_DATA_SOURCE + TARGET_EXT, "(ds=1)");
        props.put(BASIC_CONTEXT_REF_NAME_DIALECT_FACTORY + TARGET_EXT, "(df=1)");
        props.put(BASIC_CONTEXT_REF_NAME_CATALOG_MAPPING_SUPPLIER + TARGET_EXT, "(cms=1)");
        props.put(Constants.BASIC_CONTEXT_REF_NAME_EXPRESSION_COMPILER_FACTORY + TARGET_EXT, "(ecf=1)");

        c.update(props);
        Context<?> ctx = saContext.waitForService(1000);

        assertThat(saContext).isNotNull().extracting(ServiceAware::size).isEqualTo(1);
        assertThat(ctx.getConnectionWithDefaultRole()).isNotNull();
        assertThat(ctx).satisfies(x -> {
            assertThat(x.getDataSource()).isEqualTo(dataSource);
            assertThat(x.getDialect()).isEqualTo(dialect);
            assertThat(x.getExpressionCompilerFactory()).isEqualTo(expressionCompilerFactory);
            assertThat(((RolapContext) x).getCatalogMapping()).isEqualTo(catalogMapping);
        });

    }

}

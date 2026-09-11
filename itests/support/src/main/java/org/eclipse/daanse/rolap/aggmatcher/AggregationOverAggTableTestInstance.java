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
package org.eclipse.daanse.rolap.aggmatcher;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.testkit.api.DatabaseSupplier;
import org.eclipse.daanse.olap.check.runtime.api.OlapCheckSuiteSupplier;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Self-contained fixture for {@link AggregationOverAggTableTest}: the same
 * FoodMart-based "ExtraCol" cube as {@link ExplicitRecognizerTestInstances}
 * builds via {@link ExplicitRecognizerTestModifierEmf}, but with no explicit
 * {@code AggName} -- the {@code agg_c_avg_sales_fact_1997} table is picked up
 * purely by the DefaultRecognizer's naming convention.
 */
public class AggregationOverAggTableTestInstance implements CatalogTestInstance {

    @Override
    public String name() {
        return "mondrian.AggregationOverAggTableTest";
    }

    @Override
    public CatalogMappingSupplier mappingSupplier() {
        Catalog catalogMapping = new CatalogSupplier().get();
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) catalogMapping);
        Catalog catalog = (Catalog) copier.get(catalogMapping);

        class Modifier extends ExplicitRecognizerTestModifierEmf {
            Modifier(Catalog catalog, EcoreUtil.Copier copier) {
                super(catalog, copier);
            }

            @Override
            protected Column getYearCol() {
                return (Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY);
            }

            @Override
            protected Column getQuarterCol() {
                return (Column) copier.get(CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY);
            }

            @Override
            protected Column getMonthCol() {
                return (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY);
            }

            @Override
            protected String getDefaultMeasure() {
                return "Unit Sales";
            }

            @Override
            protected List<Table> getDatabaseSchemaTables() {
                return List.of(aggCAvgSalesFact1997Table());
            }
        }
        return new Modifier(catalog, copier);
    }

    @Override
    public OlapCheckSuiteSupplier checkSuiteSupplier() {
        return null;
    }

    @Override
    public Map<String, URL> csvResources() {
        Map<String, URL> m = new LinkedHashMap<>(new FoodmartTestInstance().dataSupplier().csvResources());
        m.put("agg_c_avg_sales_fact_1997",
                getClass().getResource("aggregationoveraggtabletest/data/agg_c_avg_sales_fact_1997.csv"));
        return m;
    }

    @Override
    public DatabaseSupplier databaseSupplier() {
        return AggregationOverAggTableTestInstance::databaseSchema;
    }

    /**
     * FoodMart's own CWM Schema plus a fresh (not shared with {@link #mappingSupplier()}'s
     * -- EMF containment references only allow one parent at a time) DDL
     * definition for {@code agg_c_avg_sales_fact_1997}.
     */
    private static Schema databaseSchema() {
        Schema schema = Packages.available(new CatalogSupplier().get(), Schema.class).get(0);
        schema.getOwnedElement().add(aggCAvgSalesFact1997Table());
        return schema;
    }

    //## TableName: agg_c_avg_sales_fact_1997
    //## ColumnNames: the_year,quarter,month_of_year,gender,unit_sales,fact_count
    //## ColumnTypes: INTEGER,VARCHAR(30),INTEGER,VARCHAR(30),INTEGER:NULL,INTEGER
    private static Table aggCAvgSalesFact1997Table() {
        Table table = RelationalFactory.eINSTANCE.createTable();
        table.setName("agg_c_avg_sales_fact_1997");
        table.getFeature().add(createColumn("the_year", SQLSimpleTypes.Sql99.integerType()));
        table.getFeature().add(createColumn("quarter", SQLSimpleTypes.varcharType(255)));
        table.getFeature().add(createColumn("month_of_year", SQLSimpleTypes.Sql99.integerType()));
        table.getFeature().add(createColumn("gender", SQLSimpleTypes.varcharType(255)));
        Column unitSales = createColumn("unit_sales", SQLSimpleTypes.Sql99.integerType());
        unitSales.setIsNullable(NullableType.COLUMN_NULLABLE);
        table.getFeature().add(unitSales);
        table.getFeature().add(createColumn("fact_count", SQLSimpleTypes.Sql99.integerType()));
        return table;
    }

    private static Column createColumn(String name, SQLSimpleType dataType) {
        Column column = RelationalFactory.eINSTANCE.createColumn();
        column.setName(name);
        column.setType(dataType);
        return column;
    }
}

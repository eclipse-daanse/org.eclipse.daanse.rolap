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
 *   SmartCity Jena, Stefan Bischof - initial
 *
 */
package org.eclipse.daanse.rolap.agg;


import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationColumnName;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationExclude;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationFactory;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationLevel;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationMeasure;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.ExplicitAggregationTable;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.MeasureGroup;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.CountMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MeasureFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.SumMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.TimeDimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.ExplicitHierarchy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.HierarchyFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelDefinition;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;
/**
 * EMF version of TestDistinctCountAggMeasureModifier from AggregationOnDistinctCountMeasuresTest.
 * Creates a complete new catalog with Sales cube, Time dimension, and aggregation table agg_c_10_sales_fact_1997.
 *
 * <Dimension name="Time" type="TimeDimension">
 *   <Hierarchy hasAll="false" primaryKey="time_id">
 *     <Table name="time_by_day"/>
 *     <Level name="Year" column="the_year" type="Numeric" uniqueMembers="true" levelType="TimeYears"/>
 *     <Level name="Quarter" column="quarter" uniqueMembers="false" levelType="TimeQuarters"/>
 *     <Level name="Month" column="month_of_year" type="Numeric" uniqueMembers="false" levelType="TimeMonths"/>
 *   </Hierarchy>
 * </Dimension>
 *
 * <Cube name="Sales">
 *   <Table name="sales_fact_1997">
 *     <AggExclude name="agg_c_special_sales_fact_1997"/>
 *     ... (multiple excludes)
 *     <AggName name="agg_c_10_sales_fact_1997">
 *       <AggFactCount column="fact_count"/>
 *       <AggMeasure name="[Measures].[Store Sales]" column="store_sales"/>
 *       <AggMeasure name="[Measures].[Store Cost]" column="store_cost"/>
 *       <AggMeasure name="[Measures].[Unit Sales]" column="unit_sales"/>
 *       <AggMeasure name="[Measures].[Customer Count]" column="customer_count"/>
 *       <AggLevel name="[Time].[Time].[Year]" column="the_year"/>
 *       <AggLevel name="[Time].[Time].[Quarter]" column="quarter"/>
 *       <AggLevel name="[Time].[Time].[Month]" column="month_of_year"/>
 *     </AggName>
 *   </Table>
 *   <Dimension name="Time" foreignKey="time_id" source="Time"/>
 *   <Measure name="Unit Sales" column="unit_sales" aggregator="sum" formatString="Standard"/>
 *   <Measure name="Store Cost" column="store_cost" aggregator="sum" formatString="#,###.00"/>
 *   <Measure name="Store Sales" column="store_sales" aggregator="sum" formatString="#,###.00"/>
 *   <Measure name="Customer Count" column="customer_id" aggregator="distinct-count" formatString="#,###"/>
 * </Cube>
 */
public class TestDistinctCountAggMeasureModifier implements CatalogMappingSupplier {

    private final Catalog catalog;


    public TestDistinctCountAggMeasureModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) baseCatalog);
        this.catalog = (Catalog) copier.get(baseCatalog);

        // Static Time dimension components
        Level LEVEL_YEAR;
        Level LEVEL_QUARTER;
        Level LEVEL_MONTH;
        ExplicitHierarchy HIERARCHY_TIME;
        TableSource TABLE_QUERY_TIME_BY_DAY;
        TimeDimension DIMENSION_TIME;

        // Static measures for Sales cube
        SumMeasure MEASURE_UNIT_SALES;
        SumMeasure MEASURE_STORE_COST;
        SumMeasure MEASURE_STORE_SALES;
        CountMeasure MEASURE_CUSTOMER_COUNT;

        // Static aggregation table components
        AggregationColumnName AGG_FACT_COUNT;
        AggregationMeasure AGG_MEASURE_STORE_SALES;
        AggregationMeasure AGG_MEASURE_STORE_COST;
        AggregationMeasure AGG_MEASURE_UNIT_SALES;
        AggregationMeasure AGG_MEASURE_CUSTOMER_COUNT;
        AggregationLevel AGG_LEVEL_YEAR;
        AggregationLevel AGG_LEVEL_QUARTER;
        AggregationLevel AGG_LEVEL_MONTH;
        ExplicitAggregationTable AGGREGATION_TABLE;

        // Static aggregation excludes
        AggregationExclude AGG_EXCLUDE_1;
        AggregationExclude AGG_EXCLUDE_2;
        AggregationExclude AGG_EXCLUDE_3;
        AggregationExclude AGG_EXCLUDE_4;
        AggregationExclude AGG_EXCLUDE_5;
        AggregationExclude AGG_EXCLUDE_6;
        AggregationExclude AGG_EXCLUDE_7;
        AggregationExclude AGG_EXCLUDE_8;
        AggregationExclude AGG_EXCLUDE_9;
        AggregationExclude AGG_EXCLUDE_10;

        // Static fact table query
        TableSource TABLE_QUERY_SALES_FACT;

        // Static dimension connector
        DimensionConnector CONNECTOR_TIME;

        // Static measure group
        MeasureGroup MEASURE_GROUP;

        // Static cube
        PhysicalCube CUBE_SALES;

        // Create Time dimension levels
        LEVEL_YEAR = LevelFactory.eINSTANCE.createLevel();
        LEVEL_YEAR.setName("Year");
        LEVEL_YEAR.setColumn((Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY));
        LEVEL_YEAR.setColumnType(ColumnInternalDataType.NUMERIC);
        LEVEL_YEAR.setUniqueMembers(true);
        LEVEL_YEAR.setType(LevelDefinition.TIME_YEARS);

        LEVEL_QUARTER = LevelFactory.eINSTANCE.createLevel();
        LEVEL_QUARTER.setName("Quarter");
        LEVEL_QUARTER.setColumn((Column) copier.get(CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY));
        LEVEL_QUARTER.setUniqueMembers(false);
        LEVEL_QUARTER.setType(LevelDefinition.TIME_QUARTERS);

        LEVEL_MONTH = LevelFactory.eINSTANCE.createLevel();
        LEVEL_MONTH.setName("Month");
        LEVEL_MONTH.setColumn((Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY));
        LEVEL_MONTH.setColumnType(ColumnInternalDataType.NUMERIC);
        LEVEL_MONTH.setUniqueMembers(false);
        LEVEL_MONTH.setType(LevelDefinition.TIME_MONTHS);

        // Create Time hierarchy
        TABLE_QUERY_TIME_BY_DAY = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_TIME_BY_DAY.setTable((Table) copier.get(CatalogSupplier.TABLE_TIME_BY_DAY));

        HIERARCHY_TIME = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_TIME.setHasAll(false);
        HIERARCHY_TIME.setPrimaryKey((Column) copier.get(CatalogSupplier.COLUMN_TIME_ID_TIME_BY_DAY));
        HIERARCHY_TIME.setSource(TABLE_QUERY_TIME_BY_DAY);
        HIERARCHY_TIME.getLevels().addAll(List.of(LEVEL_YEAR, LEVEL_QUARTER, LEVEL_MONTH));

        // Create Time dimension
        DIMENSION_TIME = DimensionFactory.eINSTANCE.createTimeDimension();
        DIMENSION_TIME.setName("Time");
        DIMENSION_TIME.getHierarchies().add(HIERARCHY_TIME);

        // Create measures
        MEASURE_UNIT_SALES = MeasureFactory.eINSTANCE.createSumMeasure();
        MEASURE_UNIT_SALES.setName("Unit Sales");
        MEASURE_UNIT_SALES.setColumn((Column) copier.get(CatalogSupplier.COLUMN_UNIT_SALES_SALESFACT));
        MEASURE_UNIT_SALES.setFormatString("Standard");

        MEASURE_STORE_COST = MeasureFactory.eINSTANCE.createSumMeasure();
        MEASURE_STORE_COST.setName("Store Cost");
        MEASURE_STORE_COST.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_COST_SALESFACT));
        MEASURE_STORE_COST.setFormatString("#,###.00");

        MEASURE_STORE_SALES = MeasureFactory.eINSTANCE.createSumMeasure();
        MEASURE_STORE_SALES.setName("Store Sales");
        MEASURE_STORE_SALES.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_SALES_SALESFACT));
        MEASURE_STORE_SALES.setFormatString("#,###.00");

        MEASURE_CUSTOMER_COUNT = MeasureFactory.eINSTANCE.createCountMeasure();
        MEASURE_CUSTOMER_COUNT.setName("Customer Count");
        MEASURE_CUSTOMER_COUNT.setColumn((Column) copier.get(CatalogSupplier.COLUMN_CUSTOMER_ID_SALESFACT));
        MEASURE_CUSTOMER_COUNT.setDistinct(true);
        MEASURE_CUSTOMER_COUNT.setFormatString("#,###");

        // Create aggregation table components
        AGG_FACT_COUNT = AggregationFactory.eINSTANCE.createAggregationColumnName();
        AGG_FACT_COUNT.setColumn((Column) copier.get(CatalogSupplier.COLUMN_FACT_COUNT_AGG_C_10_SALES_FACT_1997));

        AGG_MEASURE_STORE_SALES = AggregationFactory.eINSTANCE.createAggregationMeasure();
        AGG_MEASURE_STORE_SALES.setName("[Measures].[Store Sales]");
        AGG_MEASURE_STORE_SALES.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_SALES_AGG_C_10_SALES_FACT_1997));

        AGG_MEASURE_STORE_COST = AggregationFactory.eINSTANCE.createAggregationMeasure();
        AGG_MEASURE_STORE_COST.setName("[Measures].[Store Cost]");
        AGG_MEASURE_STORE_COST.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_COST_AGG_C_10_SALES_FACT_1997));

        AGG_MEASURE_UNIT_SALES = AggregationFactory.eINSTANCE.createAggregationMeasure();
        AGG_MEASURE_UNIT_SALES.setName("[Measures].[Unit Sales]");
        AGG_MEASURE_UNIT_SALES.setColumn((Column) copier.get(CatalogSupplier.COLUMN_UNIT_SALES_AGG_C_10_SALES_FACT_1997));

        AGG_MEASURE_CUSTOMER_COUNT = AggregationFactory.eINSTANCE.createAggregationMeasure();
        AGG_MEASURE_CUSTOMER_COUNT.setName("[Measures].[Customer Count]");
        AGG_MEASURE_CUSTOMER_COUNT.setColumn((Column) copier.get(CatalogSupplier.COLUMN_CUSTOMER_COUNT_AGG_C_10_SALES_FACT_1997));

        AGG_LEVEL_YEAR = AggregationFactory.eINSTANCE.createAggregationLevel();
        AGG_LEVEL_YEAR.setName("[Time].[Time].[Year]");
        AGG_LEVEL_YEAR.setColumn((Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_AGG_C_10_SALES_FACT_1997));

        AGG_LEVEL_QUARTER = AggregationFactory.eINSTANCE.createAggregationLevel();
        AGG_LEVEL_QUARTER.setName("[Time].[Time].[Quarter]");
        AGG_LEVEL_QUARTER.setColumn((Column) copier.get(CatalogSupplier.COLUMN_QUARTER_AGG_C_10_SALES_FACT_1997));

        AGG_LEVEL_MONTH = AggregationFactory.eINSTANCE.createAggregationLevel();
        AGG_LEVEL_MONTH.setName("[Time].[Time].[Month]");
        AGG_LEVEL_MONTH.setColumn((Column) copier.get(CatalogSupplier.COLUMN_MONTH_YEAR_AGG_C_10_SALES_FACT_1997));

        // Create aggregation table
        AGGREGATION_TABLE = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
        AGGREGATION_TABLE.setTable((Table) copier.get(CatalogSupplier.TABLE_AGG_C_10_SALES_FACT_1997));
        AGGREGATION_TABLE.setAggregationFactCount(AGG_FACT_COUNT);
        AGGREGATION_TABLE.getAggregationMeasures().addAll(List.of(
            AGG_MEASURE_STORE_SALES,
            AGG_MEASURE_STORE_COST,
            AGG_MEASURE_UNIT_SALES,
            AGG_MEASURE_CUSTOMER_COUNT
        ));
        AGGREGATION_TABLE.getAggregationLevels().addAll(List.of(
            AGG_LEVEL_YEAR,
            AGG_LEVEL_QUARTER,
            AGG_LEVEL_MONTH
        ));

        // Create aggregation excludes
        AGG_EXCLUDE_1 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_1.setName("agg_c_special_sales_fact_1997");

        AGG_EXCLUDE_2 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_2.setName("agg_g_ms_pcat_sales_fact_1997");

        AGG_EXCLUDE_3 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_3.setName("agg_c_14_sales_fact_1997");

        AGG_EXCLUDE_4 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_4.setName("agg_l_05_sales_fact_1997");

        AGG_EXCLUDE_5 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_5.setName("agg_lc_06_sales_fact_1997");

        AGG_EXCLUDE_6 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_6.setName("agg_l_04_sales_fact_1997");

        AGG_EXCLUDE_7 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_7.setName("agg_ll_01_sales_fact_1997");

        AGG_EXCLUDE_8 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_8.setName("agg_lc_100_sales_fact_1997");

        AGG_EXCLUDE_9 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_9.setName("agg_l_03_sales_fact_1997");

        AGG_EXCLUDE_10 = AggregationFactory.eINSTANCE.createAggregationExclude();
        AGG_EXCLUDE_10.setName("agg_pl_01_sales_fact_1997");

        // Create fact table query with aggregation table and excludes
        TABLE_QUERY_SALES_FACT = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_SALES_FACT.setTable((Table) copier.get(CatalogSupplier.TABLE_SALES_FACT));
        TABLE_QUERY_SALES_FACT.getAggregationExcludes().addAll(List.of(
            AGG_EXCLUDE_1, AGG_EXCLUDE_2, AGG_EXCLUDE_3, AGG_EXCLUDE_4, AGG_EXCLUDE_5,
            AGG_EXCLUDE_6, AGG_EXCLUDE_7, AGG_EXCLUDE_8, AGG_EXCLUDE_9, AGG_EXCLUDE_10
        ));
        TABLE_QUERY_SALES_FACT.getAggregationTables().add(AGGREGATION_TABLE);

        // Create dimension connector
        CONNECTOR_TIME = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_TIME.setOverrideDimensionName("Time");
        CONNECTOR_TIME.setDimension(DIMENSION_TIME);
        CONNECTOR_TIME.setForeignKey((Column) copier.get(CatalogSupplier.COLUMN_TIME_ID_SALESFACT));

        // Create measure group
        MEASURE_GROUP = CubeFactory.eINSTANCE.createMeasureGroup();
        MEASURE_GROUP.getMeasures().addAll(List.of(
            MEASURE_UNIT_SALES,
            MEASURE_STORE_COST,
            MEASURE_STORE_SALES,
            MEASURE_CUSTOMER_COUNT
        ));

        // Create Sales cube
        CUBE_SALES = CubeFactory.eINSTANCE.createPhysicalCube();
        CUBE_SALES.setName("Sales");
        CUBE_SALES.setDefaultMeasure(MEASURE_UNIT_SALES);
        CUBE_SALES.setSource(TABLE_QUERY_SALES_FACT);
        CUBE_SALES.getDimensionConnectors().add(CONNECTOR_TIME);
        CUBE_SALES.getMeasureGroups().add(MEASURE_GROUP);

        // Replace all cubes with just the Sales cube
        this.catalog.getOwnedElement().removeIf(org.eclipse.daanse.rolap.mapping.model.olap.cube.Cube.class::isInstance);
        this.catalog.getImportedElement().removeIf(org.eclipse.daanse.rolap.mapping.model.olap.cube.Cube.class::isInstance);
        this.catalog.getOwnedElement().removeIf(org.eclipse.daanse.rolap.mapping.model.access.common.AccessRole.class::isInstance);
        this.catalog.getImportedElement().removeIf(org.eclipse.daanse.rolap.mapping.model.access.common.AccessRole.class::isInstance);
        this.catalog.getImportedElement().add(CUBE_SALES);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

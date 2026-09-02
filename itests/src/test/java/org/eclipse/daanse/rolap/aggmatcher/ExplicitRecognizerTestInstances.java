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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.testkit.api.DatabaseSupplier;
import org.eclipse.daanse.olap.check.runtime.api.OlapCheckSuiteSupplier;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.api.CatalogTestInstance;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationColumnName;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationExclude;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationFactory;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationForeignKey;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationLevel;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationLevelProperty;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationMeasure;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationTable;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.ExplicitAggregationTable;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.MemberProperty;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * One {@link CatalogTestInstance} per test method in {@link ExplicitRecognizerTest}
 * -- each builds the FoodMart catalog (via {@link CatalogSupplier}) plus an
 * "ExtraCol" cube with a "TimeExtra" dimension and a specific explicit
 * aggregate-table configuration, exactly as the pre-migration test built it
 * inline per method via {@code setupMultiColDimCube}.
 *
 * <p>{@link #expAggTest} / {@link #expAggTestDistinctCount} are the two
 * synthetic aggregate tables shared by all scenarios (backed by the
 * {@code exp_agg_test} / {@code exp_agg_test_distinct_count} CSV fixtures);
 * {@link #csvResources()} combines those with the full FoodMart CSV set.
 */
final class ExplicitRecognizerTestInstances {

    private ExplicitRecognizerTestInstances() {
    }

    //## TableName: exp_agg_test
    //## ColumnNames:  testyear,testqtr,testmonthord,testmonthname,testmonthcap,testmonprop1,testmonprop2,gender,test_unit_sales,test_store_cost,fact_count
    //## ColumnTypes: INTEGER,VARCHAR(30),INTEGER,VARCHAR(30),VARCHAR(30),VARCHAR(30),VARCHAR(30),VARCHAR(30),INTEGER,DECIMAL(10,4),INTEGER
    private static Column testyearExpAggTest = createColumn("testyear", SQLSimpleTypes.Sql99.integerType(), null, null, null);
    private static Column testqtrExpAggTest = createColumn("testqtr", SQLSimpleTypes.varcharType(255), 30, null, null);
    private static Column testmonthordExpAggTest = createColumn("testmonthord", SQLSimpleTypes.Sql99.integerType(), null, null, null);
    private static Column testmonthnameExpAggTest = createColumn("testmonthname", SQLSimpleTypes.varcharType(255), 30, null, null);
    private static Column testmonthcapExpAggTest = createColumn("testmonthcap", SQLSimpleTypes.varcharType(255), 30, null, null);
    private static Column testmonprop1ExpAggTest = createColumn("testmonprop1", SQLSimpleTypes.varcharType(255), 30, null, null);
    private static Column testmonprop2ExpAggTest = createColumn("testmonprop2", SQLSimpleTypes.varcharType(255), 30, null, null);
    private static Column genderExpAggTest = createColumn("gender", SQLSimpleTypes.varcharType(255), 30, null, null);
    private static Column testUnitSalesExpAggTest = createColumn("test_unit_sales", SQLSimpleTypes.Sql99.integerType(), null, null, null);
    private static Column testStoreCostExpAggTest = createColumn("test_store_cost", SQLSimpleTypes.decimalType(18, 4), null, 10, 4);
    private static Column factCountExpAggTest = createColumn("fact_count", SQLSimpleTypes.Sql99.integerType(), null, null, null);

    private static Table expAggTest = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
    static {
        expAggTest.setName("exp_agg_test");
        expAggTest.getFeature().add(testyearExpAggTest);
        expAggTest.getFeature().add(testqtrExpAggTest);
        expAggTest.getFeature().add(testmonthordExpAggTest);
        expAggTest.getFeature().add(testmonthnameExpAggTest);
        expAggTest.getFeature().add(testmonthcapExpAggTest);
        expAggTest.getFeature().add(testmonprop1ExpAggTest);
        expAggTest.getFeature().add(testmonprop2ExpAggTest);
        expAggTest.getFeature().add(genderExpAggTest);
        expAggTest.getFeature().add(testUnitSalesExpAggTest);
        expAggTest.getFeature().add(testStoreCostExpAggTest);
        expAggTest.getFeature().add(factCountExpAggTest);
    }

    //## TableName:  exp_agg_test_distinct_count
    //## ColumnNames:  fact_count,testyear,gender,store_name,store_country,store_st,store_cty,store_add,unit_s,cust_cnt
    //## ColumnTypes: INTEGER,INTEGER,VARCHAR(30),VARCHAR(30),VARCHAR(30),VARCHAR(30),VARCHAR(30),VARCHAR(30),INTEGER,INTEGER
    private static Column factCountExpAggTestDistinctCount = createColumn("fact_count", SQLSimpleTypes.Sql99.integerType(), null, null, null);
    private static Column testyearExpAggTestDistinctCount = createColumn("testyear", SQLSimpleTypes.Sql99.integerType(), null, null, null);
    private static Column genderExpAggTestDistinctCount = createColumn("gender", SQLSimpleTypes.varcharType(255), 30, null, null);
    private static Column storeNameExpAggTestDistinctCount = createColumn("store_name", SQLSimpleTypes.varcharType(255), 30, null, null);
    private static Column storeCountryExpAggTestDistinctCount = createColumn("store_country", SQLSimpleTypes.varcharType(255), 30, null, null);
    private static Column storeStExpAggTestDistinctCount = createColumn("store_st", SQLSimpleTypes.varcharType(255), 30, null, null);
    private static Column storeCtyExpAggTestDistinctCount = createColumn("store_cty", SQLSimpleTypes.varcharType(255), 30, null, null);
    private static Column storeAddExpAggTestDistinctCount = createColumn("store_add", SQLSimpleTypes.varcharType(255), 30, null, null);
    private static Column unitSExpAggTestDistinctCount = createColumn("unit_s", SQLSimpleTypes.Sql99.integerType(), null, null, null);
    private static Column custCntExpAggTestDistinctCount = createColumn("cust_cnt", SQLSimpleTypes.Sql99.integerType(), null, null, null);

    private static Table expAggTestDistinctCount = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
    static {
        expAggTestDistinctCount.setName("exp_agg_test_distinct_count");
        expAggTestDistinctCount.getFeature().add(factCountExpAggTestDistinctCount);
        expAggTestDistinctCount.getFeature().add(testyearExpAggTestDistinctCount);
        expAggTestDistinctCount.getFeature().add(genderExpAggTestDistinctCount);
        expAggTestDistinctCount.getFeature().add(storeNameExpAggTestDistinctCount);
        expAggTestDistinctCount.getFeature().add(storeCountryExpAggTestDistinctCount);
        expAggTestDistinctCount.getFeature().add(storeStExpAggTestDistinctCount);
        expAggTestDistinctCount.getFeature().add(storeCtyExpAggTestDistinctCount);
        expAggTestDistinctCount.getFeature().add(storeAddExpAggTestDistinctCount);
        expAggTestDistinctCount.getFeature().add(unitSExpAggTestDistinctCount);
        expAggTestDistinctCount.getFeature().add(custCntExpAggTestDistinctCount);
    }

    private static Column createColumn(String name, org.eclipse.daanse.cwm.model.cwm.resource.relational.SQLSimpleType dataType, Integer charOctetLength, Integer columnSize, Integer decimalDigits) {
        Column column = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        column.setName(name);
        column.setType(dataType);
        return column;
    }

    private static Map<String, URL> csvResources() {
        // Header-only, matching FoodMart's own CSVs: with a DatabaseSupplier in
        // play, column types come from the CWM schema below, not a type row.
        Map<String, URL> m = new LinkedHashMap<>(new FoodmartTestInstance().dataSupplier().csvResources());
        m.put("exp_agg_test", ExplicitRecognizerTestInstances.class.getResource("explicitrecognizertest/data/exp_agg_test.csv"));
        m.put("exp_agg_test_distinct_count", ExplicitRecognizerTestInstances.class.getResource("explicitrecognizertest/data/exp_agg_test_distinct_count.csv"));
        return m;
    }

    /**
     * FoodMart's own CWM Schema plus fresh (not the shared {@link #expAggTest} /
     * {@link #expAggTestDistinctCount} statics -- those are separately attached
     * to each test's own mapping catalog, and EMF containment references only
     * allow one parent at a time) DDL definitions for the two synthetic
     * aggregate tables, so {@code DataLayer} can create and load them alongside
     * the FoodMart tables in one pass.
     */
    private static Schema databaseSchema() {
        Schema schema = org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages
                .available(new CatalogSupplier().get(), Schema.class).get(0);
        schema.getOwnedElement().add(freshExpAggTestTable());
        schema.getOwnedElement().add(freshExpAggTestDistinctCountTable());
        return schema;
    }

    private static Table freshExpAggTestTable() {
        Table table = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        table.setName("exp_agg_test");
        table.getFeature().add(createColumn("testyear", SQLSimpleTypes.Sql99.integerType(), null, null, null));
        table.getFeature().add(createColumn("testqtr", SQLSimpleTypes.varcharType(255), 30, null, null));
        table.getFeature().add(createColumn("testmonthord", SQLSimpleTypes.Sql99.integerType(), null, null, null));
        table.getFeature().add(createColumn("testmonthname", SQLSimpleTypes.varcharType(255), 30, null, null));
        table.getFeature().add(createColumn("testmonthcap", SQLSimpleTypes.varcharType(255), 30, null, null));
        table.getFeature().add(createColumn("testmonprop1", SQLSimpleTypes.varcharType(255), 30, null, null));
        table.getFeature().add(createColumn("testmonprop2", SQLSimpleTypes.varcharType(255), 30, null, null));
        table.getFeature().add(createColumn("gender", SQLSimpleTypes.varcharType(255), 30, null, null));
        table.getFeature().add(createColumn("test_unit_sales", SQLSimpleTypes.Sql99.integerType(), null, null, null));
        table.getFeature().add(createColumn("test_store_cost", SQLSimpleTypes.decimalType(18, 4), null, 10, 4));
        table.getFeature().add(createColumn("fact_count", SQLSimpleTypes.Sql99.integerType(), null, null, null));
        return table;
    }

    private static Table freshExpAggTestDistinctCountTable() {
        Table table = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        table.setName("exp_agg_test_distinct_count");
        table.getFeature().add(createColumn("fact_count", SQLSimpleTypes.Sql99.integerType(), null, null, null));
        table.getFeature().add(createColumn("testyear", SQLSimpleTypes.Sql99.integerType(), null, null, null));
        table.getFeature().add(createColumn("gender", SQLSimpleTypes.varcharType(255), 30, null, null));
        table.getFeature().add(createColumn("store_name", SQLSimpleTypes.varcharType(255), 30, null, null));
        table.getFeature().add(createColumn("store_country", SQLSimpleTypes.varcharType(255), 30, null, null));
        table.getFeature().add(createColumn("store_st", SQLSimpleTypes.varcharType(255), 30, null, null));
        table.getFeature().add(createColumn("store_cty", SQLSimpleTypes.varcharType(255), 30, null, null));
        table.getFeature().add(createColumn("store_add", SQLSimpleTypes.varcharType(255), 30, null, null));
        table.getFeature().add(createColumn("unit_s", SQLSimpleTypes.Sql99.integerType(), null, null, null));
        table.getFeature().add(createColumn("cust_cnt", SQLSimpleTypes.Sql99.integerType(), null, null, null));
        return table;
    }

    private abstract static class Base implements CatalogTestInstance {
        @Override
        public OlapCheckSuiteSupplier checkSuiteSupplier() {
            return null;
        }

        @Override
        public Map<String, URL> csvResources() {
            return ExplicitRecognizerTestInstances.csvResources();
        }

        @Override
        public DatabaseSupplier databaseSupplier() {
            return ExplicitRecognizerTestInstances::databaseSchema;
        }
    }

    /** Mirrors the old {@code setupMultiColDimCube}: wraps the given catalog/copier in an {@link ExplicitRecognizerTestModifierEmf}. */
    private static CatalogMappingSupplier buildModifier(Catalog catalog, EcoreUtil.Copier copier,
            List<AggregationTable> aggTables, Column yearCol, Column qtrCol, Column monthCol,
            Column monthCaptionCol, Column monthOrdinalCol, Column monthNameCol,
            List<MemberProperty> monthProp, List<Table> tables, String defaultMeasure) {
        class Modifier extends ExplicitRecognizerTestModifierEmf {
            Modifier(Catalog catalog, EcoreUtil.Copier copier) {
                super(catalog, copier);
            }

            @Override
            protected List<MemberProperty> getMonthProp() {
                return monthProp;
            }

            @Override
            protected Column getMonthOrdinalCol() {
                return monthOrdinalCol;
            }

            @Override
            protected Column getMonthNameCol() {
                return monthNameCol;
            }

            @Override
            protected Column getMonthCaptionCol() {
                return monthCaptionCol;
            }

            @Override
            protected List<AggregationTable> getAggTables() {
                return aggTables;
            }

            @Override
            protected List<AggregationExclude> getAggExcludes() {
                return List.of();
            }

            @Override
            protected String getDefaultMeasure() {
                return defaultMeasure;
            }

            @Override
            protected Column getQuarterCol() {
                return qtrCol;
            }

            @Override
            protected Column getMonthCol() {
                return monthCol;
            }

            @Override
            protected Column getYearCol() {
                return yearCol;
            }

            @Override
            protected List<Table> getDatabaseSchemaTables() {
                return tables;
            }
        }
        return new Modifier(catalog, copier);
    }

    private static CatalogMappingSupplier buildModifier(Catalog catalog, EcoreUtil.Copier copier,
            List<AggregationTable> aggTables, Column yearCol, Column qtrCol, Column monthCol,
            Column monthCaptionCol, Column monthOrdinalCol, Column monthNameCol,
            List<MemberProperty> monthProp, List<Table> tables) {
        return buildModifier(catalog, copier, aggTables, yearCol, qtrCol, monthCol, monthCaptionCol,
                monthOrdinalCol, monthNameCol, monthProp, tables, "Unit Sales");
    }

    public static class ExplicitAggExtraColsRequiringJoin extends Base {
        @Override
        public String name() {
            return "mondrian.ExplicitRecognizerTest.testExplicitAggExtraColsRequiringJoin";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalogMapping = new CatalogSupplier().get();
            EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) catalogMapping);
            Catalog catalog = (Catalog) copier.get(catalogMapping);

            ExplicitAggregationTable aggName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggName.setTable((Table) copier.get(CatalogSupplier.TABLE_AGG_G_MS_PCAT_SALES_FACT));

            AggregationColumnName factCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            factCount.setColumn((Column) copier.get(CatalogSupplier.COLUMN_FACT_COUNT_AGG_G_MS_PCAT_SALES_FACT_1997));
            aggName.setAggregationFactCount(factCount);

            AggregationMeasure unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn((Column) copier.get(CatalogSupplier.COLUMN_UNIT_SALES_AGG_G_MS_PCAT_SALES_FACT_1997));
            aggName.getAggregationMeasures().add(unitSalesMeasure);

            AggregationLevel genderLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            genderLevel.setName("[Gender].[Gender].[Gender]");
            genderLevel.setColumn((Column) copier.get(CatalogSupplier.COLUMN_GENDER_AGG_G_MS_PCAT_SALES_FACT_1997));
            aggName.getAggregationLevels().add(genderLevel);

            AggregationLevel yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[TimeExtra].[TimeExtra].[Year]");
            yearLevel.setColumn((Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_AGG_G_MS_PCAT_SALES_FACT_1997));
            aggName.getAggregationLevels().add(yearLevel);

            AggregationLevel quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[TimeExtra].[TimeExtra].[Quarter]");
            quarterLevel.setColumn((Column) copier.get(CatalogSupplier.COLUMN_QUARTER_AGG_G_MS_PCAT_SALES_FACT_1997));
            aggName.getAggregationLevels().add(quarterLevel);

            AggregationLevel monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[TimeExtra].[TimeExtra].[Month]");
            monthLevel.setColumn((Column) copier.get(CatalogSupplier.COLUMN_MONTH_YEAR_AGG_G_MS_PCAT_SALES_FACT_1997));
            aggName.getAggregationLevels().add(monthLevel);

            return buildModifier(catalog, copier, List.of(aggName),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_MONTH_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY), null,
                List.of(), List.of(expAggTest, expAggTestDistinctCount));
        }
    }

    public static class ExplicitForeignKey extends Base {
        @Override
        public String name() {
            return "mondrian.ExplicitRecognizerTest.testExplicitForeignKey";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalogMapping = new CatalogSupplier().get();
            EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) catalogMapping);
            Catalog catalog = (Catalog) copier.get(catalogMapping);

            ExplicitAggregationTable aggName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggName.setTable((Table) copier.get(CatalogSupplier.TABLE_AGG_C_14_SALES_FACT));

            AggregationColumnName factCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            factCount.setColumn((Column) copier.get(CatalogSupplier.COLUMN_FACT_COUNT_AGG_C_14_SALES_FACT_1997));
            aggName.setAggregationFactCount(factCount);

            AggregationForeignKey foreignKey = AggregationFactory.eINSTANCE.createAggregationForeignKey();
            foreignKey.setFactColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_ID_SALESFACT));
            foreignKey.setAggregationColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_ID_AGG_C_14_SALES_FACT_1997));
            aggName.getAggregationForeignKeys().add(foreignKey);

            AggregationMeasure unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn((Column) copier.get(CatalogSupplier.COLUMN_UNIT_SALES_AGG_C_14_SALES_FACT_1997));
            aggName.getAggregationMeasures().add(unitSalesMeasure);

            AggregationMeasure storeCostMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeCostMeasure.setName("[Measures].[Store Cost]");
            storeCostMeasure.setColumn((Column) copier.get(CatalogSupplier.COLUMN_STORE_COST_AGG_C_14_SALES_FACT_1997));
            aggName.getAggregationMeasures().add(storeCostMeasure);

            AggregationLevel yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[TimeExtra].[TimeExtra].[Year]");
            yearLevel.setColumn((Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_AGG_C_14_SALES_FACT_1997));
            aggName.getAggregationLevels().add(yearLevel);

            AggregationLevel quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[TimeExtra].[TimeExtra].[Quarter]");
            quarterLevel.setColumn((Column) copier.get(CatalogSupplier.COLUMN_QUARTER_AGG_C_14_SALES_FACT_1997));
            aggName.getAggregationLevels().add(quarterLevel);

            AggregationLevel monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[TimeExtra].[TimeExtra].[Month]");
            monthLevel.setColumn((Column) copier.get(CatalogSupplier.COLUMN_MONTH_YEAR_AGG_C_14_SALES_FACT_1997));
            aggName.getAggregationLevels().add(monthLevel);

            return buildModifier(catalog, copier, List.of(aggName),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_MONTH_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY), null,
                List.of(), List.of(expAggTest, expAggTestDistinctCount));
        }
    }

    public static class ExplicitAggOrdinalOnAggTable extends Base {
        @Override
        public String name() {
            return "mondrian.ExplicitRecognizerTest.testExplicitAggOrdinalOnAggTable";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalogMapping = new CatalogSupplier().get();
            EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) catalogMapping);
            Catalog catalog = (Catalog) copier.get(catalogMapping);

            ExplicitAggregationTable aggName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggName.setTable(expAggTest);

            AggregationColumnName factCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            factCount.setColumn(factCountExpAggTest);
            aggName.setAggregationFactCount(factCount);

            AggregationMeasure unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(testUnitSalesExpAggTest);
            aggName.getAggregationMeasures().add(unitSalesMeasure);

            AggregationLevel genderLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            genderLevel.setName("[Gender].[Gender].[Gender]");
            genderLevel.setColumn(genderExpAggTest);
            aggName.getAggregationLevels().add(genderLevel);

            AggregationLevel yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[TimeExtra].[TimeExtra].[Year]");
            yearLevel.setColumn(testyearExpAggTest);
            aggName.getAggregationLevels().add(yearLevel);

            AggregationLevel quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[TimeExtra].[TimeExtra].[Quarter]");
            quarterLevel.setColumn(testqtrExpAggTest);
            aggName.getAggregationLevels().add(quarterLevel);

            AggregationLevel monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[TimeExtra].[TimeExtra].[Month]");
            monthLevel.setColumn(testmonthnameExpAggTest);
            monthLevel.getOrdinalColumns().addAll(List.of(testmonthordExpAggTest));
            aggName.getAggregationLevels().add(monthLevel);

            return buildModifier(catalog, copier, List.of(aggName),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_MONTH_TIME_BY_DAY), null,
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY), null,
                List.of(), List.of(expAggTest, expAggTestDistinctCount));
        }
    }

    public static class ExplicitAggCaptionOnAggTable extends Base {
        @Override
        public String name() {
            return "mondrian.ExplicitRecognizerTest.testExplicitAggCaptionOnAggTable";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalogMapping = new CatalogSupplier().get();
            EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) catalogMapping);
            Catalog catalog = (Catalog) copier.get(catalogMapping);

            ExplicitAggregationTable aggName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggName.setTable(expAggTest);

            AggregationColumnName factCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            factCount.setColumn(factCountExpAggTest);
            aggName.setAggregationFactCount(factCount);

            AggregationMeasure unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(testUnitSalesExpAggTest);
            aggName.getAggregationMeasures().add(unitSalesMeasure);

            AggregationLevel genderLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            genderLevel.setName("[Gender].[Gender].[Gender]");
            genderLevel.setColumn(genderExpAggTest);
            aggName.getAggregationLevels().add(genderLevel);

            AggregationLevel yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[TimeExtra].[TimeExtra].[Year]");
            yearLevel.setColumn(testyearExpAggTest);
            aggName.getAggregationLevels().add(yearLevel);

            AggregationLevel quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[TimeExtra].[TimeExtra].[Quarter]");
            quarterLevel.setColumn(testqtrExpAggTest);
            aggName.getAggregationLevels().add(quarterLevel);

            AggregationLevel monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[TimeExtra].[TimeExtra].[Month]");
            monthLevel.setColumn(testmonthnameExpAggTest);
            monthLevel.setCaptionColumn(testmonthcapExpAggTest);
            aggName.getAggregationLevels().add(monthLevel);

            return buildModifier(catalog, copier, List.of(aggName),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_MONTH_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY), null, null,
                List.of(), List.of(expAggTest, expAggTestDistinctCount));
        }
    }

    /** Disabled in the original ({@code //TODO need investigate}); migrated for parity, still unused. */
    public static class ExplicitAggNameColumnOnAggTable extends Base {
        @Override
        public String name() {
            return "mondrian.ExplicitRecognizerTest.testExplicitAggNameColumnOnAggTable";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalogMapping = new CatalogSupplier().get();
            EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) catalogMapping);
            Catalog catalog = (Catalog) copier.get(catalogMapping);

            ExplicitAggregationTable aggName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggName.setTable(expAggTest);

            AggregationColumnName factCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            factCount.setColumn(factCountExpAggTest);
            aggName.setAggregationFactCount(factCount);

            AggregationMeasure unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(testUnitSalesExpAggTest);
            aggName.getAggregationMeasures().add(unitSalesMeasure);

            AggregationLevel genderLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            genderLevel.setName("[Gender].[Gender]");
            genderLevel.setColumn(genderExpAggTest);
            aggName.getAggregationLevels().add(genderLevel);

            AggregationLevel yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[TimeExtra].[Year]");
            yearLevel.setColumn(testyearExpAggTest);
            aggName.getAggregationLevels().add(yearLevel);

            AggregationLevel quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[TimeExtra].[Quarter]");
            quarterLevel.setColumn(testqtrExpAggTest);
            aggName.getAggregationLevels().add(quarterLevel);

            AggregationLevel monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[TimeExtra].[Month]");
            monthLevel.setColumn(testmonthnameExpAggTest);
            monthLevel.setNameColumn(testmonthcapExpAggTest);

            AggregationLevelProperty property = AggregationFactory.eINSTANCE.createAggregationLevelProperty();
            property.setName("aProperty");
            property.setColumn(testmonprop1ExpAggTest);
            monthLevel.getAggregationLevelProperties().add(property);

            aggName.getAggregationLevels().add(monthLevel);

            MemberProperty memberProperty = LevelFactory.eINSTANCE.createMemberProperty();
            memberProperty.setName("aProperty");
            memberProperty.setColumn((Column) copier.get(CatalogSupplier.COLUMN_FISCAL_PERIOD_TIME_BY_DAY));

            return buildModifier(catalog, copier, List.of(aggName),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_MONTH_TIME_BY_DAY), null, null,
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY),
                List.of(memberProperty), List.of(expAggTest, expAggTestDistinctCount));
        }
    }

    public static class ExplicitAggPropertiesOnAggTable extends Base {
        @Override
        public String name() {
            return "mondrian.ExplicitRecognizerTest.testExplicitAggPropertiesOnAggTable";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalogMapping = new CatalogSupplier().get();
            EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) catalogMapping);
            Catalog catalog = (Catalog) copier.get(catalogMapping);

            ExplicitAggregationTable aggName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggName.setTable(expAggTestDistinctCount);

            AggregationColumnName factCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            factCount.setColumn(factCountExpAggTestDistinctCount);
            aggName.setAggregationFactCount(factCount);

            AggregationMeasure unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(unitSExpAggTestDistinctCount);
            aggName.getAggregationMeasures().add(unitSalesMeasure);

            AggregationMeasure customerCountMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            customerCountMeasure.setName("[Measures].[Customer Count]");
            customerCountMeasure.setColumn(custCntExpAggTestDistinctCount);
            aggName.getAggregationMeasures().add(customerCountMeasure);

            AggregationLevel yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[TimeExtra].[TimeExtra].[Year]");
            yearLevel.setColumn(testyearExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(yearLevel);

            AggregationLevel genderLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            genderLevel.setName("[Gender].[Gender].[Gender]");
            genderLevel.setColumn(genderExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(genderLevel);

            AggregationLevel storeCountryLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            storeCountryLevel.setName("[Store].[Store].[Store Country]");
            storeCountryLevel.setColumn(storeCountryExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(storeCountryLevel);

            AggregationLevel storeStateLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            storeStateLevel.setName("[Store].[Store].[Store State]");
            storeStateLevel.setColumn(storeStExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(storeStateLevel);

            AggregationLevel storeCityLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            storeCityLevel.setName("[Store].[Store].[Store City]");
            storeCityLevel.setColumn(storeCtyExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(storeCityLevel);

            AggregationLevel storeNameLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            storeNameLevel.setName("[Store].[Store].[Store Name]");
            storeNameLevel.setColumn(storeNameExpAggTestDistinctCount);

            AggregationLevelProperty streetAddressProperty = AggregationFactory.eINSTANCE.createAggregationLevelProperty();
            streetAddressProperty.setName("Street address");
            streetAddressProperty.setColumn(storeAddExpAggTestDistinctCount);
            storeNameLevel.getAggregationLevelProperties().add(streetAddressProperty);

            aggName.getAggregationLevels().add(storeNameLevel);

            return buildModifier(catalog, copier, List.of(aggName),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_MONTH_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY), null,
                List.of(), List.of(expAggTest, expAggTestDistinctCount));
        }
    }

    public static class CountDistinctAllowableRollup extends Base {
        @Override
        public String name() {
            return "mondrian.ExplicitRecognizerTest.testCountDistinctAllowableRollup";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalogMapping = new CatalogSupplier().get();
            EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) catalogMapping);
            Catalog catalog = (Catalog) copier.get(catalogMapping);

            ExplicitAggregationTable aggName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggName.setTable(expAggTestDistinctCount);

            AggregationColumnName factCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            factCount.setColumn(factCountExpAggTestDistinctCount);
            aggName.setAggregationFactCount(factCount);

            AggregationMeasure unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(unitSExpAggTestDistinctCount);
            aggName.getAggregationMeasures().add(unitSalesMeasure);

            AggregationMeasure customerCountMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            customerCountMeasure.setName("[Measures].[Customer Count]");
            customerCountMeasure.setColumn(custCntExpAggTestDistinctCount);
            aggName.getAggregationMeasures().add(customerCountMeasure);

            AggregationLevel yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[TimeExtra].[TimeExtra].[Year]");
            yearLevel.setColumn(testyearExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(yearLevel);

            AggregationLevel genderLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            genderLevel.setName("[Gender].[Gender].[Gender]");
            genderLevel.setColumn(genderExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(genderLevel);

            AggregationLevel storeCountryLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            storeCountryLevel.setName("[Store].[Store].[Store Country]");
            storeCountryLevel.setColumn(storeCountryExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(storeCountryLevel);

            AggregationLevel storeStateLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            storeStateLevel.setName("[Store].[Store].[Store State]");
            storeStateLevel.setColumn(storeStExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(storeStateLevel);

            AggregationLevel storeCityLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            storeCityLevel.setName("[Store].[Store].[Store City]");
            storeCityLevel.setColumn(storeCtyExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(storeCityLevel);

            AggregationLevel storeNameLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            storeNameLevel.setName("[Store].[Store].[Store Name]");
            storeNameLevel.setColumn(storeNameExpAggTestDistinctCount);

            AggregationLevelProperty streetAddressProperty = AggregationFactory.eINSTANCE.createAggregationLevelProperty();
            streetAddressProperty.setName("Street address");
            streetAddressProperty.setColumn(storeAddExpAggTestDistinctCount);
            storeNameLevel.getAggregationLevelProperties().add(streetAddressProperty);

            aggName.getAggregationLevels().add(storeNameLevel);

            return buildModifier(catalog, copier, List.of(aggName),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY), null,
                List.of(), List.of(expAggTest, expAggTestDistinctCount), "Customer Count");
        }
    }

    public static class CountDisallowedRollup extends Base {
        @Override
        public String name() {
            return "mondrian.ExplicitRecognizerTest.testCountDisallowedRollup";
        }

        @Override
        public CatalogMappingSupplier mappingSupplier() {
            Catalog catalogMapping = new CatalogSupplier().get();
            EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) catalogMapping);
            Catalog catalog = (Catalog) copier.get(catalogMapping);

            ExplicitAggregationTable aggName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggName.setTable(expAggTestDistinctCount);

            AggregationColumnName factCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            factCount.setColumn(factCountExpAggTestDistinctCount);
            aggName.setAggregationFactCount(factCount);

            AggregationMeasure unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(unitSExpAggTestDistinctCount);
            aggName.getAggregationMeasures().add(unitSalesMeasure);

            AggregationMeasure customerCountMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            customerCountMeasure.setName("[Measures].[Customer Count]");
            customerCountMeasure.setColumn(custCntExpAggTestDistinctCount);
            aggName.getAggregationMeasures().add(customerCountMeasure);

            AggregationLevel yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[TimeExtra].[TimeExtra].[Year]");
            yearLevel.setColumn(testyearExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(yearLevel);

            AggregationLevel genderLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            genderLevel.setName("[Gender].[Gender].[Gender]");
            genderLevel.setColumn(genderExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(genderLevel);

            AggregationLevel storeCountryLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            storeCountryLevel.setName("[Store].[Store].[Store Country]");
            storeCountryLevel.setColumn(storeCountryExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(storeCountryLevel);

            AggregationLevel storeStateLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            storeStateLevel.setName("[Store].[Store].[Store State]");
            storeStateLevel.setColumn(storeStExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(storeStateLevel);

            AggregationLevel storeCityLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            storeCityLevel.setName("[Store].[Store].[Store City]");
            storeCityLevel.setColumn(storeCtyExpAggTestDistinctCount);
            aggName.getAggregationLevels().add(storeCityLevel);

            AggregationLevel storeNameLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            storeNameLevel.setName("[Store].[Store].[Store Name]");
            storeNameLevel.setColumn(storeNameExpAggTestDistinctCount);

            AggregationLevelProperty streetAddressProperty = AggregationFactory.eINSTANCE.createAggregationLevelProperty();
            streetAddressProperty.setName("Street address");
            streetAddressProperty.setColumn(storeAddExpAggTestDistinctCount);
            storeNameLevel.getAggregationLevelProperties().add(streetAddressProperty);

            aggName.getAggregationLevels().add(storeNameLevel);

            return buildModifier(catalog, copier, List.of(aggName),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_QUARTER_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_THE_MONTH_TIME_BY_DAY),
                (Column) copier.get(CatalogSupplier.COLUMN_MONTH_OF_YEAR_TIME_BY_DAY), null,
                List.of(), List.of(expAggTest, expAggTestDistinctCount), "Customer Count");
        }
    }
}

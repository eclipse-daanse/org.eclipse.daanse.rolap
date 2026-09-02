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

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationExclude;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationFactory;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationTable;

/**
 * One {@code CatalogMappingSupplier} subclass of {@link AggMeasureFactCountTestModifierEmf}
 * per distinct {@code AggName}/{@code AggPattern} configuration a test method
 * in {@link AggMeasureFactCountTest} needs, each wrapped in its own
 * {@link AggMeasureFactCountTestInstances} variant. Each subclass overrides {@link AggMeasureFactCountTestModifierEmf#getAggTables()}
 * / {@link AggMeasureFactCountTestModifierEmf#getAggExcludes()}, mirroring the
 * pre-migration test's anonymous-subclass-per-method trick, just as named
 * top-level classes.
 */
public class AggMeasureFactCountTestModifiers {

    private AggMeasureFactCountTestModifiers() {
    }

    private static Column notExistColumn() {
        Column notExist = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        notExist.setName("not_exist");
        notExist.setType(SQLSimpleTypes.Sql99.integerType());
        return notExist;
    }

    /** {@code <AggName name="agg_c_6_fact_csv_2016">} with an explicit {@code factColumn} on every {@code AggMeasureFactCount}. */
    public static class AggName extends AggMeasureFactCountTestModifierEmf {

        public AggName(Catalog catalogMapping) {
            super(catalogMapping);
        }

        @Override
        protected List<AggregationTable> getAggTables() {
            var aggFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            aggFactCount.setColumn(factCountAggC6FactCsv2016);

            var storeSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeSalesFactCount.setColumn(storeSalesFactCountAggC6FactCsv2016);
            storeSalesFactCount.setFactColumn(storeSalesColumnInFactCsv2016);

            var storeCostFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeCostFactCount.setColumn(storeCostFactCountAggC6FactCsv2016);
            storeCostFactCount.setFactColumn(storeCostColumnInFactCsv2016);

            var unitSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            unitSalesFactCount.setColumn(unitSalesFactCountAggC6FactCsv2016);
            unitSalesFactCount.setFactColumn(unitSalesColumnInFactCsv2016);

            var unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(unitSalesAggC6FactCsv2016);

            var storeCostMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeCostMeasure.setName("[Measures].[Store Cost]");
            storeCostMeasure.setColumn(storeCostAggC6FactCsv2016);

            var storeSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeSalesMeasure.setName("[Measures].[Store Sales]");
            storeSalesMeasure.setColumn(storeSalesAggC6FactCsv2016);

            var yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[Time].[Time].[Year]");
            yearLevel.setColumn(theYearAggC6FactCsv2016);

            var quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[Time].[Time].[Quarter]");
            quarterLevel.setColumn(quarterAggC6FactCsv2016);

            var monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[Time].[Time].[Month]");
            monthLevel.setColumn(monthOfYearAggC6FactCsv2016);

            var aggregationName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggregationName.setTable(aggC6FactCsv2016);
            aggregationName.setAggregationFactCount(aggFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeSalesFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeCostFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(unitSalesFactCount);
            aggregationName.getAggregationMeasures().add(unitSalesMeasure);
            aggregationName.getAggregationMeasures().add(storeCostMeasure);
            aggregationName.getAggregationMeasures().add(storeSalesMeasure);
            aggregationName.getAggregationLevels().add(yearLevel);
            aggregationName.getAggregationLevels().add(quarterLevel);
            aggregationName.getAggregationLevels().add(monthLevel);

            return List.of(aggregationName);
        }
    }

    /**
     * Same {@code AggName} as {@link AggName}, but every {@code AggMeasureFactCount}
     * has no {@code factColumn} -- exercises the "factColumn does not exist"
     * path. Used only by the {@code @Disabled} {@code testFactColumnNotExists}.
     */
    public static class FactColumnNotExists extends AggMeasureFactCountTestModifierEmf {

        public FactColumnNotExists(Catalog catalogMapping) {
            super(catalogMapping);
        }

        @Override
        protected List<AggregationTable> getAggTables() {
            var aggFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            aggFactCount.setColumn(factCountAggC6FactCsv2016);

            var storeSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeSalesFactCount.setColumn(storeSalesFactCountAggC6FactCsv2016);

            var storeCostFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeCostFactCount.setColumn(storeCostFactCountAggC6FactCsv2016);

            var unitSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            unitSalesFactCount.setColumn(unitSalesFactCountAggC6FactCsv2016);

            var unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(unitSalesAggC6FactCsv2016);

            var storeCostMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeCostMeasure.setName("[Measures].[Store Cost]");
            storeCostMeasure.setColumn(storeCostAggC6FactCsv2016);

            var storeSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeSalesMeasure.setName("[Measures].[Store Sales]");
            storeSalesMeasure.setColumn(storeSalesAggC6FactCsv2016);

            var yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[Time].[Year]");
            yearLevel.setColumn(theYearAggC6FactCsv2016);

            var quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[Time].[Quarter]");
            quarterLevel.setColumn(quarterAggC6FactCsv2016);

            var monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[Time].[Month]");
            monthLevel.setColumn(monthOfYearAggC6FactCsv2016);

            var aggregationName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggregationName.setTable(aggC6FactCsv2016);
            aggregationName.setAggregationFactCount(aggFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeSalesFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeCostFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(unitSalesFactCount);
            aggregationName.getAggregationMeasures().add(unitSalesMeasure);
            aggregationName.getAggregationMeasures().add(storeCostMeasure);
            aggregationName.getAggregationMeasures().add(storeSalesMeasure);
            aggregationName.getAggregationLevels().add(yearLevel);
            aggregationName.getAggregationLevels().add(quarterLevel);
            aggregationName.getAggregationLevels().add(monthLevel);

            return List.of(aggregationName);
        }
    }

    /** Every {@code AggMeasureFactCount.factColumn} points at a column that doesn't exist in the fact table. */
    public static class MeasureFactColumnNotExist extends AggMeasureFactCountTestModifierEmf {

        public MeasureFactColumnNotExist(Catalog catalogMapping) {
            super(catalogMapping);
        }

        @Override
        protected List<AggregationTable> getAggTables() {
            Column notExist = notExistColumn();

            var aggFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            aggFactCount.setColumn(factCountAggC6FactCsv2016);

            var storeSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeSalesFactCount.setColumn(storeSalesFactCountAggC6FactCsv2016);
            storeSalesFactCount.setFactColumn(notExist);

            var storeCostFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeCostFactCount.setColumn(storeCostFactCountAggC6FactCsv2016);
            storeCostFactCount.setFactColumn(notExist);

            var unitSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            unitSalesFactCount.setColumn(unitSalesFactCountAggC6FactCsv2016);
            unitSalesFactCount.setFactColumn(notExist);

            var unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(unitSalesAggC6FactCsv2016);

            var storeCostMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeCostMeasure.setName("[Measures].[Store Cost]");
            storeCostMeasure.setColumn(storeCostAggC6FactCsv2016);

            var storeSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeSalesMeasure.setName("[Measures].[Store Sales]");
            storeSalesMeasure.setColumn(storeSalesAggC6FactCsv2016);

            var yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[Time].[Time].[Year]");
            yearLevel.setColumn(theYearAggC6FactCsv2016);

            var quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[Time].[Time].[Quarter]");
            quarterLevel.setColumn(quarterAggC6FactCsv2016);

            var monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[Time].[Time].[Month]");
            monthLevel.setColumn(monthOfYearAggC6FactCsv2016);

            var aggregationName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggregationName.setTable(aggC6FactCsv2016);
            aggregationName.setAggregationFactCount(aggFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeSalesFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeCostFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(unitSalesFactCount);
            aggregationName.getAggregationMeasures().add(unitSalesMeasure);
            aggregationName.getAggregationMeasures().add(storeCostMeasure);
            aggregationName.getAggregationMeasures().add(storeSalesMeasure);
            aggregationName.getAggregationLevels().add(yearLevel);
            aggregationName.getAggregationLevels().add(quarterLevel);
            aggregationName.getAggregationLevels().add(monthLevel);

            return List.of(aggregationName);
        }
    }

    /** No {@code AggMeasureFactCount} elements at all -- falls back to the general {@code fact_count} column. */
    public static class WithoutMeasureFactColumnElement extends AggMeasureFactCountTestModifierEmf {

        public WithoutMeasureFactColumnElement(Catalog catalogMapping) {
            super(catalogMapping);
        }

        @Override
        protected List<AggregationTable> getAggTables() {
            var aggFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            aggFactCount.setColumn(factCountAggC6FactCsv2016);

            var unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(unitSalesAggC6FactCsv2016);

            var storeCostMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeCostMeasure.setName("[Measures].[Store Cost]");
            storeCostMeasure.setColumn(storeCostAggC6FactCsv2016);

            var storeSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeSalesMeasure.setName("[Measures].[Store Sales]");
            storeSalesMeasure.setColumn(storeSalesAggC6FactCsv2016);

            var yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[Time].[Time].[Year]");
            yearLevel.setColumn(theYearAggC6FactCsv2016);

            var quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[Time].[Time].[Quarter]");
            quarterLevel.setColumn(quarterAggC6FactCsv2016);

            var monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[Time].[Time].[Month]");
            monthLevel.setColumn(monthOfYearAggC6FactCsv2016);

            var aggregationName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggregationName.setTable(aggC6FactCsv2016);
            aggregationName.setAggregationFactCount(aggFactCount);
            aggregationName.getAggregationMeasures().add(unitSalesMeasure);
            aggregationName.getAggregationMeasures().add(storeCostMeasure);
            aggregationName.getAggregationMeasures().add(storeSalesMeasure);
            aggregationName.getAggregationLevels().add(yearLevel);
            aggregationName.getAggregationLevels().add(quarterLevel);
            aggregationName.getAggregationLevels().add(monthLevel);

            return List.of(aggregationName);
        }
    }

    /** Both the {@code AggFactCount} column and every {@code AggMeasureFactCount.factColumn} point at a nonexistent column. */
    public static class MeasureFactColumnAndAggFactCountNotExist extends AggMeasureFactCountTestModifierEmf {

        public MeasureFactColumnAndAggFactCountNotExist(Catalog catalogMapping) {
            super(catalogMapping);
        }

        @Override
        protected List<AggregationTable> getAggTables() {
            Column notExist = notExistColumn();

            var aggFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            aggFactCount.setColumn(notExist);

            var storeSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeSalesFactCount.setColumn(storeSalesFactCountAggC6FactCsv2016);
            storeSalesFactCount.setFactColumn(notExist);

            var storeCostFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeCostFactCount.setColumn(storeCostFactCountAggC6FactCsv2016);
            storeCostFactCount.setFactColumn(notExist);

            var unitSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            unitSalesFactCount.setColumn(unitSalesFactCountAggC6FactCsv2016);
            unitSalesFactCount.setFactColumn(notExist);

            var unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(unitSalesAggC6FactCsv2016);

            var storeCostMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeCostMeasure.setName("[Measures].[Store Cost]");
            storeCostMeasure.setColumn(storeCostAggC6FactCsv2016);

            var storeSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeSalesMeasure.setName("[Measures].[Store Sales]");
            storeSalesMeasure.setColumn(storeSalesAggC6FactCsv2016);

            var yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[Time].[Time].[Year]");
            yearLevel.setColumn(theYearAggC6FactCsv2016);

            var quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[Time].[Time].[Quarter]");
            quarterLevel.setColumn(quarterAggC6FactCsv2016);

            var monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[Time].[Time].[Month]");
            monthLevel.setColumn(monthOfYearAggC6FactCsv2016);

            var aggregationName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggregationName.setTable(aggC6FactCsv2016);
            aggregationName.setAggregationFactCount(aggFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeSalesFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeCostFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(unitSalesFactCount);
            aggregationName.getAggregationMeasures().add(unitSalesMeasure);
            aggregationName.getAggregationMeasures().add(storeCostMeasure);
            aggregationName.getAggregationMeasures().add(storeSalesMeasure);
            aggregationName.getAggregationLevels().add(yearLevel);
            aggregationName.getAggregationLevels().add(quarterLevel);
            aggregationName.getAggregationLevels().add(monthLevel);

            return List.of(aggregationName);
        }
    }

    /** {@code agg_csv_different_column_names} (differently-named fact-count columns), plus excluding {@code agg_c_6_fact_csv_2016}. */
    public static class AggNameDifferentColumnNames extends AggMeasureFactCountTestModifierEmf {

        public AggNameDifferentColumnNames(Catalog catalogMapping) {
            super(catalogMapping);
        }

        @Override
        protected List<AggregationTable> getAggTables() {
            var aggFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            aggFactCount.setColumn(factCountAggCsvDifferentColumnNames);

            var storeSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeSalesFactCount.setColumn(ssFcAggCsvDifferentColumnNames);
            storeSalesFactCount.setFactColumn(storeSalesColumnInFactCsv2016);

            var storeCostFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeCostFactCount.setColumn(scFcAggCsvDifferentColumnNames);
            storeCostFactCount.setFactColumn(storeCostColumnInFactCsv2016);

            var unitSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            unitSalesFactCount.setColumn(usFcAggCsvDifferentColumnNames);
            unitSalesFactCount.setFactColumn(unitSalesColumnInFactCsv2016);

            var unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(unitSalesAggCsvDifferentColumnNames);

            var storeCostMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeCostMeasure.setName("[Measures].[Store Cost]");
            storeCostMeasure.setColumn(storeCostAggCsvDifferentColumnNames);

            var storeSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeSalesMeasure.setName("[Measures].[Store Sales]");
            storeSalesMeasure.setColumn(storeSalesAggCsvDifferentColumnNames);

            var yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[Time].[Time].[Year]");
            yearLevel.setColumn(theYearAggCsvDifferentColumnNames);

            var quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[Time].[Time].[Quarter]");
            quarterLevel.setColumn(quarterAggCsvDifferentColumnNames);

            var monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[Time].[Time].[Month]");
            monthLevel.setColumn(monthOfYearAggCsvDifferentColumnNames);

            var aggregationName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggregationName.setTable(aggCsvDifferentColumnNames);
            aggregationName.setAggregationFactCount(aggFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeSalesFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeCostFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(unitSalesFactCount);
            aggregationName.getAggregationMeasures().add(unitSalesMeasure);
            aggregationName.getAggregationMeasures().add(storeCostMeasure);
            aggregationName.getAggregationMeasures().add(storeSalesMeasure);
            aggregationName.getAggregationLevels().add(yearLevel);
            aggregationName.getAggregationLevels().add(quarterLevel);
            aggregationName.getAggregationLevels().add(monthLevel);

            return List.of(aggregationName);
        }

        @Override
        protected List<AggregationExclude> getAggExcludes() {
            AggregationExclude aggregationExclude = AggregationFactory.eINSTANCE.createAggregationExclude();
            aggregationExclude.setName("agg_c_6_fact_csv_2016");
            return List.of(aggregationExclude);
        }
    }

    /** {@code agg_csv_divide_by_zero} (some rows carry a zero fact-count), plus excluding {@code agg_c_6_fact_csv_2016}. */
    public static class AggDivideByZero extends AggMeasureFactCountTestModifierEmf {

        public AggDivideByZero(Catalog catalogMapping) {
            super(catalogMapping);
        }

        @Override
        protected List<AggregationTable> getAggTables() {
            var aggFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            aggFactCount.setColumn(factCountAggCsvDivideByZero);

            var storeSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeSalesFactCount.setColumn(storeSalesFactCountAggCsvDivideByZero);
            storeSalesFactCount.setFactColumn(storeSalesColumnInFactCsv2016);

            var storeCostFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeCostFactCount.setColumn(storeCostFactCountAggCsvDivideByZero);
            storeCostFactCount.setFactColumn(storeCostColumnInFactCsv2016);

            var unitSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            unitSalesFactCount.setColumn(unitSalesFactCountAggCsvDivideByZero);
            unitSalesFactCount.setFactColumn(unitSalesColumnInFactCsv2016);

            var unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(unitSalesAggCsvDivideByZero);

            var storeCostMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeCostMeasure.setName("[Measures].[Store Cost]");
            storeCostMeasure.setColumn(storeCostAggCsvDivideByZero);

            var storeSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeSalesMeasure.setName("[Measures].[Store Sales]");
            storeSalesMeasure.setColumn(storeSalesAggCsvDivideByZero);

            var yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[Time].[Time].[Year]");
            yearLevel.setColumn(theYearAggCsvDivideByZero);

            var quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[Time].[Time].[Quarter]");
            quarterLevel.setColumn(quarterAggCsvDivideByZero);

            var monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[Time].[Time].[Month]");
            monthLevel.setColumn(monthOfYearAggCsvDivideByZero);

            var aggregationName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
            aggregationName.setTable(aggCsvDivideByZero);
            aggregationName.setAggregationFactCount(aggFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeSalesFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeCostFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(unitSalesFactCount);
            aggregationName.getAggregationMeasures().add(unitSalesMeasure);
            aggregationName.getAggregationMeasures().add(storeCostMeasure);
            aggregationName.getAggregationMeasures().add(storeSalesMeasure);
            aggregationName.getAggregationLevels().add(yearLevel);
            aggregationName.getAggregationLevels().add(quarterLevel);
            aggregationName.getAggregationLevels().add(monthLevel);

            return List.of(aggregationName);
        }

        @Override
        protected List<AggregationExclude> getAggExcludes() {
            AggregationExclude aggregationExclude = AggregationFactory.eINSTANCE.createAggregationExclude();
            aggregationExclude.setName("agg_c_6_fact_csv_2016");
            return List.of(aggregationExclude);
        }
    }

    /** Same shape as {@link AggName}, but matched by {@code <AggPattern>} instead of an explicit {@code <AggName>}. */
    public static class AggPattern extends AggMeasureFactCountTestModifierEmf {

        public AggPattern(Catalog catalogMapping) {
            super(catalogMapping);
        }

        @Override
        protected List<AggregationTable> getAggTables() {
            var aggFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
            aggFactCount.setColumn(factCountAggC6FactCsv2016);

            var storeSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeSalesFactCount.setColumn(storeSalesFactCountAggC6FactCsv2016);
            storeSalesFactCount.setFactColumn(storeSalesColumnInFactCsv2016);

            var storeCostFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            storeCostFactCount.setColumn(storeCostFactCountAggC6FactCsv2016);
            storeCostFactCount.setFactColumn(storeCostColumnInFactCsv2016);

            var unitSalesFactCount = AggregationFactory.eINSTANCE.createAggregationMeasureFactCount();
            unitSalesFactCount.setColumn(unitSalesFactCountAggC6FactCsv2016);
            unitSalesFactCount.setFactColumn(unitSalesColumnInFactCsv2016);

            var unitSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            unitSalesMeasure.setName("[Measures].[Unit Sales]");
            unitSalesMeasure.setColumn(unitSalesAggC6FactCsv2016);

            var storeCostMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeCostMeasure.setName("[Measures].[Store Cost]");
            storeCostMeasure.setColumn(storeCostAggC6FactCsv2016);

            var storeSalesMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
            storeSalesMeasure.setName("[Measures].[Store Sales]");
            storeSalesMeasure.setColumn(storeSalesAggC6FactCsv2016);

            var yearLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            yearLevel.setName("[Time].[Time].[Year]");
            yearLevel.setColumn(theYearAggC6FactCsv2016);

            var quarterLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            quarterLevel.setName("[Time].[Time].[Quarter]");
            quarterLevel.setColumn(quarterAggC6FactCsv2016);

            var monthLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
            monthLevel.setName("[Time].[Time].[Month]");
            monthLevel.setColumn(monthOfYearAggC6FactCsv2016);

            var aggregationName = AggregationFactory.eINSTANCE.createPatternAggregationTable();
            aggregationName.setPattern("agg_c_6_fact_csv_2016");
            aggregationName.setAggregationFactCount(aggFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeSalesFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(storeCostFactCount);
            aggregationName.getAggregationMeasureFactCounts().add(unitSalesFactCount);
            aggregationName.getAggregationMeasures().add(unitSalesMeasure);
            aggregationName.getAggregationMeasures().add(storeCostMeasure);
            aggregationName.getAggregationMeasures().add(storeSalesMeasure);
            aggregationName.getAggregationLevels().add(yearLevel);
            aggregationName.getAggregationLevels().add(quarterLevel);
            aggregationName.getAggregationLevels().add(monthLevel);

            return List.of(aggregationName);
        }
    }
}

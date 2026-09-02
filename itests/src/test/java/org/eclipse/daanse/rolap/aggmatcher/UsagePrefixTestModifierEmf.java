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

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.CatalogFactory;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationColumnName;
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
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MeasureFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.SumMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.StandardDimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.ExplicitHierarchy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.HierarchyFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.HideMemberIf;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelDefinition;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

/**
 * Self-contained fixture for {@link UsagePrefixTest}: the "Cheques" cube,
 * carrying two usages of the same "store" table shape (StoreX/StoreY) that
 * are disambiguated on the "agg_lp_595_cheques" aggregate table via
 * {@code usagePrefix} ("firstprefix_"/"secondprefix_") -- no dependency on
 * the FoodMart catalog or its data. See MONDRIAN-595.
 */
public class UsagePrefixTestModifierEmf implements CatalogMappingSupplier {

    protected final Catalog catalog;

    // cheques table columns
    protected Column storeIdCheques;
    protected Column prodIdCheques;
    protected Column amountCheques;
    protected Table cheques;

    // store_x table columns
    protected Column storeIdStoreX;
    protected Column valueStoreX;
    protected Table storeX;

    // store_y table columns
    protected Column storeIdStoreY;
    protected Column valueStoreY;
    protected Table storeY;

    // agg_lp_595_cheques table columns
    protected Column firstprefixValueAgg;
    protected Column secondprefixValueAgg;
    protected Column amountAgg;
    protected Column factCountAgg;
    protected Table aggLp595Cheques;

    // Dimensions
    protected StandardDimension dimensionStoreX;
    protected StandardDimension dimensionStoreY;

    // Hierarchies and levels
    protected ExplicitHierarchy hierarchyStoreX;
    protected Level levelStoreX;
    protected ExplicitHierarchy hierarchyStoreY;
    protected Level levelStoreY;

    // Measures
    protected SumMeasure measureAmount;

    // Cube
    protected PhysicalCube chequesCube;

    public UsagePrefixTestModifierEmf(Catalog catalogMapping) {
        this.catalog = EmfUtil.copy((CatalogImpl) catalogMapping);
        createTables();
        createDimensions();
        createMeasures();
        createCube();
    }

    /*
      + "<Dimension name='StoreX' >"
      + "  <Hierarchy hasAll='true' primaryKey='store_id'>"
      + "    <Table name='store_x'/>"
      + "    <Level name='Store Value' column='value' uniqueMembers='true'/>"
      + "  </Hierarchy>"
      + "</Dimension>"
      + "<Dimension name='StoreY' >"
      + "  <Hierarchy hasAll='true' primaryKey='store_id'>"
      + "    <Table name='store_y'/>"
      + "    <Level name='Store Value' column='value' uniqueMembers='true'/>"
      + "  </Hierarchy>"
      + "</Dimension>"
      + "<Cube name='Cheques'>"
      + "  <Table name='cheques'>"
      + "    <AggName name='agg_lp_595_cheques'>"
      + "      <AggFactCount column='FACT_COUNT'/>"
      + "      <AggMeasure name='[Measures].[Amount]' column='amount' />"
      + "      <AggLevel name=\"[StoreX].[Store Value]\" column=\"value\" />"
      + "    </AggName>"
      + "  </Table>"
      + "  <DimensionUsage name=\"StoreX\" source=\"StoreX\" foreignKey=\"store_id\" usagePrefix=\"firstprefix_\" />"
      + "  <DimensionUsage name=\"StoreY\" source=\"StoreY\" foreignKey=\"store_id\" usagePrefix=\"secondprefix_\" />"
      + "  <Measure name='Amount' column='amount' aggregator='sum' formatString='00.0'/>"
      + "</Cube>";
     */

    protected void createTables() {
        // Create cheques table columns
        storeIdCheques = RelationalFactory.eINSTANCE.createColumn();
        storeIdCheques.setName("store_id");
        storeIdCheques.setType(SQLSimpleTypes.Sql99.integerType());

        prodIdCheques = RelationalFactory.eINSTANCE.createColumn();
        prodIdCheques.setName("prod_id");
        prodIdCheques.setType(SQLSimpleTypes.Sql99.integerType());

        amountCheques = RelationalFactory.eINSTANCE.createColumn();
        amountCheques.setName("amount");
        amountCheques.setType(SQLSimpleTypes.decimalType(18, 4));

        cheques = RelationalFactory.eINSTANCE.createTable();
        cheques.setName("cheques");
        cheques.getFeature().add(storeIdCheques);
        cheques.getFeature().add(prodIdCheques);
        cheques.getFeature().add(amountCheques);

        // Create store_x table columns
        storeIdStoreX = RelationalFactory.eINSTANCE.createColumn();
        storeIdStoreX.setName("store_id");
        storeIdStoreX.setType(SQLSimpleTypes.Sql99.integerType());

        valueStoreX = RelationalFactory.eINSTANCE.createColumn();
        valueStoreX.setName("value");
        valueStoreX.setType(SQLSimpleTypes.varcharType(255));

        storeX = RelationalFactory.eINSTANCE.createTable();
        storeX.setName("store_x");
        storeX.getFeature().add(storeIdStoreX);
        storeX.getFeature().add(valueStoreX);

        // Create store_y table columns
        storeIdStoreY = RelationalFactory.eINSTANCE.createColumn();
        storeIdStoreY.setName("store_id");
        storeIdStoreY.setType(SQLSimpleTypes.Sql99.integerType());

        valueStoreY = RelationalFactory.eINSTANCE.createColumn();
        valueStoreY.setName("value");
        valueStoreY.setType(SQLSimpleTypes.varcharType(255));

        storeY = RelationalFactory.eINSTANCE.createTable();
        storeY.setName("store_y");
        storeY.getFeature().add(storeIdStoreY);
        storeY.getFeature().add(valueStoreY);

        // Create agg_lp_595_cheques table columns
        firstprefixValueAgg = RelationalFactory.eINSTANCE.createColumn();
        firstprefixValueAgg.setName("firstprefix_value");
        firstprefixValueAgg.setType(SQLSimpleTypes.varcharType(255));

        secondprefixValueAgg = RelationalFactory.eINSTANCE.createColumn();
        secondprefixValueAgg.setName("secondprefix_value");
        secondprefixValueAgg.setType(SQLSimpleTypes.varcharType(255));

        amountAgg = RelationalFactory.eINSTANCE.createColumn();
        amountAgg.setName("amount");
        amountAgg.setType(SQLSimpleTypes.decimalType(18, 4));

        factCountAgg = RelationalFactory.eINSTANCE.createColumn();
        factCountAgg.setName("FACT_COUNT");
        factCountAgg.setType(SQLSimpleTypes.Sql99.integerType());

        aggLp595Cheques = RelationalFactory.eINSTANCE.createTable();
        aggLp595Cheques.setName("agg_lp_595_cheques");
        aggLp595Cheques.getFeature().add(firstprefixValueAgg);
        aggLp595Cheques.getFeature().add(secondprefixValueAgg);
        aggLp595Cheques.getFeature().add(amountAgg);
        aggLp595Cheques.getFeature().add(factCountAgg);

        // Add tables to database schema
        if (Packages.available(catalog, Schema.class).size() > 0) {
            Schema dbSchema = Packages.available(catalog, Schema.class).get(0);
            dbSchema.getOwnedElement().add(cheques);
            dbSchema.getOwnedElement().add(storeX);
            dbSchema.getOwnedElement().add(storeY);
            dbSchema.getOwnedElement().add(aggLp595Cheques);
        }
    }

    protected void createDimensions() {
        // Create StoreX dimension
        dimensionStoreX = DimensionFactory.eINSTANCE.createStandardDimension();
        dimensionStoreX.setName("StoreX");

        TableSource queryStoreX = SourceFactory.eINSTANCE.createTableSource();
        queryStoreX.setTable(storeX);

        levelStoreX = LevelFactory.eINSTANCE.createLevel();
        levelStoreX.setName("Store Value");
        levelStoreX.setColumn(valueStoreX);
        levelStoreX.setColumnType(ColumnInternalDataType.STRING);
        levelStoreX.setUniqueMembers(true);
        levelStoreX.setType(LevelDefinition.REGULAR);
        levelStoreX.setHideMemberIf(HideMemberIf.NEVER);

        hierarchyStoreX = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        hierarchyStoreX.setHasAll(true);
        hierarchyStoreX.setPrimaryKey(storeIdStoreX);
        hierarchyStoreX.setSource(queryStoreX);
        hierarchyStoreX.getLevels().add(levelStoreX);

        dimensionStoreX.getHierarchies().add(hierarchyStoreX);

        // Create StoreY dimension
        dimensionStoreY = DimensionFactory.eINSTANCE.createStandardDimension();
        dimensionStoreY.setName("StoreY");

        TableSource queryStoreY = SourceFactory.eINSTANCE.createTableSource();
        queryStoreY.setTable(storeY);

        levelStoreY = LevelFactory.eINSTANCE.createLevel();
        levelStoreY.setName("Store Value");
        levelStoreY.setColumn(valueStoreY);
        levelStoreY.setColumnType(ColumnInternalDataType.STRING);
        levelStoreY.setUniqueMembers(true);
        levelStoreY.setType(LevelDefinition.REGULAR);
        levelStoreY.setHideMemberIf(HideMemberIf.NEVER);

        hierarchyStoreY = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        hierarchyStoreY.setHasAll(true);
        hierarchyStoreY.setPrimaryKey(storeIdStoreY);
        hierarchyStoreY.setSource(queryStoreY);
        hierarchyStoreY.getLevels().add(levelStoreY);

        dimensionStoreY.getHierarchies().add(hierarchyStoreY);
    }

    protected void createMeasures() {
        measureAmount = MeasureFactory.eINSTANCE.createSumMeasure();
        measureAmount.setName("Amount");
        measureAmount.setColumn(amountCheques);
        measureAmount.setFormatString("00.0");
        measureAmount.setVisible(true);
    }

    protected void createCube() {
        // Create table query with aggregation
        TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
        tableQuery.setTable(cheques);

        // Create aggregation
        ExplicitAggregationTable aggName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
        aggName.setTable(aggLp595Cheques);

        // Aggregation fact count
        AggregationColumnName aggFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
        aggFactCount.setColumn(factCountAgg);
        aggName.setAggregationFactCount(aggFactCount);

        // Aggregation measure
        AggregationMeasure aggMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
        aggMeasure.setColumn(amountAgg);
        aggMeasure.setName("[Measures].[Amount]");
        aggName.getAggregationMeasures().add(aggMeasure);

        // Aggregation level
        AggregationLevel aggLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
        aggLevel.setColumn(firstprefixValueAgg);
        aggLevel.setName("[StoreX].[Store Value]");
        aggName.getAggregationLevels().add(aggLevel);

        tableQuery.getAggregationTables().add(aggName);

        // Create dimension connectors
        DimensionConnector storeXConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        storeXConnector.setDimension(dimensionStoreX);
        storeXConnector.setOverrideDimensionName("StoreX");
        storeXConnector.setVisible(true);
        storeXConnector.setForeignKey(storeIdCheques);
        storeXConnector.setUsagePrefix("firstprefix_");

        DimensionConnector storeYConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        storeYConnector.setDimension(dimensionStoreY);
        storeYConnector.setOverrideDimensionName("StoreY");
        storeYConnector.setVisible(true);
        storeYConnector.setForeignKey(storeIdCheques);
        storeYConnector.setUsagePrefix("secondprefix_");

        // Create measure group
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(measureAmount);

        // Create cube
        chequesCube = CubeFactory.eINSTANCE.createPhysicalCube();
        chequesCube.setName("Cheques");
        chequesCube.setVisible(true);
        chequesCube.setCache(true);
        chequesCube.setEnabled(true);
        chequesCube.setSource(tableQuery);
        chequesCube.getDimensionConnectors().add(storeXConnector);
        chequesCube.getDimensionConnectors().add(storeYConnector);
        chequesCube.getMeasureGroups().add(measureGroup);

        // Add cube to catalog
        catalog.getImportedElement().add(chequesCube);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

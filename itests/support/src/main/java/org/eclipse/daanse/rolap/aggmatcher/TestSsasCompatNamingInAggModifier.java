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
package org.eclipse.daanse.rolap.aggmatcher;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.model.RolapMappingFactory;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationColumnName;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationFactory;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationLevel;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.AggregationMeasure;
import org.eclipse.daanse.rolap.mapping.model.database.aggregation.ExplicitAggregationTable;
import org.eclipse.daanse.rolap.mapping.model.database.source.JoinSource;
import org.eclipse.daanse.rolap.mapping.model.database.source.JoinedQueryElement;
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
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
/**
 * EMF version of TestSsasCompatNamingInAggModifier from NonCollapsedAggTest.
 */
public class TestSsasCompatNamingInAggModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    public TestSsasCompatNamingInAggModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        this.catalog = EmfUtil.copy((CatalogImpl) baseCatalog);

        // Create all physical tables and columns using RolapMappingFactory
        RolapMappingFactory factory = RolapMappingFactory.eINSTANCE;

        // Create foo_fact table with columns
        Column lineIdFooFact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        lineIdFooFact.setName("line_id");
        lineIdFooFact.setType(SQLSimpleTypes.Sql99.integerType());

        Column unitSalesFooFact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        unitSalesFooFact.setName("unit_sales");
        unitSalesFooFact.setType(SQLSimpleTypes.Sql99.integerType());

        Table fooFact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        fooFact.setName("foo_fact");
        fooFact.getFeature().add(lineIdFooFact);
        fooFact.getFeature().add(unitSalesFooFact);

        // Create line table
        Column lineIdLine = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        lineIdLine.setName("line_id");
        lineIdLine.setType(SQLSimpleTypes.Sql99.integerType());

        Column lineNameLine = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        lineNameLine.setName("line_name");
        lineNameLine.setType(SQLSimpleTypes.varcharType(255));
        // lineNameLine.setCharOctetLength(30);

        Table line = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        line.setName("line");
        line.getFeature().add(lineIdLine);
        line.getFeature().add(lineNameLine);

        // Create line_tenant table
        Column lineIdLineTenant = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        lineIdLineTenant.setName("line_id");
        lineIdLineTenant.setType(SQLSimpleTypes.Sql99.integerType());

        Column tenantIdLineTenant = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        tenantIdLineTenant.setName("tenant_id");
        tenantIdLineTenant.setType(SQLSimpleTypes.Sql99.integerType());

        Table lineTenant = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        lineTenant.setName("line_tenant");
        lineTenant.getFeature().add(lineIdLineTenant);
        lineTenant.getFeature().add(tenantIdLineTenant);

        // Create tenant table
        Column tenantIdTenant = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        tenantIdTenant.setName("tenant_id");
        tenantIdTenant.setType(SQLSimpleTypes.Sql99.integerType());

        Column tenantNameTenant = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        tenantNameTenant.setName("tenant_name");
        tenantNameTenant.setType(SQLSimpleTypes.varcharType(255));
        // tenantNameTenant.setCharOctetLength(30);

        Table tenant = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        tenant.setName("tenant");
        tenant.getFeature().add(tenantIdTenant);
        tenant.getFeature().add(tenantNameTenant);

        // Create line_line_class table
        Column lineIdLineLineClass = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        lineIdLineLineClass.setName("line_id");
        lineIdLineLineClass.setType(SQLSimpleTypes.Sql99.integerType());

        Column lineClassIdLineLineClass = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        lineClassIdLineLineClass.setName("line_class_id");
        lineClassIdLineLineClass.setType(SQLSimpleTypes.Sql99.integerType());

        Table lineLineClass = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        lineLineClass.setName("line_line_class");
        lineLineClass.getFeature().add(lineIdLineLineClass);
        lineLineClass.getFeature().add(lineClassIdLineLineClass);

        // Create distributor table
        Column distributorIdDistributor = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        distributorIdDistributor.setName("distributor_id");
        distributorIdDistributor.setType(SQLSimpleTypes.Sql99.integerType());

        Column distributorNameDistributor = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        distributorNameDistributor.setName("distributor_name");
        distributorNameDistributor.setType(SQLSimpleTypes.varcharType(255));
        // distributorNameDistributor.setCharOctetLength(30);

        Table distributor = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        distributor.setName("distributor");
        distributor.getFeature().add(distributorIdDistributor);
        distributor.getFeature().add(distributorNameDistributor);

        // Create line_class_distributor table
        Column lineClassIdLineClassDistributor = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        lineClassIdLineClassDistributor.setName("line_class_id");
        lineClassIdLineClassDistributor.setType(SQLSimpleTypes.Sql99.integerType());

        Column distributorIdLineClassDistributor = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        distributorIdLineClassDistributor.setName("distributor_id");
        distributorIdLineClassDistributor.setType(SQLSimpleTypes.Sql99.integerType());

        Table lineClassDistributor = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        lineClassDistributor.setName("line_class_distributor");
        lineClassDistributor.getFeature().add(lineClassIdLineClassDistributor);
        lineClassDistributor.getFeature().add(distributorIdLineClassDistributor);

        // Create line_class table
        Column lineClassIdLineClass = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        lineClassIdLineClass.setName("line_class_id");
        lineClassIdLineClass.setType(SQLSimpleTypes.Sql99.integerType());

        Column lineClassNameLineClass = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        lineClassNameLineClass.setName("line_class_name");
        lineClassNameLineClass.setType(SQLSimpleTypes.varcharType(255));
        // lineClassNameLineClass.setCharOctetLength(30);

        Table lineClass = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        lineClass.setName("line_class");
        lineClass.getFeature().add(lineClassIdLineClass);
        lineClass.getFeature().add(lineClassNameLineClass);

        // Create network table
        Column networkIdNetwork = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        networkIdNetwork.setName("network_id");
        networkIdNetwork.setType(SQLSimpleTypes.Sql99.integerType());

        Column networkNameNetwork = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        networkNameNetwork.setName("network_name");
        networkNameNetwork.setType(SQLSimpleTypes.varcharType(255));
        // networkNameNetwork.setCharOctetLength(30);

        Table network = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        network.setName("network");
        network.getFeature().add(networkIdNetwork);
        network.getFeature().add(networkNameNetwork);

        // Create line_class_network table
        Column lineClassIdLineClassNetwork = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        lineClassIdLineClassNetwork.setName("line_class_id");
        lineClassIdLineClassNetwork.setType(SQLSimpleTypes.Sql99.integerType());

        Column networkIdLineClassNetwork = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        networkIdLineClassNetwork.setName("network_id");
        networkIdLineClassNetwork.setType(SQLSimpleTypes.Sql99.integerType());

        Table lineClassNetwork = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        lineClassNetwork.setName("line_class_network");
        lineClassNetwork.getFeature().add(lineClassIdLineClassNetwork);
        lineClassNetwork.getFeature().add(networkIdLineClassNetwork);

        // Create aggregation tables
        // agg_tenant
        Column tenantIdAggTenant = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        tenantIdAggTenant.setName("tenant_id");
        tenantIdAggTenant.setType(SQLSimpleTypes.Sql99.integerType());

        Column unitSalesAggTenant = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        unitSalesAggTenant.setName("unit_sales");
        unitSalesAggTenant.setType(SQLSimpleTypes.Sql99.integerType());

        Column factCountAggTenant = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        factCountAggTenant.setName("fact_count");
        factCountAggTenant.setType(SQLSimpleTypes.Sql99.integerType());

        Table aggTenant = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        aggTenant.setName("agg_tenant");
        aggTenant.getFeature().add(tenantIdAggTenant);
        aggTenant.getFeature().add(unitSalesAggTenant);
        aggTenant.getFeature().add(factCountAggTenant);

        // agg_line_class
        Column lineClassIdAggLineClass = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        lineClassIdAggLineClass.setName("line_class_id");
        lineClassIdAggLineClass.setType(SQLSimpleTypes.Sql99.integerType());

        Column unitSalesAggLineClass = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        unitSalesAggLineClass.setName("unit_sales");
        unitSalesAggLineClass.setType(SQLSimpleTypes.Sql99.integerType());

        Column factCountAggLineClass = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        factCountAggLineClass.setName("fact_count");
        factCountAggLineClass.setType(SQLSimpleTypes.Sql99.integerType());

        Table aggLineClass = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        aggLineClass.setName("agg_line_class");
        aggLineClass.getFeature().add(lineClassIdAggLineClass);
        aggLineClass.getFeature().add(unitSalesAggLineClass);
        aggLineClass.getFeature().add(factCountAggLineClass);

        // Create TableSource for fooFact with aggregation tables
        TableSource fooFactQuery = SourceFactory.eINSTANCE.createTableSource();
        fooFactQuery.setTable(fooFact);

        // Create aggregation definitions
        // Aggregation 1: agg_tenant
        AggregationColumnName aggTenantFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
        aggTenantFactCount.setColumn(factCountAggTenant);

        AggregationMeasure aggTenantMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
        aggTenantMeasure.setName("[Measures].[Unit Sales]");
        aggTenantMeasure.setColumn(unitSalesAggTenant);

        AggregationLevel aggTenantLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
        aggTenantLevel.setName("[dimension].[tenant].[tenant]");
        aggTenantLevel.setColumn(tenantIdAggTenant);
        aggTenantLevel.setCollapsed(false);

        ExplicitAggregationTable aggName1 = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
        aggName1.setTable(aggTenant);
        aggName1.setAggregationFactCount(aggTenantFactCount);
        aggName1.getAggregationMeasures().add(aggTenantMeasure);
        aggName1.getAggregationLevels().add(aggTenantLevel);

        // Aggregation 2: agg_line_class for distributor
        AggregationColumnName aggLineClassFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
        aggLineClassFactCount.setColumn(factCountAggLineClass);

        AggregationMeasure aggLineClassMeasure1 = AggregationFactory.eINSTANCE.createAggregationMeasure();
        aggLineClassMeasure1.setName("[Measures].[Unit Sales]");
        aggLineClassMeasure1.setColumn(unitSalesAggLineClass);

        AggregationLevel aggLineClassLevel1 = AggregationFactory.eINSTANCE.createAggregationLevel();
        aggLineClassLevel1.setName("[dimension].[distributor].[line class]");
        aggLineClassLevel1.setColumn(lineClassIdAggLineClass);
        aggLineClassLevel1.setCollapsed(false);

        ExplicitAggregationTable aggName2 = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
        aggName2.setTable(aggLineClass);
        aggName2.setAggregationFactCount(aggLineClassFactCount);
        aggName2.getAggregationMeasures().add(aggLineClassMeasure1);
        aggName2.getAggregationLevels().add(aggLineClassLevel1);

        // Aggregation 3: agg_line_class for network
        AggregationColumnName aggLineClassFactCount2 = AggregationFactory.eINSTANCE.createAggregationColumnName();
        aggLineClassFactCount2.setColumn(factCountAggLineClass);

        AggregationMeasure aggLineClassMeasure2 = AggregationFactory.eINSTANCE.createAggregationMeasure();
        aggLineClassMeasure2.setName("[Measures].[Unit Sales]");
        aggLineClassMeasure2.setColumn(unitSalesAggLineClass);

        AggregationLevel aggLineClassLevel2 = AggregationFactory.eINSTANCE.createAggregationLevel();
        aggLineClassLevel2.setName("[dimension].[network].[line class]");
        aggLineClassLevel2.setColumn(lineClassIdAggLineClass);
        aggLineClassLevel2.setCollapsed(false);

        ExplicitAggregationTable aggName3 = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
        aggName3.setTable(aggLineClass);
        aggName3.setAggregationFactCount(aggLineClassFactCount2);
        aggName3.getAggregationMeasures().add(aggLineClassMeasure2);
        aggName3.getAggregationLevels().add(aggLineClassLevel2);

        fooFactQuery.getAggregationTables().add(aggName1);
        fooFactQuery.getAggregationTables().add(aggName2);
        fooFactQuery.getAggregationTables().add(aggName3);

        // Create hierarchies
        // Tenant hierarchy
        TableSource lineTenantQuery = SourceFactory.eINSTANCE.createTableSource();
        lineTenantQuery.setTable(lineTenant);

        TableSource tenantQuery = SourceFactory.eINSTANCE.createTableSource();
        tenantQuery.setTable(tenant);

        JoinedQueryElement tenantJoinRight = SourceFactory.eINSTANCE.createJoinedQueryElement();
        tenantJoinRight.setKey(tenantIdTenant);
        tenantJoinRight.setSource(tenantQuery);

        JoinedQueryElement tenantLineJoinLeft = SourceFactory.eINSTANCE.createJoinedQueryElement();
        tenantLineJoinLeft.setKey(tenantIdLineTenant);
        tenantLineJoinLeft.setSource(lineTenantQuery);

        JoinSource lineTenantToTenant = SourceFactory.eINSTANCE.createJoinSource();
        lineTenantToTenant.setLeft(tenantLineJoinLeft);
        lineTenantToTenant.setRight(tenantJoinRight);

        JoinedQueryElement lineTenantJoinRight = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineTenantJoinRight.setKey(lineIdLineTenant);
        lineTenantJoinRight.setAlias("line_tenant");
        lineTenantJoinRight.setSource(lineTenantToTenant);

        TableSource lineQuery1 = SourceFactory.eINSTANCE.createTableSource();
        lineQuery1.setTable(line);

        JoinedQueryElement lineJoinLeft = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineJoinLeft.setKey(lineIdLine);
        lineJoinLeft.setSource(lineQuery1);

        JoinSource tenantHierarchyJoin = SourceFactory.eINSTANCE.createJoinSource();
        tenantHierarchyJoin.setLeft(lineJoinLeft);
        tenantHierarchyJoin.setRight(lineTenantJoinRight);

        Level tenantLevel = LevelFactory.eINSTANCE.createLevel();
        tenantLevel.setName("tenant");
        tenantLevel.setColumn(tenantIdTenant);
        tenantLevel.setNameColumn(tenantNameTenant);
        tenantLevel.setUniqueMembers(true);

        Level lineLevel1 = LevelFactory.eINSTANCE.createLevel();
        lineLevel1.setName("line");
        lineLevel1.setColumn(lineIdLine);
        lineLevel1.setNameColumn(lineNameLine);

        ExplicitHierarchy tenantHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        tenantHierarchy.setName("tenant");
        tenantHierarchy.setHasAll(true);
        tenantHierarchy.setAllMemberName("All tenants");
        tenantHierarchy.setPrimaryKey(lineIdLine);
        tenantHierarchy.setSource(tenantHierarchyJoin);
        tenantHierarchy.getLevels().add(tenantLevel);
        tenantHierarchy.getLevels().add(lineLevel1);

        // Distributor hierarchy (complex multi-level join)
        TableSource lineQuery2 = SourceFactory.eINSTANCE.createTableSource();
        lineQuery2.setTable(line);

        TableSource lineLineClassQuery = SourceFactory.eINSTANCE.createTableSource();
        lineLineClassQuery.setTable(lineLineClass);

        TableSource lineClassQuery1 = SourceFactory.eINSTANCE.createTableSource();
        lineClassQuery1.setTable(lineClass);

        TableSource lineClassDistributorQuery = SourceFactory.eINSTANCE.createTableSource();
        lineClassDistributorQuery.setTable(lineClassDistributor);

        TableSource distributorQuery = SourceFactory.eINSTANCE.createTableSource();
        distributorQuery.setTable(distributor);

        // Build distributor hierarchy join (innermost to outermost)
        JoinedQueryElement distributorJoinLeft = SourceFactory.eINSTANCE.createJoinedQueryElement();
        distributorJoinLeft.setKey(distributorIdLineClassDistributor);
        distributorJoinLeft.setSource(lineClassDistributorQuery);

        JoinedQueryElement distributorJoinRight = SourceFactory.eINSTANCE.createJoinedQueryElement();
        distributorJoinRight.setKey(distributorIdDistributor);
        distributorJoinRight.setSource(distributorQuery);

        JoinSource lineClassDistributorToDistributor = SourceFactory.eINSTANCE.createJoinSource();
        lineClassDistributorToDistributor.setLeft(distributorJoinLeft);
        lineClassDistributorToDistributor.setRight(distributorJoinRight);

        JoinedQueryElement lineClassJoinLeft = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineClassJoinLeft.setKey(lineClassIdLineClass);
        lineClassJoinLeft.setSource(lineClassQuery1);

        JoinedQueryElement lineClassDistributorJoinRight = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineClassDistributorJoinRight.setKey(lineClassIdLineClassDistributor);
        lineClassDistributorJoinRight.setAlias("line_class_distributor");
        lineClassDistributorJoinRight.setSource(lineClassDistributorToDistributor);

        JoinSource lineClassToDistributor = SourceFactory.eINSTANCE.createJoinSource();
        lineClassToDistributor.setLeft(lineClassJoinLeft);
        lineClassToDistributor.setRight(lineClassDistributorJoinRight);

        JoinedQueryElement lineLineClassJoinLeft = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineLineClassJoinLeft.setKey(lineClassIdLineLineClass);
        lineLineClassJoinLeft.setSource(lineLineClassQuery);

        JoinedQueryElement lineClassJoinRight = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineClassJoinRight.setKey(lineClassIdLineClassDistributor);
        lineClassJoinRight.setAlias("line_class");
        lineClassJoinRight.setSource(lineClassToDistributor);

        JoinSource lineLineClassToLineClass = SourceFactory.eINSTANCE.createJoinSource();
        lineLineClassToLineClass.setLeft(lineLineClassJoinLeft);
        lineLineClassToLineClass.setRight(lineClassJoinRight);

        JoinedQueryElement lineJoinLeft2 = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineJoinLeft2.setKey(lineIdLine);
        lineJoinLeft2.setSource(lineQuery2);

        JoinedQueryElement lineLineClassJoinRight = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineLineClassJoinRight.setKey(lineIdLineLineClass);
        lineLineClassJoinRight.setAlias("line_line_class");
        lineLineClassJoinRight.setSource(lineLineClassToLineClass);

        JoinSource distributorHierarchyJoin = SourceFactory.eINSTANCE.createJoinSource();
        distributorHierarchyJoin.setLeft(lineJoinLeft2);
        distributorHierarchyJoin.setRight(lineLineClassJoinRight);

        Level distributorLevel = LevelFactory.eINSTANCE.createLevel();
        distributorLevel.setName("distributor");
        distributorLevel.setColumn(distributorIdDistributor);
        distributorLevel.setNameColumn(distributorNameDistributor);

        Level lineClassLevel1 = LevelFactory.eINSTANCE.createLevel();
        lineClassLevel1.setName("line class");
        lineClassLevel1.setColumn(lineClassIdLineClass);
        lineClassLevel1.setNameColumn(lineClassNameLineClass);
        lineClassLevel1.setUniqueMembers(true);

        Level lineLevel2 = LevelFactory.eINSTANCE.createLevel();
        lineLevel2.setName("line");
        lineLevel2.setColumn(lineIdLine);
        lineLevel2.setNameColumn(lineNameLine);

        ExplicitHierarchy distributorHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        distributorHierarchy.setName("distributor");
        distributorHierarchy.setHasAll(true);
        distributorHierarchy.setAllMemberName("All distributors");
        distributorHierarchy.setPrimaryKey(lineIdLine);
        distributorHierarchy.setSource(distributorHierarchyJoin);
        distributorHierarchy.getLevels().add(distributorLevel);
        distributorHierarchy.getLevels().add(lineClassLevel1);
        distributorHierarchy.getLevels().add(lineLevel2);

        // Network hierarchy (complex multi-level join)
        TableSource lineQuery3 = SourceFactory.eINSTANCE.createTableSource();
        lineQuery3.setTable(line);

        TableSource lineLineClassQuery2 = SourceFactory.eINSTANCE.createTableSource();
        lineLineClassQuery2.setTable(lineLineClass);

        TableSource lineClassQuery2 = SourceFactory.eINSTANCE.createTableSource();
        lineClassQuery2.setTable(lineClass);

        TableSource lineClassNetworkQuery = SourceFactory.eINSTANCE.createTableSource();
        lineClassNetworkQuery.setTable(lineClassNetwork);

        TableSource networkQuery = SourceFactory.eINSTANCE.createTableSource();
        networkQuery.setTable(network);

        // Build network hierarchy join
        JoinedQueryElement lineClassNetworkJoinLeft = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineClassNetworkJoinLeft.setKey(networkIdLineClassNetwork);
        lineClassNetworkJoinLeft.setSource(lineClassNetworkQuery);

        JoinedQueryElement networkJoinRight = SourceFactory.eINSTANCE.createJoinedQueryElement();
        networkJoinRight.setKey(networkIdNetwork);
        networkJoinRight.setSource(networkQuery);

        JoinSource lineClassNetworkToNetwork = SourceFactory.eINSTANCE.createJoinSource();
        lineClassNetworkToNetwork.setLeft(lineClassNetworkJoinLeft);
        lineClassNetworkToNetwork.setRight(networkJoinRight);

        JoinedQueryElement lineClassJoinLeft2 = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineClassJoinLeft2.setKey(lineClassIdLineClass);
        lineClassJoinLeft2.setSource(lineClassQuery2);

        JoinedQueryElement lineClassNetworkJoinRight = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineClassNetworkJoinRight.setKey(lineClassIdLineClassNetwork);
        lineClassNetworkJoinRight.setAlias("line_class_network");
        lineClassNetworkJoinRight.setSource(lineClassNetworkToNetwork);

        JoinSource lineClassToNetwork = SourceFactory.eINSTANCE.createJoinSource();
        lineClassToNetwork.setLeft(lineClassJoinLeft2);
        lineClassToNetwork.setRight(lineClassNetworkJoinRight);

        JoinedQueryElement lineLineClassJoinLeft2 = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineLineClassJoinLeft2.setKey(lineClassIdLineLineClass);
        lineLineClassJoinLeft2.setSource(lineLineClassQuery2);

        JoinedQueryElement lineClassJoinRight2 = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineClassJoinRight2.setKey(lineClassIdLineClass);
        lineClassJoinRight2.setAlias("line_class");
        lineClassJoinRight2.setSource(lineClassToNetwork);

        JoinSource lineLineClassToLineClass2 = SourceFactory.eINSTANCE.createJoinSource();
        lineLineClassToLineClass2.setLeft(lineLineClassJoinLeft2);
        lineLineClassToLineClass2.setRight(lineClassJoinRight2);

        JoinedQueryElement lineJoinLeft3 = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineJoinLeft3.setKey(lineIdLine);
        lineJoinLeft3.setSource(lineQuery3);

        JoinedQueryElement lineLineClassJoinRight2 = SourceFactory.eINSTANCE.createJoinedQueryElement();
        lineLineClassJoinRight2.setKey(lineIdLineLineClass);
        lineLineClassJoinRight2.setAlias("line_line_class");
        lineLineClassJoinRight2.setSource(lineLineClassToLineClass2);

        JoinSource networkHierarchyJoin = SourceFactory.eINSTANCE.createJoinSource();
        networkHierarchyJoin.setLeft(lineJoinLeft3);
        networkHierarchyJoin.setRight(lineLineClassJoinRight2);

        Level networkLevel = LevelFactory.eINSTANCE.createLevel();
        networkLevel.setName("network");
        networkLevel.setColumn(networkIdNetwork);
        networkLevel.setNameColumn(networkNameNetwork);

        Level lineClassLevel2 = LevelFactory.eINSTANCE.createLevel();
        lineClassLevel2.setName("line class");
        lineClassLevel2.setColumn(lineClassIdLineClass);
        lineClassLevel2.setNameColumn(lineClassNameLineClass);
        lineClassLevel2.setUniqueMembers(true);

        Level lineLevel3 = LevelFactory.eINSTANCE.createLevel();
        lineLevel3.setName("line");
        lineLevel3.setColumn(lineIdLine);
        lineLevel3.setNameColumn(lineNameLine);

        ExplicitHierarchy networkHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        networkHierarchy.setName("network");
        networkHierarchy.setHasAll(true);
        networkHierarchy.setAllMemberName("All networks");
        networkHierarchy.setPrimaryKey(lineIdLine);
        networkHierarchy.setSource(networkHierarchyJoin);
        networkHierarchy.getLevels().add(networkLevel);
        networkHierarchy.getLevels().add(lineClassLevel2);
        networkHierarchy.getLevels().add(lineLevel3);

        // Create dimension
        StandardDimension dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        dimension.setName("dimension");
        dimension.getHierarchies().add(tenantHierarchy);
        dimension.getHierarchies().add(distributorHierarchy);
        dimension.getHierarchies().add(networkHierarchy);

        // Create dimension connector
        DimensionConnector dimensionConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        dimensionConnector.setOverrideDimensionName("dimension");
        dimensionConnector.setForeignKey(lineIdFooFact);
        dimensionConnector.setDimension(dimension);

        // Create measure
        SumMeasure unitSalesMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        unitSalesMeasure.setName("Unit Sales");
        unitSalesMeasure.setColumn(unitSalesFooFact);
        unitSalesMeasure.setFormatString("Standard");

        // Create measure group
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(unitSalesMeasure);

        // Create cube
        PhysicalCube testSsasCube = CubeFactory.eINSTANCE.createPhysicalCube();
        testSsasCube.setName("testSsas");
        testSsasCube.setSource(fooFactQuery);
        testSsasCube.getDimensionConnectors().add(dimensionConnector);
        testSsasCube.getMeasureGroups().add(measureGroup);

        // Add cube to catalog
        this.catalog.getImportedElement().add(testSsasCube);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

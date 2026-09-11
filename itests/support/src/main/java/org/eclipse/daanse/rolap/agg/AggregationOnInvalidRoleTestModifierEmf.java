/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.agg;


import static org.eclipse.daanse.rolap.mapping.model.provider.util.Expressions.mdx;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.util.SQLSimpleTypes;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.model.access.common.AccessCatalogGrant;
import org.eclipse.daanse.rolap.mapping.model.access.common.AccessRole;
import org.eclipse.daanse.rolap.mapping.model.access.common.CatalogAccess;
import org.eclipse.daanse.rolap.mapping.model.access.common.CommonFactory;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessCubeGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessHierarchyGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.AccessMemberGrant;
import org.eclipse.daanse.rolap.mapping.model.access.olap.CubeAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.HierarchyAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.MemberAccess;
import org.eclipse.daanse.rolap.mapping.model.access.olap.OlapFactory;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
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
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
public class AggregationOnInvalidRoleTestModifierEmf implements CatalogMappingSupplier {

    protected final Catalog catalog;

    // mondrian2225_customer table columns
    protected Column customerIdMondrian2225Customer;
    protected Column customerNameMondrian2225Customer;
    protected Table mondrian2225Customer;

    // mondrian2225_fact table columns
    protected Column productIdMondrian2225Fact;
    protected Column customerIdMondrian2225Fact;
    protected Column factMondrian2225Fact;
    protected Table mondrian2225Fact;

    // mondrian2225_dim table columns
    protected Column productIdMondrian2225Dim;
    protected Column productCodeMondrian2225Dim;
    protected Table mondrian2225Dim;

    // mondrian2225_agg table columns
    protected Column dimCodeMondrian2225Agg;
    protected Column factMeasureMondrian2225Agg;
    protected Column factCountMondrian2225Agg;
    protected Table mondrian2225Agg;

    // Dimensions
    protected StandardDimension customerDimension;
    protected StandardDimension productCodeDimension;

    // Hierarchies and levels
    protected ExplicitHierarchy customerHierarchy;
    protected Level firstNameLevel;

    // Measures
    protected SumMeasure measureMeasure;

    // Cube
    protected PhysicalCube mondrian2225Cube;

    public AggregationOnInvalidRoleTestModifierEmf(Catalog catalogMapping) {
        this.catalog = EmfUtil.copy((CatalogImpl) catalogMapping);
        createTables();
        createDimensions();
        createMeasures();
        createCube();
        createAccessRoles();
    }

    /*
      + "<Cube name=\"mondrian2225\" visible=\"true\" cache=\"true\" enabled=\"true\">"
        + "  <Table name=\"mondrian2225_fact\">"
        + "    <AggName name=\"mondrian2225_agg\" ignorecase=\"true\">"
        + "      <AggFactCount column=\"fact_count\"/>"
        + "      <AggMeasure column=\"fact_Measure\" name=\"[Measures].[Measure]\"/>"
        + "      <AggLevel column=\"dim_code\" name=\"[Product Code].[Code]\" collapsed=\"true\"/>"
        + "    </AggName>"
        + "  </Table>"
        + "  <Dimension type=\"StandardDimension\" visible=\"true\" foreignKey=\"customer_id\" highCardinality=\"false\" name=\"Customer\">"
        + "    <Hierarchy name=\"Customer\" visible=\"true\" hasAll=\"true\" primaryKey=\"customer_id\">"
        + "      <Table name=\"mondrian2225_customer\"/>"
        + "        <Level name=\"First Name\" visible=\"true\" column=\"customer_name\" type=\"String\" uniqueMembers=\"false\" levelType=\"Regular\" hideMemberIf=\"Never\"/>"
        + "    </Hierarchy>"
        + "  </Dimension>"
        + "  <Dimension type=\"StandardDimension\" visible=\"true\" foreignKey=\"product_ID\" highCardinality=\"false\" name=\"Product Code\">"
        + "    <Hierarchy name=\"Product Code\" visible=\"true\" hasAll=\"true\" primaryKey=\"product_id\">"
        + "      <Table name=\"mondrian2225_dim\"/>"
        + "      <Level name=\"Code\" visible=\"true\" column=\"product_code\" type=\"String\" uniqueMembers=\"false\" levelType=\"Regular\" hideMemberIf=\"Never\"/>"
        + "    </Hierarchy>"
        + "  </Dimension>"
        + "  <Measure name=\"Measure\" column=\"fact\" aggregator=\"sum\" visible=\"true\"/>"
        + "</Cube>";
     */

    protected void createTables() {
        // Create mondrian2225_customer table columns
        customerIdMondrian2225Customer = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        customerIdMondrian2225Customer.setName("customer_id");
        customerIdMondrian2225Customer.setType(SQLSimpleTypes.Sql99.integerType());

        customerNameMondrian2225Customer = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        customerNameMondrian2225Customer.setName("customer_name");
        customerNameMondrian2225Customer.setType(SQLSimpleTypes.varcharType(255));
        // customerNameMondrian2225Customer.setCharOctetLength(45);

        mondrian2225Customer = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        mondrian2225Customer.setName("mondrian2225_customer");
        mondrian2225Customer.getFeature().add(customerIdMondrian2225Customer);
        mondrian2225Customer.getFeature().add(customerNameMondrian2225Customer);

        // Create mondrian2225_fact table columns
        productIdMondrian2225Fact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        productIdMondrian2225Fact.setName("product_ID");
        productIdMondrian2225Fact.setType(SQLSimpleTypes.Sql99.integerType());

        customerIdMondrian2225Fact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        customerIdMondrian2225Fact.setName("customer_id");
        customerIdMondrian2225Fact.setType(SQLSimpleTypes.Sql99.integerType());

        factMondrian2225Fact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        factMondrian2225Fact.setName("fact");
        factMondrian2225Fact.setType(SQLSimpleTypes.Sql99.integerType());

        mondrian2225Fact = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        mondrian2225Fact.setName("mondrian2225_fact");
        mondrian2225Fact.getFeature().add(productIdMondrian2225Fact);
        mondrian2225Fact.getFeature().add(customerIdMondrian2225Fact);
        mondrian2225Fact.getFeature().add(factMondrian2225Fact);

        // Create mondrian2225_dim table columns
        productIdMondrian2225Dim = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        productIdMondrian2225Dim.setName("product_id");
        productIdMondrian2225Dim.setType(SQLSimpleTypes.Sql99.integerType());

        productCodeMondrian2225Dim = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        productCodeMondrian2225Dim.setName("product_code");
        productCodeMondrian2225Dim.setType(SQLSimpleTypes.varcharType(255));
        // productCodeMondrian2225Dim.setCharOctetLength(45);

        mondrian2225Dim = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        mondrian2225Dim.setName("mondrian2225_dim");
        mondrian2225Dim.getFeature().add(productIdMondrian2225Dim);
        mondrian2225Dim.getFeature().add(productCodeMondrian2225Dim);

        // Create mondrian2225_agg table columns
        dimCodeMondrian2225Agg = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        dimCodeMondrian2225Agg.setName("dim_code");
        dimCodeMondrian2225Agg.setType(SQLSimpleTypes.varcharType(255));
        // dimCodeMondrian2225Agg.setCharOctetLength(45);
        // setNullable removed (CWM Column has isNullable enum): dimCodeMondrian2225Agg true

        factMeasureMondrian2225Agg = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        factMeasureMondrian2225Agg.setName("fact_measure");
        factMeasureMondrian2225Agg.setType(SQLSimpleTypes.decimalType(18, 4));
        // factMeasureMondrian2225Agg.setColumnSize(10);
        // factMeasureMondrian2225Agg.setDecimalDigits(2);

        factCountMondrian2225Agg = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createColumn();
        factCountMondrian2225Agg.setName("fact_count");
        factCountMondrian2225Agg.setType(SQLSimpleTypes.Sql99.integerType());

        mondrian2225Agg = org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory.eINSTANCE.createTable();
        mondrian2225Agg.setName("mondrian2225_agg");
        mondrian2225Agg.getFeature().add(dimCodeMondrian2225Agg);
        mondrian2225Agg.getFeature().add(factMeasureMondrian2225Agg);
        mondrian2225Agg.getFeature().add(factCountMondrian2225Agg);

        // Add tables to database schema
        if (Packages.available(catalog, Schema.class).size() > 0) {
            Schema dbSchema = Packages.available(catalog, Schema.class).get(0);
            dbSchema.getOwnedElement().add(mondrian2225Customer);
            dbSchema.getOwnedElement().add(mondrian2225Fact);
            dbSchema.getOwnedElement().add(mondrian2225Dim);
            dbSchema.getOwnedElement().add(mondrian2225Agg);
        }
    }

    protected void createDimensions() {
        // Create Customer dimension
        customerDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        customerDimension.setName("Customer");

        // Create Customer hierarchy
        TableSource customerQuery = SourceFactory.eINSTANCE.createTableSource();
        customerQuery.setTable(mondrian2225Customer);

        firstNameLevel = LevelFactory.eINSTANCE.createLevel();
        firstNameLevel.setName("First Name");
        firstNameLevel.setVisible(true);
        firstNameLevel.setColumn(customerNameMondrian2225Customer);
        firstNameLevel.setColumnType(ColumnInternalDataType.STRING);
        firstNameLevel.setUniqueMembers(false);
        firstNameLevel.setType(LevelDefinition.REGULAR);
        firstNameLevel.setHideMemberIf(HideMemberIf.NEVER);

        customerHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        customerHierarchy.setName("Customer");
        customerHierarchy.setVisible(true);
        customerHierarchy.setHasAll(true);
        customerHierarchy.setPrimaryKey(customerIdMondrian2225Customer);
        customerHierarchy.setSource(customerQuery);
        customerHierarchy.getLevels().add(firstNameLevel);

        customerDimension.getHierarchies().add(customerHierarchy);

        // Create Product Code dimension
        productCodeDimension = DimensionFactory.eINSTANCE.createStandardDimension();
        productCodeDimension.setName("Product Code");

        // Create Product Code hierarchy
        TableSource productCodeQuery = SourceFactory.eINSTANCE.createTableSource();
        productCodeQuery.setTable(mondrian2225Dim);

        Level codeLevel = LevelFactory.eINSTANCE.createLevel();
        codeLevel.setName("Code");
        codeLevel.setVisible(true);
        codeLevel.setColumn(productCodeMondrian2225Dim);
        codeLevel.setColumnType(ColumnInternalDataType.STRING);
        codeLevel.setUniqueMembers(false);
        codeLevel.setType(LevelDefinition.REGULAR);
        codeLevel.setHideMemberIf(HideMemberIf.NEVER);

        ExplicitHierarchy productCodeHierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        productCodeHierarchy.setName("Product Code");
        productCodeHierarchy.setVisible(true);
        productCodeHierarchy.setHasAll(true);
        productCodeHierarchy.setPrimaryKey(productIdMondrian2225Dim);
        productCodeHierarchy.setSource(productCodeQuery);
        productCodeHierarchy.getLevels().add(codeLevel);

        productCodeDimension.getHierarchies().add(productCodeHierarchy);
    }

    protected void createMeasures() {
        measureMeasure = MeasureFactory.eINSTANCE.createSumMeasure();
        measureMeasure.setName("Measure");
        measureMeasure.setColumn(factMondrian2225Fact);
        measureMeasure.setVisible(true);
    }

    protected void createCube() {
        // Create table query with aggregation
        TableSource tableQuery = SourceFactory.eINSTANCE.createTableSource();
        tableQuery.setTable(mondrian2225Fact);

        // Create aggregation
        ExplicitAggregationTable aggName = AggregationFactory.eINSTANCE.createExplicitAggregationTable();
        aggName.setTable(mondrian2225Agg);
        aggName.setIgnorecase(true);

        // Aggregation fact count
        AggregationColumnName aggFactCount = AggregationFactory.eINSTANCE.createAggregationColumnName();
        aggFactCount.setColumn(factCountMondrian2225Agg);
        aggName.setAggregationFactCount(aggFactCount);

        // Aggregation measure
        AggregationMeasure aggMeasure = AggregationFactory.eINSTANCE.createAggregationMeasure();
        aggMeasure.setColumn(factMeasureMondrian2225Agg);
        aggMeasure.setName("[Measures].[Measure]");
        aggName.getAggregationMeasures().add(aggMeasure);

        // Aggregation level
        AggregationLevel aggLevel = AggregationFactory.eINSTANCE.createAggregationLevel();
        aggLevel.setColumn(dimCodeMondrian2225Agg);
        aggLevel.setName("[Product Code].[Product Code].[Code]");
        aggLevel.setCollapsed(true);
        aggName.getAggregationLevels().add(aggLevel);

        tableQuery.getAggregationTables().add(aggName);

        // Create dimension connectors
        DimensionConnector customerConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        customerConnector.setDimension(customerDimension);
        customerConnector.setOverrideDimensionName("Customer");
        customerConnector.setVisible(true);
        customerConnector.setForeignKey(customerIdMondrian2225Fact);

        DimensionConnector productCodeConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        productCodeConnector.setDimension(productCodeDimension);
        productCodeConnector.setOverrideDimensionName("Product Code");
        productCodeConnector.setVisible(true);
        productCodeConnector.setForeignKey(productIdMondrian2225Fact);

        // Create measure group
        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().add(measureMeasure);

        // Create cube
        mondrian2225Cube = CubeFactory.eINSTANCE.createPhysicalCube();
        mondrian2225Cube.setName("mondrian2225");
        mondrian2225Cube.setVisible(true);
        mondrian2225Cube.setCache(true);
        mondrian2225Cube.setEnabled(true);
        mondrian2225Cube.setSource(tableQuery);
        mondrian2225Cube.getDimensionConnectors().add(customerConnector);
        mondrian2225Cube.getDimensionConnectors().add(productCodeConnector);
        mondrian2225Cube.getMeasureGroups().add(measureGroup);

        // Add cube to catalog
        catalog.getImportedElement().add(mondrian2225Cube);
    }

    /*
            + "<Role name=\"Test\">"
        + "  <SchemaGrant access=\"none\">"
        + "    <CubeGrant cube=\"mondrian2225\" access=\"all\">"
        + "      <HierarchyGrant hierarchy=\"[Customer.Customer]\" topLevel=\"[Customer.Customer].[First Name]\" access=\"custom\">"
        + "        <MemberGrant member=\"[Customer.Customer].[NonExistingName]\" access=\"all\"/>"
        + "      </HierarchyGrant>"
        + "    </CubeGrant>"
        + "  </SchemaGrant>"
        + "</Role>";

     */

    protected void createAccessRoles() {
        // Create member grant
        AccessMemberGrant memberGrant = OlapFactory.eINSTANCE.createAccessMemberGrant();
        memberGrant.setMember(mdx("[Customer].[Customer].[NonExistingName]"));
        memberGrant.setMemberAccess(MemberAccess.ALL);

        // Create hierarchy grant
        AccessHierarchyGrant hierarchyGrant = OlapFactory.eINSTANCE.createAccessHierarchyGrant();
        hierarchyGrant.setHierarchy(customerHierarchy);
        hierarchyGrant.setTopLevel(firstNameLevel);
        hierarchyGrant.setHierarchyAccess(HierarchyAccess.CUSTOM);
        hierarchyGrant.getMemberGrants().add(memberGrant);

        // Create cube grant
        AccessCubeGrant cubeGrant = OlapFactory.eINSTANCE.createAccessCubeGrant();
        cubeGrant.setCube(mondrian2225Cube);
        cubeGrant.setCubeAccess(CubeAccess.ALL);
        cubeGrant.getHierarchyGrants().add(hierarchyGrant);

        // Create catalog grant
        AccessCatalogGrant catalogGrant = CommonFactory.eINSTANCE.createAccessCatalogGrant();
        catalogGrant.setCatalogAccess(CatalogAccess.NONE);
        catalogGrant.getCubeGrants().add(cubeGrant);

        // Create role
        AccessRole role = CommonFactory.eINSTANCE.createAccessRole();
        role.setName("Test");
        role.getAccessCatalogGrants().add(catalogGrant);

        // Add role to catalog
        catalog.getImportedElement().add(role);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

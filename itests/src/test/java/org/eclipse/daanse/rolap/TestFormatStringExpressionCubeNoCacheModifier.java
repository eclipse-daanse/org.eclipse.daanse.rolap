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
package org.eclipse.daanse.rolap;

import static org.eclipse.daanse.rolap.mapping.model.provider.util.Expressions.mdx;
import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.MeasureGroup;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.VirtualCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.BaseMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MeasureFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.SumMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.Dimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.CalculatedMember;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.CalculatedMemberProperty;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.EcoreUtil;
/**
 * EMF version of TestFormatStringExpressionCubeNoCacheModifier from
 * VirtualCubeTest. Creates a physical cube "Warehouse No Cache" and a virtual
 * cube "Warehouse and Sales Format Expression Cube No Cache" with calculated
 * members that use format string expressions. Uses objects from
 * CatalogSupplier.
 *
 * <Cube name="Warehouse No Cache" cache="false">
 * <Table name="inventory_fact_1997"/>
 * <DimensionUsage name="Time" source="Time" foreignKey="time_id"/>
 * <DimensionUsage name="Store" source="Store" foreignKey="store_id"/>
 * <Measure name="Units Shipped" column="units_shipped" aggregator="sum"
 * formatString="#.0"/> </Cube>
 * <VirtualCube name="Warehouse and Sales Format Expression Cube No Cache">
 * <VirtualCubeDimension name="Store"/> <VirtualCubeDimension name="Time"/>
 * <VirtualCubeMeasure cubeName="Sales" name="[Measures].[Store Cost]"/>
 * <VirtualCubeMeasure cubeName="Sales" name="[Measures].[Store Sales]"/>
 * <VirtualCubeMeasure cubeName="Warehouse No Cache" name="[Measures].[Units
 * Shipped]"/> <CalculatedMember name="Profit" dimension="Measures">
 * <Formula>[Measures].[Store Sales] - [Measures].[Store Cost]</Formula>
 * </CalculatedMember>
 * <CalculatedMember name="Profit Per Unit Shipped" dimension="Measures">
 * <Formula>[Measures].[Profit] / [Measures].[Units Shipped]</Formula>
 * <CalculatedMemberProperty name="FORMAT_STRING" expression=
 * "IIf(([Measures].[Profit Per Unit Shipped] > 2.0), '|0.#|style=green',
 * '|0.#|style=red')"/> </CalculatedMember> </VirtualCube>
 */
public class TestFormatStringExpressionCubeNoCacheModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    public TestFormatStringExpressionCubeNoCacheModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) baseCatalog);
        this.catalog = (Catalog) copier.get(baseCatalog);
        // Static table query
        TableSource TABLE_QUERY_INVENTORY_FACT;

        // Static measure for Warehouse No Cache cube
        SumMeasure MEASURE_UNITS_SHIPPED_LOCAL;
        MeasureGroup MEASURE_GROUP_WAREHOUSE_NO_CACHE;

        // Static dimension connectors for physical cube
        DimensionConnector CONNECTOR_TIME;
        DimensionConnector CONNECTOR_STORE;

        // Static physical cube
        PhysicalCube CUBE_WAREHOUSE_NO_CACHE;

        // Static dimension connectors for virtual cube
        DimensionConnector VC_CONNECTOR_STORE;
        DimensionConnector VC_CONNECTOR_TIME;

        // Static calculated members
        CalculatedMember CALCULATED_MEMBER_PROFIT;
        CalculatedMemberProperty PROPERTY_FORMAT_STRING;
        CalculatedMember CALCULATED_MEMBER_PROFIT_PER_UNIT_SHIPPED;

        // Static virtual cube
        VirtualCube VIRTUAL_CUBE_WAREHOUSE_AND_SALES_FORMAT_EXPRESSION;

        // Create table query
        TABLE_QUERY_INVENTORY_FACT = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_INVENTORY_FACT.setTable((Table) copier.get(CatalogSupplier.TABLE_INVENTORY_FACT));

        // Create Units Shipped measure for Warehouse No Cache cube
        MEASURE_UNITS_SHIPPED_LOCAL = MeasureFactory.eINSTANCE.createSumMeasure();
        MEASURE_UNITS_SHIPPED_LOCAL.setName("Units Shipped");
        MEASURE_UNITS_SHIPPED_LOCAL.setColumn((Column) copier.get(CatalogSupplier.COLUMN_UNITS_SHIPPED_INVENTORY_FACT));
        MEASURE_UNITS_SHIPPED_LOCAL.setFormatString("#.0");

        // Create measure group
        MEASURE_GROUP_WAREHOUSE_NO_CACHE = CubeFactory.eINSTANCE.createMeasureGroup();
        MEASURE_GROUP_WAREHOUSE_NO_CACHE.getMeasures().add(MEASURE_UNITS_SHIPPED_LOCAL);

        // Create dimension connectors for physical cube
        CONNECTOR_TIME = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_TIME.setOverrideDimensionName("Time");
        CONNECTOR_TIME.setDimension((Dimension) copier.get(CatalogSupplier.DIMENSION_TIME));
        CONNECTOR_TIME.setForeignKey((Column) copier.get(CatalogSupplier.COLUMN_TIME_ID_INVENTORY_FACT));

        CONNECTOR_STORE = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_STORE.setOverrideDimensionName("Store");
        CONNECTOR_STORE.setDimension((Dimension) copier.get(CatalogSupplier.DIMENSION_STORE));
        CONNECTOR_STORE.setForeignKey((Column) copier.get(CatalogSupplier.COLUMN_STORE_ID_INVENTORY_FACT));

        // Create physical cube "Warehouse No Cache"
        CUBE_WAREHOUSE_NO_CACHE = CubeFactory.eINSTANCE.createPhysicalCube();
        CUBE_WAREHOUSE_NO_CACHE.setName("Warehouse No Cache");
        CUBE_WAREHOUSE_NO_CACHE.setSource(TABLE_QUERY_INVENTORY_FACT);
        CUBE_WAREHOUSE_NO_CACHE.getDimensionConnectors().addAll(List.of(CONNECTOR_TIME, CONNECTOR_STORE));
        CUBE_WAREHOUSE_NO_CACHE.getMeasureGroups().add(MEASURE_GROUP_WAREHOUSE_NO_CACHE);

        // Create dimension connectors for virtual cube
        VC_CONNECTOR_STORE = DimensionFactory.eINSTANCE.createDimensionConnector();
        VC_CONNECTOR_STORE.setOverrideDimensionName("Store");
        VC_CONNECTOR_STORE.setDimension((Dimension) copier.get(CatalogSupplier.DIMENSION_STORE));

        VC_CONNECTOR_TIME = DimensionFactory.eINSTANCE.createDimensionConnector();
        VC_CONNECTOR_TIME.setOverrideDimensionName("Time");
        VC_CONNECTOR_TIME.setDimension((Dimension) copier.get(CatalogSupplier.DIMENSION_TIME));

        // Create calculated member: Profit
        CALCULATED_MEMBER_PROFIT = LevelFactory.eINSTANCE.createCalculatedMember();
        CALCULATED_MEMBER_PROFIT.setName("Profit");
        CALCULATED_MEMBER_PROFIT.setFormula(mdx("[Measures].[Store Sales] - [Measures].[Store Cost]"));

        // Create calculated member property: FORMAT_STRING
        PROPERTY_FORMAT_STRING = LevelFactory.eINSTANCE.createCalculatedMemberProperty();
        PROPERTY_FORMAT_STRING.setName("FORMAT_STRING");
        PROPERTY_FORMAT_STRING.setExpression(
                mdx("IIf(([Measures].[Profit Per Unit Shipped] > 2.0), '|0.#|style=green', '|0.#|style=red')"));

        // Create calculated member: Profit Per Unit Shipped
        CALCULATED_MEMBER_PROFIT_PER_UNIT_SHIPPED = LevelFactory.eINSTANCE.createCalculatedMember();
        CALCULATED_MEMBER_PROFIT_PER_UNIT_SHIPPED.setName("Profit Per Unit Shipped");
        CALCULATED_MEMBER_PROFIT_PER_UNIT_SHIPPED.setFormula(mdx("[Measures].[Profit] / [Measures].[Units Shipped]"));
        CALCULATED_MEMBER_PROFIT_PER_UNIT_SHIPPED.getCalculatedMemberProperties().add(PROPERTY_FORMAT_STRING);

        // Create virtual cube
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_FORMAT_EXPRESSION = CubeFactory.eINSTANCE.createVirtualCube();
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_FORMAT_EXPRESSION
                .setName("Warehouse and Sales Format Expression Cube No Cache");

        // Add dimension connectors
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_FORMAT_EXPRESSION.getDimensionConnectors()
                .addAll(List.of(VC_CONNECTOR_STORE, VC_CONNECTOR_TIME));

        // Add referenced measures
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_FORMAT_EXPRESSION.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_STORE_COST));
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_FORMAT_EXPRESSION.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_STORE_SALES));
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_FORMAT_EXPRESSION.getReferencedMeasures().add((BaseMeasure)  MEASURE_UNITS_SHIPPED_LOCAL);

        // Add calculated members
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES_FORMAT_EXPRESSION.getCalculatedMembers()
                .addAll(List.of(CALCULATED_MEMBER_PROFIT, CALCULATED_MEMBER_PROFIT_PER_UNIT_SHIPPED));

        // Add the physical cube and virtual cube to the catalog
        this.catalog.getImportedElement().add(CUBE_WAREHOUSE_NO_CACHE);
        this.catalog.getImportedElement().add(VIRTUAL_CUBE_WAREHOUSE_AND_SALES_FORMAT_EXPRESSION);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

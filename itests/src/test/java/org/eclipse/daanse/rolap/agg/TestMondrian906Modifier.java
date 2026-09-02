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

import static org.eclipse.daanse.rolap.mapping.model.provider.util.Expressions.mdx;
import java.util.List;

import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
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
import org.eclipse.daanse.rolap.mapping.model.olap.cube.Cube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.VirtualCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.BaseMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.Hierarchy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.RollupPolicy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Member;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;
/**
 * EMF version of TestMondrian906Modifier from AggregationOnDistinctCountMeasuresTest.
 * Creates two virtual cubes "Warehouse and Sales2" and "Warehouse and Sales3" with access roles.
 *
 * Virtual Cube 1: "Warehouse and Sales2"
 * - Dimensions: Gender, Store, Product, Warehouse
 * - Measures: Store Sales, Customer Count
 * - Default Measure: Store Sales
 *
 * Virtual Cube 2: "Warehouse and Sales3"
 * - Cube Usage: Sales (with ignoreUnrelatedDimensions=true)
 * - Dimensions: Gender, Store, Product, Warehouse
 * - Measures: Customer Count
 * - Default Measure: Store Invoice (not defined in this schema)
 *
 * Access Role: "Role1"
 * - Grants access to Sales cube with custom hierarchy access for Customers
 * - Allows access to [Customers].[USA].[OR] and [Customers].[USA].[WA]
 * - Rollup policy: PARTIAL
 */
public class TestMondrian906Modifier implements CatalogMappingSupplier {

    private final Catalog catalog;


    static {
    }

    public TestMondrian906Modifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) baseCatalog);
        this.catalog = (Catalog) copier.get(baseCatalog);

        // Static virtual cubes
        VirtualCube VIRTUAL_CUBE_WAREHOUSE_AND_SALES2;
        VirtualCube VIRTUAL_CUBE_WAREHOUSE_AND_SALES3;

        // Static dimension connectors for Virtual Cube 1
        //DimensionConnector CONNECTOR_VC1_GENDER;
        //DimensionConnector CONNECTOR_VC1_STORE;
        //DimensionConnector CONNECTOR_VC1_PRODUCT;
        //DimensionConnector CONNECTOR_VC1_WAREHOUSE;

        // Static dimension connectors for Virtual Cube 2
        //DimensionConnector CONNECTOR_VC2_GENDER;
        //DimensionConnector CONNECTOR_VC2_STORE;
        //DimensionConnector CONNECTOR_VC2_PRODUCT;
        //DimensionConnector CONNECTOR_VC2_WAREHOUSE;

        // Static cube connector for Virtual Cube 2
        CubeConnector CUBE_CONNECTOR_SALES;

        // Static access role components
        AccessMemberGrant MEMBER_GRANT_OR;
        AccessMemberGrant MEMBER_GRANT_WA;
        AccessHierarchyGrant HIERARCHY_GRANT_CUSTOMERS;
        AccessCubeGrant CUBE_GRANT_SALES;
        AccessCatalogGrant CATALOG_GRANT;
        AccessRole ACCESS_ROLE_ROLE1;


        // Create dimension connectors for Virtual Cube 1: "Warehouse and Sales2"
        //CONNECTOR_VC1_GENDER = DimensionFactory.eINSTANCE.createDimensionConnector();
        //CONNECTOR_VC1_GENDER.setOverrideDimensionName("Gender");
        //CONNECTOR_VC1_GENDER.setPhysicalCube(CatalogSupplier.CUBE_SALES);
        //CONNECTOR_VC1_GENDER.setDimension(CatalogSupplier.DIMENSION_GENDER);

        //CONNECTOR_VC1_STORE = DimensionFactory.eINSTANCE.createDimensionConnector();
        //CONNECTOR_VC1_STORE.setOverrideDimensionName("Store");
        //CONNECTOR_VC1_STORE.setPhysicalCube(CatalogSupplier.CUBE_SALES);
        //CONNECTOR_VC1_STORE.setDimension(CatalogSupplier.DIMENSION_STORE);

        //CONNECTOR_VC1_PRODUCT = DimensionFactory.eINSTANCE.createDimensionConnector();
        //CONNECTOR_VC1_PRODUCT.setOverrideDimensionName("Product");
        //CONNECTOR_VC1_PRODUCT.setPhysicalCube(CatalogSupplier.CUBE_SALES);
        //CONNECTOR_VC1_PRODUCT.setDimension(CatalogSupplier.DIMENSION_PRODUCT);

        //CONNECTOR_VC1_WAREHOUSE = DimensionFactory.eINSTANCE.createDimensionConnector();
        //CONNECTOR_VC1_WAREHOUSE.setOverrideDimensionName("Warehouse");
        //CONNECTOR_VC1_WAREHOUSE.setPhysicalCube(CatalogSupplier.CUBE_WAREHOUSE);
        //CONNECTOR_VC1_WAREHOUSE.setDimension(CatalogSupplier.DIMENSION_WAREHOUSE);

        // Create Virtual Cube 1: "Warehouse and Sales2"
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES2 = CubeFactory.eINSTANCE.createVirtualCube();
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES2.setName("Warehouse and Sales2");
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES2.setDefaultMeasure((org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.MemberLike) copier.get(CatalogSupplier.MEASURE_STORE_SALES));
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES2.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_GENDER));
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES2.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_STORE));
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES2.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_PRODUCT));
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES2.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_WAREHOUSE_WAREHOUSE));

        VIRTUAL_CUBE_WAREHOUSE_AND_SALES2.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_STORE_SALES));
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES2.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_CUSTOMER_COUNT));

        // Create cube connector for Virtual Cube 2
        CUBE_CONNECTOR_SALES = CubeFactory.eINSTANCE.createCubeConnector();
        CUBE_CONNECTOR_SALES.setCube((Cube) copier.get(CatalogSupplier.CUBE_SALES));
        CUBE_CONNECTOR_SALES.setIgnoreUnrelatedDimensions(true);

        // Create dimension connectors for Virtual Cube 2: "Warehouse and Sales3"
        //CONNECTOR_VC2_GENDER = DimensionFactory.eINSTANCE.createDimensionConnector();
        //CONNECTOR_VC2_GENDER.setOverrideDimensionName("Gender");
        //CONNECTOR_VC2_GENDER.setPhysicalCube(CatalogSupplier.CUBE_SALES);

        //CONNECTOR_VC2_STORE = DimensionFactory.eINSTANCE.createDimensionConnector();
        //CONNECTOR_VC2_STORE.setOverrideDimensionName("Store");

        //CONNECTOR_VC2_PRODUCT = DimensionFactory.eINSTANCE.createDimensionConnector();
        //CONNECTOR_VC2_PRODUCT.setOverrideDimensionName("Product");

        //CONNECTOR_VC2_WAREHOUSE = DimensionFactory.eINSTANCE.createDimensionConnector();
        //CONNECTOR_VC2_WAREHOUSE.setOverrideDimensionName("Warehouse");
        //CONNECTOR_VC2_WAREHOUSE.setPhysicalCube(CatalogSupplier.CUBE_WAREHOUSE);

        // Create Virtual Cube 2: "Warehouse and Sales3"
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES3 = CubeFactory.eINSTANCE.createVirtualCube();
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES3.setName("Warehouse and Sales3");
        // Note: defaultMeasure is set to MEASURE_STORE_INVOICE which doesn't exist in CatalogSupplier
        // Keeping it null as in original POJO implementation
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES3.getCubeUsages().add(CUBE_CONNECTOR_SALES);
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES3.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_GENDER));
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES3.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_STORE));
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES3.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_PRODUCT));
        VIRTUAL_CUBE_WAREHOUSE_AND_SALES3.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_WAREHOUSE_WAREHOUSE));

        VIRTUAL_CUBE_WAREHOUSE_AND_SALES3.getReferencedMeasures().add(
                (BaseMeasure) copier.get(CatalogSupplier.MEASURE_CUSTOMER_COUNT)
        );

        // Create Access Role "Role1"
        MEMBER_GRANT_OR = OlapFactory.eINSTANCE.createAccessMemberGrant();
        MEMBER_GRANT_OR.setMember(mdx("[Customers].[USA].[OR]"));
        MEMBER_GRANT_OR.setMemberAccess(MemberAccess.ALL);

        MEMBER_GRANT_WA = OlapFactory.eINSTANCE.createAccessMemberGrant();
        MEMBER_GRANT_WA.setMember(mdx("[Customers].[USA].[WA]"));
        MEMBER_GRANT_WA.setMemberAccess(MemberAccess.ALL);

        HIERARCHY_GRANT_CUSTOMERS = OlapFactory.eINSTANCE.createAccessHierarchyGrant();
        HIERARCHY_GRANT_CUSTOMERS.setHierarchy((Hierarchy) copier.get(CatalogSupplier.HIERARCHY_CUSTOMER));
        HIERARCHY_GRANT_CUSTOMERS.setHierarchyAccess(HierarchyAccess.CUSTOM);
        HIERARCHY_GRANT_CUSTOMERS.setRollupPolicy(RollupPolicy.PARTIAL);
        HIERARCHY_GRANT_CUSTOMERS.getMemberGrants().addAll(List.of(
            MEMBER_GRANT_OR,
            MEMBER_GRANT_WA
        ));

        CUBE_GRANT_SALES = OlapFactory.eINSTANCE.createAccessCubeGrant();
        CUBE_GRANT_SALES.setCube((Cube) copier.get(CatalogSupplier.CUBE_SALES));
        CUBE_GRANT_SALES.setCubeAccess(CubeAccess.ALL);
        CUBE_GRANT_SALES.getHierarchyGrants().add(HIERARCHY_GRANT_CUSTOMERS);

        CATALOG_GRANT = CommonFactory.eINSTANCE.createAccessCatalogGrant();
        CATALOG_GRANT.setCatalogAccess(CatalogAccess.ALL);
        CATALOG_GRANT.getCubeGrants().add(CUBE_GRANT_SALES);

        ACCESS_ROLE_ROLE1 = CommonFactory.eINSTANCE.createAccessRole();
        ACCESS_ROLE_ROLE1.setName("Role1");
        ACCESS_ROLE_ROLE1.getAccessCatalogGrants().add(CATALOG_GRANT);

        // Add the virtual cubes to the catalog
        this.catalog.getImportedElement().add(VIRTUAL_CUBE_WAREHOUSE_AND_SALES2);
        this.catalog.getImportedElement().add(VIRTUAL_CUBE_WAREHOUSE_AND_SALES3);

        // Add the access role to the catalog
        this.catalog.getImportedElement().add(ACCESS_ROLE_ROLE1);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

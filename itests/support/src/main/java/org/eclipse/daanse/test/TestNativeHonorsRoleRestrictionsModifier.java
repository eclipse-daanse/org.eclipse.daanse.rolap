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
package org.eclipse.daanse.test;

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
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.RollupPolicy;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
/**
 * EMF version of TestNativeHonorsRoleRestrictionsModifier from NativeSetEvaluationTest.
 * Creates an access role "Test" with restricted access to specific battery products in the Sales cube.
 *
 * <Role name="Test">
 *   <SchemaGrant access="none">
 *     <CubeGrant cube="Sales" access="all">
 *       <HierarchyGrant hierarchy="[Product]" rollupPolicy="partial" access="custom">
 *         <MemberGrant member="[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Cormorant].[Cormorant AA-Size Batteries]" access="all"/>
 *         <MemberGrant member="[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Cormorant].[Cormorant AAA-Size Batteries]" access="all"/>
 *         <MemberGrant member="[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Cormorant].[Cormorant C-Size Batteries]" access="all"/>
 *         <MemberGrant member="[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Denny].[Denny AA-Size Batteries]" access="all"/>
 *         <MemberGrant member="[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Denny].[Denny AAA-Size Batteries]" access="all"/>
 *       </HierarchyGrant>
 *     </CubeGrant>
 *   </SchemaGrant>
 * </Role>
 *
 * This role tests native set evaluation with role restrictions when the number of accessible members
 * exceeds MaxConstraints. The test verifies that only permitted battery products are returned.
 */
public class TestNativeHonorsRoleRestrictionsModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    // Static member grants for battery products
    private static final AccessMemberGrant MEMBER_GRANT_CORMORANT_AA_1;
    private static final AccessMemberGrant MEMBER_GRANT_CORMORANT_AA_2;
    private static final AccessMemberGrant MEMBER_GRANT_CORMORANT_AAA;
    private static final AccessMemberGrant MEMBER_GRANT_CORMORANT_C;
    private static final AccessMemberGrant MEMBER_GRANT_DENNY_AA;
    private static final AccessMemberGrant MEMBER_GRANT_DENNY_AAA;

    // Static hierarchy grant
    private static final AccessHierarchyGrant HIERARCHY_GRANT_PRODUCT;

    // Static cube grant
    private static final AccessCubeGrant CUBE_GRANT_SALES;

    // Static catalog grant
    private static final AccessCatalogGrant CATALOG_GRANT;

    // Static access role
    private static final AccessRole ACCESS_ROLE_TEST;

    static {
        // Create member grants for specific battery products
        MEMBER_GRANT_CORMORANT_AA_1 = OlapFactory.eINSTANCE.createAccessMemberGrant();
        MEMBER_GRANT_CORMORANT_AA_1.setMember(mdx("[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Cormorant].[Cormorant AA-Size Batteries]"));
        MEMBER_GRANT_CORMORANT_AA_1.setMemberAccess(MemberAccess.ALL);

        // Note: Original POJO has duplicate entry for Cormorant AA-Size Batteries
        MEMBER_GRANT_CORMORANT_AA_2 = OlapFactory.eINSTANCE.createAccessMemberGrant();
        MEMBER_GRANT_CORMORANT_AA_2.setMember(mdx("[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Cormorant].[Cormorant AA-Size Batteries]"));
        MEMBER_GRANT_CORMORANT_AA_2.setMemberAccess(MemberAccess.ALL);

        MEMBER_GRANT_CORMORANT_AAA = OlapFactory.eINSTANCE.createAccessMemberGrant();
        MEMBER_GRANT_CORMORANT_AAA.setMember(mdx("[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Cormorant].[Cormorant AAA-Size Batteries]"));
        MEMBER_GRANT_CORMORANT_AAA.setMemberAccess(MemberAccess.ALL);

        MEMBER_GRANT_CORMORANT_C = OlapFactory.eINSTANCE.createAccessMemberGrant();
        MEMBER_GRANT_CORMORANT_C.setMember(mdx("[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Cormorant].[Cormorant C-Size Batteries]"));
        MEMBER_GRANT_CORMORANT_C.setMemberAccess(MemberAccess.ALL);

        MEMBER_GRANT_DENNY_AA = OlapFactory.eINSTANCE.createAccessMemberGrant();
        MEMBER_GRANT_DENNY_AA.setMember(mdx("[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Denny].[Denny AA-Size Batteries]"));
        MEMBER_GRANT_DENNY_AA.setMemberAccess(MemberAccess.ALL);

        MEMBER_GRANT_DENNY_AAA = OlapFactory.eINSTANCE.createAccessMemberGrant();
        MEMBER_GRANT_DENNY_AAA.setMember(mdx("[Product].[Non-Consumable].[Household].[Electrical].[Batteries].[Denny].[Denny AAA-Size Batteries]"));
        MEMBER_GRANT_DENNY_AAA.setMemberAccess(MemberAccess.ALL);

        // Create hierarchy grant for Product with CUSTOM access and PARTIAL rollup
        HIERARCHY_GRANT_PRODUCT = OlapFactory.eINSTANCE.createAccessHierarchyGrant();
        HIERARCHY_GRANT_PRODUCT.setHierarchy(CatalogSupplier.HIERARCHY_PRODUCT);
        HIERARCHY_GRANT_PRODUCT.setHierarchyAccess(HierarchyAccess.CUSTOM);
        HIERARCHY_GRANT_PRODUCT.setRollupPolicy(RollupPolicy.PARTIAL);
        HIERARCHY_GRANT_PRODUCT.getMemberGrants().addAll(List.of(
            MEMBER_GRANT_CORMORANT_AA_1,
            MEMBER_GRANT_CORMORANT_AA_2,  // Duplicate entry as in original
            MEMBER_GRANT_CORMORANT_AAA,
            MEMBER_GRANT_CORMORANT_C,
            MEMBER_GRANT_DENNY_AA,
            MEMBER_GRANT_DENNY_AAA
        ));

        // Create cube grant for Sales cube with ALL access
        CUBE_GRANT_SALES = OlapFactory.eINSTANCE.createAccessCubeGrant();
        CUBE_GRANT_SALES.setCube(CatalogSupplier.CUBE_SALES);
        CUBE_GRANT_SALES.setCubeAccess(CubeAccess.ALL);
        CUBE_GRANT_SALES.getHierarchyGrants().add(HIERARCHY_GRANT_PRODUCT);

        // Create catalog grant with NONE access (only specific cubes granted)
        CATALOG_GRANT = CommonFactory.eINSTANCE.createAccessCatalogGrant();
        CATALOG_GRANT.setCatalogAccess(CatalogAccess.NONE);
        CATALOG_GRANT.getCubeGrants().add(CUBE_GRANT_SALES);

        // Create access role "Test"
        ACCESS_ROLE_TEST = CommonFactory.eINSTANCE.createAccessRole();
        ACCESS_ROLE_TEST.setName("Test");
        ACCESS_ROLE_TEST.getAccessCatalogGrants().add(CATALOG_GRANT);
    }

    public TestNativeHonorsRoleRestrictionsModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        this.catalog = EmfUtil.copy((CatalogImpl) baseCatalog);

        // Add the access role to the catalog
        this.catalog.getImportedElement().add(ACCESS_ROLE_TEST);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

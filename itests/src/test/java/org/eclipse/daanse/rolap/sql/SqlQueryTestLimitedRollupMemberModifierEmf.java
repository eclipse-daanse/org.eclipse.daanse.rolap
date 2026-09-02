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
package org.eclipse.daanse.rolap.sql;

import static org.eclipse.daanse.rolap.mapping.model.provider.util.Expressions.mdx;

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
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.Hierarchy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.RollupPolicy;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;

/**
 * Self-contained fixture for {@link SqlQueryTest#testLimitedRollupMemberRetrievableFromCache}:
 * adds a "justCA" access role, granting the Sales cube's Store hierarchy
 * custom/partial-rollup access limited to {@code [Store].[USA].[CA]}, on top
 * of the FoodMart catalog.
 */
public class SqlQueryTestLimitedRollupMemberModifierEmf implements CatalogMappingSupplier {

    private CatalogImpl catalog;

    public SqlQueryTestLimitedRollupMemberModifierEmf(Catalog cat) {
        // Copy catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) cat);
        catalog = (CatalogImpl) copier.get(cat);

        // Create member grant
        AccessMemberGrant memberGrant = OlapFactory.eINSTANCE.createAccessMemberGrant();
        memberGrant.setMember(mdx("[Store].[USA].[CA]"));
        memberGrant.setMemberAccess(MemberAccess.ALL);

        // Create hierarchy grant
        AccessHierarchyGrant hierarchyGrant = OlapFactory.eINSTANCE.createAccessHierarchyGrant();
        hierarchyGrant.setHierarchy((Hierarchy) copier.get(CatalogSupplier.HIERARCHY_STORE));
        hierarchyGrant.setHierarchyAccess(HierarchyAccess.CUSTOM);
        hierarchyGrant.setRollupPolicy(RollupPolicy.PARTIAL);
        hierarchyGrant.getMemberGrants().add(memberGrant);

        // Create cube grant
        AccessCubeGrant cubeGrant = OlapFactory.eINSTANCE.createAccessCubeGrant();
        cubeGrant.setCube((Cube) copier.get(CatalogSupplier.CUBE_SALES));
        cubeGrant.setCubeAccess(CubeAccess.ALL);
        cubeGrant.getHierarchyGrants().add(hierarchyGrant);

        // Create catalog grant
        AccessCatalogGrant catalogGrant = CommonFactory.eINSTANCE.createAccessCatalogGrant();
        catalogGrant.setCatalogAccess(CatalogAccess.ALL);
        catalogGrant.getCubeGrants().add(cubeGrant);

        // Create role
        AccessRole role = CommonFactory.eINSTANCE.createAccessRole();
        role.setName("justCA");
        role.getAccessCatalogGrants().add(catalogGrant);

        // Add role to catalog
        catalog.getImportedElement().add(role);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

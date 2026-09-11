/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation.
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

package org.eclipse.daanse.test.clearview;


import static org.eclipse.daanse.rolap.mapping.model.provider.util.Expressions.mdx;

import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.CalculatedMember;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.CalculatedMemberProperty;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.Cube;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
/*
<CalculatedMember
  name='Sales as % of Cost'
    dimension='Measures'
    formula='([Measures].[Store Sales] - [Measures].[Store Cost])/[Measures].[Store Cost]'>
    <CalculatedMemberProperty name='FORMAT_STRING' value='####0.0%'/>
  </CalculatedMember>
 */
/*
public class MiscTestModifier extends PojoMappingModifier {

    public MiscTestModifier(CatalogMapping catalog) {
        super(catalog);
    }


    protected List<? extends CalculatedMemberMapping> cubeCalculatedMembers(CubeMapping cube) {
        List<CalculatedMemberMapping> result = new ArrayList<>();
        result.addAll(super.cubeCalculatedMembers(cube));
        if ("Sales".equals(cube.getName())) {
            result.add(CalculatedMemberMappingImpl.builder()
                .withName("Sales as % of Cost")
                //.withDimension("Measures")
                .withFormula("([Measures].[Store Sales] - [Measures].[Store Cost])/[Measures].[Store Cost]")
                .withCalculatedMemberProperties(List.of(
                	CalculatedMemberPropertyMappingImpl.builder()
                        .withName("FORMAT_STRING")
                        .withValue("####0.0%")
                        .build()
                ))
                .build());
        }
        return result;

    }
}
*/
public class MiscTestModifier implements CatalogMappingSupplier {

    private final Catalog originalCatalog;

    public MiscTestModifier(Catalog catalog) {
        this.originalCatalog = catalog;
    }

    /*
    <CalculatedMember
      name='Sales as % of Cost'
        dimension='Measures'
        formula='([Measures].[Store Sales] - [Measures].[Store Cost])/[Measures].[Store Cost]'>
        <CalculatedMemberProperty name='FORMAT_STRING' value='####0.0%'/>
      </CalculatedMember>
     */

    @Override
    public Catalog get() {
        // Copy the catalog using EcoreUtil.copy
        Catalog catalogCopy = EmfUtil.copy((CatalogImpl) originalCatalog);

        // Find the "Sales" cube and add calculated member to it
        for (int i = 0; i < Packages.available(catalogCopy, Cube.class).size(); i++) {
            if (Packages.available(catalogCopy, Cube.class).get(i) instanceof PhysicalCube) {
                PhysicalCube cube = (PhysicalCube) Packages.available(catalogCopy, Cube.class).get(i);

                if ("Sales".equals(cube.getName())) {
                    // Create calculated member property using RolapMappingFactory
                    CalculatedMemberProperty formatStringProperty = LevelFactory.eINSTANCE.createCalculatedMemberProperty();
                    formatStringProperty.setName("FORMAT_STRING");
                    formatStringProperty.setValue("####0.0%");

                    // Create calculated member using RolapMappingFactory
                    CalculatedMember calculatedMember = LevelFactory.eINSTANCE.createCalculatedMember();
                    calculatedMember.setName("Sales as % of Cost");
                    calculatedMember.setFormula(mdx("([Measures].[Store Sales] - [Measures].[Store Cost])/[Measures].[Store Cost]"));
                    calculatedMember.getCalculatedMemberProperties().add(formatStringProperty);

                    // Add calculated member to the cube
                    cube.getCalculatedMembers().add(calculatedMember);

                    break;
                }
            }
        }

        return catalogCopy;
    }
}

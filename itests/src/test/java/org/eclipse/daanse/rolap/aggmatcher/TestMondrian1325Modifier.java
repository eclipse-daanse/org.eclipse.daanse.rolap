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


import java.util.Optional;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.Cube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.MeasureGroup;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.impl.PhysicalCubeImpl;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MaxMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MeasureFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
/**
 * EMF version of TestMondrian1325Modifier from NonCollapsedAggTest.
 * Adds a "Bogus Number" measure to the Sales cube.
 *
 * Adds to Sales cube:
 * <Measure name="Bogus Number" column="promotion_id" datatype="Numeric" aggregator="max" visible="true"/>
 */
public class TestMondrian1325Modifier implements CatalogMappingSupplier {

    private final Catalog catalog;


    public TestMondrian1325Modifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) baseCatalog);
        this.catalog = (Catalog) copier.get(baseCatalog);
        // Static measure
        MaxMeasure MEASURE_BOGUS_NUMBER;
        MeasureGroup MEASURE_GROUP;
        MEASURE_BOGUS_NUMBER = MeasureFactory.eINSTANCE.createMaxMeasure();
        MEASURE_BOGUS_NUMBER.setName("Bogus Number");
        MEASURE_BOGUS_NUMBER.setColumn((Column) copier.get(CatalogSupplier.COLUMN_PROMOTION_ID_SALESFACT));
        MEASURE_BOGUS_NUMBER.setDataType(ColumnInternalDataType.NUMERIC);
        MEASURE_BOGUS_NUMBER.setVisible(true);

        // Create measure group
        MEASURE_GROUP = CubeFactory.eINSTANCE.createMeasureGroup();
        MEASURE_GROUP.getMeasures().add(MEASURE_BOGUS_NUMBER);

        // Find the Sales cube and add the measure group
        Optional<Cube> salesCubeOpt = Packages.available(this.catalog, Cube.class).stream()
            .filter(c -> "Sales".equals(c.getName()))
            .findFirst();

        if (salesCubeOpt.isPresent() && salesCubeOpt.get() instanceof PhysicalCubeImpl) {
            PhysicalCubeImpl salesCube = (PhysicalCubeImpl) salesCubeOpt.get();
            salesCube.getMeasureGroups().add(MEASURE_GROUP);
        }
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

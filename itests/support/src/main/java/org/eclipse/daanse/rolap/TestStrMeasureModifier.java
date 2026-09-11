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

import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.database.relational.ColumnInternalDataType;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.MeasureGroup;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MaxMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MeasureFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.StandardDimension;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.ExplicitHierarchy;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.HierarchyFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.Level;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.hierarchy.level.LevelFactory;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.EcoreUtil;
/**
 * EMF version of TestStrMeasureModifier from NonEmptyTest. Creates a cube
 * "StrMeasure" with a string measure using MAX aggregator.
 *
 * <Cube name="StrMeasure">
 * <Table name="promotion"/>
 * <Dimension name="Promotions"> <Hierarchy hasAll="true">
 * <Level name="Promotion Name" column="promotion_name" uniqueMembers="true"/>
 * </Hierarchy> </Dimension>
 * <Measure name="Media" column="media_type" aggregator="max" datatype=
 * "String"/> </Cube>
 */
public class TestStrMeasureModifier implements CatalogMappingSupplier {

    private final Catalog catalog;

    public TestStrMeasureModifier(Catalog baseCatalog) {
        // Copy the base catalog using EcoreUtil
        EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) baseCatalog);
        this.catalog = (Catalog) copier.get(baseCatalog);
        Level LEVEL_PROMOTION_NAME;
        ExplicitHierarchy HIERARCHY_PROMOTIONS;
        StandardDimension DIMENSION_PROMOTIONS;
        DimensionConnector CONNECTOR_PROMOTIONS;
        TableSource TABLE_QUERY_PROMOTION;
        MaxMeasure MEASURE_MEDIA;
        MeasureGroup MEASURE_GROUP;
        PhysicalCube CUBE_STR_MEASURE;

        // Create level
        LEVEL_PROMOTION_NAME = LevelFactory.eINSTANCE.createLevel();
        LEVEL_PROMOTION_NAME.setName("Promotion Name");
        LEVEL_PROMOTION_NAME.setColumn(CatalogSupplier.COLUMN_PROMOTION_NAME_PROMOTION);
        LEVEL_PROMOTION_NAME.setUniqueMembers(true);

        // Create hierarchy
        HIERARCHY_PROMOTIONS = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        HIERARCHY_PROMOTIONS.setHasAll(true);
        HIERARCHY_PROMOTIONS.getLevels().add(LEVEL_PROMOTION_NAME);

        // Create dimension
        DIMENSION_PROMOTIONS = DimensionFactory.eINSTANCE.createStandardDimension();
        DIMENSION_PROMOTIONS.setName("Promotions");
        DIMENSION_PROMOTIONS.getHierarchies().add(HIERARCHY_PROMOTIONS);

        // Create dimension connector
        CONNECTOR_PROMOTIONS = DimensionFactory.eINSTANCE.createDimensionConnector();
        CONNECTOR_PROMOTIONS.setOverrideDimensionName("Promotions");
        CONNECTOR_PROMOTIONS.setDimension(DIMENSION_PROMOTIONS);

        // Create table query
        TABLE_QUERY_PROMOTION = SourceFactory.eINSTANCE.createTableSource();
        TABLE_QUERY_PROMOTION.setTable(CatalogSupplier.TABLE_PROMOTION);

        // Create measure with MAX aggregator and String datatype
        MEASURE_MEDIA = MeasureFactory.eINSTANCE.createMaxMeasure();
        MEASURE_MEDIA.setName("Media");
        MEASURE_MEDIA.setColumn(CatalogSupplier.COLUMN_MEDIA_TYPE_PROMOTION);
        MEASURE_MEDIA.setDataType(ColumnInternalDataType.STRING);

        // Create measure group
        MEASURE_GROUP = CubeFactory.eINSTANCE.createMeasureGroup();
        MEASURE_GROUP.getMeasures().add(MEASURE_MEDIA);

        // Create cube
        CUBE_STR_MEASURE = CubeFactory.eINSTANCE.createPhysicalCube();
        CUBE_STR_MEASURE.setName("StrMeasure");
        CUBE_STR_MEASURE.setSource(TABLE_QUERY_PROMOTION);
        CUBE_STR_MEASURE.getDimensionConnectors().add(CONNECTOR_PROMOTIONS);
        CUBE_STR_MEASURE.getMeasureGroups().add(MEASURE_GROUP);

        // Add the cube to the catalog
        this.catalog.getImportedElement().add(CUBE_STR_MEASURE);
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

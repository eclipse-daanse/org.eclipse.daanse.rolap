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
package org.eclipse.daanse.rolap.testkit.nullsemantics;

import java.util.List;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Column;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.RelationalFactory;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Table;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.enumerations.NullableType;
import org.eclipse.daanse.cwm.util.resource.relational.SqlSimpleTypes;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.CatalogFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.SourceFactory;
import org.eclipse.daanse.rolap.mapping.model.database.source.TableSource;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.MeasureGroup;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.PhysicalCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.AvgMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MaxMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MeasureFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.MinMeasure;
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
 * Bespoke catalog for the NULL/decimal-semantics characterization
 * tests.
 *
 * <p>
 * One fact table {@code FACT(KEY VARCHAR, VAL DOUBLE,
 * DEC_VAL DECIMAL(19,4))} plus a single-level KEY dimension so every fact row
 * is addressable as an individual cell:
 * <ul>
 * <li>{@code VAL} — nullable double, exercises SQL-NULL cells,</li>
 * <li>{@code DEC_VAL} — DECIMAL(19,4) values whose exact sum is not
 * representable as a double (precision-roundtrip demo).</li>
 * </ul>
 */
public class NullSemanticsCatalogSupplier implements CatalogMappingSupplier {

    public static final String CATALOG_NAME = "NullSemanticsCatalog";
    public static final String CUBE_NAME = "NullSemantics";

    public static final String MEASURE_VAL_SUM = "ValSum";
    public static final String MEASURE_VAL_MIN = "ValMin";
    public static final String MEASURE_VAL_MAX = "ValMax";
    public static final String MEASURE_VAL_AVG = "ValAvg";
    public static final String MEASURE_DEC_SUM = "DecSum";

    private final Schema databaseSchema;
    private final Catalog catalog;

    public NullSemanticsCatalogSupplier() {
        RelationalFactory rf = RelationalFactory.eINSTANCE;

        Column keyColumn = rf.createColumn();
        keyColumn.setName("KEY");
        keyColumn.setType(SqlSimpleTypes.varcharType(20));

        Column valColumn = rf.createColumn();
        valColumn.setName("VAL");
        valColumn.setType(SqlSimpleTypes.Sql99.doublePrecisionType());
        // Explicitly nullable — SQL-NULL cells are the point of this catalog.
        valColumn.setIsNullable(NullableType.COLUMN_NULLABLE);

        Column decValColumn = rf.createColumn();
        decValColumn.setName("DEC_VAL");
        decValColumn.setType(SqlSimpleTypes.decimalType(19, 4));

        Table table = rf.createTable();
        table.setName("FACT");
        table.getFeature().addAll(List.of(keyColumn, valColumn, decValColumn));

        databaseSchema = rf.createSchema();
        databaseSchema.getOwnedElement().add(table);

        TableSource query = SourceFactory.eINSTANCE.createTableSource();
        query.setTable(table);

        MeasureFactory mf = MeasureFactory.eINSTANCE;

        SumMeasure valSum = mf.createSumMeasure();
        valSum.setName(MEASURE_VAL_SUM);
        valSum.setColumn(valColumn);

        MinMeasure valMin = mf.createMinMeasure();
        valMin.setName(MEASURE_VAL_MIN);
        valMin.setColumn(valColumn);

        MaxMeasure valMax = mf.createMaxMeasure();
        valMax.setName(MEASURE_VAL_MAX);
        valMax.setColumn(valColumn);

        AvgMeasure valAvg = mf.createAvgMeasure();
        valAvg.setName(MEASURE_VAL_AVG);
        valAvg.setColumn(valColumn);

        SumMeasure decSum = mf.createSumMeasure();
        decSum.setName(MEASURE_DEC_SUM);
        decSum.setColumn(decValColumn);

        MeasureGroup measureGroup = CubeFactory.eINSTANCE.createMeasureGroup();
        measureGroup.getMeasures().addAll(List.of(valSum, valMin, valMax, valAvg, decSum));

        Level level = LevelFactory.eINSTANCE.createLevel();
        level.setName("KeyLevel");
        level.setColumn(keyColumn);

        ExplicitHierarchy hierarchy = HierarchyFactory.eINSTANCE.createExplicitHierarchy();
        hierarchy.setName("KeyHierarchy");
        hierarchy.setPrimaryKey(keyColumn);
        hierarchy.setSource(query);
        hierarchy.getLevels().add(level);

        StandardDimension dimension = DimensionFactory.eINSTANCE.createStandardDimension();
        dimension.setName("KeyDim");
        dimension.getHierarchies().add(hierarchy);

        DimensionConnector dimensionConnector = DimensionFactory.eINSTANCE.createDimensionConnector();
        dimensionConnector.setDimension(dimension);

        PhysicalCube cube = CubeFactory.eINSTANCE.createPhysicalCube();
        cube.setName(CUBE_NAME);
        cube.setSource(query);
        cube.getMeasureGroups().add(measureGroup);
        cube.getDimensionConnectors().add(dimensionConnector);

        catalog = CatalogFactory.eINSTANCE.createCatalog();
        catalog.setName(CATALOG_NAME);
        catalog.setDescription("NULL/decimal semantics characterization catalog");
        catalog.getDbschemas().add(databaseSchema);
        catalog.getCubes().add(cube);
    }

    /** The CWM database schema, for {@code DatabaseLayer.apply}. */
    public Schema schema() {
        return databaseSchema;
    }

    @Override
    public Catalog get() {
        return catalog;
    }
}

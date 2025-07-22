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
 * ---- All changes after Fork in 2023 ------------------------
 *
 * Project: Eclipse daanse
 *
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors after Fork in 2023:
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.common;

import static org.eclipse.daanse.rolap.common.util.RelationUtil.getAlias;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.DrillThroughColumn;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.element.PhysicalCube;
import org.eclipse.daanse.olap.api.element.Property;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.olap.api.formatter.CellFormatter;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.common.StandardProperty;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.exceptions.BadMeasureSourceException;
import org.eclipse.daanse.rolap.aggregator.CountAggregator;
import org.eclipse.daanse.rolap.common.format.FormatterCreateContext;
import org.eclipse.daanse.rolap.common.format.FormatterFactory;
import org.eclipse.daanse.rolap.element.RolapMetaData;
import org.eclipse.daanse.rolap.mapping.api.model.ActionMapping;
import org.eclipse.daanse.rolap.mapping.api.model.CatalogMapping;
import org.eclipse.daanse.rolap.mapping.api.model.ColumnMapping;
import org.eclipse.daanse.rolap.mapping.api.model.ColumnMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.CountMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.CubeMapping;
import org.eclipse.daanse.rolap.mapping.api.model.DrillThroughActionMapping;
import org.eclipse.daanse.rolap.mapping.api.model.DrillThroughAttributeMapping;
import org.eclipse.daanse.rolap.mapping.api.model.MeasureGroupMapping;
import org.eclipse.daanse.rolap.mapping.api.model.MeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.PhysicalColumnMapping;
import org.eclipse.daanse.rolap.mapping.api.model.PhysicalCubeMapping;
import org.eclipse.daanse.rolap.mapping.api.model.RelationalQueryMapping;
import org.eclipse.daanse.rolap.mapping.api.model.SQLExpressionColumnMapping;
import org.eclipse.daanse.rolap.mapping.api.model.SqlExpressionMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.WritebackAttributeMapping;
import org.eclipse.daanse.rolap.mapping.api.model.WritebackMeasureMapping;
import org.eclipse.daanse.rolap.mapping.api.model.WritebackTableMapping;
import org.eclipse.daanse.rolap.mapping.pojo.AnnotationMappingImpl;
import org.eclipse.daanse.rolap.mapping.pojo.CountMeasureMappingImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RolapPhysicalCube extends RolapCube implements PhysicalCube {

    private final static String measureOrdinalsNotUnique = "Cube ''{0}'': Ordinal {1} is not unique: ''{2}'' and ''{3}''";
    private static final Logger LOGGER = LoggerFactory.getLogger(RolapPhysicalCube.class);

    /**
     * Creates a RolapCube from a regular cube.
     */
    RolapPhysicalCube(RolapCatalog catalog, CatalogMapping catalogMapping, PhysicalCubeMapping cubeMapping,
            Context context) {
        super(catalog, catalogMapping, cubeMapping, cubeMapping.isCache(),
                (RelationalQueryMapping) cubeMapping.getQuery(), context);

        if (getFact() == null) {
            throw Util.newError(
                    new StringBuilder("Must specify fact table of cube '").append(getName()).append("'").toString());
        }

        if (getLogger().isDebugEnabled()) {
            String msg = new StringBuilder("RolapCube<init>: cube=").append(this.name).toString();
            getLogger().debug(msg);
        }

        // Initialize closure bit key only when we know how many columns are in
        // the star.
        setClosureColumnBitKey(BitKey.Factory.makeBitKey(getStar().getColumnCount()));



        if (getAlias(getFact()) == null) {
            throw Util.newError(new StringBuilder("Must specify alias for fact table of cube '").append(getName())
                    .append("'").toString());
        }

        // since Measure and VirtualCubeMeasure
        // can not be treated as the same, measure creation can not be
        // done in a common constructor.
        List<RolapMember> measureList = initMeasures(cubeMapping);

        init(cubeMapping.getDimensionConnectors());
        init(cubeMapping, measureList);

        setMeasuresHierarchyMemberReader(
                new CacheMemberReader(new MeasureMemberSource(this.getMeasuresHierarchy(), measureList)));

        checkOrdinals(cubeMapping.getName(), measureList);
        loadAggGroup(cubeMapping);
        initActions(cubeMapping);
        initWritebackTables(cubeMapping);
    }

    @Override
    protected void logMessage() {
        if (getLogger().isDebugEnabled()) {
            String msg = new StringBuilder("RolapCube<init>: cube=").append(this.name).toString();
            getLogger().debug(msg);
        }
    }

    private List<RolapMember> initMeasures(PhysicalCubeMapping cubeMapping) {
        RolapLevel measuresLevel = this.getMeasuresHierarchy().newMeasuresLevel();

        List<? extends MeasureMapping> measureMappings = cubeMapping.getMeasureGroups().stream()
                .map(MeasureGroupMapping::getMeasures).flatMap(Collection::stream).toList();

        List<RolapMember> measureList = new ArrayList<>(measureMappings.size());

        AtomicInteger ai = new AtomicInteger();
        Member defaultMeasure = null;
        for (MeasureMapping measureMapping : measureMappings) {
            RolapBaseCubeMeasure measure = createMeasure(cubeMapping, measuresLevel, ai.getAndIncrement(),
                    measureMapping);
            measureList.add(measure);

            // Is this the default measure?
            if (measureMapping.equals(cubeMapping.getDefaultMeasure())) {
                defaultMeasure = measure;
            }

            if (measure.getAggregator() == CountAggregator.INSTANCE) {
                factCountMeasure = measure;
            }
        }

        // boolean writebackEnabled = false;
        // for (RolapHierarchy hierarchy : hierarchyList) {
        // if (ScenarioImpl.isScenario(hierarchy)) {
        // writebackEnabled = true;
        // }
        // }

        // Ensure that cube has an atomic cell count
        // measure even if the schema does not contain one.
        if (factCountMeasure == null) {
            AnnotationMappingImpl internalUsage = AnnotationMappingImpl.builder().withName("Internal Use")
                    .withValue("For internal use").build();
            List<AnnotationMappingImpl> annotations = new ArrayList<>();
            annotations.add(internalUsage);
            final CountMeasureMappingImpl mappingMeasure = CountMeasureMappingImpl.builder().withName("Fact Count")
                    .withVisible(false).withAnnotations(annotations).build();
            factCountMeasure = createMeasure(cubeMapping, measuresLevel, -1, mappingMeasure);
            measureList.add(factCountMeasure);
        }
        setMeasuresHierarchyMemberReader(
                new CacheMemberReader(new MeasureMemberSource(this.getMeasuresHierarchy(), measureList)));

        this.getMeasuresHierarchy().setDefaultMember(defaultMeasure);

        return measureList;
    }

    private void initWritebackTables(PhysicalCubeMapping cubeMapping) {
        if (cubeMapping.getWritebackTable() != null) {
            WritebackTableMapping writebackTable = cubeMapping.getWritebackTable();
            List<RolapWritebackColumn> columns = new ArrayList<>();

            for (WritebackAttributeMapping writebackAttribute : writebackTable.getWritebackAttribute()) {

                Dimension dimension = null;
                for (Dimension currentDimension : this.getDimensions()) {
                    if (currentDimension.getName()
                            .equals(writebackAttribute.getDimensionConnector().getOverrideDimensionName())) {
                        dimension = currentDimension;
                        break;
                    }
                }
                if (dimension == null) {
                    throw Util.newError(new StringBuilder("Error while creating `WritebackTable`. Dimension '")
                            .append(writebackAttribute.getDimensionConnector().getOverrideDimensionName())
                            .append("' not found").toString());
                }

                columns.add(new RolapWritebackAttribute(dimension, writebackAttribute.getColumn()));

            }
            for (WritebackMeasureMapping writebackMeasure : writebackTable.getWritebackMeasure()) {
                Member measure = null;
                for (Member currentMeasure : this.getMeasures()) {
                    if (currentMeasure instanceof RolapBaseCubeMeasure rbcm
                            && rbcm.getKey().equals(writebackMeasure.getName())) {
                        measure = currentMeasure;
                        break;
                    }
                }
                if (measure == null) {
                    throw Util.newError(new StringBuilder("Error while creating DrillThrough  action. Measure '")
                            .append(writebackMeasure.getName()).append("' not found").toString());
                }
                columns.add(new RolapWritebackMeasure(measure, writebackMeasure.getColumn()));
            }
            RolapWritebackTable rolapWritebackTable = new RolapWritebackTable(writebackTable.getName(),
                    writebackTable.getSchema(), columns);
            this.setWritebackTable(Optional.of(rolapWritebackTable));
        }
    }

    private void initActions(PhysicalCubeMapping cubeMapping) {
        for (ActionMapping mappingAction : cubeMapping.getAction()) {
            if (mappingAction instanceof DrillThroughActionMapping mappingDrillThroughAction) {
                List<DrillThroughColumn> columns = new ArrayList<>();

                for (DrillThroughAttributeMapping mappingDrillThroughAttribute : mappingDrillThroughAction
                        .getDrillThroughAttribute()) {
                    Dimension dimension = null;
                    Hierarchy hierarchy = null;
                    Level level = null;
                    RolapProperty property = null;
                    for (Dimension currntDimension : this.getDimensions()) {
                        if (currntDimension.getName().equals(mappingDrillThroughAttribute.getDimension().getName())) {
                            dimension = currntDimension;
                            break;
                        }
                    }
                    if (dimension == null) {
                        throw Util.newError(new StringBuilder("Error while creating DrillThrough  action. Dimension '")
                                .append(mappingDrillThroughAttribute.getDimension()).append("' not found").toString());
                    } else {
                        if (mappingDrillThroughAttribute.getHierarchy() != null) {
                            for (Hierarchy currentHierarchy : dimension.getHierarchies()) {
                                if (currentHierarchy instanceof RolapCubeHierarchy rolapCubeHierarchy
                                        && rolapCubeHierarchy.getSubName()
                                                .equals(mappingDrillThroughAttribute.getHierarchy().getName())) {
                                    hierarchy = currentHierarchy;
                                    break;
                                }
                            }
                            if (hierarchy == null) {
                                throw Util.newError(
                                        new StringBuilder("Error while creating DrillThrough  action. Hierarchy '")
                                                .append(mappingDrillThroughAttribute.getHierarchy())
                                                .append("' not found").toString());
                            } else {
                                if (mappingDrillThroughAttribute.getLevel() != null
                                        && !mappingDrillThroughAttribute.getLevel().getName().equals("")) {
                                    for (Level currentLevel : hierarchy.getLevels()) {
                                        if (currentLevel.getName()
                                                .equals(mappingDrillThroughAttribute.getLevel().getName())) {
                                            level = currentLevel;
                                            break;
                                        }
                                    }
                                    if (level == null) {
                                        throw Util.newError(
                                                new StringBuilder("Error while creating DrillThrough  action. Level '")
                                                        .append(mappingDrillThroughAttribute.getLevel())
                                                        .append("' not found").toString());
                                    } else {
                                        if (mappingDrillThroughAttribute.getProperty() != null
                                                && !mappingDrillThroughAttribute.getProperty().equals("")) {
                                            for (Property currentProperty : level.getProperties()) {
                                                if (currentProperty instanceof RolapProperty rolapProperty
                                                        && currentProperty.getName()
                                                                .equals(mappingDrillThroughAttribute.getProperty())) {
                                                    property = rolapProperty;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }

                            }
                        }
                    }

                    columns.add(new RolapDrillThroughAttribute(dimension, hierarchy, level, property));

                }
                for (MeasureMapping drillThroughMeasure : mappingDrillThroughAction.getDrillThroughMeasure()) {
                    Member measure = null;
                    for (Member currntMeasure : this.getMeasures()) {
                        if (currntMeasure.getName().equals(drillThroughMeasure.getName())) {
                            measure = currntMeasure;
                            break;
                        }
                    }
                    if (measure == null) {
                        throw Util.newError(new StringBuilder("Error while creating DrillThrough  action. Measure '")
                                .append(drillThroughMeasure.getName()).append("' not found").toString());
                    }
                    columns.add(new RolapDrillThroughMeasure(measure));
                }

                RolapDrillThroughAction rolapDrillThroughAction = new RolapDrillThroughAction(
                        mappingDrillThroughAction.getName(), mappingDrillThroughAction.getName(),
                        mappingDrillThroughAction.getDescription(), mappingDrillThroughAction.isDefault(), columns);
                this.actionList.add(rolapDrillThroughAction);
            }
        }
    }

    /**
     * Creates a measure.
     *
     * @param cubeMapping    XML cube
     * @param measuresLevel  Member that all measures belong to
     * @param ordinal        Ordinal of measure
     * @param measureMapping XML measure
     * @return Measure
     */
    private RolapBaseCubeMeasure createMeasure(CubeMapping cubeMapping, RolapLevel measuresLevel, int ordinal,
            final MeasureMapping measureMapping) {
        ColumnMapping columnMapping;
        RolapSqlExpression measureExp = null;
        if (measureMapping instanceof ColumnMeasureMapping cmm) {
            columnMapping = cmm.getColumn();
            if (columnMapping instanceof PhysicalColumnMapping pc) {
                measureExp = new RolapColumn(getAlias(getFact()), pc.getName());
            } else if (columnMapping instanceof SQLExpressionColumnMapping scm) {
                measureExp = new RolapSqlExpression(scm);
            } else if (measureMapping instanceof CountMeasureMapping) {
                // it's ok if count has no expression; it means 'count(*)'
                measureExp = null;
            } else {
                throw new BadMeasureSourceException(cubeMapping.getName(), measureMapping.getName());
            }
        }
        if (measureMapping instanceof SqlExpressionMeasureMapping cmm) {
            if (cmm.getColumn() != null) {
                measureExp = new RolapSqlExpression(cmm.getColumn());
            } else {
                throw new BadMeasureSourceException(cubeMapping.getName(), measureMapping.getName());
            }
        }
        // Validate aggregator name. Substitute deprecated "distinct count"
        // with modern "distinct-count".
        Aggregator aggregator = this.getContext().getAggragationFactory().getAggregator(measureMapping);
        final RolapBaseCubeMeasure measure = new RolapBaseCubeMeasure(this, null, measuresLevel,
                measureMapping.getName(), measureMapping.getName(), measureMapping.getDescription(),
                measureMapping.getFormatString(), measureExp, aggregator, measureMapping.getDatatype(),
                RolapMetaData.createMetaData(measureMapping.getAnnotations()));

        FormatterCreateContext formatterContext = new FormatterCreateContext.Builder(measure.getUniqueName())
                .formatterDef(measureMapping.getCellFormatter()).formatterAttr(measureMapping.getFormatter()).build();
        CellFormatter cellFormatter = FormatterFactory.instance().createCellFormatter(formatterContext);
        if (cellFormatter != null) {
            measure.setFormatter(cellFormatter);
        }

        // Set member's caption, if present.
        if (!Util.isEmpty(measureMapping.getName())) {
            // there is a special caption string
            measure.setProperty(StandardProperty.CAPTION.getName(), measureMapping.getName());
        }

        // Set member's visibility, default true.
        Boolean visible = measureMapping.isVisible();
        measure.setProperty(StandardProperty.VISIBLE.getName(), visible);

        measure.setProperty(StandardProperty.DISPLAY_FOLDER.getName(), measureMapping.getDisplayFolder());

        measure.setProperty(StandardProperty.BACK_COLOR.getName(), measureMapping.getBackColor());

        List<String> propNames = new ArrayList<>();
        List<String> propExprs = new ArrayList<>();
        validateMemberProps(measureMapping.getCalculatedMemberProperties(), propNames, propExprs,
                measureMapping.getName());
        for (int j = 0; j < propNames.size(); j++) {
            String propName = propNames.get(j);
            final Object propExpr = propExprs.get(j);
            measure.setProperty(propName, propExpr);
            if (propName.equals(StandardProperty.MEMBER_ORDINAL.getName()) && propExpr instanceof String expr
                    && expr.startsWith("\"") && expr.endsWith("\"")) {
                try {
                    ordinal = Integer.valueOf(expr.substring(1, expr.length() - 1));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
//                    discard(e);
                }
            }
        }
        measure.setOrdinal(ordinal);
        return measure;
    }

    /**
     * Post-initialization, doing things which cannot be done in the constructor.
     */
    private void init(CubeMapping mappingCube, final List<RolapMember> memberList) {
        // Load calculated members and named sets.
        // (We cannot do this in the constructor, because
        // cannot parse the generated query, because the schema has not been
        // set in the cube at this point.)
        createCalcMembersAndNamedSets(mappingCube.getCalculatedMembers(), mappingCube.getNamedSets(), memberList,
                this, true);
    }

    /**
     * Checks that the ordinals of measures (including calculated measures) are
     * unique.
     *
     * @param cubeName name of the cube (required for error messages)
     * @param measures measure list
     */
    protected void checkOrdinals(String cubeName, List<RolapMember> measures) {
        Map<Integer, String> ordinals = new HashMap<>();
        for (RolapMember measure : measures) {
            Integer ordinal = measure.getOrdinal();
            if (!ordinals.containsKey(ordinal)) {
                ordinals.put(ordinal, measure.getUniqueName());
            } else {
                LOGGER.warn(MessageFormat.format(measureOrdinalsNotUnique, cubeName,
                        ordinal.toString(), ordinals.get(ordinal), measure.getUniqueName()));
            }
        }
    }
}

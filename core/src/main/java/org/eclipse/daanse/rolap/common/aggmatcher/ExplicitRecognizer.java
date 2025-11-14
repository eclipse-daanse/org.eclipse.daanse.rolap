/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
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

package org.eclipse.daanse.rolap.common.aggmatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.eclipse.daanse.olap.api.NameSegment;
import org.eclipse.daanse.olap.api.Segment;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import org.eclipse.daanse.olap.common.Util;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.HierarchyUsage;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.aggmatcher.JdbcSchema.Table.Column;
import org.eclipse.daanse.rolap.element.RolapCube;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.recorder.MessageRecorder;

/**
 * This is the Recognizer for the aggregate table descriptions that appear in
 * the catalog schema files; the user explicitly defines the aggregate.
 *
 * @author Richard M. Emberson
 */
class ExplicitRecognizer extends Recognizer {

    public static final String THE_AGGREGATE_TABLE = "The aggregate table ";
    public static final String CONTAINS_THE_COLUMN = " contains the column ";
    public static final String WHICH_MAPS_TO_THE_LEVEL = " which maps to the level ";
    private ExplicitRules.TableDef tableDef;
    private RolapCube cube;

    ExplicitRecognizer(
        final ExplicitRules.TableDef tableDef,
        final RolapStar star,
        RolapCube cube,
        final JdbcSchema.Table dbFactTable,
        final JdbcSchema.Table aggTable,
        final MessageRecorder msgRecorder)
    {
        super(star, dbFactTable, aggTable, msgRecorder);
        this.tableDef = tableDef;
        this.cube = cube;
    }

    /**
     * Get the ExplicitRules.TableDef associated with this instance.
     */
    protected ExplicitRules.TableDef getTableDef() {
        return tableDef;
    }

    /**
     * Get the Matcher to be used to match columns to be ignored.
     */
    @Override
	protected Recognizer.Matcher getIgnoreMatcher() {
        return getTableDef().getIgnoreMatcher();
    }

    /**
     * Get the Matcher to be used to match the column which is the fact count
     * column.
     */
    @Override
	protected Recognizer.Matcher getFactCountMatcher() {
        return getTableDef().getFactCountMatcher();
    }

    @Override
    protected Matcher getMeasureFactCountMatcher() {
        return  getTableDef().getMeasureFactCountMatcher();
    }

    /**
     * Make the measures for this aggregate table.
     * 
     * First, iterate through all of the columns in the table.
     * For each column, iterate through all of the tableDef measures, the
     * explicit definitions of a measure.
     * If the table's column name matches the column name in the measure
     * definition, then make a measure.
     * Next, look through all of the fact table column usage measures.
     * For each such measure usage that has a sibling foreign key usage
     * see if the tableDef has a foreign key defined with the same name.
     * If so, then, for free, we can make a measure for the aggregate using
     * its foreign key.
     * 
     *
     * @return number of measures created.
     */
    @Override
	protected int checkMeasures() {
        msgRecorder.pushContextName("ExplicitRecognizer.checkMeasures");
        try {
            int measureColumnCounts = 0;
            // Look at each aggregate table column. For each measure defined,
            // see if the measure's column name equals the column's name.
            // If so, make the aggregate measure usage for that column.
            for (JdbcSchema.Table.Column aggColumn : aggTable.getColumns()) {
                // if marked as ignore, then do not consider
                if (aggColumn.hasUsage(JdbcSchema.UsageType.IGNORE)) {
                    continue;
                }



                for (ExplicitRules.TableDef.Measure measure
                    : getTableDef().getMeasures())
                {
                    // Column name match is case insensitive
                    if (measure.getColumn().getName().equals(aggColumn.getName()))
                    {
                        String name = measure.getName();
                        List<Segment> parts = Util.parseIdentifier(name);
                        Segment nameLast = Util.last(parts);

                        RolapStar.Measure m = null;
                        if (nameLast instanceof NameSegment nameSegment) {
                            m = star.getFactTable().lookupMeasureByName(
                                cube.getName(),
                                nameSegment.getName());
                        }
                        Aggregator agg = null;
                        if (m != null) {
                            agg = m.getAggregator();
                        }
                        // Ok, got a match, so now make a measure
                        makeMeasure(measure, agg, aggColumn);
                        measureColumnCounts++;
                    }
                }
            }
            // Ok, now look at all of the fact table columns with measure usage
            // that have a sibling foreign key usage. These can be automagically
            // generated for the aggregate table as long as it still has the
            // foreign key.
            for (Iterator<JdbcSchema.Table.Column.Usage> it =
                     dbFactTable.getColumnUsages(JdbcSchema.UsageType.MEASURE);
                 it.hasNext();)
            {
                JdbcSchema.Table.Column.Usage factUsage = it.next();
                JdbcSchema.Table.Column factColumn = factUsage.getColumn();

                if (factColumn.hasUsage(JdbcSchema.UsageType.FOREIGN_KEY)) {
                    // What we've got here is a measure based upon a foreign key
                	org.eclipse.daanse.rolap.mapping.model.Column aggFK =
                        getTableDef().getAggregateFK(factColumn.getName());
                    // OK, not a lost dimension
                    if (aggFK != null) {
                        JdbcSchema.Table.Column aggColumn =
                            aggTable.getColumn(aggFK.getName());

                        // Column name match is case insensitive
                        if (aggColumn == null) {
                            aggColumn = aggTable.getColumn(aggFK.getName().toLowerCase());
                        }
                        if (aggColumn == null) {
                            aggColumn = aggTable.getColumn(aggFK.getName().toUpperCase());
                        }

                        if (aggColumn != null) {
                            makeMeasure(factUsage, aggColumn);
                            measureColumnCounts++;
                        }
                    }
                }
            }
            return measureColumnCounts;
        } finally {
            msgRecorder.popContextName();
        }
    }

    /**
     * Make a measure. This makes a measure usage using the Aggregator found in
     * the RolapStar.Measure associated with the ExplicitRules.TableDef.Measure.
     */
    protected void makeMeasure(
        final ExplicitRules.TableDef.Measure measure,
        Aggregator factAgg,
        final JdbcSchema.Table.Column aggColumn)
    {
        RolapStar.Measure rm = measure.getRolapStarMeasure();

        JdbcSchema.Table.Column.Usage aggUsage =
            aggColumn.newUsage(JdbcSchema.UsageType.MEASURE);

        aggUsage.setSymbolicName(measure.getSymbolicName());

        ExplicitRules.TableDef.RollupType explicitRollupType = measure
                .getExplicitRollupType();
        Aggregator ra = null;

        // precedence to the explicitly defined rollup type
        if (explicitRollupType != null) {
            String factCountExpr = getFactCountExpr(aggUsage);
            ra = explicitRollupType.getAggregator(factCountExpr);
        } else {
            ra = (factAgg == null)
                    ? convertAggregator(aggUsage, rm.getAggregator())
                    : convertAggregator(aggUsage, factAgg, rm.getAggregator());
        }

        aggUsage.setAggregator(ra);
        aggUsage.rMeasure = rm;
    }

    /**
     * Creates a foreign key usage.
     *
     *  First the column name of the fact usage which is a foreign key is
     * used to search for a foreign key definition in the
     * ExplicitRules.tableDef.  If not found, thats ok, it is just a lost
     * dimension.  If found, look for a column in the aggregate table with that
     * name and make a foreign key usage.
     */
    @Override
	protected int matchForeignKey(
        final JdbcSchema.Table.Column.Usage factUsage)
    {
        JdbcSchema.Table.Column factColumn = factUsage.getColumn();
        org.eclipse.daanse.rolap.mapping.model.Column aggFK = getTableDef().getAggregateFK(factColumn.getName());

        // OK, a lost dimension
        if (aggFK == null) {
            return 0;
        }

        int matchCount = 0;
        for (JdbcSchema.Table.Column aggColumn : aggTable.getColumns()) {
            // if marked as ignore, then do not consider
            if (aggColumn.hasUsage(JdbcSchema.UsageType.IGNORE)) {
                continue;
            }

            if (aggFK.getName().equals(aggColumn.getName())) {
                makeForeignKey(factUsage, aggColumn, aggFK.getName());
                matchCount++;
            }
        }
        return matchCount;
    }

    /**
     * Creates a level usage. A level usage is a column that is used in a
     * collapsed dimension aggregate table.
     *
     *  First, iterate through the ExplicitRules.TableDef's level
     * definitions for one with a name equal to the RolapLevel unique name,
     * i.e., [Time].[Quarter].  Now, using the level's column name, search
     * through the aggregate table's columns for one with that name and make a
     * level usage for the column.
     */
    @Override
	protected void matchLevels(
        final Hierarchy hierarchy,
        final HierarchyUsage hierarchyUsage)
    {
        msgRecorder.pushContextName("ExplicitRecognizer.matchLevel");
        try {
            // Try to match a Level's name against the RolapLevel
            // unique name.
            List<Pair<RolapLevel, ExplicitRules.TableDef.Level>> levelMatches =
                new ArrayList<>();
            List<ExplicitRules.TableDef.Level> aggLevels =
                new ArrayList<>();

            Map<String, Column> aggTableColumnMap =
                getCaseInsensitiveColumnMap();
            Map<String, ExplicitRules.TableDef.Level>
                tableDefLevelUniqueNameMap  = getTableDefLevelUniqueNameMap();

            for (Level hLevel : hierarchy.getLevels()) {
                final RolapLevel rLevel = (RolapLevel) hLevel;
                String levelUniqueName = rLevel.getUniqueName();

                if (tableDefLevelUniqueNameMap.containsKey(levelUniqueName)) {
                    ExplicitRules.TableDef.Level level =
                        tableDefLevelUniqueNameMap.get(levelUniqueName);
                    if (aggTableColumnMap.containsKey(level.getColumn().getName())) {
                        levelMatches.add(
                            new Pair<>(
                                rLevel, level));
                        aggLevels.add(level);
                    }
                }
            }
            if (levelMatches.isEmpty()) {
                return;
            }
            sortLevelMatches(levelMatches, aggLevels);
            boolean forceCollapse =
                validateLevelMatches(levelMatches, aggLevels);

            if (msgRecorder.hasErrors()) {
                return;
            }
            // All checks out. Let's create the levels.
            for (Pair<RolapLevel, ExplicitRules.TableDef.Level> pair
                : levelMatches)
            {
                RolapLevel rolapLevel = pair.left;
                ExplicitRules.TableDef.Level aggLevel = pair.right;

                makeLevelColumnUsage(
                    aggTableColumnMap.get(aggLevel.getColumn().getName()),
                    hierarchyUsage,
                    getColumnName(aggLevel.getRolapFieldName()),
                    aggLevels.get(levelMatches.indexOf(pair)).getColumn().getName(),
                    rolapLevel.getName(),
                    forceCollapse
                        ? true
                        : aggLevels.get(levelMatches.indexOf(pair))
                        .isCollapsed(),
                    rolapLevel,
                    getColumn(aggLevel.getOrdinalColumn(), aggTableColumnMap),
                    getColumn(aggLevel.getCaptionColumn(), aggTableColumnMap),
                    getProperties(aggLevel.getProperties(), aggTableColumnMap));
            }
        } finally {
            msgRecorder.popContextName();
        }
    }

    private Column getColumn(
    		org.eclipse.daanse.rolap.mapping.model.Column columnName, Map<String, Column> aggTableColumnMap)
    {
        if (columnName == null) {
            return null;
        }
        return aggTableColumnMap.get(columnName.getName());
    }

    private Map<String, Column> getProperties(
        Map<String, org.eclipse.daanse.rolap.mapping.model.Column> properties, Map<String, Column> columnMap)
    {
        Map<String, Column> map = new HashMap<>();
        for (Map.Entry<String, org.eclipse.daanse.rolap.mapping.model.Column> entry : properties.entrySet()) {
            map.put(entry.getKey(), getColumn(entry.getValue(), columnMap));
        }
        return Collections.unmodifiableMap(map);
    }

    private Map<String, ExplicitRules.TableDef.Level>
    getTableDefLevelUniqueNameMap()
    {
        Map<String, ExplicitRules.TableDef.Level> tableDefUniqueNameMap =
            new HashMap<>();
        for (ExplicitRules.TableDef.Level level : getTableDef().getLevels()) {
            tableDefUniqueNameMap.put(level.getName(), level);
        }
        return Collections.unmodifiableMap(tableDefUniqueNameMap);
    }

    private Map<String, Column> getCaseInsensitiveColumnMap() {
        Map<String, Column> aggTableColumnMap =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        aggTableColumnMap.putAll(aggTable.getColumnMap());
        return Collections.unmodifiableMap(aggTableColumnMap);
    }

    private boolean validateLevelMatches(
        List<Pair<RolapLevel, ExplicitRules.TableDef.Level>> levelMatches,
        List<ExplicitRules.TableDef.Level> aggLevels)
    {
        // Validate by iterating.
        boolean forceCollapse = false;
        for (Pair<RolapLevel, ExplicitRules.TableDef.Level> pair
            : levelMatches)
        {
            // Fail if the level is not the first match
            // but the one before is not its parent.
            if (levelMatches.indexOf(pair) > 0
                && pair.left.getDepth() - 1
                    != levelMatches.get(
                        levelMatches.indexOf(pair) - 1).left.getDepth())
            {
                msgRecorder.reportError(
                    new StringBuilder(THE_AGGREGATE_TABLE)
                        .append(aggTable.getName())
                        .append(CONTAINS_THE_COLUMN)
                        .append(pair.right.getName())
                        .append(WHICH_MAPS_TO_THE_LEVEL)
                        .append(pair.left.getUniqueName())
                        .append(" but its parent level is not part of that aggregation.").toString());
            }
            // Warn if this level is marked as non-collapsed but the level
            // above it is present in this agg table.
            if (levelMatches.indexOf(pair) > 0
                && !aggLevels.get(levelMatches.indexOf(pair)).isCollapsed())
            {
                forceCollapse = true;
                msgRecorder.reportWarning(
                    new StringBuilder(THE_AGGREGATE_TABLE).append(aggTable.getName())
                        .append(CONTAINS_THE_COLUMN).append(pair.right.getName())
                        .append(WHICH_MAPS_TO_THE_LEVEL)
                        .append(pair.left.getUniqueName())
                        .append(" and is marked as non-collapsed, but its parent column is already present.")
                        .toString());
            }
            // Fail if the level is the first, it isn't at the top,
            // but it is marked as collapsed.
            if (levelMatches.indexOf(pair) == 0
                && pair.left.getDepth() > 1
                && aggLevels.get(levelMatches.indexOf(pair)).isCollapsed())
            {
                msgRecorder.reportError(
                    new StringBuilder(THE_AGGREGATE_TABLE)
                        .append(aggTable.getName())
                        .append(CONTAINS_THE_COLUMN)
                        .append(pair.right.getName())
                        .append(WHICH_MAPS_TO_THE_LEVEL)
                        .append(pair.left.getUniqueName())
                        .append(" but its parent level is not part of that aggregation and this level is marked as collapsed.")
                        .toString());
            }
            // Fail if the level is non-collapsed but its members
            // are not unique.
            if (!aggLevels.get(
                    levelMatches.indexOf(pair)).isCollapsed()
                        && !pair.left.isUnique())
            {
                msgRecorder.reportError(
                    new StringBuilder(THE_AGGREGATE_TABLE)
                        .append(aggTable.getName())
                        .append(CONTAINS_THE_COLUMN)
                        .append(pair.right.getName())
                        .append(WHICH_MAPS_TO_THE_LEVEL)
                        .append(pair.left.getUniqueName())
                        .append(" but that level doesn't have unique members and this level is marked as non collapsed.")
                        .toString());
            }
        }
        return forceCollapse;
    }

    private void sortLevelMatches(
        List<Pair<RolapLevel, ExplicitRules.TableDef.Level>> levelMatches,
        List<ExplicitRules.TableDef.Level> aggLevels)
    {
        // Sort the matches by level depth.
        Collections.sort(
            levelMatches,
            new Comparator<Pair<RolapLevel, ExplicitRules.TableDef.Level>>() {
                @Override
				public int compare(
                    Pair<RolapLevel, ExplicitRules.TableDef.Level> o1,
                    Pair<RolapLevel, ExplicitRules.TableDef.Level> o2)
                {
                    return Integer.compare(
                        o1.left.getDepth(),
                        o2.left.getDepth());
                }
            });
        Collections.sort(
            aggLevels,
            new Comparator<ExplicitRules.TableDef.Level>() {
                @Override
				public int compare(
                    ExplicitRules.TableDef.Level o1,
                    ExplicitRules.TableDef.Level o2)
                {
                    return Integer.compare(
                        o1.getRolapLevel().getDepth(),
                        o2.getRolapLevel().getDepth());
                }
            });
    }

    @Override
	protected String getFactCountColumnName
            (final JdbcSchema.Table.Column.Usage aggUsage) {
        String measureName = aggUsage.getColumn().getName();
        Map<org.eclipse.daanse.rolap.mapping.model.Column, org.eclipse.daanse.rolap.mapping.model.Column> measuresFactCount = tableDef.getMeasuresFactCount();
        Optional<Map.Entry<org.eclipse.daanse.rolap.mapping.model.Column, org.eclipse.daanse.rolap.mapping.model.Column>> op = measuresFactCount.entrySet()
        		.stream().filter(e -> e.getKey().getName().equals(measureName)).findAny();
        String factCountColumnName;
        if (op.isPresent() && !Util.isEmpty(op.get().getValue().getName())) {
        	factCountColumnName = op.get().getValue().getName();
        } else {
            factCountColumnName = getFactCountColumnName();
        }

        return factCountColumnName;
    }
}

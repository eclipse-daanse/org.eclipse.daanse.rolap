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

import static org.eclipse.daanse.rolap.common.util.ExpressionUtil.getExpression;
import static org.eclipse.daanse.rolap.common.util.RelationUtil.getAlias;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.daanse.olap.api.SqlExpression;
import org.eclipse.daanse.olap.api.aggregator.Aggregator;
import org.eclipse.daanse.olap.api.element.Dimension;
import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.exception.OlapRuntimeException;
import org.eclipse.daanse.rolap.aggregator.AbstractAggregator;
import org.eclipse.daanse.rolap.aggregator.AvgAggregator;
import org.eclipse.daanse.rolap.aggregator.DistinctCountAggregator;
import org.eclipse.daanse.rolap.aggregator.SumAggregator;
import org.eclipse.daanse.rolap.aggregator.countbased.AvgFromAvgAggregator;
import org.eclipse.daanse.rolap.aggregator.countbased.AvgFromSumAggregator;
import org.eclipse.daanse.rolap.aggregator.countbased.SumFromAvgAggregator;
import org.eclipse.daanse.rolap.common.HierarchyUsage;
import org.eclipse.daanse.rolap.common.RolapCatalog;
import org.eclipse.daanse.rolap.common.RolapColumn;
import org.eclipse.daanse.rolap.common.RolapCube;
import org.eclipse.daanse.rolap.common.RolapLevel;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.RolapVirtualCube;
import org.eclipse.daanse.rolap.common.Utils;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;
import org.eclipse.daanse.rolap.mapping.api.model.ColumnMapping;
import org.eclipse.daanse.rolap.mapping.api.model.RelationalQueryMapping;
import org.eclipse.daanse.rolap.recorder.MessageRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract Recognizer class used to determine if a candidate aggregate table
 * has the column categories: "fact_count" column, measure columns, foreign key
 * and level columns.
 *
 * Derived classes use either the default or explicit column descriptions in
 * matching column categories. The basic matching algorithm is in this class
 * while some specific column category matching and column building must be
 * specified in derived classes.
 *
 * A Recognizer is created per candidate aggregate table. The tables columns
 * are then categorized. All errors and warnings are added to a MessageRecorder.
 *
 * This class is less about defining a type and more about code sharing.
 *
 * @author Richard M. Emberson
 */
public abstract class Recognizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Recognizer.class);
    private final static String nonNumericFactCountColumn = """
        Candidate aggregate table ''{0}'' for fact table ''{1}'' has candidate fact count column ''{2}'' has type ''{3}'' that is not numeric.
    """;
    private final static String aggUnknownColumn =
        "Candidate aggregate table ''{0}'' for fact table ''{1}'' has a column ''{2}'' with unknown usage.";
    private final static String doubleMatchForLevel = """
        Double Match for candidate aggregate table ''{0}'' for fact table ''{1}'' and column ''{2}'' matched two hierarchies: 1) table=''{3}'', column=''{4}'' and 2) table=''{5}'', column=''{6}''
    """;
    private final static String noAggregatorFound = """
    No aggregator found while converting fact table aggregator: for usage
            ''{0}'', fact aggregator ''{1}'' and sibling aggregator ''{2}''
            """;
    private final static String noColumnNameFromExpression =
        "Could not get a column name from a level key expression: ''{0}''.";
    private final static String noFactCountColumns =
        "Candidate aggregate table ''{0}'' for fact table ''{1}'' has no fact count columns.";
    private final static String noMeasureColumns =
        "Candidate aggregate table ''{0}'' for fact table ''{1}'' has no measure columns.";
    private final static String tooManyFactCountColumns =
        "Candidate aggregate table ''{0}'' for fact table ''{1}'' has ''{2,number}'' fact count columns.";
    private final static String tooManyMatchingForeignKeyColumns = """
        Candidate aggregate table ''{0}'' for fact table ''{1}'' had ''{2,number}'' columns matching foreign key ''{3}''
    """;

    /**
     * This is used to wrap column name matching rules.
     */
    public interface Matcher {

        /**
         * Return true it the name matches and false otherwise.
         */
        boolean matches(String name);
    }

    protected final RolapStar star;
    protected final JdbcSchema.Table dbFactTable;
    protected final JdbcSchema.Table aggTable;
    protected final MessageRecorder msgRecorder;
    protected boolean returnValue;

    protected Recognizer(
        final RolapStar star,
        final JdbcSchema.Table dbFactTable,
        final JdbcSchema.Table aggTable,
        final MessageRecorder msgRecorder
    ) {
        this.star = star;
        this.dbFactTable = dbFactTable;
        this.aggTable = aggTable;
        this.msgRecorder = msgRecorder;

        returnValue = true;
    }

    /**
     * Return true if the candidate aggregate table was successfully mapped into
     * the fact table. This is the top-level checking method.
     * 
     * It first checks the ignore columns.
     * 
     * Next, the existence of a fact count column is checked.
     * 
     * Then the measures are checked. First the specified (defined,
     * explicit) measures are all determined. There must be at least one such
     * measure. This if followed by checking for implied measures (e.g., if base
     * fact table as both sum and average of a column and the aggregate has a
     * sum measure, the there is an implied average measure in the aggregate).
     * 
     * Now the levels are checked. This is in two parts. First, foreign keys are
     * checked followed by level columns (for collapsed dimension aggregates).
     * 
     * If eveything checks out, returns true.
     */
    public boolean check() {
        checkIgnores();
        checkFactColumns();

        // Check measures
        int nosMeasures = checkMeasures();
        // There must be at least one measure
        checkNosMeasures(nosMeasures);
        generateImpliedMeasures();

        // Check levels
        List<JdbcSchema.Table.Column.Usage> notSeenForeignKeys =
            checkForeignKeys();
        checkLevels(notSeenForeignKeys);

        if (returnValue) {
            // Add all unused columns as warning to the MessageRecorder
            checkUnusedColumns();
        }

        return returnValue;
    }

    /**
     * Return the ignore column Matcher.
     */
    protected abstract Matcher getIgnoreMatcher();

    /**
     * Check all columns to be marked as ignore.
     */
    protected void checkIgnores() {
        Matcher ignoreMatcher = getIgnoreMatcher();

        for (JdbcSchema.Table.Column aggColumn : aggTable.getColumns()) {
            if (ignoreMatcher.matches(aggColumn.getName())) {
                makeIgnore(aggColumn);
            }
        }
    }

    /**
     * Create an ignore usage for the aggColumn.
     */
    protected void makeIgnore(final JdbcSchema.Table.Column aggColumn) {
        JdbcSchema.Table.Column.Usage usage =
            aggColumn.newUsage(JdbcSchema.UsageType.IGNORE);
        usage.setSymbolicName("Ignore");
    }

    /**
     * Return the fact count column Matcher.
     */
    protected abstract Matcher getFactCountMatcher();

    /**
     * Return the fact count column Matcher.
     */
    protected abstract Matcher getMeasureFactCountMatcher();

    /**
     * Make sure that the aggregate table has one fact count column and that its
     * type is numeric and find measure fact columns
     */
    protected void checkFactColumns() {
        msgRecorder.pushContextName("Recognizer.checkFactCount");

        try {
            Matcher factCountMatcher = getFactCountMatcher();
            int nosOfFactCounts = 0;
            for (JdbcSchema.Table.Column aggColumn : aggTable.getColumns()) {
                // if marked as ignore, then do not consider
                if (aggColumn.hasUsage(JdbcSchema.UsageType.IGNORE)) {
                    continue;
                }
                if (factCountMatcher.matches(aggColumn.getName())) {
                    if (aggColumn.getDatatype().isNumeric()) {
                        makeFactCount(aggColumn);
                        nosOfFactCounts++;
                    } else {
                        String msg = MessageFormat.format(nonNumericFactCountColumn,
                            aggTable.getName(),
                            dbFactTable.getName(),
                            aggColumn.getName(),
                            aggColumn.getTypeName());
                        msgRecorder.reportError(msg);

                        returnValue = false;
                    }
                }
            }
            if (nosOfFactCounts == 0) {
                String msg = MessageFormat.format(noFactCountColumns,
                    aggTable.getName(),
                    dbFactTable.getName());
                msgRecorder.reportError(msg);

                returnValue = false;

            } else if (nosOfFactCounts > 1) {
                String msg = MessageFormat.format(tooManyFactCountColumns,
                    aggTable.getName(),
                    dbFactTable.getName(),
                    nosOfFactCounts);
                msgRecorder.reportError(msg);

                returnValue = false;
            } else {
                // if fact column is correct
                // find fact columns for specific measure
                Matcher measureFactCountMatcher = getMeasureFactCountMatcher();
                for (JdbcSchema.Table.Column aggColumn
                    : aggTable.getColumns()) {
                    if (!aggColumn.hasUsage()
                        && measureFactCountMatcher.matches
                        (aggColumn.getName())) {
                        makeMeasureFactCount(aggColumn);
                    }
                }
            }
        } finally {
            msgRecorder.popContextName();
        }
    }

    /**
     * Check all measure columns returning the number of measure columns.
     */
    protected abstract int checkMeasures();

    /**
     * Create a fact count usage for the aggColumn.
     */
    protected void makeFactCount(final JdbcSchema.Table.Column aggColumn) {
        JdbcSchema.Table.Column.Usage usage =
            aggColumn.newUsage(JdbcSchema.UsageType.FACT_COUNT);
        usage.setSymbolicName("Fact Count");
    }

    /**
     * Create measure fact count usage for the aggColumn.
     */
    protected void makeMeasureFactCount
    (final JdbcSchema.Table.Column aggColumn) {
        JdbcSchema.Table.Column.Usage usage =
            aggColumn.newUsage(JdbcSchema.UsageType.MEASURE_FACT_COUNT);
        usage.setSymbolicName("Measure Fact Count");
    }

    protected String getFactCountColumnName() {
        // iterator over fact count usages - in the end there can be only one!!
        Iterator<JdbcSchema.Table.Column.Usage> it =
            aggTable.getColumnUsages(JdbcSchema.UsageType.FACT_COUNT);
        return it.hasNext() ? it.next().getColumn().getName() : null;
    }

    protected abstract String getFactCountColumnName
        (final JdbcSchema.Table.Column.Usage aggUsage);

    /**
     * Make sure there was at least one measure column identified.
     */
    protected void checkNosMeasures(int nosMeasures) {
        msgRecorder.pushContextName("Recognizer.checkNosMeasures");

        try {
            if (nosMeasures == 0) {
                String msg = MessageFormat.format(noMeasureColumns,
                    aggTable.getName(),
                    dbFactTable.getName());
                msgRecorder.reportError(msg);

                returnValue = false;
            }
        } finally {
            msgRecorder.popContextName();
        }
    }

    /**
     * An implied measure in an aggregate table is one where there is both a sum
     * and average measures in the base fact table and the aggregate table has
     * either a sum or average, the other measure is implied and can be
     * generated from the measure and the fact_count column.
     * 
     * For each column in the fact table, get its measure usages. If there is
     * both an average and sum aggregator associated with the column, then
     * iterator over all of the column usage of type measure of the aggregator
     * table. If only one aggregate column usage measure is found and this
     * RolapStar.Measure measure instance variable is the same as the
     * the fact table's usage's instance variable, then the other measure is
     * implied and the measure is created for the aggregate table.
     */
    protected void generateImpliedMeasures() {
        for (JdbcSchema.Table.Column factColumn : aggTable.getColumns()) {
            JdbcSchema.Table.Column.Usage sumFactUsage = null;
            JdbcSchema.Table.Column.Usage avgFactUsage = null;

            for (Iterator<JdbcSchema.Table.Column.Usage> mit =
                 factColumn.getUsages(JdbcSchema.UsageType.MEASURE);
                 mit.hasNext(); ) {
                JdbcSchema.Table.Column.Usage factUsage = mit.next();
                if (factUsage.getAggregator() == AvgAggregator.INSTANCE) {
                    avgFactUsage = factUsage;
                } else if (factUsage.getAggregator() == SumAggregator.INSTANCE) {
                    sumFactUsage = factUsage;
                }
            }

            if (avgFactUsage != null && sumFactUsage != null) {
                JdbcSchema.Table.Column.Usage sumAggUsage = null;
                JdbcSchema.Table.Column.Usage avgAggUsage = null;
                int seenCount = 0;
                for (Iterator<JdbcSchema.Table.Column.Usage> mit =
                     aggTable.getColumnUsages(JdbcSchema.UsageType.MEASURE);
                     mit.hasNext(); ) {
                    JdbcSchema.Table.Column.Usage aggUsage = mit.next();
                    if (aggUsage.rMeasure == avgFactUsage.rMeasure) {
                        avgAggUsage = aggUsage;
                        seenCount++;
                    } else if (aggUsage.rMeasure == sumFactUsage.rMeasure) {
                        sumAggUsage = aggUsage;
                        seenCount++;
                    }
                }
                if (seenCount == 1) {
                    if (avgAggUsage != null) {
                        makeMeasure(sumFactUsage, avgAggUsage);
                    }
                    if (sumAggUsage != null) {
                        makeMeasure(avgFactUsage, sumAggUsage);
                    }
                }
            }
        }
    }

    /**
     * Here we have the fact usage of either sum or avg and an aggregate usage
     * of the opposite type. We wish to make a new aggregate usage based
     * on the existing usage's column of the same type as the fact usage.
     *
     * @param factUsage       fact usage
     * @param aggSiblingUsage existing sibling usage
     */
    protected void makeMeasure(
        final JdbcSchema.Table.Column.Usage factUsage,
        final JdbcSchema.Table.Column.Usage aggSiblingUsage
    ) {
        JdbcSchema.Table.Column aggColumn = aggSiblingUsage.getColumn();

        JdbcSchema.Table.Column.Usage aggUsage =
            aggColumn.newUsage(JdbcSchema.UsageType.MEASURE);

        aggUsage.setSymbolicName(factUsage.getSymbolicName());
        Aggregator ra = convertAggregator(
            aggUsage,
            factUsage.getAggregator(),
            aggSiblingUsage.getAggregator());
        aggUsage.setAggregator(ra);
        aggUsage.rMeasure = factUsage.rMeasure;
    }

    /**
     * Creates an aggregate table column measure usage from a fact
     * table column measure usage.
     */
    protected void makeMeasure(
        final JdbcSchema.Table.Column.Usage factUsage,
        final JdbcSchema.Table.Column aggColumn
    ) {
        JdbcSchema.Table.Column.Usage aggUsage =
            aggColumn.newUsage(JdbcSchema.UsageType.MEASURE);

        aggUsage.setSymbolicName(factUsage.getSymbolicName());
        Aggregator ra =
            convertAggregator(aggUsage, factUsage.getAggregator());
        aggUsage.setAggregator(ra);
        aggUsage.rMeasure = factUsage.rMeasure;
    }

    /**
     * This method determine how may aggregate table column's match the fact
     * table foreign key column return in the number matched. For each matching
     * column a foreign key usage is created.
     */
    protected abstract int matchForeignKey(
        JdbcSchema.Table.Column.Usage factUsage
    );

    /**
     * This method checks the foreign key columns.
     * 
     * For each foreign key column usage in the fact table, determine how many
     * aggregate table columns match that column usage. If there is more than
     * one match, then that is an error. If there were no matches, then the
     * foreign key usage is added to the list of fact column foreign key that
     * were not in the aggregate table. This list is returned by this method.
     * 
     * This matches foreign keys that were not "lost" or "collapsed".
     *
     * @return list on not seen foreign key column usages
     */
    protected List<JdbcSchema.Table.Column.Usage> checkForeignKeys() {
        msgRecorder.pushContextName("Recognizer.checkForeignKeys");

        try {
            List<JdbcSchema.Table.Column.Usage> notSeenForeignKeys =
                Collections.emptyList();

            for (Iterator<JdbcSchema.Table.Column.Usage> it =
                 dbFactTable.getColumnUsages(JdbcSchema.UsageType.FOREIGN_KEY);
                 it.hasNext(); ) {
                JdbcSchema.Table.Column.Usage factUsage = it.next();

                int matchCount = matchForeignKey(factUsage);

                if (matchCount > 1) {
                    String msg = MessageFormat.format(tooManyMatchingForeignKeyColumns,
                        aggTable.getName(),
                        dbFactTable.getName(),
                        matchCount,
                        factUsage.getColumn().getName());
                    msgRecorder.reportError(msg);

                    returnValue = false;

                } else if (matchCount == 0) {
                    if (notSeenForeignKeys.isEmpty()) {
                        notSeenForeignKeys =
                            new ArrayList<>();
                    }
                    notSeenForeignKeys.add(factUsage);
                }
            }
            return notSeenForeignKeys;
        } finally {
            msgRecorder.popContextName();
        }
    }

    /**
     * This method identifies those columns in the aggregate table that match
     * "collapsed" dimension columns. Remember that a collapsed dimension is one
     * where the higher levels of some hierarchy are columns in the aggregate
     * table (and all of the lower levels are missing - it has aggregated up to
     * the first existing level).
     * 
     * Here, we do not start from the fact table, we iterator over each cube.
     * For each of the cube's dimensions, the dimension's hirarchies are
     * iterated over. In turn, each hierarchy's usage is iterated over.
     * if the hierarchy's usage's foreign key is not in the list of not seen
     * foreign keys (the notSeenForeignKeys parameter), then that hierarchy is
     * not considered. If the hierarchy's usage's foreign key is in the not seen
     * list, then starting with the hierarchy's top level, it is determined if
     * the combination of hierarchy, hierarchy usage, and level matches an
     * aggregated table column. If so, then a level usage is created for that
     * column and the hierarchy's next level is considered and so on until a
     * for a level an aggregate table column does not match. Then we continue
     * iterating over the hierarchy usages.
     * 
     * This check is different. The others mine the fact table usages. This
     * looks through the fact table's cubes' dimension, hierarchy,
     * hiearchy usages, levels to match up symbolic names for levels. The other
     * checks match on "physical" characteristics, the column name; this matches
     * on "logical" characteristics.
     * 
     * Note: Levels should not be created for foreign keys that WERE seen.
     * Currently, this is NOT checked explicitly. For the explicit rules any
     * extra columns MUST ge declared ignored or one gets an error.
     */
    protected void checkLevels(
        List<JdbcSchema.Table.Column.Usage> notSeenForeignKeys
    ) {
        // These are the factTable that do not appear in the aggTable.
        // 1) find all cubes with this given factTable
        // 1) per cube, find all usages with the column as foreign key
        // 2) for each usage, find dimension and its levels
        // 3) determine if level columns are represented

        // In generaly, there is only one cube.
        for (RolapCube cube : findCubes()) {
            List<? extends Dimension> dims = cube.getDimensions();
            // start dimensions at 1 (0 is measures)
            for (int j = 1; j < dims.size(); j++) {
                Dimension dim = dims.get(j);
                // Ok, got dimension.
                // See if any of the levels exist as columns in the
                // aggTable. This requires applying a map from:
                //   hierarchyName
                //   levelName
                //   levelColumnName
                // to each "unassigned" column in the aggTable.
                // Remember that the rule is if a level does appear,
                // then all of the higher levels must also appear.

                List<? extends Hierarchy> hierarchies = dim.getHierarchies();
                for (Hierarchy hierarchy : hierarchies) {
                    HierarchyUsage[] hierarchyUsages =
                        cube.getUsages(hierarchy);
                    for (HierarchyUsage hierarchyUsage : hierarchyUsages) {
                        // Search through the notSeenForeignKeys list
                        // making sure that this HierarchyUsage's
                        // foreign key is not in the list.
                    	ColumnMapping foreignKey = hierarchyUsage.getForeignKey();
                        boolean b = foreignKey == null
                            || inNotSeenForeignKeys(
                            foreignKey,
                            notSeenForeignKeys);
                        if (!b) {
                            // It was not in the not seen list, so ignore
                            continue;
                        }

                        matchLevels(hierarchy, hierarchyUsage);
                    }
                }
            }
        }
    }

    /**
     * Return true if the foreignKey column name is in the list of not seen
     * foreign keys.
     */
    boolean inNotSeenForeignKeys(
    	ColumnMapping foreignKey,
        List<JdbcSchema.Table.Column.Usage> notSeenForeignKeys
    ) {
        for (JdbcSchema.Table.Column.Usage usage : notSeenForeignKeys) {
            if (usage.getColumn().getName().equals(foreignKey != null ? foreignKey.getName() : null)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Debug method: Print out not seen foreign key list.
     */
    private void printNotSeenForeignKeys(List<JdbcSchema.Table.Column.Usage> notSeenForeignKeys) {
        LOGGER.debug(
            "Recognizer.printNotSeenForeignKeys: {}", aggTable.getName());
        for (Iterator<JdbcSchema.Table.Column.Usage> it = notSeenForeignKeys.iterator(); it.hasNext(); ) {
            JdbcSchema.Table.Column.Usage usage = it.next();
            String msg = new StringBuilder("  ").append(usage.getColumn().getName()).toString();
            LOGGER.debug(msg);
        }
    }

    /**
     * Here a measure ussage is created and the right join condition is
     * explicitly supplied. This is needed is when the aggregate table's column
     * names may not match those found in the RolapStar.
     */
    protected void makeForeignKey(
        final JdbcSchema.Table.Column.Usage factUsage,
        final JdbcSchema.Table.Column aggColumn,
        final String rightJoinConditionColumnName
    ) {
        JdbcSchema.Table.Column.Usage aggUsage =
            aggColumn.newUsage(JdbcSchema.UsageType.FOREIGN_KEY);
        aggUsage.setSymbolicName("FOREIGN_KEY");
        // Extract from RolapStar enough stuff to build
        // AggStar subtable except the column name of the right join
        // condition might be different
        aggUsage.rTable = factUsage.rTable;
        aggUsage.rightJoinConditionColumnName = rightJoinConditionColumnName;

        aggUsage.rColumn = factUsage.rColumn;
    }

    /**
     * Match a aggregate table column given the hierarchy and hierarchy usage.
     */
    protected abstract void matchLevels(
        final Hierarchy hierarchy,
        final HierarchyUsage hierarchyUsage
    );

    /**
     * Make a level column usage.
     *
     *  Note there is a check in this code. If a given aggregate table
     * column has already has a level usage, then that usage must all refer to
     * the same hierarchy usage join table and column name as the one that
     * calling this method was to create. If there is an existing level usage
     * for the column and it matches something else, then it is an error.
     */
    protected void makeLevelColumnUsage(
        final JdbcSchema.Table.Column aggColumn,
        final HierarchyUsage hierarchyUsage,
        final String factColumnName,
        final String levelColumnName,
        final String symbolicName,
        final boolean isCollapsed,
        final RolapLevel rLevel,
        JdbcSchema.Table.Column ordinalColumn,
        JdbcSchema.Table.Column captionColumn,
        Map<String, JdbcSchema.Table.Column> properties
    ) {
        msgRecorder.pushContextName("Recognizer.makeLevelColumnUsage");

        try {
            if (aggColumn.hasUsage(JdbcSchema.UsageType.LEVEL)) {
                // The column has at least one usage of level type
                // make sure we are looking at the
                // same table and column
                for (Iterator<JdbcSchema.Table.Column.Usage> uit =
                     aggColumn.getUsages(JdbcSchema.UsageType.LEVEL);
                     uit.hasNext(); ) {
                    JdbcSchema.Table.Column.Usage aggUsage = uit.next();

                    RelationalQueryMapping rel = hierarchyUsage.getJoinTable();

                    if (!aggUsageMatchesHierarchyUsage(aggUsage,
                        hierarchyUsage, levelColumnName)) {
                        // Levels should have only one usage.
                        String msg = MessageFormat.format(doubleMatchForLevel,
                            aggTable.getName(),
                            dbFactTable.getName(),
                            aggColumn.getName(),
                            aggUsage.relation.toString(),
                            aggColumn.column.getName(),
                            rel.toString(),
                            levelColumnName);
                        msgRecorder.reportError(msg);

                        returnValue = false;

                        msgRecorder.throwRTException();
                    }
                }
            } else {
                JdbcSchema.Table.Column.Usage aggUsage =
                    aggColumn.newUsage(JdbcSchema.UsageType.LEVEL);
                // Cache table and column for the above
                // check
                aggUsage.relation = hierarchyUsage.getJoinTable();
                aggUsage.joinExp = hierarchyUsage.getJoinExp();
                aggUsage.levelColumnName = levelColumnName;
                aggUsage.rightJoinConditionColumnName = levelColumnName;
                aggUsage.collapsed = isCollapsed;
                aggUsage.level = rLevel;
                aggUsage.captionColumn = captionColumn;
                aggUsage.ordinalColumn = ordinalColumn;
                aggUsage.properties = properties;

                makeLevelExtraUsages(
                    aggUsage.captionColumn, aggUsage.ordinalColumn,
                    aggUsage.properties);

                aggUsage.setSymbolicName(symbolicName);

                String tableAlias;
                if (aggUsage.joinExp instanceof org.eclipse.daanse.rolap.common.RolapColumn mcolumn) {
                    tableAlias = mcolumn.getTable();
                } else {
                    tableAlias = getAlias(aggUsage.relation);
                }

                RolapStar.Table factTable = star.getFactTable();
                RolapStar.Table descTable =
                    factTable.findDescendant(tableAlias);

                if (descTable == null) {
                    // TODO: what to do here???
                    StringBuilder buf = new StringBuilder(256);
                    buf.append("descendant table is null for factTable=");
                    buf.append(factTable.getAlias());
                    buf.append(", tableAlias=");
                    buf.append(tableAlias);
                    msgRecorder.reportError(buf.toString());

                    returnValue = false;

                    msgRecorder.throwRTException();
                }

                RolapStar.Column rc = null;
                if (descTable != null) {
                    rc = descTable.lookupColumn(factColumnName);
                }
                if (rc == null) {
                    rc = lookupInChildren(descTable, factColumnName);
                }

                if (rc == null && hierarchyUsage.getUsagePrefix() != null && descTable != null) {
                    // look for the name w/ usage prefix stripped off
                    rc = descTable.lookupColumn(
                        factColumnName.substring(
                            hierarchyUsage.getUsagePrefix().length()));
                }
                if (rc == null) {
                    StringBuilder buf = new StringBuilder(256);
                    buf.append("Rolap.Column not found (null) for tableAlias=");
                    buf.append(tableAlias);
                    buf.append(", factColumnName=");
                    buf.append(factColumnName);
                    buf.append(", levelColumnName=");
                    buf.append(levelColumnName);
                    buf.append(", symbolicName=");
                    buf.append(symbolicName);
                    msgRecorder.reportError(buf.toString());

                    returnValue = false;

                    msgRecorder.throwRTException();
                } else {
                    aggUsage.rColumn = rc;
                }
            }
        } finally {
            msgRecorder.popContextName();
        }
    }

    /**
     * If present, marks caption/ordinal/properties columns as LEVEL_EXTRA
     */
    private void makeLevelExtraUsages(
        JdbcSchema.Table.Column captionColumn,
        JdbcSchema.Table.Column ordinalColumn,
        Map<String, JdbcSchema.Table.Column> properties
    ) {
        if (captionColumn != null) {
            captionColumn.newUsage(JdbcSchema.UsageType.LEVEL_EXTRA);
        }
        if (ordinalColumn != null) {
            ordinalColumn.newUsage(JdbcSchema.UsageType.LEVEL_EXTRA);
        }
        if (properties != null) {
            for (JdbcSchema.Table.Column column : properties.values()) {
                column.newUsage(JdbcSchema.UsageType.LEVEL_EXTRA);
            }
        }
    }

    /**
     * returns true if aggUsage matches the relation and
     * column name of hiearchyUsage & levelColumnName.
     * Adjusts aggUsage column name based on usagePrefix, if present.
     */
    private boolean aggUsageMatchesHierarchyUsage(
        JdbcSchema.Table.Column.Usage aggUsage,
        HierarchyUsage hierarchyUsage,
        String levelColumnName
    ) {
        RelationalQueryMapping rel = hierarchyUsage.getJoinTable();

        JdbcSchema.Table.Column aggColumn = aggUsage.getColumn();
        String aggColumnName = aggColumn.column.getName();
        String usagePrefix = hierarchyUsage.getUsagePrefix() == null
            ? "" : hierarchyUsage.getUsagePrefix();

        if (usagePrefix.length() > 0
            && !usagePrefix.equals(
            aggColumnName.substring(0, usagePrefix.length()))) {
            throw new OlapRuntimeException(
                new StringBuilder("usagePrefix attribute ")
                    .append(usagePrefix)
                    .append(" was specified for ").append(hierarchyUsage.getHierarchyName())
                    .append(", but found agg column without prefix:  ").append(aggColumnName).toString());
        }
        String aggColumnWithoutPrefix = aggColumnName.substring(
            usagePrefix.length());

        return Utils.equalsQuery(aggUsage.relation, rel)
            && aggColumnWithoutPrefix.equalsIgnoreCase(levelColumnName);
    }

    protected RolapStar.Column lookupInChildren(
        final RolapStar.Table table,
        final String factColumnName
    ) {
        // This can happen if we are looking at a collapsed dimension
        // table, and the collapsed dimension in question in the
        // fact table is a snowflake (not just a star), so we
        // must look deeper...
        for (RolapStar.Table child : table.getChildren()) {
            RolapStar.Column rc = child.lookupColumn(factColumnName);
            if (rc != null) {
                return rc;
            } else {
                rc = lookupInChildren(child, factColumnName);
                if (rc != null) {
                    return rc;
                }
            }
        }
        return null;
    }

    // Question: what if foreign key is seen, but there are also level
    // columns - is this at least is a warning.

    /**
     * If everything is ok, issue warning for each aggTable column
     * that has not been identified as a FACT_COLUMN, MEASURE_COLUMN or
     * LEVEL_COLUMN.
     */
    protected void checkUnusedColumns() {
        msgRecorder.pushContextName("Recognizer.checkUnusedColumns");
        // Collection of messages for unused columns, sorted by column name
        // so that tests are deterministic.
        SortedMap<String, String> unusedColumnMsgs =
            new TreeMap<>();
        for (JdbcSchema.Table.Column aggColumn : aggTable.getColumns()) {
            if (!aggColumn.hasUsage()) {
                String msg = MessageFormat.format(aggUnknownColumn,
                    aggTable.getName(),
                    dbFactTable.getName(),
                    aggColumn.getName());
                unusedColumnMsgs.put(aggColumn.getName(), msg);
                // since the column has no usage it will be ignored
                makeIgnore(aggColumn);
            }
        }
        for (String msg : unusedColumnMsgs.values()) {
            msgRecorder.reportWarning(msg);
        }
        msgRecorder.popContextName();
    }

    /**
     * Figure out what aggregator should be associated with a column usage.
     * Generally, this aggregator is simply the RolapAggregator returned by
     * calling the getRollup() method of the fact table column's
     * RolapAggregator. But in the case that the fact table column's
     * RolapAggregator is the "Avg" aggregator, then the special
     * RolapAggregator.AvgFromSum is used.
     * 
     * Note: this code assumes that the aggregate table does not have an
     * explicit average aggregation column.
     */
    protected Aggregator convertAggregator(
        final JdbcSchema.Table.Column.Usage aggUsage,
        final Aggregator factAgg
    ) {
        // NOTE: This assumes that the aggregate table does not have an explicit
        // average column.
        if (factAgg == AvgAggregator.INSTANCE) {
            String columnExpr = getFactCountExpr(aggUsage);
            return new AvgFromSumAggregator(columnExpr);
        } else {
            return factAgg;
        }
    }

    /**
     * The method chooses a special aggregator for the aggregate table column's
     * usage.
     *
     * If the fact table column's aggregator was "Avg":
     *   then if the sibling aggregator was "Avg":
     *      the new aggregator is RolapAggregator.AvgFromAvg
     *   else if the sibling aggregator was "Sum":
     *      the new aggregator is RolapAggregator.AvgFromSum
     * else if the fact table column's aggregator was "Sum":
     *   if the sibling aggregator was "Avg":
     *      the new aggregator is RolapAggregator.SumFromAvg
     *
     * Note that there is no SumFromSum since that is not a special case
     * requiring a special aggregator.
     * 
     * if no new aggregator was selected, then the fact table's aggregator
     * rollup aggregator is used.
     */
    protected Aggregator convertAggregator(
        final JdbcSchema.Table.Column.Usage aggUsage,
        final Aggregator factAgg,
        final Aggregator siblingAgg
    ) {
        msgRecorder.pushContextName("Recognizer.convertAggregator");
        Aggregator rollupAgg = null;

        String columnExpr = getFactCountExpr(aggUsage);
        if (factAgg == AvgAggregator.INSTANCE) {
            if (siblingAgg == AvgAggregator.INSTANCE) {
                rollupAgg = new AvgFromAvgAggregator(columnExpr);
            } else if (siblingAgg == SumAggregator.INSTANCE) {
                rollupAgg = new AvgFromSumAggregator(columnExpr);
            }
        } else if (factAgg == SumAggregator.INSTANCE) {
            if (siblingAgg == AvgAggregator.INSTANCE|| siblingAgg instanceof AvgFromAvgAggregator) {
                rollupAgg = new SumFromAvgAggregator(columnExpr);
            }
        } else if (factAgg == DistinctCountAggregator.INSTANCE) {
            rollupAgg = factAgg;
        }

        if (rollupAgg == null && factAgg != null) {
            rollupAgg = (AbstractAggregator) factAgg.getRollup();
        }

        if (rollupAgg == null && factAgg != null) {
            String msg = MessageFormat.format(noAggregatorFound,
                aggUsage.getSymbolicName(),
                factAgg.getName(),
                siblingAgg.getName());
            msgRecorder.reportError(msg);
        }

        msgRecorder.popContextName();
        return rollupAgg;
    }

    protected void checkMeasureFactCount() {
        JdbcSchema.Table.Column factColumn = null;
        Set<String> allowedMeasureFactColumnNames = new HashSet<>();

        for (Iterator<JdbcSchema.Table.Column.Usage> it =
             aggTable.getColumnUsages(JdbcSchema.UsageType.FACT_COUNT);
             it.hasNext(); ) {
            JdbcSchema.Table.Column.Usage usage = it.next();
            factColumn = usage.getColumn();
        }

        if (factColumn != null) {
            for (Iterator<JdbcSchema.Table.Column.Usage> it =
                 aggTable.getColumnUsages(JdbcSchema.UsageType.MEASURE);
                 it.hasNext(); ) {
                JdbcSchema.Table.Column.Usage usage = it.next();
                JdbcSchema.Table.Column measureColumn = usage.getColumn();
                allowedMeasureFactColumnNames.add
                    (new StringBuilder(factColumn.getName()).append("_").append(measureColumn.getName()).toString());
            }

            for (JdbcSchema.Table.Column aggColumn : aggTable.getColumns()) {
                if (!aggColumn.hasUsage()
                    && allowedMeasureFactColumnNames.contains
                    (aggColumn.getName())) {
                    makeMeasureFactCount(aggColumn);
                }
            }
        }
    }

    /**
     * Given an aggregate table column usage, find the column name of the
     * table's fact count column usage.
     *
     * @param aggUsage Aggregate table column usage
     * @return The name of the column which holds the fact count.
     */
    protected String getFactCountExpr
    (final JdbcSchema.Table.Column.Usage aggUsage) {
        String tableName = aggTable.getName();
        String factCountColumnName = getFactCountColumnName(aggUsage);

        // we want the fact count expression
        org.eclipse.daanse.rolap.common.RolapColumn column =
            new org.eclipse.daanse.rolap.common.RolapColumn(tableName, factCountColumnName);
        SqlQuery sqlQuery = star.getSqlQuery();
        return getExpression(column, sqlQuery);
    }

    /**
     * Finds all cubes that use this fact table.
     */
    protected List<RolapCube> findCubes() {
        String name = dbFactTable.getName();

        List<RolapCube> list = new ArrayList<>();
        RolapCatalog schema = star.getCatalog();
        for (RolapCube cube : schema.getCubeList()) {
            if (cube instanceof RolapVirtualCube) {
                continue;
            }
            RolapStar cubeStar = cube.getStar();
            String factTableName = getFactTableName(cubeStar);
            if (name.equals(factTableName)) {
                list.add(cube);
            }
        }
        return list;
    }

    private String getFactTableName(RolapStar star) {
        String factTableName = star.getFactTable().getTableName();
        return
            factTableName == null
                ? star.getFactTable().getAlias()
                : factTableName;
    }

    /**
     * Given a MappingExpression, returns
     * the associated column name.
     *
     * Note: if the MappingExpression is
     * not a MappingColumn or mondrian.olap.KeyExpression, returns null. This
     * will result in an error.
     */
    protected String getColumnName(SqlExpression expr) {
        msgRecorder.pushContextName("Recognizer.getColumnName");

        try {
            if (expr instanceof RolapColumn column) {
                return column.getName();
            }
            return null;
        } finally {
            msgRecorder.popContextName();
        }
    }
}

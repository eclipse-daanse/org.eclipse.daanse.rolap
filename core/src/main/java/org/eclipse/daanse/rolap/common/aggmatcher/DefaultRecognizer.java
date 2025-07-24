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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.daanse.olap.api.element.Hierarchy;
import org.eclipse.daanse.olap.api.element.Level;
import  org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.common.HierarchyUsage;
import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.aggmatcher.JdbcSchema.Table.Column;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.recorder.MessageRecorder;


/**
 * This is the default Recognizer. It uses the rules found in the file
 * DefaultRules.xml to find aggregate tables and there columns.
 *
 * @author Richard M. Emberson
 */
class DefaultRecognizer extends Recognizer {

    private final DefaultRules aggDefault;
    private final static String aggMultipleMatchingMeasure = """
        Context ''{0}'': Candidate aggregate table ''{1}'' for fact table ''{2}'' has ''{3,number}'' columns matching measure ''{4}'', ''{5}'', ''{6}''\".
    """;

    DefaultRecognizer(
        final DefaultRules aggDefault,
        final RolapStar star,
        final JdbcSchema.Table dbFactTable,
        final JdbcSchema.Table aggTable,
        final MessageRecorder msgRecorder)
    {
        super(star, dbFactTable, aggTable, msgRecorder);
        this.aggDefault = aggDefault;
    }

    /**
     * Get the DefaultRules instance associated with this object.
     */
    DefaultRules getRules() {
        return aggDefault;
    }

    /**
     * Get the Matcher to be used to match columns to be ignored.
     */
    @Override
	protected Recognizer.Matcher getIgnoreMatcher() {
        return getRules().getIgnoreMatcher();
    }

    /**
     * Get the Matcher to be used to match the column which is the fact count
     * column.
     */
    @Override
	protected Recognizer.Matcher getFactCountMatcher() {
        return getRules().getFactCountMatcher();
    }

    @Override
    protected Matcher getMeasureFactCountMatcher() {
        return new Matcher() {
            @Override
            public boolean matches(String name) {
                String factCountColumnName = getFactCountColumnName();
                return name.contains(factCountColumnName);
            }
        };
    }

    /**
     * Get the Match used to identify columns that are measures.
     */
    protected Recognizer.Matcher getMeasureMatcher(
        JdbcSchema.Table.Column.Usage factUsage)
    {
        String measureName = factUsage.getSymbolicName();
        String measureColumnName = factUsage.getColumn().getName();
        String aggregateName = factUsage.getAggregator().getName();

        return getRules().getMeasureMatcher(
            measureName,
            measureColumnName,
            aggregateName);
    }

    /**
     * Create measures for an aggregate table.
     * 
     * First, iterator through all fact table measure usages.
     * Create a Matcher for each such usage.
     * Iterate through all aggregate table columns.
     * For each column that matches create a measure usage.
     * 
     * Per fact table measure usage, at most only one aggregate measure should
     * be created.
     *
     * @return number of measures created.
     */
    @Override
	protected int checkMeasures() {
        msgRecorder.pushContextName("DefaultRecognizer.checkMeasures");

        try {
            int measureCountCount = 0;

            for (Iterator<JdbcSchema.Table.Column.Usage> it =
                     dbFactTable.getColumnUsages(JdbcSchema.UsageType.MEASURE);
                it.hasNext();)
            {
                JdbcSchema.Table.Column.Usage factUsage = it.next();

                Matcher matcher = getMeasureMatcher(factUsage);

                int matchCount = 0;
                for (JdbcSchema.Table.Column aggColumn
                    : aggTable.getColumns())
                {
                    // if marked as ignore, then do not consider
                    if (aggColumn.hasUsage(JdbcSchema.UsageType.IGNORE)) {
                        continue;
                    }

                    if (matcher.matches(aggColumn.getName())) {
                        makeMeasure(factUsage, aggColumn);

                        measureCountCount++;
                        matchCount++;
                    }
                }

                if (matchCount > 1) {
                    String msg = MessageFormat.format(aggMultipleMatchingMeasure,
                        msgRecorder.getContext(),
                        aggTable.getName(),
                        dbFactTable.getName(),
                        matchCount,
                        factUsage.getSymbolicName(),
                        factUsage.getColumn().getName(),
                        factUsage.getAggregator().getName());
                    msgRecorder.reportError(msg);

                    returnValue = false;
                }
            }
            return measureCountCount;
        } finally {
            msgRecorder.popContextName();
        }
    }

    /**
     * This creates a foreign key usage.
     *
     * Using the foreign key Matcher with the fact usage's column name the
     * aggregate table's columns are searched for one that matches.  For each
     * that matches a foreign key usage is created (thought if more than one is
     * created its is an error which is handled in the calling code.
     */
    @Override
	protected int matchForeignKey(JdbcSchema.Table.Column.Usage factUsage) {
        JdbcSchema.Table.Column factColumn = factUsage.getColumn();

        // search to see if any of the aggTable's columns match
        Recognizer.Matcher matcher =
            getRules().getForeignKeyMatcher(factColumn.getName());

        int matchCount = 0;
        for (JdbcSchema.Table.Column aggColumn : aggTable.getColumns()) {
            // if marked as ignore, then do not consider
            if (aggColumn.hasUsage(JdbcSchema.UsageType.IGNORE)) {
                continue;
            }

            if (matcher.matches(aggColumn.getName())) {
                makeForeignKey(factUsage, aggColumn, null);
                matchCount++;
            }
        }
        return matchCount;
    }

    /**
     * Create level usages.
     *
     *  A Matcher is created using the Hierarchy's name, the RolapLevel
     * name, and the column name associated with the RolapLevel's key
     * expression.  The aggregate table columns are search for the first match
     * and, if found, a level usage is created for that column.
     */
    @Override
	protected void matchLevels(
        final Hierarchy hierarchy,
        final HierarchyUsage hierarchyUsage)
    {
        msgRecorder.pushContextName("DefaultRecognizer.matchLevel");
        try {
            List<Pair<RolapLevel, JdbcSchema.Table.Column>> levelMatches =
                new ArrayList<>();
            level_loop:
            for (Level level : hierarchy.getLevels()) {
                if (level.isAll()) {
                    continue;
                }
                final RolapLevel rLevel = (RolapLevel) level;

                String usagePrefix = hierarchyUsage.getUsagePrefix();
                String hierName = hierarchy.getName();
                String levelName = rLevel.getName();
                String levelColumnName = getColumnName(rLevel.getKeyExp());

                Recognizer.Matcher matcher = getRules().getLevelMatcher(
                    usagePrefix, hierName, levelName, levelColumnName);

                for (JdbcSchema.Table.Column aggColumn
                    : aggTable.getColumns())
                {
                    if (matcher.matches(aggColumn.getName())) {
                        levelMatches.add(
                            new Pair<>(
                                    rLevel, aggColumn));
                        continue level_loop;
                    }
                }
            }
            if (levelMatches.size() == 0) {
                return;
            }
            // Sort the matches by level depth.
            Collections.sort(
                levelMatches,
                new Comparator<Pair<RolapLevel, JdbcSchema.Table.Column>>() {
                    @Override
					public int compare(
                        Pair<RolapLevel, Column> o1,
                        Pair<RolapLevel, Column> o2)
                    {
                        return
                            Integer.valueOf(o1.left.getDepth()).compareTo(
                                Integer.valueOf(o2.left.getDepth()));
                    }
                });
            // Validate by iterating.
            for (Pair<RolapLevel, JdbcSchema.Table.Column> pair
                : levelMatches)
            {
                boolean collapsed = true;
                if (levelMatches.indexOf(pair) == 0
                    && pair.left.getDepth() > 1)
                {
                    collapsed = false;
                }
                // Fail if the level is not the first match
                // but the one before is not its parent.
                if (levelMatches.indexOf(pair) > 0
                    && pair.left.getDepth() - 1
                        != levelMatches.get(
                            levelMatches.indexOf(pair) - 1).left.getDepth())
                {
                    msgRecorder.reportError(
                        new StringBuilder("The aggregate table ")
                            .append(aggTable.getName())
                            .append(" contains the column ")
                            .append(pair.right.getName())
                            .append(" which maps to the level ")
                            .append(pair.left.getUniqueName())
                            .append(" but its parent level is not part of that aggregation.").toString());
                }
                // Fail if the level is non-collapsed but its members
                // are not unique.
                if (!collapsed
                    && !pair.left.isUnique())
                {
                    msgRecorder.reportError(
                        new StringBuilder("The aggregate table ")
                            .append(aggTable.getName())
                            .append(" contains the column ")
                            .append(pair.right.getName())
                            .append(" which maps to the level ")
                            .append(pair.left.getUniqueName())
                            .append(" but that level doesn't have unique members and this level is marked as non collapsed.")
                            .toString());
                }
            }
            if (msgRecorder.hasErrors()) {
                return;
            }
            // All checks out. Let's create the levels.
            for (Pair<RolapLevel, JdbcSchema.Table.Column> pair
                : levelMatches)
            {
                boolean collapsed = true;
                if (levelMatches.indexOf(pair) == 0
                    && pair.left.getDepth() > 1)
                {
                    collapsed = false;
                }
                makeLevelColumnUsage(
                    pair.right,
                    hierarchyUsage,
                    pair.right.column.getName(),
                    getColumnName(pair.left.getKeyExp()),
                    pair.left.getName(),
                    collapsed,
                    pair.left, null, null, null);
            }
        } finally {
            msgRecorder.popContextName();
        }
    }

    @Override
    protected String getFactCountColumnName(Column.Usage aggUsage) {
        // get the fact count column name.
        JdbcSchema.Table aggTable = aggUsage.getColumn().getTable();

        // get the columns name
        String factCountColumnName = getFactCountColumnName();

        // check if there is a fact column for specific measure
        String measureFactColumnName =  new StringBuilder(aggUsage.getColumn().getName())
                .append("_").append(factCountColumnName).toString();
        for (Iterator<JdbcSchema.Table.Column.Usage> iter =
             aggTable.getColumnUsages(JdbcSchema.UsageType.MEASURE_FACT_COUNT);
             iter.hasNext();)
        {
            Column.Usage usage = iter.next();
            if (usage.getColumn().getName().equals(measureFactColumnName)) {
                factCountColumnName = measureFactColumnName;
                break;
            }
        }

        return factCountColumnName;
    }
}

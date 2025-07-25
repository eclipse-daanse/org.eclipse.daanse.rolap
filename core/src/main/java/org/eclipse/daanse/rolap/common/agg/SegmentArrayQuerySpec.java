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


package org.eclipse.daanse.rolap.common.agg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.common.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.StarPredicate;
import org.eclipse.daanse.rolap.common.sql.SqlQuery;

/**
 * Provides the information necessary to generate a SQL statement to
 * retrieve a list of segments.
 *
 * @author jhyde
 * @author Richard M. Emberson
 */
class SegmentArrayQuerySpec extends AbstractQuerySpec {
    private final List<Segment> segments;
    private final Segment segment0;
    private final GroupingSetsList groupingSetsList;

    /*
     * Compound member predicates.
     * Each list constrains one dimension.
     */
    private final List<StarPredicate> compoundPredicateList;

    /**
     * Creates a SegmentArrayQuerySpec.
     *
     * @param groupingSetsList Collection of grouping sets
     * @param compoundPredicateList list of predicates representing the
     * compound member constraints
     */
    SegmentArrayQuerySpec(
        GroupingSetsList groupingSetsList,
        List<StarPredicate> compoundPredicateList)
    {
        super(groupingSetsList.getStar(), false);
        this.segments = groupingSetsList.getDefaultSegments();
        this.segment0 = segments.get(0);
        this.groupingSetsList = groupingSetsList;
        this.compoundPredicateList = compoundPredicateList;
        assert isValid(true);
    }

    /**
     * Returns whether this query specification is valid, or throws if invalid
     * and fail is true.
     *
     * @param fail Whether to throw if invalid
     * @return Whether this query specification is valid
     */
    private boolean isValid(boolean fail) {
        assert segments.size() > 0;
        for (Segment segment : segments) {
            int n = segment.predicates.length;
            if (n != segment0.predicates.length) {
                assert !fail;
                return false;
            }
            for (int j = 0; j < segment.predicates.length; j++) {
                // We only require that the two arrays have the same
                // contents, we but happen to know they are the same array,
                // because we constructed them at the same time.
                if (segment.predicates[j] != segment0.predicates[j]) {
                    assert !fail;
                    return false;
                }
            }
        }
        return true;
    }

    @Override
	public int getMeasureCount() {
        return segments.size();
    }

    @Override
	public RolapStar.Measure getMeasure(final int i) {
        return segments.get(i).measure;
    }

    @Override
	public String getMeasureAlias(final int i) {
        return "m" + Integer.toString(i);
    }

    @Override
	public RolapStar.Column[] getColumns() {
        return segment0.getColumns();
    }

    /**
     * SqlQuery relies on "c" and index. All this should go into SqlQuery!
     *
     * @see org.eclipse.daanse.rolap.common.sql.SqlQuery#addOrderBy
     */
    @Override
	public String getColumnAlias(final int i) {
        return "c" + Integer.toString(i);
    }

    @Override
	public StarColumnPredicate getColumnPredicate(final int i) {
        return segment0.predicates[i];
    }

    @Override
	protected List<StarPredicate> getPredicateList() {
        if (compoundPredicateList == null) {
            return super.getPredicateList();
        } else {
            return compoundPredicateList;
        }
    }

    @Override
	protected void addGroupingFunction(SqlQuery sqlQuery) {
        List<RolapStar.Column> list = groupingSetsList.getRollupColumns();
        for (RolapStar.Column column : list) {
            sqlQuery.addGroupingFunction(column.generateExprString(sqlQuery));
        }
    }

    @Override
	protected void addGroupingSets(
        SqlQuery sqlQuery,
        Map<String, String> groupingSetsAliases)
    {
        List<RolapStar.Column[]> groupingSetsColumns =
            groupingSetsList.getGroupingSetsColumns();
        for (RolapStar.Column[] groupingSetsColumn : groupingSetsColumns) {
            ArrayList<String> groupingColumnsExpr = new ArrayList<>();
            for (RolapStar.Column aColumn : groupingSetsColumn) {
                final String columnExpr = aColumn.generateExprString(sqlQuery);
                if (groupingSetsAliases.containsKey(columnExpr)) {
                    groupingColumnsExpr.add(
                        groupingSetsAliases.get(columnExpr));
                } else {
                    groupingColumnsExpr.add(columnExpr);
                }
            }
            sqlQuery.addGroupingSet(groupingColumnsExpr);
        }
    }

    @Override
	protected boolean isAggregate() {
        return true;
    }
}

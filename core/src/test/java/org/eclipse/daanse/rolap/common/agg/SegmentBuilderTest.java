/*
 *   This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.olap.util.Pair.of;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.daanse.jdbc.db.dialect.api.type.Datatype;
import org.eclipse.daanse.olap.common.SystemWideProperties;
import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentColumn;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.eclipse.daanse.olap.util.ByteString;
import org.eclipse.daanse.olap.util.Pair;
import org.eclipse.daanse.rolap.aggregator.SumAggregator;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * <p>Test for <code>SegmentBuilder</code>.</p>
 *
 * @author mcampbell
 */
class SegmentBuilderTest {

    public static final double MOCK_CELL_VALUE = 123.123;

    @AfterEach void afterEach() {
        SystemWideProperties.instance().populateInitial();
        RolapUtil.setHook(null);
    }

    @Test
    void rollupWithNullAxisVals() {
        // Perform two rollups.  One with two columns each containing 3 values.
        // The second with two columns containing 2 values + null.
        // The rolled up values should be equal in the two cases.
        Pair<SegmentHeader, SegmentBody> rollupNoNulls = SegmentBuilder.rollup(
                makeSegmentMap(
                        new String[]{"col1", "col2"}, null, 3, 9, true,
                        new boolean[]{false, false}),// each axis sets null axis flag=F
                Collections.singleton("col2"),
                null, SumAggregator.INSTANCE, Datatype.NUMERIC, 1000, 0.5);
        Pair<SegmentHeader, SegmentBody> rollupWithNullMembers =
                SegmentBuilder.rollup(
                        makeSegmentMap(
                                new String[]{"col1", "col2"}, null, 2, 9, true,
                                // each axis sets null axis flag=T
                                new boolean[]{true, true}),
                        Collections.singleton("col2"),
                        null, SumAggregator.INSTANCE, Datatype.NUMERIC, 1000, 0.5);
        assertArraysAreEqual(
                (double[]) rollupNoNulls.getValue().getValueArray(),
                (double[]) rollupWithNullMembers.getValue().getValueArray());
        assertThat(rollupWithNullMembers.getValue().getNullAxisFlags().length == 1
            && rollupWithNullMembers.getValue().getNullAxisFlags()[0]).as("Rolled up column should have nullAxisFlag set.").isTrue();
        assertThat(rollupWithNullMembers.getKey().getConstrainedColumns()
            .get(0).columnExpression).isEqualTo("col2");
    }

    @Test
    void rollupWithMixOfNullAxisValues() {
        // constructed segment has 3 columns:
        //    2 values in the first
        //    2 values + null in the second and third
        //  = 18 values
        Pair<SegmentHeader, SegmentBody> rollup = SegmentBuilder.rollup(
                makeSegmentMap(
                        new String[]{"col1", "col2", "col3"}, null, 2, 18, true,
                        // col2 & col3 have nullAxisFlag=T
                        new boolean[]{false, true, true}),
                Collections.singleton("col2"),
                null, SumAggregator.INSTANCE, Datatype.NUMERIC, 1000, 0.5);

        // expected value is 6 * MOCK_CELL_VALUE for each of 3 column values,
        // since each of the 18 cells are being rolled up to 3 buckets
        double expectedVal = 6 * MOCK_CELL_VALUE;
        assertArraysAreEqual(
                new double[]{expectedVal, expectedVal, expectedVal},
                (double[]) rollup.getValue().getValueArray());
        assertThat(rollup.getValue().getNullAxisFlags().length == 1
            && rollup.getValue().getNullAxisFlags()[0]).as("Rolled up column should have nullAxisFlag set.").isTrue();
        assertThat(rollup.getKey().getConstrainedColumns()
            .get(0).columnExpression).isEqualTo("col2");
    }

    @Test
    void rollup2ColumnsWithMixOfNullAxisValues() {
        // constructed segment has 3 columns:
        //    2 values in the first
        //    2 values + null in the second and third
        //  = 18 values
        Pair<SegmentHeader, SegmentBody> rollup = SegmentBuilder.rollup(
                makeSegmentMap(
                        new String[]{"col1", "col2", "col3"}, null, 2, 12, true,
                        // col2 & col3 have nullAxisFlag=T
                        new boolean[]{false, true, false}),
                new HashSet<>(Arrays.asList("col1", "col2")),
                null, SumAggregator.INSTANCE, Datatype.NUMERIC, 1000, 0.5);

        // expected value is 2 * MOCK_CELL_VALUE for each of 3 column values,
        // since each of the 12 cells are being rolled up to 3 * 2 buckets
        double expectedVal = 2 * MOCK_CELL_VALUE;
        assertArraysAreEqual(
                new double[]{expectedVal, expectedVal, expectedVal,
                        expectedVal, expectedVal, expectedVal},
                (double[]) rollup.getValue().getValueArray());
        assertThat(rollup.getValue().getNullAxisFlags().length == 2
            && !rollup.getValue().getNullAxisFlags()[0]
            && rollup.getValue().getNullAxisFlags()[1]).as("Rolled up column should have nullAxisFlag set to false for "
            + "the first column, true for second column.").isTrue();
        assertThat(rollup.getKey().getConstrainedColumns()
            .get(0).columnExpression).isEqualTo("col1");
        assertThat(rollup.getKey().getConstrainedColumns()
            .get(1).columnExpression).isEqualTo("col2");
    }

    @Test
    void multiSegRollupWithMixOfNullAxisValues() {
        // rolls up 2 segments.
        // Segment 1 has 3 columns:
        //    2 values in the first
        //    1 values + null in the second
        //    2 vals + null in the third
        //  = 12 values
        //  Segment 2 has the same 3 columns, difft values for 3rd column.
        //
        //  none of the columns are wildcarded.
        final Map<SegmentHeader, SegmentBody> map = makeSegmentMap(
                new String[]{"col1", "col2", "col3"},
                new String[][]{{"col1A", "col1B"}, {"col2A"}, {"col3A", "col3B"}},
                -1, 12, false,
                // col2 & col3 have nullAxisFlag=T
                new boolean[]{false, true, true});
        map.putAll(
                makeSegmentMap(
                        new String[]{"col1", "col2", "col3"},
                        new String[][]{{"col1A", "col1B"}, {"col2A"}, {"col3C",
                                "col3D"}},
                        -1, 8, false,
                        // col3 has nullAxisFlag=T
                        new boolean[]{false, true, false}));
        Pair<SegmentHeader, SegmentBody> rollup = SegmentBuilder.rollup(
                map,
                Collections.singleton("col2"),
                null, SumAggregator.INSTANCE, Datatype.NUMERIC,
                1000,
                0.5);
        // expected value is 10 * MOCK_CELL_VALUE for each of 2 column values,
        // since the 20 cells across 2 segments are being rolled up to 2 buckets
        double expectedVal = 10 * MOCK_CELL_VALUE;
        assertArraysAreEqual(
                new double[]{expectedVal, expectedVal},
                (double[]) rollup.getValue().getValueArray());
        assertThat(rollup.getValue().getNullAxisFlags().length == 1
            && rollup.getValue().getNullAxisFlags()[0]).as("Rolled up column should have nullAxisFlag set to true for "
            + "a single column.").isTrue();
        assertThat(rollup.getKey().getConstrainedColumns()
            .get(0).columnExpression).isEqualTo("col2");
    }


    private void assertArraysAreEqual(double[] expected, double[] actual) {
        assertThat(doubleArraysEqual(actual, expected)).as("Expected double array:  "
            + Arrays.toString(expected)
            + ", but got "
            + Arrays.toString(actual)).isTrue();
    }

    private boolean doubleArraysEqual(
            double[] valueArray, double[] expectedVal) {
        if (valueArray.length != expectedVal.length) {
            return false;
        }
        double within = 0.00000001;
        for (int i = 0; i < valueArray.length; i++) {
            if (Math.abs(valueArray[i] - expectedVal[i]) > within) {
                return false;
            }
        }
        return true;
    }

    @Test
    void segmentBodyIterator() {
        // checks that cell key coordinates are generated correctly
        // when a null member is present.
        List<Pair<SortedSet<Comparable>, Boolean>> axes =
                new ArrayList<>();
        axes.add(new Pair<SortedSet<Comparable>, Boolean>(
                new TreeSet<Comparable>(
                        Arrays.asList("foo1", "bar1")), true)); // nullAxisFlag=T
        axes.add(new Pair<SortedSet<Comparable>, Boolean>(
                new TreeSet<Comparable>(
                        Arrays.asList("foo2", "bar2", "baz3")), false));
        SegmentBody testBody = new DenseIntSegmentBody(
                new BitSet(), new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9},
                axes);
        Map valueMap = testBody.getValueMap();
        assertThat(valueMap.toString()).isEqualTo("{(0, 0)=1, "
            + "(0, 1)=2, "
            + "(0, 2)=3, "
            + "(1, 0)=4, "
            + "(1, 1)=5, "
            + "(1, 2)=6, "
            + "(2, 0)=7, "
            + "(2, 1)=8, "
            + "(2, 2)=9}");
    }

    @Test
    void rollupWithIntOverflowPossibility() {
        // rolling up a segment that would cause int overflow if
        // rolled up to a dense segment
        // MONDRIAN-1377

        // make a source segment w/ 3 cols, 47K vals each,
        // target segment has 2 of the 3 cols.
        // count of possible values will exceed Integer.MAX_VALUE
        Pair<SegmentHeader, SegmentBody> rollup =
                SegmentBuilder.rollup(
                        makeSegmentMap(
                                new String[]{"col1", "col2", "col3"},
                                null, 47000, 4, false, null),
                        new HashSet<>(Arrays.asList("col1", "col2")),
                        null, SumAggregator.INSTANCE, Datatype.NUMERIC,
                        1000, 0.5);
        assertThat(rollup.right).isInstanceOf(SparseSegmentBody.class);
    }

    @Test
    void rollupWithOOMPossibility() {
        // rolling up a segment that would cause OOM if
        // rolled up to a dense segment
        // MONDRIAN-1377

        // make a source segment w/ 3 cols, 44K vals each,
        // target segment has 2 of the 3 cols.
        Pair<SegmentHeader, SegmentBody> rollup =
                SegmentBuilder.rollup(
                        makeSegmentMap(
                                new String[]{"col1", "col2", "col3"},
                                null, 44000, 4, false, null),
                        new HashSet<>(Arrays.asList("col1", "col2")),
                        null, SumAggregator.INSTANCE, Datatype.NUMERIC,
                        1000, 0.5);
        assertThat(rollup.right).isInstanceOf(SparseSegmentBody.class);
    }

    @Test
    void rollupShouldBeDense() {
        // Fewer than 1000 column values in rolled up segment.
        Pair<SegmentHeader, SegmentBody> rollup =
                SegmentBuilder.rollup(
                        makeSegmentMap(
                                new String[]{"col1", "col2", "col3"},
                                null, 10, 15, false, null),
                        new HashSet<>(Arrays.asList("col1", "col2")),
                        null, SumAggregator.INSTANCE, Datatype.NUMERIC,
                        1000, 0.5);
        assertThat(rollup.right).isInstanceOf(DenseDoubleSegmentBody.class);

        // greater than 1K col vals, above density ratio
        rollup =
                SegmentBuilder.rollup(
                        makeSegmentMap(
                                new String[]{"col1", "col2", "col3", "col4"},
                                null, 11, 10000, false, null),
                        // 1331 possible intersections (11*3)
                        new HashSet<>(Arrays.asList("col1", "col2", "col3")),
                        null, SumAggregator.INSTANCE, Datatype.NUMERIC,
                        1000, 0.5);
        assertThat(rollup.right).isInstanceOf(DenseDoubleSegmentBody.class);
    }

    @Test
    void rollupWithDenseIntBody() {
        //
        //  We have the following data:
        //
        //           1 _ _
        //    col2   1 2 _
        //           1 _ 1
        //            col1
        //   So, after rolling it up with the SUM function, we expect to get
        //
        //           3 2 1
        //            col1
        //
        String[][] colValues = dummyColumnValues(2, 3);
        int[] values = {1, 1, 1, 0, 2, 0, 1};

        BitSet nulls = new BitSet();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) {
                nulls.set(i);
            }
        }

        List<Pair<SortedSet<Comparable>, Boolean>> axes =
                new ArrayList<>();
        List<SegmentColumn> segmentColumns = new ArrayList<>();
        for (int i = 0; i < colValues.length; i++) {
            axes.add(of(toSortedSet(colValues[i]), false));
            segmentColumns.add(new SegmentColumn(
                    "col" + (i + 1),
                    colValues[i].length,
                    toSortedSet(colValues[i])));
        }
        SegmentHeader header = makeDummySegmentHeader(segmentColumns);
        SegmentBody body = new DenseIntSegmentBody(nulls, values, axes);
        Map<SegmentHeader, SegmentBody> segmentsMap = singletonMap(header, body);

        Pair<SegmentHeader, SegmentBody> rollup =
                SegmentBuilder.rollup(
                        segmentsMap, singleton("col1"),
                        null, SumAggregator.INSTANCE, Datatype.NUMERIC,
                        1000, 0.5);

        double[] result = (double[]) rollup.right.getValueArray();
        double[] expected = {3, 2, 1};
        assertThat(result.length).isEqualTo(expected.length);
        for (int i = 0; i < expected.length; i++) {
            double exp = expected[i];
            double act = result[i];
            assertThat(Math.abs(exp - act) < 1e-6).as(String.format("%d %f %f", i, exp, act)).isTrue();
        }
    }


    enum Order {
        FORWARD, REVERSE
    }


    /**
     * Creates a rough segment map for testing purposes, containing
     * the array of column names passed in, with numValsPerCol dummy
     * values placed per column.  Populates the cells in the body with
     * numPopulatedCells dummy values placed in the first N places of the
     * values array.
     */
    private Map<SegmentHeader, SegmentBody> makeSegmentMap(
            String[] colNames, String[][] colVals,
            int numValsPerCol, int numPopulatedCells,
            boolean wildcardCols, boolean[] nullAxisFlags) {
        if (colVals == null) {
            colVals = dummyColumnValues(colNames.length, numValsPerCol);
        }

        Pair<SegmentHeader, SegmentBody> headerBody = makeDummyHeaderBodyPair(
                colNames,
                colVals,
                numPopulatedCells, wildcardCols, nullAxisFlags);
        Map<SegmentHeader, SegmentBody> map =
                new HashMap<>();
        map.put(headerBody.left, headerBody.right);

        return map;
    }

    private Pair<SegmentHeader, SegmentBody> makeDummyHeaderBodyPair(
            String[] colExps, String[][] colVals, int numCellVals,
            boolean wildcardCols, boolean[] nullAxisFlags) {
        final List<SegmentColumn> constrainedColumns =
                new ArrayList<>();

        final List<Pair<SortedSet<Comparable>, Boolean>> axes =
                new ArrayList<>();
        for (int i = 0; i < colVals.length; i++) {
            String colExp = colExps[i];
            SortedSet<Comparable> headerVals = null;
            SortedSet<Comparable> vals = toSortedSet(colVals[i]);
            if (!wildcardCols) {
                headerVals = vals;
            }
            boolean nullAxisFlag = nullAxisFlags != null && nullAxisFlags[i];
            constrainedColumns.add(
                    new SegmentColumn(
                            colExp,
                            colVals[i].length,
                            headerVals));
            axes.add(Pair.of(vals, nullAxisFlag));
        }

        Object[] cells = new Object[numCellVals];
        for (int i = 0; i < numCellVals; i++) {
            cells[i] = MOCK_CELL_VALUE; // assign a non-null val
        }
        return Pair.<SegmentHeader, SegmentBody>of(
                makeDummySegmentHeader(constrainedColumns),
                new DenseObjectSegmentBody(
                        cells,
                        axes));
    }

    private SegmentHeader makeDummySegmentHeader(
            List<SegmentColumn> constrainedColumns) {
        return new SegmentHeader(
                "dummySchemaName",
                new ByteString(new byte[0]),
                "dummyCubeName",
                "dummyMeasureName",
                constrainedColumns,
                Collections.<String>emptyList(),
                "dummyFactTable",
                BitKey.Factory.makeBitKey(3),
                Collections.<SegmentColumn>emptyList());
    }

    private String[][] dummyColumnValues(int cols, int numVals) {
        String[][] dummyColVals = new String[cols][numVals];
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < numVals; j++) {
                dummyColVals[i][j] = "c" + i + "v" + j;
            }
        }
        return dummyColVals;
    }

    private static SortedSet<Comparable> toSortedSet(Comparable... comparables) {
        List<Comparable> list = asList(comparables);
        return new TreeSet<>(list);
    }
}

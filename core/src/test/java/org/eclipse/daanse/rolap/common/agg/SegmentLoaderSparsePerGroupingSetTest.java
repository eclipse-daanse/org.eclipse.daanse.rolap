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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.agg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.daanse.olap.key.BitKey;
import org.eclipse.daanse.olap.util.ByteString;
import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.eclipse.daanse.rolap.common.star.StarColumnPredicate;
import org.eclipse.daanse.rolap.common.star.StarPredicate;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.sql.model.type.BestFitColumnType;
import org.junit.jupiter.api.Test;

/**
 * The density decision is made per grouping set: a sparse-worthy detail
 * cohort and a dense-worthy rollup cohort in the same load get different
 * dataset representations.
 */
class SegmentLoaderSparsePerGroupingSetTest {

    private final RolapStar star = starMock();
    private final RolapStar.Measure measure = mock(RolapStar.Measure.class);
    private final RolapStar.Column colA = columnMock("A");
    private final RolapStar.Column colB = columnMock("B");

    @Test
    void mixedDensityAcrossGroupingSets() {
        GroupingSetsList gsl = new GroupingSetsList(List.of(
                groupingSet(colA, colB), // detail
                groupingSet(colA)));     // rollup: B rolled away
        BitKey detailKey = gsl.getRollupColumnsBitKeyList().get(0);
        BitKey rollupKey = gsl.getRollupColumnsBitKeyList().get(1);

        SortedSet<Comparable>[] axisValueSets = axisValues(10, 10);

        // 5 detail rows in a 100-cell space (sparse), 10 rollup rows in a
        // 10-cell space (100% dense)
        SegmentLoader.RowList rows = new SegmentLoader.RowList(List.of(
                BestFitColumnType.OBJECT, BestFitColumnType.OBJECT,
                BestFitColumnType.DOUBLE, BestFitColumnType.OBJECT));
        for (int i = 0; i < 5; i++) {
            rows.createRow();
            rows.setObject(0, "a" + i);
            rows.setObject(1, "b" + i);
            rows.setDouble(2, i);
            rows.setGroupingKey(3, detailKey);
        }
        for (int i = 0; i < 10; i++) {
            rows.createRow();
            rows.setObject(0, "a" + i);
            rows.setObject(1, null);
            rows.setDouble(2, i);
            rows.setGroupingKey(3, rollupKey);
        }

        SegmentLoader loader = new SegmentLoader(mock(SegmentCacheManager.class));
        boolean[] sparse = loader.setAxisDataAndDecideSparseUse(axisValueSets,
                new boolean[] { false, false }, gsl, rows, 0, 0.5);
        assertThat(sparse).containsExactly(true, false);

        Map<BitKey, GroupingSetsList.Cohort> cohorts = loader.createDataSetsForGroupingSets(gsl, sparse,
                rows.getTypes().subList(2, rows.getTypes().size()));
        assertThat(cohorts.get(detailKey).segmentDatasetList.get(0)).isInstanceOf(SparseSegmentDataset.class);
        assertThat(cohorts.get(rollupKey).segmentDatasetList.get(0)).isInstanceOf(DenseDoubleSegmentDataset.class);

        // mixed cohorts populate from one row list without error
        loader.loadDataToDataSets(gsl, rows, cohorts);
    }

    @Test
    void singleGroupingSetKeepsTheGlobalDecision() {
        SortedSet<Comparable>[] axisValueSets = axisValues(2, 2);
        SegmentLoader.RowList oneRow = rowsWithoutValues(1);
        SegmentLoader.RowList fourRows = rowsWithoutValues(4);
        SegmentLoader loader = new SegmentLoader(mock(SegmentCacheManager.class));

        boolean[] sparse = loader.setAxisDataAndDecideSparseUse(axisValueSets,
                new boolean[] { false, false }, new GroupingSetsList(List.of(groupingSet(colA, colB))),
                oneRow, 0, 0.5);
        assertThat(sparse).containsExactly(SegmentLoader.useSparse(4, 1, 0, 0.5));
        assertThat(sparse[0]).isTrue();

        sparse = loader.setAxisDataAndDecideSparseUse(axisValueSets,
                new boolean[] { false, false }, new GroupingSetsList(List.of(groupingSet(colA, colB))),
                fourRows, 0, 0.5);
        assertThat(sparse).containsExactly(SegmentLoader.useSparse(4, 4, 0, 0.5));
        assertThat(sparse[0]).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static SortedSet<Comparable>[] axisValues(int aCount, int bCount) {
        SortedSet<Comparable>[] sets = new SortedSet[2];
        sets[0] = new TreeSet<>();
        sets[1] = new TreeSet<>();
        for (int i = 0; i < aCount; i++) {
            sets[0].add("a" + i);
        }
        for (int i = 0; i < bCount; i++) {
            sets[1].add("b" + i);
        }
        return sets;
    }

    private static SegmentLoader.RowList rowsWithoutValues(int count) {
        SegmentLoader.RowList rows = new SegmentLoader.RowList(List.of(
                BestFitColumnType.OBJECT, BestFitColumnType.OBJECT, BestFitColumnType.DOUBLE));
        for (int i = 0; i < count; i++) {
            rows.createRow();
        }
        return rows;
    }

    private GroupingSet groupingSet(RolapStar.Column... columns) {
        StarColumnPredicate[] predicates = new StarColumnPredicate[columns.length];
        for (int i = 0; i < predicates.length; i++) {
            predicates[i] = mock(StarColumnPredicate.class);
            when(predicates[i].getConstrainedColumn()).thenReturn(columns[i]);
        }
        // known header skips SegmentBuilder.toHeader — mocks carry no SQL
        Segment segment = new Segment(star, mock(BitKey.class), columns, measure,
                predicates, new ArrayList<>(), new ArrayList<StarPredicate>(),
                mock(org.eclipse.daanse.olap.spi.SegmentHeader.class));
        return new GroupingSet(Arrays.asList(segment), mock(BitKey.class), mock(BitKey.class), predicates, columns);
    }

    private static RolapStar.Column columnMock(String name) {
        RolapStar.Column column = mock(RolapStar.Column.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        RolapStar.Table table = mock(RolapStar.Table.class);
        when(column.getName()).thenReturn(name);
        when(column.getTable()).thenReturn(table);
        when(table.getAlias()).thenReturn("T" + name);
        return column;
    }

    private static RolapStar starMock() {
        RolapStar star = mock(RolapStar.class);
        RolapStar.Table table = mock(RolapStar.Table.class);
        RolapCatalog catalog = mock(RolapCatalog.class);
        when(star.getCatalog()).thenReturn(catalog);
        when(catalog.getChecksum()).thenReturn(new ByteString("test".getBytes()));
        when(star.getFactTable()).thenReturn(table);
        when(table.getAlias()).thenReturn("FACT");
        when(star.getDialect()).thenReturn(mock(org.eclipse.daanse.sql.dialect.api.Dialect.class));
        return star;
    }
}

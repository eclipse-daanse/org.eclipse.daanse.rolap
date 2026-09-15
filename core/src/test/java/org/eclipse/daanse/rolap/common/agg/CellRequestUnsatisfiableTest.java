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

import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.junit.jupiter.api.Test;

/**
 * Two DIFFERENT constraints on the same column can never both hold (the
 * Burbank-under-Los-Angeles case): the request marks itself
 * unsatisfiable and the batcher answers null without SQL. Equal or null
 * re-constraints stay satisfiable. The field javadoc explained all of
 * this; no test pinned it.
 */
class CellRequestUnsatisfiableTest {

    private final RolapStar star = mock(RolapStar.class);

    private RolapStar.Column column(int bitPosition) {
        RolapStar.Column column = mock(RolapStar.Column.class);
        when(column.getStar()).thenReturn(star);
        when(column.getBitPosition()).thenReturn(bitPosition);
        // the predicate derives its own bitkey from table + star width
        when(column.getTable()).thenReturn(mock(RolapStar.Table.class));
        when(star.getColumnCount()).thenReturn(8);
        return column;
    }

    private CellRequest request() {
        RolapStar.Measure measure = mock(RolapStar.Measure.class);
        when(measure.getStar()).thenReturn(star);
        return new CellRequest(measure, false, false);
    }

    @Test
    void conflictingConstraintsOnOneColumnAreUnsatisfiable() {
        CellRequest request = request();
        RolapStar.Column column = column(0);
        request.addConstrainedColumn(column,
            new ValueColumnPredicate(column, "Burbank"));
        request.addConstrainedColumn(column,
            new ValueColumnPredicate(column, "Los Angeles"));

        assertThat(request.isUnsatisfiable()).isTrue();
    }

    @Test
    void repeatingTheSameConstraintStaysSatisfiable() {
        CellRequest request = request();
        RolapStar.Column column = column(0);
        request.addConstrainedColumn(column,
            new ValueColumnPredicate(column, "Burbank"));
        request.addConstrainedColumn(column,
            new ValueColumnPredicate(column, "Burbank"));

        assertThat(request.isUnsatisfiable()).isFalse();
    }

    @Test
    void aNullReConstraintKeepsTheExistingOne() {
        CellRequest request = request();
        RolapStar.Column column = column(0);
        request.addConstrainedColumn(column,
            new ValueColumnPredicate(column, "Burbank"));
        request.addConstrainedColumn(column, null);

        assertThat(request.isUnsatisfiable()).isFalse();
        assertThat(request.getValueAt(0)).isNotNull();
    }

    @Test
    void reAddingAConstraintAfterTheIdentityWasPublishedThrows() {
        RolapStar deepStar = mock(RolapStar.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(deepStar.getColumnCount()).thenReturn(8);
        when(deepStar.getCatalog().getName()).thenReturn("catalog");
        when(deepStar.getCatalog().getChecksum()).thenReturn(
            new org.eclipse.daanse.olap.util.ByteString(new byte[] { 1 }));
        when(deepStar.getFactTable().getAlias()).thenReturn("fact");
        RolapStar.Measure measure = mock(RolapStar.Measure.class);
        when(measure.getStar()).thenReturn(deepStar);
        when(measure.getCubeName()).thenReturn("cube");
        when(measure.getName()).thenReturn("m");
        CellRequest request = new CellRequest(measure, false, false);
        RolapStar.Column column = mock(RolapStar.Column.class);
        when(column.getStar()).thenReturn(deepStar);
        when(column.getBitPosition()).thenReturn(0);
        when(column.getTable()).thenReturn(mock(RolapStar.Table.class));
        request.addConstrainedColumn(column,
            new ValueColumnPredicate(column, "Burbank"));

        request.segmentIdentity();

        // the duplicate-column branch never touches the frozen BitKey - it
        // must still throw instead of silently diverging predicates and
        // unsatisfiability from the published identity
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            request.addConstrainedColumn(column,
                new ValueColumnPredicate(column, "Los Angeles")))
            .isInstanceOf(IllegalStateException.class);
    }
}

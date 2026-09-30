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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.eclipse.daanse.rolap.common.star.RolapStar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The request keeps its constrained columns in compact parallel arrays:
 * readers see them ascending by bit position and index-parallel to the
 * predicates, duplicate adds keep the sparse-array semantics, and the
 * coordinate map is a view over the arrays.
 */
class CellRequestCompactTest {

    private RolapStar star;
    private RolapStar.Measure measure;

    @BeforeEach
    void setUp() {
        star = mock(RolapStar.class);
        when(star.getColumnCount()).thenReturn(64);
        measure = mock(RolapStar.Measure.class);
        when(measure.getStar()).thenReturn(star);
    }

    private RolapStar.Column column(int bitPosition, String expression) {
        RolapStar.Column column = mock(RolapStar.Column.class);
        when(column.getBitPosition()).thenReturn(bitPosition);
        when(column.getStar()).thenReturn(star);
        when(column.genericSql()).thenReturn(expression);
        return column;
    }

    private ValueColumnPredicate value(Object value) {
        ValueColumnPredicate predicate = mock(ValueColumnPredicate.class);
        when(predicate.getValue()).thenReturn(value);
        return predicate;
    }

    @Test
    void readersSeeColumnsAscendingByBitPositionAndParallelValues() {
        CellRequest request = new CellRequest(measure, false, false);
        RolapStar.Column c21 = column(21, "c21");
        RolapStar.Column c3 = column(3, "c3");
        RolapStar.Column c16 = column(16, "c16");
        request.addConstrainedColumn(c21, value("v21"));
        request.addConstrainedColumn(c3, value("v3"));
        request.addConstrainedColumn(c16, value("v16"));

        assertThat(request.getConstrainedColumns()).containsExactly(c3, c16, c21);
        assertThat(request.getNumValues()).isEqualTo(3);
        assertThat(request.getSingleValues()).containsExactly("v3", "v16", "v21");
        assertThat(((ValueColumnPredicate) request.getValueAt(0)).getValue()).isEqualTo("v3");
        assertThat(((ValueColumnPredicate) request.getValueAt(2)).getValue()).isEqualTo("v21");

        Map<String, Comparable> coordinates = request.getMappedCellValues();
        assertThat(coordinates).hasSize(3);
        assertThat(coordinates.get("c16")).isEqualTo("v16");
        assertThat(coordinates.containsKey("c3")).isTrue();
        assertThat(coordinates.containsKey("nope")).isFalse();
        assertThat(coordinates.get("nope")).isNull();
        assertThat(coordinates).containsEntry("c21", "v21");
    }

    /**
     * segmentIdentity() publishes the key and must freeze it through the
     * getter: a later addConstrainedColumn throws instead of silently
     * diverging from the cached identity.
     */
    @Test
    void segmentIdentityFreezesTheKey() {
        org.eclipse.daanse.rolap.element.RolapCatalog catalog =
                mock(org.eclipse.daanse.rolap.element.RolapCatalog.class);
        when(catalog.getName()).thenReturn("cat");
        when(catalog.getChecksum())
                .thenReturn(new org.eclipse.daanse.olap.util.ByteString(new byte[] { 1 }));
        when(star.getCatalog()).thenReturn(catalog);
        RolapStar.Table fact = mock(RolapStar.Table.class);
        when(fact.getAlias()).thenReturn("FACT");
        when(star.getFactTable()).thenReturn(fact);
        when(measure.getCubeName()).thenReturn("Cube");
        when(measure.getName()).thenReturn("M");

        CellRequest request = new CellRequest(measure, false, false);
        request.addConstrainedColumn(column(3, "c3"), value("v3"));
        var identity = request.segmentIdentity();

        // the guard throws BEFORE the frozen BitKey would: one uniform
        // IllegalStateException for new and duplicate columns alike
        assertThatThrownBy(() -> request.addConstrainedColumn(column(7, "c7"), value("v7")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(request.getConstrainedColumnsBitKey())
                .isEqualTo(identity.constrainedColsBitKey());
    }

    @Test
    void duplicateAddsKeepTheirSemantics() {
        // null then predicate: the predicate wins
        CellRequest request = new CellRequest(measure, false, false);
        RolapStar.Column c5 = column(5, "c5");
        request.addConstrainedColumn(c5, null);
        request.addConstrainedColumn(c5, value("v5"));
        assertThat(request.isUnsatisfiable()).isFalse();
        assertThat(request.getSingleValues()).containsExactly("v5");

        // predicate then null: the predicate stays
        request = new CellRequest(measure, false, false);
        request.addConstrainedColumn(c5, value("v5"));
        request.addConstrainedColumn(c5, null);
        assertThat(request.getSingleValues()).containsExactly("v5");

        // equal constraint again: no-op
        request = new CellRequest(measure, false, false);
        ValueColumnPredicate first = value("v5");
        ValueColumnPredicate again = value("v5");
        when(again.equalConstraint(first)).thenReturn(true);
        request.addConstrainedColumn(c5, first);
        request.addConstrainedColumn(c5, again);
        assertThat(request.isUnsatisfiable()).isFalse();
        assertThat(request.getValueAt(0)).isSameAs(first);

        // conflicting constraint: unsatisfiable, slot cleared
        request = new CellRequest(measure, false, false);
        ValueColumnPredicate other = value("other");
        request.addConstrainedColumn(c5, first);
        request.addConstrainedColumn(c5, other);
        assertThat(request.isUnsatisfiable()).isTrue();
        assertThat(request.getValueAt(0)).isNull();
    }

    @Test
    void manyColumnsGrowTheArrays() {
        CellRequest request = new CellRequest(measure, false, false);
        for (int i = 0; i < 20; i++) {
            request.addConstrainedColumn(column(19 - i, "c" + (19 - i)), value(19 - i));
        }
        assertThat(request.getNumValues()).isEqualTo(20);
        assertThat(request.getSingleValues()[0]).isEqualTo(0);
        assertThat(request.getSingleValues()[19]).isEqualTo(19);
    }

    @Test
    void addAfterReadThrows() {
        CellRequest request = new CellRequest(measure, false, false);
        request.addConstrainedColumn(column(1, "c1"), value("v1"));
        request.getConstrainedColumns();
        assertThatThrownBy(() -> request.addConstrainedColumn(column(2, "c2"), value("v2")))
                .isInstanceOf(IllegalStateException.class);
    }
}

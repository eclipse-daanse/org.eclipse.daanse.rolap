/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2015-2017 Hitachi Vantara..  All rights reserved.
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.daanse.olap.util.Pair.of;

import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.eclipse.daanse.olap.key.CellKey;
import  org.eclipse.daanse.olap.util.Pair;
import org.junit.jupiter.api.Test;

/**
 * This is a base class for two heirs. It provides several template methods
 * for testing
 * @author Andrey Khayrutdinov
 */
abstract class DenseSegmentBodyTestBase<T extends AbstractSegmentBody, V>
{

  final V nonNull = createNonNullValue();
  final V nullValue = createNullValue();

    @Test void getObjectNonNull() {
    T body = withOutAxes(nonNull);
        assertThat(body.getObject(0)).isEqualTo(nonNull);
  }

    @Test void getObjectNull() {
    T body = withOutAxes(nullValue);
        assertThat(body.getObject(0)).isNull();
  }

    @Test void getSizeNoNulls() {
    T body = withOutAxes(nonNull, nonNull, nonNull);
        assertThat(body.getEffectiveSize()).isEqualTo(body.getSize());
  }

    @Test void getSizeHasNulls() {
    T body = withOutAxes(nonNull, nullValue, nonNull);
        assertThat(body.getSize()).isEqualTo(3);
        assertThat(body.getEffectiveSize()).isEqualTo(2);
  }

    @Test void getSizeOnlyNulls() {
    T body = withOutAxes(nullValue, nullValue, nullValue);
        assertThat(body.getSize()).isEqualTo(3);
        assertThat(body.getEffectiveSize()).isEqualTo(0);
  }

    @Test void getValueMapNoNullCellsNoNullAxes() {
    SortedSet<Comparable> axis1 = new TreeSet<>(asList(1, 2));
    SortedSet<Comparable> axis2 = new TreeSet<>(asList(3));
    List<Pair<SortedSet<Comparable>, Boolean>> axes = asList(
        of(axis1, false), of(axis2, false));
    T body = withAxes(axes, nonNull, nonNull, nonNull);
    assertValuesMapIsCorrect(body, 3);
  }

    @Test void getValueMapNoNullCellsHasNullAxes() {
    SortedSet<Comparable> axis1 = new TreeSet<>(asList(1, 2));
    SortedSet<Comparable> axis2 = new TreeSet<>(asList(3));
    List<Pair<SortedSet<Comparable>, Boolean>> axes = asList(
        of(axis1, false), of(axis2, true));
    T body = withAxes(axes, nonNull, nonNull, nonNull, nonNull);
    assertValuesMapIsCorrect(body, 4);
  }

    @Test void getValueMapHasNullCellsNoNullAxes() {
    SortedSet<Comparable> axis1 = new TreeSet<>(asList(1, 2));
    SortedSet<Comparable> axis2 = new TreeSet<>(asList(3));
    List<Pair<SortedSet<Comparable>, Boolean>> axes = asList(
        of(axis1, false), of(axis2, false));
    T body = withAxes(axes, nonNull, nullValue, nonNull, nullValue, nonNull);
    assertValuesMapIsCorrect(body, 3);
  }

    @Test void getValueMapHasNullCellsHasNullAxes() {
    SortedSet<Comparable> axis1 = new TreeSet<>(asList(1, 2));
    SortedSet<Comparable> axis2 = new TreeSet<>(asList(3));
    List<Pair<SortedSet<Comparable>, Boolean>> axes = asList(
        of(axis1, false), of(axis2, true));
    T body = withAxes(
        axes, nonNull, nullValue, nonNull, nullValue, nonNull, nonNull);
    assertValuesMapIsCorrect(body, 4);
  }

    @Test void getValueMapOnlyNullCellsNoNullAxes() {
    SortedSet<Comparable> axis1 = new TreeSet<>(asList(1, 2));
    SortedSet<Comparable> axis2 = new TreeSet<>(asList(3));
    List<Pair<SortedSet<Comparable>, Boolean>> axes = asList(
        of(axis1, false), of(axis2, false));
    T body = withAxes(axes, nullValue, nullValue);
    assertValuesMapIsCorrect(body, 0);
  }

    @Test void getValueMapOnlyNullCellsHasNullAxes() {
    SortedSet<Comparable> axis1 = new TreeSet<>(asList(1, 2));
    SortedSet<Comparable> axis2 = new TreeSet<>(asList(3));
    List<Pair<SortedSet<Comparable>, Boolean>> axes = asList(
        of(axis1, false), of(axis2, true));
    T body = withAxes(axes, nullValue, nullValue);
    assertValuesMapIsCorrect(body, 0);
  }

  private void assertValuesMapIsCorrect(T body, int expectedSize) {
    Map<CellKey, Object> valueMap = body.getValueMap();

      assertThat(valueMap.size()).isEqualTo(expectedSize);
      assertThat(valueMap.keySet().size()).isEqualTo(expectedSize);
      assertThat(valueMap.values().size()).isEqualTo(expectedSize);
      assertThat(valueMap.entrySet().size()).isEqualTo(expectedSize);

    int i = 0;
    Iterator<Map.Entry<CellKey, Object>> it = valueMap.entrySet().iterator();
    while (i < expectedSize) {
        assertThat(it.hasNext()).as(Integer.toString(i)).isTrue();
        assertThat(it.next()).isNotNull();
      i++;
    }
      assertThat(it.hasNext()).isFalse();
  }

  abstract V createNullValue();
  abstract V createNonNullValue();
  abstract boolean isNull(V value);

  abstract T createSegmentBody(
      BitSet nullValues, Object array,
      List<Pair<SortedSet<Comparable>, Boolean>> axes);

  T withOutAxes(V... values) {
    return withAxes(
        Collections.<Pair<SortedSet<Comparable>, Boolean>>emptyList(),
        values);
  }

  T withAxes(List<Pair<SortedSet<Comparable>, Boolean>> axes, V... values) {
    BitSet nullValues = new BitSet();
    for (int i = 0; i < values.length; i++) {
      if (isNull(values[i])) {
        nullValues.set(i);
      }
    }
    return createSegmentBody(nullValues, values, axes);
  }
}

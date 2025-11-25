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

import java.util.BitSet;
import java.util.List;
import java.util.SortedSet;

import  org.eclipse.daanse.olap.util.Pair;

/**
 * @author Andrey Khayrutdinov
 */
class DenseDoubleSegmentBodyTest extends
  DenseSegmentBodyTestBase<DenseDoubleSegmentBody, Double>
{

  @Override
  Double createNullValue() {
    return 0d;
  }

  @Override
  Double createNonNullValue() {
    return 1d;
  }

  @Override
  boolean isNull(Double value) {
    return (value == null) || (value == 0);
  }

  @Override
  DenseDoubleSegmentBody createSegmentBody(
      BitSet nullValues,
      Object array,
      List<Pair<SortedSet<Comparable>, Boolean>> axes)
  {
    Object[] doubles = (Object[]) array;
    double[] values = new double[doubles.length];
    for (int i = 0; i < doubles.length; i++) {
      values[i] = (Double)doubles[i];
    }
    return new DenseDoubleSegmentBody(nullValues, values, axes);
  }
}

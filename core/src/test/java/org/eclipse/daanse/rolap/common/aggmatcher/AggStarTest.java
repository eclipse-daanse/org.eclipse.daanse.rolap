/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
 * All Rights Reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.eclipse.daanse.rolap.common.RolapStar;
import org.eclipse.daanse.rolap.recorder.MessageRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AggStarTest {

  private AggStar aggStar;
  private RolapStar star;
  private JdbcSchema.Table table;
  private MessageRecorder messageRecorder;
  private static final Long BIG_NUMBER = Integer.MAX_VALUE + 1L;


  @BeforeEach
  public void beforeEach() {
    star = mock(RolapStar.class);
    table = mock(JdbcSchema.Table.class);
    messageRecorder = mock(MessageRecorder.class);

    Mockito.when(table.getColumnUsages(Mockito.any())).thenCallRealMethod();
    Mockito.when(table.getName()).thenReturn("TestAggTable");
    Mockito.when(table.getTotalColumnSize()).thenReturn(1);
    Mockito.when(table.getNumberOfRows()).thenReturn(BIG_NUMBER);

    aggStar = AggStar.makeAggStar(star, table, 0L);
    aggStar = Mockito.spy(aggStar);

  }

  @Test
  void testSizeIntegerOverflow() {
    assertEquals(BIG_NUMBER.longValue(), aggStar.getSize(false));
  }

  @Test
  void testVolumeIntegerOverflow() {
    assertEquals(BIG_NUMBER.longValue(), aggStar.getSize(true));
  }
}

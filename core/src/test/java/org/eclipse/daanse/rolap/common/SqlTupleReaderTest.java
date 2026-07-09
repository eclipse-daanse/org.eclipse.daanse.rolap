/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
 * Copyright (C) 2005-2018 Hitachi Vantara and others
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
package org.eclipse.daanse.rolap.common;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Created by Dmitriy Stepanov on 20.01.18.
 */
class SqlTupleReaderTest {


  /**
   * The builder/recorder route is decided on the SQL-carrying subset of the target group — targets
   * with enumerated source members are answered Java-side ({@code addTargets}) and skipped by the
   * recorder's {@code addLevelMemberSql} loop, so they must not steer the routing. Order is
   * preserved (the surviving targets are projected in group order).
   */
  @Test
  void sqlTargetsKeepsOnlySrcMemberFreeTargetsInOrder() {
    TargetBase sqlTarget1 = mock( TargetBase.class );
    doReturn( null ).when( sqlTarget1 ).getSrcMembers();
    TargetBase enumerated = mock( TargetBase.class );
    doReturn( new LinkedList<>() ).when( enumerated ).getSrcMembers();
    TargetBase sqlTarget2 = mock( TargetBase.class );
    doReturn( null ).when( sqlTarget2 ).getSrcMembers();

    org.assertj.core.api.Assertions.assertThat(
        SqlTupleReader.sqlTargets( List.of( sqlTarget1, enumerated, sqlTarget2 ) ) )
      .containsExactly( sqlTarget1, sqlTarget2 );
    org.assertj.core.api.Assertions.assertThat(
        SqlTupleReader.sqlTargets( List.of( enumerated ) ) )
      .isEmpty();
  }

}

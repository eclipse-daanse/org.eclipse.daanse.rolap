/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2015-2017 Hitachi Vantara and others
// All Rights Reserved.
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
package org.eclipse.daanse.rolap.common.sql;

import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.QueryCanceledException;
import org.eclipse.daanse.olap.api.monitor.EventBus;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.olap.execution.StatementImpl;
import org.eclipse.daanse.rolap.common.SqlStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * @author Andrey Khayrutdinov
 */
class SqlStatementTest {

  private EventBus monitor;
  private Context context;
  private Connection rolapConnection;
  private StatementImpl statMock;
  private ExecutionImpl execution;
  private ExecutionContext executionContext;
  private SqlStatement statement;

    @BeforeEach void beforeEach() {
    monitor = mock(EventBus.class);

    context = mock(Context.class);
    when(context.getMonitor()).thenReturn(monitor);
    when(context.getDataSource()).thenReturn(null);

    rolapConnection = mock(Connection.class);
    when(rolapConnection.getContext()).thenReturn(context);

    statMock = mock(StatementImpl.class);
    when(statMock.getDaanseConnection()).thenReturn(rolapConnection);

    execution = new ExecutionImpl(statMock, Optional.empty());
    execution = spy(execution);
    doThrow(new QueryCanceledException("Query canceled"))
            .when(execution).checkCancelOrTimeout();

    executionContext = execution.asContext();

    statement = new SqlStatement(context, "sql", null, 0, 0, executionContext, 0, 0, null);
    statement = spy(statement);
  }

  @Test
  @Disabled("Disabled until we have a way to cancel before start")
  void printingNilDurationIfCancelledBeforeStart() throws Exception {
    ExecutionContext.where(executionContext, () -> {
      try {
        statement.execute();
      } catch (Exception e) {
        Throwable cause = e.getCause();
        if (!(cause instanceof QueryCanceledException)) {
          String message = "Expected QueryCanceledException but caught "
            + ((cause == null) ? null : cause.getClass().getSimpleName());
            fail(message);
        }
      }

      verify(statement).formatTimingStatus(eq(Duration.ZERO), anyInt());
    });
  }

}

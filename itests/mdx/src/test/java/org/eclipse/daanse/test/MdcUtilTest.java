/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2021 Hitachi Vantara
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
package org.eclipse.daanse.test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.eclipse.daanse.rolap.testkit.assertions.FlushSchemaCacheModifier.flushSchemaCache;

import java.io.StringWriter;

//import org.apache.logging.log4j.ThreadContext;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.common.RolapUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.MdxAssert;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies that MDC context is passed between threads.
 *
 * @author Benny
 */
//disabled by reason of we don't plan use log4j
@Disabled
@RolapContextTest(FoodmartTestInstance.class)
class MdcUtilTest {

  private static Logger rolapUtilLogger = LoggerFactory.getLogger( RolapUtil.class );

  @Test
  void testMdcContext(Context<?> context) throws Exception {

    Connection connection = context.getConnectionWithDefaultRole();
    flushSchemaCache(connection);

//    ThreadContext.put( "sessionName", "hello-world" );
    StringWriter writer = new StringWriter();

    //final Appender appender =
    //    Util.makeAppender(
    //        "testMdcContext",
    //        writer,
    //        "sessionName:%X{sessionName} %t %m");

    //Util.addAppender(appender, rolapUtilLogger, null);
    //Level oldLevel = rolapUtilLogger.getLevel();
    //Util.setLevel( rolapUtilLogger, Level.DEBUG );

    //Util.addAppender(appender, RolapUtil.MONITOR_LOGGER, null);
    //Level oldMonitorLevel = RolapUtil.MONITOR_LOGGER.getLevel();
    //Util.setLevel( RolapUtil.MONITOR_LOGGER, Level.DEBUG );

    //Util.addAppender(appender, RolapUtil.MDX_LOGGER, null);
    //Level oldMdxLevel = RolapUtil.MDX_LOGGER.getLevel();
    //Util.setLevel( RolapUtil.MDX_LOGGER, Level.DEBUG );

    String log = "";
    try {

      String query =
          "SELECT {[Measures].[Unit Sales]} " + "on columns, {[Gender].Members} on rows FROM [Sales]";

      String expected =
          "Axis #0:\n" + "{}\n" + "Axis #1:\n" + "{[Measures].[Unit Sales]}\n" + "Axis #2:\n"
              + "{[Gender].[All Gender]}\n" + "{[Gender].[F]}\n" + "{[Gender].[M]}\n" + "Row #0: 266,773\n"
              + "Row #1: 131,558\n" + "Row #2: 135,215\n";

      MdxAssert.assertThatQuery(context.getConnectionWithDefaultRole(), query).returnsGrid(expected);
      log = writer.toString();

    } finally {

      //Util.removeAppender(appender, rolapUtilLogger);
      //Util.setLevel( rolapUtilLogger, oldLevel );

      //Util.removeAppender(appender, RolapUtil.MONITOR_LOGGER);
      //Util.setLevel( RolapUtil.MONITOR_LOGGER, oldMonitorLevel );

      //Util.removeAppender(appender, RolapUtil.MDX_LOGGER);
      //Util.setLevel( RolapUtil.MDX_LOGGER, oldMdxLevel );

    }

    // Mondrian uses another thread pool to execute SQL statements.
    // Verify that sessionName is now present on the SQL log statements
    assertContains(log, "sessionName:hello-world mondrian.rolap.agg.SegmentCacheManager$sqlExecutor_");
    assertContains(log, "sessionName:hello-world Mondrian Monitor StatementStartEvent");
    assertContains(log, "sessionName:hello-world mondrian.rolap.RolapResultShepherd$executor_");
  }

  public void assertContains( String actual, String contains ) {
    if ( actual.contains( contains ) ) {
      return;
    }
    fail( "Test String does not contain: " + contains + " Test String: " + actual );
  }

}

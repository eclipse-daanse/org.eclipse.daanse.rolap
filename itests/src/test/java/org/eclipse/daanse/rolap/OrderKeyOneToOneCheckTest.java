/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
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
package org.eclipse.daanse.rolap;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.fail;
import static org.eclipse.daanse.rolap.testkit.assertions.FlushSchemaCacheModifier.flushSchemaCache;

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

//Disabled by reason log4j. log4j is not using any more
@Disabled
@RolapContextTest(FoodmartTestInstance.class)
class OrderKeyOneToOneCheckTest {

  public static class FoodmartData implements DataSupplier {
    @Override
    public Map<String, URL> csvResources() {
      return new FoodmartTestInstance().dataSupplier().csvResources();
    }
  }

  //private TestAppender memberSourceAppender;
  //private TestAppender sqlReaderAppender;

  @BeforeEach
  public void beforeEach() throws Exception {
    //Logger memberSourceLogger = (Logger) LoggerFactory.getLogger(SqlMemberSource.class);
    //Logger sqlReaderLogger = (Logger) LoggerFactory.getLogger(SqlTupleReader.class);

    //memberSourceAppender = new TestAppender();
    //sqlReaderAppender = new TestAppender();
    //memberSourceLogger.addAppender(memberSourceAppender);
    //sqlReaderLogger.addAppender(sqlReaderAppender);
  }

  @AfterEach
  protected void afterEach() throws Exception {
    //Logger memberSourceLogger = (Logger) LoggerFactory.getLogger(SqlMemberSource.class);
    //Logger sqlReaderLogger = (Logger) LoggerFactory.getLogger(SqlTupleReader.class);
    //memberSourceLogger.removeAppender(memberSourceAppender);
    //sqlReaderLogger.removeAppender(sqlReaderAppender);
  }

  public void prepareContext(Context<?> context) {
    //TestContext<?> testContext<?> = super.getTestContext()
    //        .withFreshConnection();
    flushSchemaCache(context.getConnectionWithDefaultRole());
    /*
    withSchema(context,
            ""
                    + "<?xml version=\"1.0\"?>\n"
                    + "<Schema name=\"FoodMart 2358\">\n"
                    + "  <Dimension name=\"Time\" type=\"TimeDimension\">\n"
                    + "    <Hierarchy hasAll=\"false\" primaryKey=\"time_id\">\n"
                    + "      <Table name=\"time_by_day\"/>\n"
                    + "      <Level name=\"Year\" column=\"the_year\" type=\"Numeric\" uniqueMembers=\"true\"\n"
                    + "          levelType=\"TimeYears\"/> \n"
                    + "      <Level name=\"Quarter\" column=\"quarter\" ordinalColumn=\"month_of_year\" uniqueMembers=\"false\" levelType=\"TimeQuarters\"/>  \n"
                    + "      <Level name=\"Month\" column=\"month_of_year\" uniqueMembers=\"false\" type=\"Numeric\"\n"
                    + "          levelType=\"TimeMonths\"/>\n"
                    + "    </Hierarchy>\n"
                    + "  </Dimension>\n"
                    + "<Cube name=\"Sales\" defaultMeasure=\"Unit Sales\">\n"
                    + "  <Table name=\"sales_fact_1997\"/>\n"
                    + "  <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/>\n"
                    + "  <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"\n"
                    + "      formatString=\"Standard\"/>\n"
                    + "</Cube>\n"
                    + "</Schema>");
     */
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.OrderKeyOneToOneCheckTestModifier.class },
      database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testMemberSource(Context<?> context) {
    String mdx =
            "with member [Measures].[Count Month] as 'Count(Descendants(Time.CurrentMember, [Time].[Month]))' \n"
                    + "select [Measures].[Count Month] on 0,\n"
                    + "[Time].[1997] on 1 \n"
                    + "from [Sales]";
    prepareContext(context);
    executeQuery(context.getConnectionWithDefaultRole(), mdx);
    fail("need slf4j implementation");
    //TODO need slf4j implementation
    //assertEquals(
    //        8,
    //        sqlReaderAppender.getLogEvents().size(),
    //        "Running with modified schema should log 8 error");
    //assertEquals(
    //        8,
    //        memberSourceAppender.getLogEvents().size(),
    //        "Running with modified schema should log 8 error");
  }

  @Test
  @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.OrderKeyOneToOneCheckTestModifier.class },
      database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
  void testSqlReader(Context<?> context) {
    String mdx = ""
            + "select [Time].[Quarter].Members on 0"
            + "from [Sales]";

    prepareContext(context);
    executeQuery(context.getConnectionWithDefaultRole(), mdx);
    fail("need slf4j implementation");
    //TODO need slf4j implementation
    //assertEquals(
    //        16,
    //        sqlReaderAppender.getLogEvents().size(),
    //        "Running with modified schema should log 16 error");
  }
}

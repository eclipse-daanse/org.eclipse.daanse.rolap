/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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
package org.eclipse.daanse.rolap.aggmatcher;


import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.common.aggmatcher.AggGen;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Test if lookup columns are there after loading them in
 * AggGen#addCollapsedColumn(...).
 *
 * @author Sherman Wood
 */
@RolapContextTest(FoodmartTestInstance.class)
class AggGenTest {
    @AfterEach
    public void afterEach() {
    }

    @Test
    @RolapConfig(key = ConfigConstants.USE_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.READ_AGGREGATES, value = "true", type = Boolean.class)
    @RolapConfig(key = ConfigConstants.GENERATE_AGGREGATE_SQL, value = "true", type = Boolean.class)
    void testCallingLoadColumnsInAddCollapsedColumnOrAddzSpecialCollapsedColumn(Context<?> context) throws Exception
    {
        Logger logger = LoggerFactory.getLogger(AggGen.class);
        StringWriter writer = new StringWriter();

        //TODO use log in tests?
        //final Appender appender =
        //    Util.makeAppender("testMdcContext", writer, null);

        //Util.addAppender(appender, logger, org.apache.logging.log4j.Level.DEBUG);

        // If run in Ant and with mondrian.jar, please comment out this line:
//        ((TestContextImpl)context).setAggregateRules("/DefaultRules.xml");
        //((TestContextImpl)context).setUseAggregates(true);
        //((TestContextImpl)context).setReadAggregates(true);
        //((TestContextImpl)context).setGenerateAggregateSql(true);

        final org.eclipse.daanse.olap.api.connection.Connection rolapConn = (org.eclipse.daanse.olap.api.connection.Connection) context.getConnectionWithDefaultRole();
        Query query =
            rolapConn.parseQuery(
                "select {[Measures].[Count]} on columns from [HR]");
        rolapConn.execute(query);

        //Util.removeAppender(appender, logger);

        final DataSource dataSource = rolapConn.getDataSource();
        Connection sqlConnection = null;
        try {
            sqlConnection = dataSource.getConnection();
            DatabaseMetaData dbmeta = sqlConnection.getMetaData();
            Catalog catalogMapping = ((RolapContext) context).getCatalogMapping();
            List<? extends Schema> schemas = Packages.available(catalogMapping, Schema.class);
            Schema databaseSchema = schemas.getFirst();


            String log = writer.toString();
            Pattern p = Pattern.compile(
                "DEBUG - Init: Column: [^:]+: `(\\w+)`.`(\\w+)`"
                + Util.NL
                + "WARN - Can not find column: \\2");
            Matcher m = p.matcher(log);

            while (m.find()) {
                ResultSet rs =
                    dbmeta.getColumns(
                    		catalogMapping.getName(), databaseSchema.getName(), m.group(1), m.group(2));
                assertTrue(!rs.next());
            }
        } finally {
            if (sqlConnection != null) {
                try {
                    sqlConnection.close();
                } catch (SQLException e) {
                    // ignore
                }
            }
        }
    }

}

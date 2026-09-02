/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2019 Hitachi Vantara..  All rights reserved.
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


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.eclipse.daanse.cwm.model.cwm.resource.relational.Schema;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.api.RolapContext;
import org.eclipse.daanse.rolap.common.aggmatcher.JdbcSchema;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.cwm.model.cwm.objectmodel.core.util.Packages;
/**
  * Test if AggSchemaScan and AggCatalogScan properties are used in JdbcSchema loadTablesOfType
  *
  */
@RolapContextTest(FoodmartTestInstance.class)
class AggSchemaScanTest {


  @Test
  void testAggScanPropertiesEmptySchema(Context<?> context) throws Exception {
    final org.eclipse.daanse.olap.api.connection.Connection rolapConn = (org.eclipse.daanse.olap.api.connection.Connection) context.getConnectionWithDefaultRole();
    final DataSource dataSource = rolapConn.getDataSource();
    Connection sqlConnection = null;
    try {
      sqlConnection = dataSource.getConnection();

      Catalog catalogMapping = ((RolapContext) context).getCatalogMapping();
      List<? extends Schema> schemas = Packages.available(catalogMapping, Schema.class);
      Schema databaseSchema = schemas.getFirst();

      //RolapConnectionPropsR rc = new ConnectionProps(List.of(), false, Locale.getDefault(), 0l, TimeUnit.SECONDS, Optional.of("bogus"),Optional.of("bogus"));
      JdbcSchema jdbcSchema = new JdbcSchema(databaseSchema);
      //jdbcSchema.resetAllTablesLoaded();
      jdbcSchema.getTablesMap().clear();

      //jdbcSchema.loadTables( rc );
      assertEquals( 0, jdbcSchema.getTablesMap().size() );
    } finally {
      if (sqlConnection != null) {
        try {
          sqlConnection.close();
        } catch ( SQLException e) {
          // ignore
        }
      }
    }
  }


  @Test
  void testAggScanPropertiesPopulatedSchema(Context<?> context) throws Exception {
    final org.eclipse.daanse.olap.api.connection.Connection rolapConn = (org.eclipse.daanse.olap.api.connection.Connection) context.getConnectionWithDefaultRole();
    final DataSource dataSource = rolapConn.getDataSource();
    Connection sqlConnection = null;
    try {
      sqlConnection = dataSource.getConnection();
      DatabaseMetaData dbmeta = sqlConnection.getMetaData();
      if ( !dbmeta.supportsSchemasInTableDefinitions() && !dbmeta.supportsCatalogsInTableDefinitions() ) {
        System.out.println( "Database does not support schema or catalog in table definitions.  Cannot run test." );
        return;
      }
		String propCatalog = null;
		String propSchema = null;
      boolean foundSchema = false;
      // Different databases treat catalogs and schemas differently.  Figure out whether foodmart is a schema or catalog in this database
      try {
        String schema = sqlConnection.getSchema();
        String catalog = sqlConnection.getCatalog();
        if ( schema != null || catalog != null ) {
          foundSchema = true;
          propCatalog= catalog ;
          propSchema= schema ;
        }
      } catch ( AbstractMethodError | Exception ex ) {
        // Catch if the JDBC client throws an exception.  Do nothing.
      }

      // Some databases like Oracle do not implement getSchema and getCatalog with the connection, so try the dbmeta instead
      if ( !foundSchema && dbmeta.supportsSchemasInTableDefinitions() ) {
        try ( ResultSet resultSet = dbmeta.getSchemas() ) {
           if ( resultSet.getMetaData().getColumnCount() == 2 ) {
             while ( resultSet.next() ) {
               if ( resultSet.getString( 1 ).equalsIgnoreCase( "foodmart" ) ) {

                   propCatalog= resultSet.getString( 2 ) ;
                   propSchema= resultSet.getString( 1 ) ;

                 foundSchema = true;
                 break;
               }
             }
           }

        }
      }

      if (dbmeta.supportsCatalogsInTableDefinitions() && !foundSchema) {
        try ( ResultSet resultSet = dbmeta.getCatalogs() ) {
          if ( resultSet.getMetaData().getColumnCount() == 1 ) {
            while ( resultSet.next() ) {
              if ( resultSet.getString( 1 ).equalsIgnoreCase( "foodmart" ) ) {
                propCatalog= resultSet.getString( 1 ) ;
                foundSchema = true;
                break;
              }
            }
          }
        }
      }

      if ( !foundSchema ) {
        System.out.println( "Cannot find foodmart schema or catalog in database.  Cannot run test." );
        return;
      }
      Catalog catalogMapping = ((RolapContext) context).getCatalogMapping();
      List<? extends Schema> schemas = Packages.available(catalogMapping, Schema.class);
      Schema databaseSchema = schemas.getFirst();
      JdbcSchema jdbcSchema = new JdbcSchema(databaseSchema);
      //The foodmart schema has 37 tables.
      assertEquals( 37, jdbcSchema.getTablesMap().size() );
    } finally {
      if (sqlConnection != null) {
        try {
          sqlConnection.close();
        } catch ( SQLException e) {
          // ignore
        }
      }
    }
  }
}

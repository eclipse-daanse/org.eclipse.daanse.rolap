/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/

package org.eclipse.daanse.rolap.sql;


import static org.eclipse.daanse.rolap.testkit.assertions.Dialect.getDialect;
import java.net.URL;
import java.util.Map;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.sql.dialect.api.Dialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.SchemaModifiersEmf;
import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;

/**
 * Test that various values of {@link Dialect#allowsSelectNotInGroupBy}
 * produce correctly optimized SQL.
 *
 * <p>{@code SAME_THREAD}: every scenario composes its own {@code CatalogSupplier}
 * (FoodMart mapping) instance -- like {@link mondrian.rolap.aggmatcher.ExplicitRecognizerTest},
 * this opts out of the module's default concurrent execution so those
 * constructions don't race across this class's own methods.
 *
 * @author Eric McDermid
 */
@RolapContextTest(FoodmartTestInstance.class)
@Execution(ExecutionMode.SAME_THREAD)
class SelectNotInGroupByTest {

    public static final String queryCubeA =
        "select {[Measures].[Custom Store Sales],[Measures].[Custom Store Cost]} on columns, {[CustomStore].[Store Name].Members} on rows from CustomSales";

    public static final String sqlWithAllGroupBy =
        "select\n"
        + "    `store`.`store_country` as `c0`,\n"
        + "    `store`.`store_city` as `c1`,\n"
        + "    `store`.`store_state` as `c2`,\n"
        + "    `store`.`store_name` as `c3`\n"
        + "from\n"
        + "    `store` as `store`\n"
        + "group by\n"
        + "    `store`.`store_country`,\n"
        + "    `store`.`store_city`,\n"
        + "    `store`.`store_state`,\n"
        + "    `store`.`store_name`\n"
        + "order by\n"
        + "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
        + "    ISNULL(`c3`) ASC, `c3` ASC";

    public static final String sqlWithNoGroupBy =
        "select\n"
        + "    `store`.`store_country` as `c0`,\n"
        + "    `store`.`store_city` as `c1`,\n"
        + "    `store`.`store_state` as `c2`,\n"
        + "    `store`.`store_name` as `c3`\n"
        + "from\n"
        + "    `store` as `store`\n"
        + "order by\n"
        + "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
        + "    ISNULL(`c3`) ASC, `c3` ASC";

    public static final String sqlWithLevelGroupBy =
        "select\n"
        + "    `store`.`store_country` as `c0`,\n"
        + "    `store`.`store_city` as `c1`,\n"
        + "    `store`.`store_state` as `c2`,\n"
        + "    `store`.`store_name` as `c3`\n"
        + "from\n"
        + "    `store` as `store`\n"
        + "group by \n"
        + "    `store`.`store_country`,\n"
        + "    `store`.`store_city`,\n"
        + "    `store`.`store_name`\n"
        + "order by\n"
        + "    ISNULL(`c0`) ASC, `c0` ASC,\n"
        + "    ISNULL(`c1`) ASC, `c1` ASC,\n"
        + "    ISNULL(`c3`) ASC, `c3` ASC";

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SelectNotInGroupByTestModifier1.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    void testDependentPropertySkipped(Connection connection) {
        // Property group by should be skipped only if dialect supports it
        String sqlpat;
        if (dialectAllowsSelectNotInGroupBy(connection)) {
            sqlpat = sqlWithLevelGroupBy;
        } else {
            sqlpat = sqlWithAllGroupBy;
        }
        SqlPattern[] sqlPatterns = {
            new SqlPattern(DatabaseProduct.MYSQL, sqlpat, sqlpat)
        };

        // Use dimension with level-dependent property
        SqlAssert.forQuery(connection, queryCubeA).expectSql(sqlPatterns).verify();
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SelectNotInGroupByTestModifier2.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    void testIndependentPropertyNotSkipped(Connection connection) {
        SqlPattern[] sqlPatterns = {
            new SqlPattern(
                DatabaseProduct.MYSQL,
                sqlWithAllGroupBy,
                sqlWithAllGroupBy)
        };

        // Use dimension with level-independent property
        SqlAssert.forQuery(connection, queryCubeA).expectSql(sqlPatterns).verify();
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SelectNotInGroupByTestModifier3.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    void testGroupBySkippedIfUniqueLevel(Connection connection) {
        // If unique level is included and all properties are level
        // dependent, then group by can be skipped regardless of dialect
        SqlPattern[] sqlPatterns = {
            new SqlPattern(
                DatabaseProduct.MYSQL,
                sqlWithNoGroupBy,
                sqlWithNoGroupBy)
        };

        // Use dimension with unique level & level-dependent properties
        SqlAssert.forQuery(connection, queryCubeA).expectSql(sqlPatterns).verify();
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.SelectNotInGroupByTestModifier4.class },
            database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
    void testGroupByNotSkippedIfIndependentProperty(Connection connection) {
        SqlPattern[] sqlPatterns = {
            new SqlPattern(
                DatabaseProduct.MYSQL,
                sqlWithAllGroupBy,
                sqlWithAllGroupBy)
        };

        // Use dimension with unique level but level-indpendent property
        SqlAssert.forQuery(connection, queryCubeA).expectSql(sqlPatterns).verify();
    }

    private boolean dialectAllowsSelectNotInGroupBy(Connection connection) {
        final Dialect dialect = getDialect(connection);
        return dialect.allowsSelectNotInGroupBy();
    }

    /** Named bridge onto the FoodMart CSVs (for the {@code data =} supplier form). */
    public static class FoodmartData implements DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }
}

/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 */
package org.eclipse.daanse.rolap.sql;

import static org.eclipse.daanse.rolap.testkit.assertions.Dialect.getDialect;
import static org.eclipse.daanse.rolap.testkit.assertions.FlushSchemaCacheModifier.flushSchemaCache;
import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.SqlAssert;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.testkit.assertions.SqlPattern;

/**
 * Tests member-cache reuse across level-members / children-members MDX, and
 * the {@code LevelPreCacheThreshold} setting that governs how eagerly a
 * level's members get pulled into cache.
 */
@RolapContextTest(FoodmartTestInstance.class)
@RolapConfig(key = ConfigConstants.GENERATE_FORMATTED_SQL, value = "true", type = Boolean.class)
class EffectiveMemberCacheTest {

    @Test
    void testCachedLevelMembers(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        // verify query for specific members can be fulfilled by members cached
        // from a level members query.
        String sql = "select\n"
                + "    `product`.`product_name` as `c0`\n"
                + "from\n"
                + "    `product` as `product` join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id`\n"
                + "where\n"
                + "    (`product`.`brand_name` = 'Hermanos' and `product_class`.`product_subcategory` = 'Fresh Fruit' and `product_class`.`product_category` = 'Fruit' and `product_class`.`product_department` = 'Produce' and `product_class`.`product_family` = 'Food')\n"
                + "and\n"
                + "    ( UPPER(`product`.`product_name`) IN (UPPER('Hermanos Fancy Plums'),UPPER('Hermanos Lemons'),UPPER('Hermanos Plums')))\n"
                + "group by\n"
                + "    `product`.`product_name`\n"
                + "order by\n"
                + (getDialect(connection).requiresOrderByAlias()
                ? "    ISNULL(`c0`) ASC, `c0` ASC"
                : "    ISNULL(`product`.`product_name`) ASC, "
                + "`product`.`product_name` ASC");
        testWithAndWithoutCachedMembers(connection,
            "select Product.[Product Name].members on 0 from sales",
            "select "
            + " { [Product].[Food].[Produce].[Fruit].[Fresh Fruit].[Hermanos].[Hermanos Fancy Plums], "
            + "[Product].[Food].[Produce].[Fruit].[Fresh Fruit].[Hermanos].[Hermanos Lemons],"
            + "[Product].[Food].[Produce].[Fruit].[Fresh Fruit].[Hermanos].[Hermanos Plums] }"
            + " on 0 from sales",
            new SqlPattern[]{
                new SqlPattern(
                    DatabaseProduct.MYSQL, sql, null)}
        );
    }

    @Test
    void testCachedChildMembers(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        // verify query for specific members can be fulfilled by members cached
        // from a child members query.
        String sql = "select\n"
                + "    `product`.`product_name` as `c0`\n"
                + "from\n"
                + "    `product` as `product` join `product_class` as `product_class` on `product`.`product_class_id` = `product_class`.`product_class_id`\n"
                + "where\n"
                + "    (`product`.`brand_name` = 'Hermanos' and `product_class`.`product_subcategory` = 'Fresh Fruit' and `product_class`.`product_category` = 'Fruit' and `product_class`.`product_department` = 'Produce' and `product_class`.`product_family` = 'Food')\n"
                + "and\n"
                + "    ( UPPER(`product`.`product_name`) IN "
                + "(UPPER('Hermanos Fancy Plums'),UPPER('Hermanos Lemons'),UPPER('Hermanos Plums')))\n"
                + "group by\n"
                + "    `product`.`product_name`\n"
                + "order by\n"
                + (getDialect(connection).requiresOrderByAlias()
                ? "    ISNULL(`c0`) ASC, `c0` ASC"
                : "    ISNULL(`product`.`product_name`) ASC, "
                + "`product`.`product_name` ASC");
        testWithAndWithoutCachedMembers(connection,
            "select [Product].[Food].[Produce].[Fruit].[Fresh Fruit].[Hermanos].Children on 0 from sales",
            "select "
            + " { [Product].[Food].[Produce].[Fruit].[Fresh Fruit].[Hermanos].[Hermanos Fancy Plums], "
            + "[Product].[Food].[Produce].[Fruit].[Fresh Fruit].[Hermanos].[Hermanos Lemons],"
            + "[Product].[Food].[Produce].[Fruit].[Fresh Fruit].[Hermanos].[Hermanos Plums] }"
            + " on 0 from sales",
            new SqlPattern[]{
                new SqlPattern(
                    DatabaseProduct.MYSQL, sql, null) }
        );
    }

    @Test
    @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "300", type = Integer.class)
    void testLevelPreCacheThreshold(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        flushSchemaCache(connection);
        // [Store Type] members cardinality falls well below
        // LevelPreCacheThreshold.  All members should be loaded, not
        // just the 2 referenced.
        String sql = "select\n"
                + "    `store`.`store_type` as `c0`\n"
                + "from\n"
                + "    `store` as `store`\n"
                + "group by\n"
                + "    `store`.`store_type`\n"
                + "order by\n"
                + (getDialect(connection).requiresOrderByAlias()
                ? "    ISNULL(`c0`) ASC, `c0` ASC"
                : "    ISNULL(`store`.`store_type`) ASC, "
                + "`store`.`store_type` ASC");
        SqlAssert.forQuery(connection,
                "select {[Store Type].[Gourmet Supermarket], "
                + "[Store Type].[HeadQuarters]} on 0 from sales")
            .expectSql(new SqlPattern(DatabaseProduct.MYSQL, sql, null))
            .verify();
    }

    @Test
    @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "0", type = Integer.class)
    void testLevelPreCacheThresholdDisabled(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        flushSchemaCache(connection);
        // with LevelPreCacheThreshold set to 0, we should not load
        // all [store type] members, we should only retrieve the 2
        // specified.
        String sql = "select\n"
                + "    `store`.`store_type` as `c0`\n"
                + "from\n"
                + "    `store` as `store`\n"
                + "where\n"
                + "    ( UPPER(`store`.`store_type`) IN "
                + "(UPPER('Gourmet Supermarket'),UPPER('HeadQuarters')))\n"
                + "group by\n"
                + "    `store`.`store_type`\n"
                + "order by\n"
                + (getDialect(connection).requiresOrderByAlias()
                ? "    ISNULL(`c0`) ASC, `c0` ASC"
                : "    ISNULL(`store`.`store_type`) ASC, "
                + "`store`.`store_type` ASC");
        SqlAssert.forQuery(connection,
                "select {[Store Type].[Store Type].[Gourmet Supermarket], "
                + "[Store Type].[Store Type].[HeadQuarters]} on 0 from sales")
            .expectSql(new SqlPattern(DatabaseProduct.MYSQL, sql, null))
            .verify();
    }

    @Test
    @RolapConfig(key = ConfigConstants.LEVEL_PRE_CACHE_THRESHOLD, value = "1000", type = Integer.class)
    void testLevelPreCacheThresholdParentDegenerate(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        flushSchemaCache(connection);
        // we should avoid pulling all deg members, regardless of cardinality.
        // The cost of doing full scans of the fact table is assumed
        // to be too high.
        String sql = "select\n"
                + "    `store`.`coffee_bar` as `c0`\n"
                + "from\n"
                + "    `store` as `store`\n"
                + "where\n"
                + "    `store`.`coffee_bar` = false\n"
                + "group by\n"
                + "    `store`.`coffee_bar`\n"
                + "order by\n"
                + (getDialect(connection).requiresOrderByAlias()
                ? "    ISNULL(`c0`) ASC, `c0` ASC"
                : "    ISNULL(`store`.`coffee_bar`) ASC, "
                + "`store`.`coffee_bar` ASC");
        SqlAssert.forQuery(connection,
                "select {[Has coffee bar].[All Has coffee bars].[false]} on 0 from Store")
            .expectSql(new SqlPattern(DatabaseProduct.MYSQL, sql, null))
            .verify();
    }

    /**
     * Execute testMdx both with and without running the cacheMdx first,
     * validating that sqlToLoadTestMdxMembers either fires or doesn't fire,
     * as appropriate.
     *
     * Assumption is that if the cacheMdx has fired, then members should
     * already be in cache and there is no need to load them.  If cacheMdx
     * is not fired we should see the sqlToLoadTestMdxMembers.
     */
    private void testWithAndWithoutCachedMembers(Connection connection,
        String cacheMdx, String testMdx, SqlPattern[] sqlToLoadTestMdxMembers)
    {
        for (boolean membersCached : new boolean[] {false, true}) {
            flushSchemaCache(connection);
            if (membersCached) {
                executeQuery(connection, cacheMdx);
            }
            SqlAssert.QuerySqlAssert assertion =
                    SqlAssert.forQuery(connection, testMdx).keepCache();
            if (membersCached) {
                assertion.expectNoSql(sqlToLoadTestMdxMembers);
            } else {
                assertion.expectSql(sqlToLoadTestMdxMembers);
            }
            assertion.verify();
        }
    }
}

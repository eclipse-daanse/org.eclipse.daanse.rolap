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

package org.eclipse.daanse.test;

import static org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct.getDatabaseProduct;
import static org.eclipse.daanse.rolap.testkit.assertions.Dialect.getDialect;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.query.component.Query;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * Unit test special "caption" settings.
 *
 * @author hhaas
 */
@RolapContextTest(FoodmartTestInstance.class)
class CaptionTest{

    /**
     * set caption "Anzahl Verkauf" for measure "Unit Sales"
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, MyFoodmartModifierEmf.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testMeasureCaption(Context<?> context) {
        final Connection monConnection =
                context.getConnectionWithDefaultRole();
        String mdxQuery =
                "SELECT {[Measures].[Unit Sales]} ON COLUMNS, "
                        + "{[Time].[1997].[Q1]} ON ROWS FROM [Sales]";
        Query monQuery = monConnection.parseQuery(mdxQuery);
        Result monResult = monConnection.execute(monQuery);
        Axis[] axes = monResult.getAxes();
        List<Position> positions = axes[0].getPositions();
        Member m0 = positions.get(0).get(0);
        String caption = m0.getCaption();
        assertEquals("Unit Sales", caption);
    }

    /**
     * set caption "Werbemedium" for nonshared dimension "Promotion Media"
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, MyFoodmartModifierEmf.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testDimCaption(Context<?> context) {
        final Connection monConnection =
                context.getConnectionWithDefaultRole();
        String mdxQuery =
                "SELECT {[Measures].[Unit Sales]} ON COLUMNS, "
                        + "{[Promotion Media].[All Media]} ON ROWS FROM [Sales]";
        Query monQuery = monConnection.parseQuery(mdxQuery);
        Result monResult = monConnection.execute(monQuery);
        Axis[] axes = monResult.getAxes();
        List<Position> positions = axes[1].getPositions();
        Member mall = positions.get(0).get(0);

        String caption = mall.getHierarchy().getCaption();
        assertEquals("Promotion Media", caption);
    }

    /**
     * set caption "Quadrat-Fuesse:-)" for shared dimension "Store Size in SQFT"
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, MyFoodmartModifierEmf.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testDimCaptionShared(Context<?> context) {
        String mdxQuery =
                "SELECT {[Measures].[Unit Sales]} ON COLUMNS, "
                        + "{[Store Size in SQFT].[All Store Size in SQFTs]} ON ROWS "
                        + "FROM [Sales]";
        final Connection monConnection =
                context.getConnectionWithDefaultRole();
        Query monQuery = monConnection.parseQuery(mdxQuery);
        Result monResult = monConnection.execute(monQuery);
        Axis[] axes = monResult.getAxes();
        List<Position> positions = axes[1].getPositions();
        Member mall = positions.get(0).get(0);

        String caption = mall.getHierarchy().getCaption();
        assertEquals("Store Size in SQFT", caption);
    }


    /**
     * Tests the &lt;CaptionExpression&gt; element. The caption for
     * [Time].[1997] should be "1997-12-31".
     *
     * <p>Test case for
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-236">Bug MONDRIAN-683,
     * "Caption expression for dimension levels missing implementation"</a>.
     */
    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, MyFoodmartModifierEmf.class },
    database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testLevelCaptionExpression(Context<?> context) {

        switch (getDatabaseProduct(getDialect(context.getConnectionWithDefaultRole()).name())) {
            case ACCESS:
            case ORACLE:
            case MARIADB:
            case MYSQL:
                break;
            default:
                // Due to provider-specific SQL in CaptionExpression, only Access,
                // Oracle and MySQL are supported in this test.
                return;
        }
        final Connection monConnection =
                context.getConnectionWithDefaultRole();
        String mdxQuery =
                "SELECT {[Measures].[Unit Sales]} ON COLUMNS, "
                        + "{[Time].[Year].Members} ON ROWS FROM [Sales]";
        Query monQuery = monConnection.parseQuery(mdxQuery);
        Result monResult = monConnection.execute(monQuery);
        Axis[] axes = monResult.getAxes();
        List<Position> positions = axes[1].getPositions();
        Member mall = positions.get(0).get(0);

        String caption = mall.getCaption();
        assertEquals("1997-12-31", caption);
    }

    /** Named bridge onto the FoodMart CSVs (for the {@code data =} supplier form). */
    public static class FoodmartData implements DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }

}

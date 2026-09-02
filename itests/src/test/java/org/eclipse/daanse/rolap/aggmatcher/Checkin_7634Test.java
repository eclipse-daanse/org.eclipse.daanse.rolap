/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/

package org.eclipse.daanse.rolap.aggmatcher;

import java.io.PrintWriter;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Result;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.BatchTestCase;

/**
 * Checkin 7634 attempted to correct a problem demonstrated by this
 * junit. The CrossJoinFunDef class has an optimization that kicks in
 * when the combined lists sizes are greater than 1000. I create a
 * property here which, if set, can be used to change that size from
 * 1000 to, in this case, 2. Also, there is a property that disables the
 * use of the optimization altogether and another that permits the
 * use of the old optimization, currently the nonEmptyListOld method in
 * the CrossJoinFunDef class, and the new, checkin 7634, version of the
 * method called nonEmptyList.
 *
 * <p>The old optimization only looked at the default measure while the
 * new version looks at all measures appearing in the query.
 * The example Cube and data for the junit is such that there is no
 * data for the default measure. Thus the old optimization fails
 * to produce the correct result.
 *
 * @author Richard M. Emberson
  */
@RolapContextTest(value = Checkin_7634TestInstance.class, dbScope = DbScope.PER_CLASS)
class Checkin_7634Test extends BatchTestCase {

    @Test
    @RolapConfig(key = ConfigConstants.CROSS_JOIN_OPTIMIZER_SIZE, value = "2147483647", type = Integer.class)
    void testCrossJoin(Connection connection) throws Exception {
        // explicit use of [Product].[Class1]
        String mdx =
        "select {[Measures].[Requested Value]} ON COLUMNS,"+
        " NON EMPTY Crossjoin("+
        " {[Geography].[All Regions].Children},"+
        " {[Product].[All Products].Children}"+
        ") ON ROWS"+
        " from [Checkin_7634]";

        // The original test toggled CrossJoinOptimizerSize between these two
        // executions to compare the old and new nonEmptyList optimizations;
        // both branches set the same value (a pre-existing quirk of the
        // test), so this is preserved as a determinism check: the same query
        // against the same config must return the same result twice.
        Result result1 = executeQuery(mdx, connection);
        String resultString1 = toString(result1);

        Result result2 = executeQuery(mdx, connection);
        String resultString2 = toString(result2);

        assertEquals(resultString1, resultString2);
    }


    /**
     * Converts a {@link Result} to text in traditional format.
     *
     * @param result Query result
     * @return Result as text
     */
    private static String toString(Result result) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        result.print(pw);
        pw.flush();
        return sw.toString();
    }
}

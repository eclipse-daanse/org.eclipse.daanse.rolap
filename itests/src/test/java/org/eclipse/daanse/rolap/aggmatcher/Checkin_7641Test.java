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
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.BatchTestCase;

/**
 * Checkin 7641 attempted to correct a problem demonstrated by this
 * junit. The original problem involved implicit Time member usage in
 * on axis and the use of the default Time member in the other axis.
 * This junit defines a hierarchy Product with a default member 'Class2',
 * The MDX in one axis explicitly uses the {Product][Class1] member.
 * Depending upon whether the 7641 code is used or not (its use
 * depends upon the existance of a System property) one gets different
 * answers when the mdx is evaluated.
 *
 * @author Richard M. Emberson
 */
@RolapContextTest(value = Checkin_7641TestInstance.class, dbScope = DbScope.PER_CLASS)
class Checkin_7641Test extends BatchTestCase {

    @Test
    void testImplicitMember(Connection connection) throws Exception {
        // explicit use of [Product].[Class1]
        String mdx =
            " select NON EMPTY Crossjoin("
            + " Hierarchize(Union({[Product].[Class1]}, "
            + "[Product].[Class1].Children)), "
            + " {[Measures].[Requested Value], "
            + " [Measures].[Shipped Value]}"
            + ") ON COLUMNS,"
            + " NON EMPTY Hierarchize(Union({[Geography].[All Regions]},"
            + "[Geography].[All Regions].Children)) ON ROWS"
            + " from [ImplicitMember]";

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

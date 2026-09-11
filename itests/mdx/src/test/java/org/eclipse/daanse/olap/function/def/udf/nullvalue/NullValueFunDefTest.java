/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.olap.function.def.udf.nullvalue;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.result.Cell;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.assertions.MdxAssert;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class NullValueFunDefTest {

    @Test
    void testNullValue(Context<?> context) {
        Connection connection=context.getConnectionWithDefaultRole();
        MdxAssert.assertThatExpr(context.getConnectionWithDefaultRole(), "Sales", "NullValue()/NullValue()")
        .returns("");

        String cubeName="Sales";
        Cell c=  executeQuery(connection, "with member [Measures].[Foo] as " + Util.singleQuoteString(" NullValue()/NullValue() ")
            + " select {[Measures].[Foo]} on columns from " + cubeName).getCell(new int[] { 0 });
        String s=c.getFormattedValue();
        assertEquals("", s);

        c = executeQuery(connection, "with member [Measures].[Foo] as " + Util.singleQuoteString(" NullValue()/NullValue() = NULL ")
            + " select {[Measures].[Foo]} on columns from " + cubeName).getCell(new int[] { 0 });
        s=c.getFormattedValue();

        assertEquals("false", s);

        boolean hasException = false;
        try {
            c = executeQuery(connection, "with member [Measures].[Foo] as " + Util.singleQuoteString(" NullValue() IS NULL ")
            + " select {[Measures].[Foo]} on columns from " + cubeName).getCell(new int[] { 0 });
            s=c.getFormattedValue();
        } catch (Exception ex) {
            hasException = true;
        }
        assertTrue(hasException);

        // MDX NULL is represented as Java null in the calc layer; IsEmpty of
        // a NULL-valued expression is therefore true - which matches MSAS.
        c = executeQuery(connection, "with member [Measures].[Foo] as " + Util.singleQuoteString(" IsEmpty(NullValue()) ")
            + " select {[Measures].[Foo]} on columns from " + cubeName).getCell(new int[] { 0 });
        s=c.getFormattedValue();

        assertEquals("true", s);

        // NullValue()/NullValue() evaluates to DoubleNull
        // but DoubleNull evaluates to null, so this seems
        // to be broken??
        // s = executeExpr(" IsEmpty(NullValue()/NullValue()) ");
        // assertEquals("false", s);

        c = executeQuery(connection, "with member [Measures].[Foo] as " + Util.singleQuoteString(" 4 + NullValue() ")
            + " select {[Measures].[Foo]} on columns from " + cubeName).getCell(new int[] { 0 });
        s=c.getFormattedValue();
        assertEquals("4", s);

        c = executeQuery(connection, "with member [Measures].[Foo] as " + Util.singleQuoteString(" NullValue() - 4 ")
            + " select {[Measures].[Foo]} on columns from " + cubeName).getCell(new int[] { 0 });
        s=c.getFormattedValue();
        assertEquals("-4", s);

        c = executeQuery(connection, "with member [Measures].[Foo] as " + Util.singleQuoteString(" 4*NullValue() ")
            + " select {[Measures].[Foo]} on columns from " + cubeName).getCell(new int[] { 0 });
        s=c.getFormattedValue();
        assertEquals("", s);

        c = executeQuery(connection, "with member [Measures].[Foo] as " + Util.singleQuoteString(" NullValue()*4 ")
            + " select {[Measures].[Foo]} on columns from " + cubeName).getCell(new int[] { 0 });
        s=c.getFormattedValue();
        assertEquals("", s);

        c = executeQuery(connection, "with member [Measures].[Foo] as " + Util.singleQuoteString(" 4/NullValue() ")
            + " select {[Measures].[Foo]} on columns from " + cubeName).getCell(new int[] { 0 });
        s=c.getFormattedValue();
        assertEquals("Infinity", s);

        c = executeQuery(connection, "with member [Measures].[Foo] as " + Util.singleQuoteString(" NullValue()/4 ")
            + " select {[Measures].[Foo]} on columns from " + cubeName).getCell(new int[] { 0 });
        s=c.getFormattedValue();
        assertEquals("", s);
        /*
         */
    }

}

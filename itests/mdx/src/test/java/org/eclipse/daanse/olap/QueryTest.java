/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (C) 1998-2005 Julian Hyde
* Copyright (C) 2005-2017 Hitachi Vantara and others
* All Rights Reserved.
*
* Shishir, 08 May, 2007
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

package org.eclipse.daanse.olap;

import static org.eclipse.daanse.olap.common.Util.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.execution.Statement;
import org.eclipse.daanse.olap.api.query.component.CellProperty;
import org.eclipse.daanse.olap.api.query.component.Formula;
import org.eclipse.daanse.olap.connection.ConnectionBase;
import org.eclipse.daanse.olap.query.component.CellPropertyImpl;
import org.eclipse.daanse.olap.query.component.IdImpl;
import org.eclipse.daanse.olap.query.component.QueryAxisImpl;
import org.eclipse.daanse.olap.query.component.QueryImpl;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Query test.
 */
@RolapContextTest(FoodmartTestInstance.class)
class QueryTest {
    private CellProperty[] cellProps = {
        new CellPropertyImpl(IdImpl.toList("Value")),
        new CellPropertyImpl(IdImpl.toList("Formatted_Value")),
        new CellPropertyImpl(IdImpl.toList("Format_String")),
    };
    private QueryAxisImpl[] axes = new QueryAxisImpl[0];
    private Formula[] formulas = new Formula[0];
    private QueryImpl queryWithCellProps;
    private QueryImpl queryWithoutCellProps;


    private void beforeTest(Context<?> context)
    {

        ConnectionBase connection =
                (ConnectionBase) context.getConnectionWithDefaultRole();
        final Statement statement =
                connection.getInternalStatement();

        try {
            queryWithCellProps =
                    new QueryImpl(
                            statement, formulas, axes, "Sales",
                            null, cellProps, false);
            queryWithoutCellProps =
                    new QueryImpl(
                            statement, formulas, axes, "Sales",
                            null, new CellProperty[0], false);
        } finally {
            statement.close();
        }
    }

    @AfterEach
    public void afterEach() {
        queryWithCellProps = null;
        queryWithoutCellProps = null;
    }

    @Test
    void testHasCellPropertyWhenQueryHasCellProperties(Context<?> context) {
        beforeTest(context);
        assertTrue(queryWithCellProps.hasCellProperty("Value"));
        assertFalse(queryWithCellProps.hasCellProperty("Language"));
    }

    @Test
    void testIsCellPropertyEmpty(Context<?> context) {
        beforeTest(context);
        assertTrue(queryWithoutCellProps.isCellPropertyEmpty());
    }
}

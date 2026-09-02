/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2001-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara and others
 * All Rights Reserved.
 *
 * ajogleka, 19 December, 2007
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


package org.eclipse.daanse.olap.fun;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.function.def.set.SetFunDef;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the set constructor function <code>{ ... }</code>,
 * {@link SetFunDef}.
 *
 * @author ajogleka
 * @since 19 December, 2007
 */
@RolapContextTest(FoodmartTestInstance.class)
class SetFunDefTest {

    @Test
    void testSetWithMembersFromDifferentHierarchies(Context<?> context) {
        assertQueryFailsInSetValidation(context.getConnectionWithDefaultRole(),
            "with member store.x as "
            + "'{[Gender].[M],[Store].[USA].[CA]}' "
            + " SELECT store.x on 0, [measures].[customer count] on 1 from sales");
    }

    @Test
    void testSetWith2TuplesWithDifferentHierarchies(Context<?> context) {
        assertQueryFailsInSetValidation(context.getConnectionWithDefaultRole(),
            "with member store.x as '{([Gender].[M],[Store].[All Stores].[USA].[CA]),"
            + "([Store].[USA].[OR],[Gender].[F])}'\n"
            + " SELECT store.x on 0, [measures].[customer count] on 1 from sales");
    }

    private void assertQueryFailsInSetValidation(Connection connection, String query) {
        assertThatQuery(connection, query)
            .throwsMessage("All arguments to function '{}' "
            + "must have same hierarchy");
    }
}

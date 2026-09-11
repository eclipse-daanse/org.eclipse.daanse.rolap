/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.olap.function.def.strtoset;

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class StrToSetFunDefTest {


    @Test
    void testStrToSet(Context<?> context) {
        // TODO: handle text after '}'
        // TODO: handle string which ends too soon
        // TODO: handle spaces before first '{'
        // TODO: test spaces before unbracketed names,
        //       e.g. "{Gender. M, Gender. F   }".

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "StrToSet("
                + " \"{[Gender].[F], [Gender].[M]}\","
                + " [Gender])")
            .returns(
            "[Gender].[Gender].[F]\n"
                + "[Gender].[Gender].[M]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "StrToSet("
                + " \"{[Gender].[F], [Time].[1997]}\","
                + " [Gender])")
            .throwsMessage( "member is of wrong hierarchy" );

        // whitespace ok
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "StrToSet("
                + " \"  {   [Gender] .  [F]  ,[Gender].[M] }  \","
                + " [Gender])")
            .returns(
            "[Gender].[Gender].[F]\n"
                + "[Gender].[Gender].[M]" );

        // tuples
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "StrToSet("
                + "\""
                + "{"
                + " ([Gender].[F], [Time].[1997].[Q2]), "
                + " ([Gender].[M], [Time].[1997])"
                + "}"
                + "\","
                + " [Gender],"
                + " [Time])")
            .returns(
            "{[Gender].[Gender].[F], [Time].[Time].[1997].[Q2]}\n"
                + "{[Gender].[Gender].[M], [Time].[Time].[1997]}" );

        // matches unique name
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "StrToSet("
                + "\""
                + "{"
                + " [Store].[USA].[CA], "
                + " [Store].[All Stores].[USA].OR,"
                + " [Store].[All Stores]. [USA] . [WA]"
                + "}"
                + "\","
                + " [Store])")
            .returns(
            "[Store].[Store].[USA].[CA]\n"
                + "[Store].[Store].[USA].[OR]\n"
                + "[Store].[Store].[USA].[WA]" );
    }

    @Test
    void testStrToSetDupDimensionsFails(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "StrToSet("
                + "\""
                + "{"
                + " ([Gender].[F], [Time].[1997].[Q2], [Gender].[F]), "
                + " ([Gender].[M], [Time].[1997], [Gender].[F])"
                + "}"
                + "\","
                + " [Gender],"
                + " [Time],"
                + " [Gender])")
            .throwsMessage( "Tuple contains more than one member of hierarchy '[Gender].[Gender]'." );
    }

    @Test
    @RolapConfig(key = ConfigConstants.IGNORE_INVALID_MEMBERS_DURING_QUERY, value = "true", type = Boolean.class)
    void testStrToSetIgnoreInvalidMembers(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "StrToSet("
                + "\""
                + "{"
                + " [Product].[Food],"
                + " [Product].[Food].[You wouldn't like],"
                + " [Product].[Drink].[You would like],"
                + " [Product].[Drink].[Dairy]"
                + "}"
                + "\","
                + " [Product])")
            .returns(
            "[Product].[Product].[Food]\n"
                + "[Product].[Product].[Drink].[Dairy]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "StrToSet("
                + "\""
                + "{"
                + " ([Gender].[M], [Product].[Food]),"
                + " ([Gender].[F], [Product].[Food].[You wouldn't like]),"
                + " ([Gender].[M], [Product].[Drink].[You would like]),"
                + " ([Gender].[F], [Product].[Drink].[Dairy])"
                + "}"
                + "\","
                + " [Gender], [Product])")
            .returns(
            "{[Gender].[Gender].[M], [Product].[Product].[Food]}\n"
                + "{[Gender].[Gender].[F], [Product].[Product].[Drink].[Dairy]}" );
    }


}

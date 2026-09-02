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
package org.eclipse.daanse.olap.function.def.descendants;

import static org.eclipse.daanse.olap.function.TestResources.hierarchized1997;
import static org.eclipse.daanse.olap.function.TestResources.months;
import static org.eclipse.daanse.olap.function.TestResources.quarters;
import static org.eclipse.daanse.olap.function.TestResources.year1997;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatSetExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatExpr;

import javax.sql.DataSource;

import org.eclipse.daanse.olap.api.Context;
import  org.eclipse.daanse.olap.util.Bug;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class DescendantsByLevelFunDefTest {

    @Test
    void testDescendantsM(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997].[Q1])")
            .returns(
            "[Time].[Time].[1997].[Q1]\n"
                + "[Time].[Time].[1997].[Q1].[1]\n"
                + "[Time].[Time].[1997].[Q1].[2]\n"
                + "[Time].[Time].[1997].[Q1].[3]" );
    }

    @Test
    void testDescendantsDepends(Context<?> context) {
        assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[Time].CurrentMember)")
            .dependsOn( "[Time].[Time]" );
    }

    @Test
    void testDescendantsML(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], [Time].[Month])")
            .returns( months );
    }

    @Test
    void testDescendantsMLSelf(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], [Time].[Quarter], SELF)")
            .returns( quarters );
    }

    @Test
    void testDescendantsMLLeaves(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], [Time].[Year], LEAVES)")
            .returns( "[Time].[Time].[1997]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], [Time].[Quarter], LEAVES)")
            .returns( "[Time].[Time].[1997].[Q1]\n" + "[Time].[Time].[1997].[Q2]\n" + "[Time].[Time].[1997].[Q3]\n" + "[Time].[Time].[1997].[Q4]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], [Time].[Month], LEAVES)")
            .returns( months );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Gender].[Gender], [Gender].[Gender].[Gender], leaves)")
            .returns( "[Gender].[Gender].[F]\n" + "[Gender].[Gender].[M]" );
    }

    @Test
    void testDescendantsMLLeavesRagged(Context<?> context) {
        // no cities are at leaf level
        //final TestContext<?> raggedContext<?> =
        //  getTestContext().withCube( "[Sales Ragged]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales Ragged",
            "Descendants([Store].[Israel], [Store].[Store City], leaves)")
            .returns( "[Store].[Store].[Israel].[Israel].[Haifa]\n" + "[Store].[Store].[Israel].[Israel].[Tel Aviv]" );

        // all cities are leaves
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales Ragged",
            "Descendants([Geography].[Israel], [Geography].[City], leaves)")
            .returns(
            "[Geography].[Geography].[Israel].[Israel].[Haifa]\n"
                + "[Geography].[Geography].[Israel].[Israel].[Tel Aviv]" );

        // No state is a leaf (not even Israel, which is both a country and a
        // a state, or Vatican, with is a country/state/city)
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales Ragged",
            "Descendants([Geography], [Geography].[State], leaves)")
            .returns(
            "[Geography].[Geography].[Canada].[BC]\n" +
                "[Geography].[Geography].[Mexico].[DF]\n" +
                "[Geography].[Geography].[Mexico].[Guerrero]\n" +
                "[Geography].[Geography].[Mexico].[Jalisco]\n" +
                "[Geography].[Geography].[Mexico].[Veracruz]\n" +
                "[Geography].[Geography].[Mexico].[Yucatan]\n" +
                "[Geography].[Geography].[Mexico].[Zacatecas]\n" +
                "[Geography].[Geography].[USA].[CA]\n" +
                "[Geography].[Geography].[USA].[OR]\n" +
                "[Geography].[Geography].[USA].[WA]\n" +
                "[Geography].[Geography].[Vatican]"
        );

    }

    @Test
    void testDescendantsMNLeaves(Context<?> context) {
        // leaves at depth 0 returns the member itself
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997].[Q2].[4], 0, Leaves)")
            .returns( "[Time].[Time].[1997].[Q2].[4]" );

        // leaves at depth > 0 returns the member itself
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997].[Q2].[4], 100, Leaves)")
            .returns( "[Time].[Time].[1997].[Q2].[4]" );

        // leaves at depth < 0 returns all descendants
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997].[Q2], -1, Leaves)")
            .returns(
            "[Time].[Time].[1997].[Q2].[4]\n"
                + "[Time].[Time].[1997].[Q2].[5]\n"
                + "[Time].[Time].[1997].[Q2].[6]" );

        // leaves at depth 0 returns the member itself
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997].[Q2], 0, Leaves)")
            .returns(
            "[Time].[Time].[1997].[Q2].[4]\n"
                + "[Time].[Time].[1997].[Q2].[5]\n"
                + "[Time].[Time].[1997].[Q2].[6]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997].[Q2], 3, Leaves)")
            .returns(
            "[Time].[Time].[1997].[Q2].[4]\n"
                + "[Time].[Time].[1997].[Q2].[5]\n"
                + "[Time].[Time].[1997].[Q2].[6]" );
    }

    @Test
    void testDescendantsMLSelfBefore(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], [Time].[Quarter], SELF_AND_BEFORE)")
            .returns( year1997 + "\n" + quarters );
    }

    @Test
    void testDescendantsMLSelfBeforeAfter(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], [Time].[Quarter], SELF_BEFORE_AFTER)")
            .returns( hierarchized1997 );
    }

    @Test
    void testDescendantsMLBefore(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], [Time].[Quarter], BEFORE)").returns( year1997 );
    }

    @Test
    void testDescendantsMLBeforeAfter(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], [Time].[Quarter], BEFORE_AND_AFTER)")
            .returns( year1997 + "\n" + months );
    }

    @Test
    void testDescendantsMLAfter(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], [Time].[Quarter], AFTER)").returns( months );
    }

    @Test
    void testDescendantsMLAfterEnd(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], [Time].[Month], AFTER)").returns( "" );
    }

    @Test
    void testDescendantsM0(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], 0)").returns( year1997 );
    }

    @Test
    void testDescendantsM2(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], 2)").returns( months );
    }

    @Test
    void testDescendantsM2Self(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], 2, Self)").returns( months );
    }

    @Test
    void testDescendantsM2Leaves(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], 2, Leaves)").returns( months );
    }

    @Test
    void testDescendantsMFarLeaves(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], 10000, Leaves)").returns( months );
    }

    @Test
    void testDescendantsMEmptyLeaves(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], , Leaves)")
            .returns( months );
    }

    @Test
    void testDescendantsMEmptyLeavesFail(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997],)")
            .throwsMessage( "No function matches signature 'Descendants(<Member>, <Empty>)" );
    }

    @Test
    void testDescendantsMEmptyLeavesFail2(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], , AFTER)")
            .throwsMessage( "depth must be specified unless DESC_FLAG is LEAVES" );
    }

    @Test
    void testDescendantsMFarSelf(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], 10000, Self)")
            .returns( "" );
    }

    @Test
    void testDescendantsMNY(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], 1, BEFORE_AND_AFTER)")
            .returns( year1997 + "\n" + months );
    }

    @Test
    void testDescendants2ndHier(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[Weekly].[1997].[10], [Time].[Weekly].[Day])")
            .returns(
            "[Time].[Weekly].[1997].[10].[1]\n"
                + "[Time].[Weekly].[1997].[10].[23]\n"
                + "[Time].[Weekly].[1997].[10].[24]\n"
                + "[Time].[Weekly].[1997].[10].[25]\n"
                + "[Time].[Weekly].[1997].[10].[26]\n"
                + "[Time].[Weekly].[1997].[10].[27]\n"
                + "[Time].[Weekly].[1997].[10].[28]" );
    }

    @Test
    void testDescendantsParentChild(Context<?> context) {
        //getTestContext().withCube( "HR" ).
        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Descendants([Employees], 2)")
            .returns(
            "[Employees].[Employees].[Sheri Nowmer].[Derrick Whelply]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Michael Spence]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Maya Gutierrez]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Roberta Damstra]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Rebecca Kanagaki]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Darren Stanz]\n"
                + "[Employees].[Employees].[Sheri Nowmer].[Donna Arnold]" );
    }

    @Test
    void testDescendantsParentChildBefore(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Descendants([Employees], 2, BEFORE)")
            .returns(
            "[Employees].[Employees].[All Employees]\n"
                + "[Employees].[Employees].[Sheri Nowmer]" );
    }

    @Disabled //disabled for CI build
    @Test
    void testDescendantsParentChildLeaves(Context<?> context) {
        //final TestContext<?> testContext<?> = getTestContext().withCube( "HR" );
        DataSource dataSource = context.getConnectionWithDefaultRole().getDataSource();
        if (Bug.avoidSlowTestOnLucidDB( context.getDialect())) {
            return;
        }

        // leaves, restricted by level
        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Descendants([Employees].[All Employees].[Sheri Nowmer].[Michael Spence], [Employees].[Employee Id], LEAVES)")
            .returns(
            "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[John "
                + "Brooks]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Todd "
                + "Logan]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Joshua "
                + "Several]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[James "
                + "Thomas]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Robert "
                + "Vessa]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Bronson"
                + " Jacobs]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Rebecca"
                + " Barley]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Emilio "
                + "Alvaro]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Becky "
                + "Waters]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[A. "
                + "Joyce Jarvis]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Ruby "
                + "Sue Styles]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Lisa "
                + "Roy]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Ingrid "
                + "Burkhardt]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Todd "
                + "Whitney]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Barbara"
                + " Wisnewski]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Karren "
                + "Burkhardt]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[John "
                + "Long]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Edwin "
                + "Olenzek]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Jessie "
                + "Valerio]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Robert "
                + "Ahlering]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Megan "
                + "Burke]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Mary Sandidge].[Karel "
                + "Bates]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[James "
                + "Tran]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Shelley"
                + " Crow]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Anne "
                + "Sims]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard]"
                + ".[Clarence Tatman]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Jan "
                + "Nelsen]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Jeanie "
                + "Glenn]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Peggy "
                + "Smith]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Tish "
                + "Duff]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Anita "
                + "Lucero]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Stephen"
                + " Burton]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Amy "
                + "Consentino]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Stacie "
                + "Mcanich]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Mary "
                + "Browning]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard]"
                + ".[Alexandra Wellington]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Cory "
                + "Bacugalupi]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Stacy "
                + "Rizzi]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Mike "
                + "White]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Marty "
                + "Simpson]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Robert "
                + "Jones]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Raul "
                + "Casts]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Bridget"
                + " Browqett]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Monk Skonnard].[Kay "
                + "Kartz]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Jeanette Cole]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Phyllis Huntsman]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Hannah Arakawa]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Wathalee Steuber]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Pamela Cox]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Helen Lutes]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Linda Ecoffey]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Katherine Swint]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Dianne Slattengren]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Ronald Heymsfield]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Steven Whitehead]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[William Sotelo]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck].[Beth"
                + " Stanley]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck].[Jill"
                + " Markwood]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Mildred Valentine]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Suzann Reams]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Audrey Wold]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Susan French]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Trish Pederson]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck].[Eric"
                + " Renn]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck]"
                + ".[Elizabeth Catalano]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Christopher Beck].[Eric"
                + " Coleman]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Catherine Abel]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Emilo Miller]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Daniel Wolter].[Michael John Troyer].[Hazel Walker]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Sara Pettengill].[Linda "
                + "Blasingame]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Sara Pettengill].[Jackie "
                + "Blackwell]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Sara Pettengill].[John "
                + "Ortiz]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Sara Pettengill].[Stacey "
                + "Tearpak]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Sara Pettengill].[Fannye "
                + "Weber]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Sara Pettengill].[Diane "
                + "Kabbes]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Sara Pettengill].[Brenda "
                + "Heaney]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Sara Pettengill].[Judith "
                + "Karavites]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Jauna Elson]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Nancy Hirota]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Marie Moya]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Nicky Chesnut]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Karen Hall]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Greg Narberes]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Anna Townsend]\n"
                + "[Employees].[Sheri Nowmer].[Michael Spence].[Dianne Collins].[Lawrence Hurkett].[Carol Ann Rockne]" );

        // leaves, restricted by depth
        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Descendants([Employees], 1, LEAVES)").returns( "" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Descendants([Employees], 2, LEAVES)")
            .returns(
            "[Employees].[Sheri Nowmer].[Roberta Damstra].[Jennifer Cooper]\n"
                + "[Employees].[Sheri Nowmer].[Roberta Damstra].[Peggy Petty]\n"
                + "[Employees].[Sheri Nowmer].[Roberta Damstra].[Jessica Olguin]\n"
                + "[Employees].[Sheri Nowmer].[Roberta Damstra].[Phyllis Burchett]\n"
                + "[Employees].[Sheri Nowmer].[Rebecca Kanagaki].[Juanita Sharp]\n"
                + "[Employees].[Sheri Nowmer].[Rebecca Kanagaki].[Sandra Brunner]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Ernest Staton]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Rose Sims]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Lauretta De Carlo]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Mary Williams]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Terri Burke]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Audrey Osborn]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Brian Binai]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Concepcion Lozada]\n"
                + "[Employees].[Sheri Nowmer].[Donna Arnold].[Howard Bechard]\n"
                + "[Employees].[Sheri Nowmer].[Donna Arnold].[Doris Carter]" );

        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Descendants([Employees], 3, LEAVES)")
            .returns(
            "[Employees].[Sheri Nowmer].[Roberta Damstra].[Jennifer Cooper]\n"
                + "[Employees].[Sheri Nowmer].[Roberta Damstra].[Peggy Petty]\n"
                + "[Employees].[Sheri Nowmer].[Roberta Damstra].[Jessica Olguin]\n"
                + "[Employees].[Sheri Nowmer].[Roberta Damstra].[Phyllis Burchett]\n"
                + "[Employees].[Sheri Nowmer].[Rebecca Kanagaki].[Juanita Sharp]\n"
                + "[Employees].[Sheri Nowmer].[Rebecca Kanagaki].[Sandra Brunner]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Ernest Staton]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Rose Sims]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Lauretta De Carlo]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Mary Williams]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Terri Burke]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Audrey Osborn]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Brian Binai]\n"
                + "[Employees].[Sheri Nowmer].[Darren Stanz].[Concepcion Lozada]\n"
                + "[Employees].[Sheri Nowmer].[Donna Arnold].[Howard Bechard]\n"
                + "[Employees].[Sheri Nowmer].[Donna Arnold].[Doris Carter]" );

        // note that depth is RELATIVE to the starting member
        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Descendants([Employees].[Sheri Nowmer].[Roberta Damstra], 1, LEAVES)")
            .returns(
            "[Employees].[Sheri Nowmer].[Roberta Damstra].[Jennifer Cooper]\n"
                + "[Employees].[Sheri Nowmer].[Roberta Damstra].[Peggy Petty]\n"
                + "[Employees].[Sheri Nowmer].[Roberta Damstra].[Jessica Olguin]\n"
                + "[Employees].[Sheri Nowmer].[Roberta Damstra].[Phyllis Burchett]" );

        // Howard Bechard is a leaf member -- appears even at depth 0
        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Descendants([Employees].[All Employees].[Sheri Nowmer].[Donna Arnold].[Howard Bechard], 0, LEAVES)")
            .returns( "[Employees].[Sheri Nowmer].[Donna Arnold].[Howard Bechard]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "HR",
            "Descendants([Employees].[All Employees].[Sheri Nowmer].[Donna Arnold].[Howard Bechard], 1, LEAVES)")
            .returns( "[Employees].[Sheri Nowmer].[Donna Arnold].[Howard Bechard]" );

        assertThatExpr(context.getConnectionWithDefaultRole(), "HR",
            "Count(Descendants([Employees], 2, LEAVES))").returns( "16" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "HR",
            "Count(Descendants([Employees], 3, LEAVES))").returns( "16" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "HR",
            "Count(Descendants([Employees], 4, LEAVES))").returns( "63" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "HR",
            "Count(Descendants([Employees], 999, LEAVES))").returns( "1,044" );

        // Negative depth acts like +infinity (per MSAS).  Run the test several
        // times because we had a non-deterministic bug here.
        for ( int i = 0; i < 100; ++i ) {
            assertThatExpr(context.getConnectionWithDefaultRole(), "HR",
                "Count(Descendants([Employees], -1, LEAVES))").returns( "1,044" );
        }
    }

    @Test
    void testDescendantsSBA(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], 1, SELF_BEFORE_AFTER)")
            .returns( hierarchized1997 );
    }

    @Test
    void testDescendantsSet(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants({[Time].[1997].[Q4], [Time].[1997].[Q2]}, 1)")
            .returns(
            "[Time].[Time].[1997].[Q4].[10]\n"
                + "[Time].[Time].[1997].[Q4].[11]\n"
                + "[Time].[Time].[1997].[Q4].[12]\n"
                + "[Time].[Time].[1997].[Q2].[4]\n"
                + "[Time].[Time].[1997].[Q2].[5]\n"
                + "[Time].[Time].[1997].[Q2].[6]" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants({[Time].[1997]}, [Time].[Month], LEAVES)")
            .returns( months );
    }

    @Test
    void testDescendantsSetEmpty(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants({}, 1)")
            .throwsMessage( "Cannot deduce type of set" );
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants(Filter({[Time].[Time].Members}, 1=0), 1)")
            .returns( "" );
    }

    @Test
    void testItemMember(Context<?> context) {
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Descendants([Time].[1997], [Time].[Month]).Item(1).Item(0).UniqueName")
            .returns( "[Time].[Time].[1997].[Q1].[2]" );

        // Access beyond the list yields the Null member.
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Time].[1997].Children.Item(6).UniqueName").returns( "[Time].[Time].[#null]" );
        assertThatExpr(context.getConnectionWithDefaultRole(), "Sales",
            "[Time].[1997].Children.Item(-1).UniqueName").returns( "[Time].[Time].[#null]" );
    }
}

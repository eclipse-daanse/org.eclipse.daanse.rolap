/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2004-2005 Julian Hyde
 * Copyright (C) 2005-2017 Hitachi Vantara
 * All Rights Reserved.
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
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.DbScope;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

import org.eclipse.daanse.rolap.testkit.assertions.DatabaseProduct;
import org.eclipse.daanse.rolap.SchemaModifiersEmf;

/**
 * <code>RaggedHierarchyTest</code> tests ragged hierarchies.
 * <p>
 *
 * @author jhyde
 * @since Apr 19, 2004
 */
@RolapContextTest(FoodmartTestInstance.class)
@RolapConfig(key = ConfigConstants.NULL_MEMBER_REPRESENTATION, value = "null", type = String.class)
class RaggedHierarchyTest {


	  // NullMemberRepresentation is set for the whole class by the class-level
	  // @RolapConfig; a method-level @RolapContextTest (for the schema-modified
	  // tests below) replaces class config entirely, so it is re-declared there.

    private void assertRaggedReturns(Connection connection, String expression, String expected) {
        //getTestContext().withCube("[Sales Ragged]")
        assertThatAxis(connection, "Sales Ragged", expression).returns(expected);
    }

    // ~ The tests ------------------------------------------------------------

    @Test
    void testChildrenOfRoot(Connection connection) {
        assertRaggedReturns(connection,
            "[Store].[Store].children",
            "[Store].[Store].[Canada]\n"
            + "[Store].[Store].[Israel]\n"
            + "[Store].[Store].[Mexico]\n"
            + "[Store].[Store].[USA]\n"
            + "[Store].[Store].[Vatican]");
    }

    @Test
    void testChildrenOfUSA(Connection connection) {
        assertRaggedReturns(connection,
            "[Store].[Store].[USA].children",
            "[Store].[Store].[USA].[CA]\n"
            + "[Store].[Store].[USA].[OR]\n"
            + "[Store].[Store].[USA].[USA].[Washington]\n"
            + "[Store].[Store].[USA].[WA]");
    }

    // Israel has one real child, which is hidden, and which has children
    // Haifa and Tel Aviv
    @Test
    void testChildrenOfIsrael(Connection connection) {
        assertRaggedReturns(connection,
            "[Store].[Israel].children",
            "[Store].[Store].[Israel].[Israel].[Haifa]\n"
            + "[Store].[Store].[Israel].[Israel].[Tel Aviv]");
    }

    // Vatican's descendants at the province and city level are hidden
    @Test
    public void testChildrenOfVatican(Connection connection) {
        assertRaggedReturns(connection,
            "[Store].[Vatican].children",
            "[Store].[Store].[Vatican].[Vatican].[null].[Store 17]");
    }

    @Test
    void testParentOfHaifa(Connection connection) {
        assertRaggedReturns(connection,
            "[Store].[Store].[Israel].[Haifa].Parent", "[Store].[Store].[Israel]");
    }

    @Test
    void testParentOfVatican(Connection connection) {
        assertRaggedReturns(connection,
            "[Store].[Vatican].Parent", "[Store].[Store].[All Stores]");
    }

    // PrevMember must return something at the same level -- a city
    @Test
    void testPrevMemberOfHaifa(Connection connection) {
        assertRaggedReturns(connection,
            "[Store].[Israel].[Haifa].PrevMember",
            "[Store].[Store].[Canada].[BC].[Victoria]");
    }

    // PrevMember must return something at the same level -- a city
    @Test
    void testNextMemberOfTelAviv(Connection connection) {
        assertRaggedReturns(connection,
            "[Store].[Israel].[Tel Aviv].NextMember",
            "[Store].[Store].[Mexico].[DF].[Mexico City]");
    }

    @Test
    void testNextMemberOfBC(Connection connection) {
        // The next state after BC is Israel, but it's hidden
        assertRaggedReturns(connection,
            "[Store].[Canada].[BC].NextMember",
            "[Store].[Store].[Mexico].[DF]");
    }

    @Test
    void testLead(Connection connection) {
        assertRaggedReturns(connection,
            "[Store].[Mexico].[DF].Lead(1)",
            "[Store].[Store].[Mexico].[Guerrero]");
        assertRaggedReturns(connection,
            "[Store].[Mexico].[DF].Lead(0)",
            "[Store].[Store].[Mexico].[DF]");
        // Israel is immediately before Mexico, but is hidden
        assertRaggedReturns(connection,
            "[Store].[Mexico].[DF].Lead(-1)",
            "[Store].[Store].[Canada].[BC]");
        assertRaggedReturns(connection,
            "[Store].[Mexico].[DF].Lag(1)",
            "[Store].[Store].[Canada].[BC]");
        // Fall off the edge of the world
        assertRaggedReturns(connection,
            "[Store].[Mexico].[DF].Lead(-2)", "");
        assertRaggedReturns(connection,
            "[Store].[Mexico].[DF].Lead(-543)", "");
    }

    @Test
    public void testDescendantsOfVatican(Connection connection) {
        assertRaggedReturns(connection,
            "Descendants([Store].[Vatican])",
            "[Store].[Store].[Vatican]\n"
            + "[Store].[Store].[Vatican].[Vatican].[null].[Store 17]");
    }

    // The only child of Vatican at state level is hidden
    @Test
    void testDescendantsOfVaticanAtStateLevel(Connection connection) {
        assertRaggedReturns(connection,
            "Descendants([Store].[Vatican], [Store].[Store State])",
            "");
    }

    @Test
    void testDescendantsOfRootAtCity(Connection connection) {
        assertRaggedReturns(connection,
            "Descendants([Store], [Store City])",
            "[Store].[Store].[Canada].[BC].[Vancouver]\n"
            + "[Store].[Store].[Canada].[BC].[Victoria]\n"
            + "[Store].[Store].[Israel].[Israel].[Haifa]\n"
            + "[Store].[Store].[Israel].[Israel].[Tel Aviv]\n"
            + "[Store].[Store].[Mexico].[DF].[Mexico City]\n"
            + "[Store].[Store].[Mexico].[DF].[San Andres]\n"
            + "[Store].[Store].[Mexico].[Guerrero].[Acapulco]\n"
            + "[Store].[Store].[Mexico].[Jalisco].[Guadalajara]\n"
            + "[Store].[Store].[Mexico].[Veracruz].[Orizaba]\n"
            + "[Store].[Store].[Mexico].[Yucatan].[Merida]\n"
            + "[Store].[Store].[Mexico].[Zacatecas].[Camacho]\n"
            + "[Store].[Store].[Mexico].[Zacatecas].[Hidalgo]\n"
            + "[Store].[Store].[USA].[CA].[Alameda]\n"
            + "[Store].[Store].[USA].[CA].[Beverly Hills]\n"
            + "[Store].[Store].[USA].[CA].[Los Angeles]\n"
            + "[Store].[Store].[USA].[CA].[San Francisco]\n"
            + "[Store].[Store].[USA].[OR].[Portland]\n"
            + "[Store].[Store].[USA].[OR].[Salem]\n"
            + "[Store].[Store].[USA].[USA].[Washington]\n"
            + "[Store].[Store].[USA].[WA].[Bellingham]\n"
            + "[Store].[Store].[USA].[WA].[Bremerton]\n"
            + "[Store].[Store].[USA].[WA].[Seattle]\n"
            + "[Store].[Store].[USA].[WA].[Spokane]");
    }

    // no ancestor at the State level
    @Test
    void testAncestorOfHaifa(Connection connection) {
        assertRaggedReturns(connection,
            "Ancestor([Store].[Israel].[Haifa], [Store].[Store State])",
            "");
    }

    @Test
    void testHierarchize(Connection connection) {
        // Haifa and Tel Aviv should appear directly after Israel
        // Vatican should have no children
        // Washington should appear after WA
        assertRaggedReturns(connection,
            "Hierarchize(Descendants([Store], [Store].[Store City], SELF_AND_BEFORE))",
            "[Store].[Store].[All Stores]\n"
            + "[Store].[Store].[Canada]\n"
            + "[Store].[Store].[Canada].[BC]\n"
            + "[Store].[Store].[Canada].[BC].[Vancouver]\n"
            + "[Store].[Store].[Canada].[BC].[Victoria]\n"
            + "[Store].[Store].[Israel]\n"
            + "[Store].[Store].[Israel].[Israel].[Haifa]\n"
            + "[Store].[Store].[Israel].[Israel].[Tel Aviv]\n"
            + "[Store].[Store].[Mexico]\n"
            + "[Store].[Store].[Mexico].[DF]\n"
            + "[Store].[Store].[Mexico].[DF].[Mexico City]\n"
            + "[Store].[Store].[Mexico].[DF].[San Andres]\n"
            + "[Store].[Store].[Mexico].[Guerrero]\n"
            + "[Store].[Store].[Mexico].[Guerrero].[Acapulco]\n"
            + "[Store].[Store].[Mexico].[Jalisco]\n"
            + "[Store].[Store].[Mexico].[Jalisco].[Guadalajara]\n"
            + "[Store].[Store].[Mexico].[Veracruz]\n"
            + "[Store].[Store].[Mexico].[Veracruz].[Orizaba]\n"
            + "[Store].[Store].[Mexico].[Yucatan]\n"
            + "[Store].[Store].[Mexico].[Yucatan].[Merida]\n"
            + "[Store].[Store].[Mexico].[Zacatecas]\n"
            + "[Store].[Store].[Mexico].[Zacatecas].[Camacho]\n"
            + "[Store].[Store].[Mexico].[Zacatecas].[Hidalgo]\n"
            + "[Store].[Store].[USA]\n"
            + "[Store].[Store].[USA].[CA]\n"
            + "[Store].[Store].[USA].[CA].[Alameda]\n"
            + "[Store].[Store].[USA].[CA].[Beverly Hills]\n"
            + "[Store].[Store].[USA].[CA].[Los Angeles]\n"
            + "[Store].[Store].[USA].[CA].[San Francisco]\n"
            + "[Store].[Store].[USA].[OR]\n"
            + "[Store].[Store].[USA].[OR].[Portland]\n"
            + "[Store].[Store].[USA].[OR].[Salem]\n"
            + "[Store].[Store].[USA].[USA].[Washington]\n"
            + "[Store].[Store].[USA].[WA]\n"
            + "[Store].[Store].[USA].[WA].[Bellingham]\n"
            + "[Store].[Store].[USA].[WA].[Bremerton]\n"
            + "[Store].[Store].[USA].[WA].[Seattle]\n"
            + "[Store].[Store].[USA].[WA].[Spokane]\n"
            + "[Store].[Store].[Vatican]");
    }

    /**
     * Make sure that the numbers are right!
     *
     * <p>The Vatican is the tricky case,
     * because one of the columns is null, so the SQL generator might get
     * confused.
     */
    @Test
    public void testMeasuresVatican(Connection connection) {
        assertThatQuery(connection,
            "SELECT {[Measures].[Unit Sales]} ON COLUMNS,\n"
            + " {Descendants([Store].[Vatican])} ON ROWS\n"
            + "FROM [Sales Ragged]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[Vatican]}\n"
            + "{[Store].[Store].[Vatican].[Vatican].[null].[Store 17]}\n"
            + "Row #0: 35,257\n"
            + "Row #1: 35,257\n");
    }

    // Make sure that the numbers are right!
    /**
     */
    @Test
    public void testMeasures(Connection connection) {
        assertThatQuery(connection,
            "SELECT {[Measures].[Unit Sales]} ON COLUMNS,\n"
            + " NON EMPTY {Descendants([Store])} ON ROWS\n"
            + "FROM [Sales Ragged]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[All Stores]}\n"
            + "{[Store].[Store].[Israel]}\n"
            + "{[Store].[Store].[Israel].[Israel].[Haifa]}\n"
            + "{[Store].[Store].[Israel].[Israel].[Haifa].[Store 22]}\n"
            + "{[Store].[Store].[Israel].[Israel].[Tel Aviv]}\n"
            + "{[Store].[Store].[Israel].[Israel].[Tel Aviv].[Store 23]}\n"
            + "{[Store].[Store].[USA]}\n"
            + "{[Store].[Store].[USA].[CA]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles].[Store 7]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco].[Store 14]}\n"
            + "{[Store].[Store].[USA].[OR]}\n"
            + "{[Store].[Store].[USA].[OR].[Portland]}\n"
            + "{[Store].[Store].[USA].[OR].[Portland].[Store 11]}\n"
            + "{[Store].[Store].[USA].[OR].[Salem]}\n"
            + "{[Store].[Store].[USA].[OR].[Salem].[Store 13]}\n"
            + "{[Store].[Store].[USA].[USA].[Washington]}\n"
            + "{[Store].[Store].[USA].[USA].[Washington].[Store 24]}\n"
            + "{[Store].[Store].[USA].[WA]}\n"
            + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
            + "{[Store].[Store].[USA].[WA].[Bellingham].[Store 2]}\n"
            + "{[Store].[Store].[USA].[WA].[Bremerton]}\n"
            + "{[Store].[Store].[USA].[WA].[Bremerton].[Store 3]}\n"
            + "{[Store].[Store].[USA].[WA].[Seattle]}\n"
            + "{[Store].[Store].[USA].[WA].[Seattle].[Store 15]}\n"
            + "{[Store].[Store].[USA].[WA].[Spokane]}\n"
            + "{[Store].[Store].[USA].[WA].[Spokane].[Store 16]}\n"
            + "{[Store].[Store].[Vatican]}\n"
            + "{[Store].[Store].[Vatican].[Vatican].[null].[Store 17]}\n"
            + "Row #0: 266,773\n"
            + "Row #1: 13,694\n"
            + "Row #2: 2,203\n"
            + "Row #3: 2,203\n"
            + "Row #4: 11,491\n"
            + "Row #5: 11,491\n"
            + "Row #6: 217,822\n"
            + "Row #7: 49,113\n"
            + "Row #8: 21,333\n"
            + "Row #9: 21,333\n"
            + "Row #10: 25,663\n"
            + "Row #11: 25,663\n"
            + "Row #12: 2,117\n"
            + "Row #13: 2,117\n"
            + "Row #14: 67,659\n"
            + "Row #15: 26,079\n"
            + "Row #16: 26,079\n"
            + "Row #17: 41,580\n"
            + "Row #18: 41,580\n"
            + "Row #19: 25,635\n"
            + "Row #20: 25,635\n"
            + "Row #21: 75,415\n"
            + "Row #22: 2,237\n"
            + "Row #23: 2,237\n"
            + "Row #24: 24,576\n"
            + "Row #25: 24,576\n"
            + "Row #26: 25,011\n"
            + "Row #27: 25,011\n"
            + "Row #28: 23,591\n"
            + "Row #29: 23,591\n"
            + "Row #30: 35,257\n"
            + "Row #31: 35,257\n");
    }

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-628">MONDRIAN-628</a>,
     * "ClassCastException in Mondrian for query using Sales Ragged cube".
     *
     * <p>Cause was that ancestor yielded a null member, which was a RolapMember
     * but Order required it to be a RolapCubeMember.
     */
    @Test
    void testNullMember(Connection connection) {
        assertThatQuery(connection,
            "With \n"
            + " Set [*NATIVE_CJ_SET] as '[*BASE_MEMBERS_Geography]' \n"
            + " Set [*SORTED_ROW_AXIS] as 'Order([*CJ_ROW_AXIS],Ancestor([Geography].CurrentMember, [Geography].[Country]).OrderKey,BASC,Ancestor([Geography].CurrentMember, [Geography].[State]).OrderKey,BASC,[Geography].CurrentMember.OrderKey,BASC)' \n"
            + " Set [*BASE_MEMBERS_Geography] as '[Geography].[City].Members' \n"
            + " Set [*NATIVE_MEMBERS_Geography] as 'Generate([*NATIVE_CJ_SET], {[Geography].CurrentMember})' \n"
            + " Set [*BASE_MEMBERS_Measures] as '{[Measures].[*ZERO]}' \n"
            + " Set [*CJ_ROW_AXIS] as 'Generate([*NATIVE_CJ_SET], {([Geography].currentMember)})' \n"
            + " Set [*CJ_COL_AXIS] as '[*NATIVE_CJ_SET]' \n"
            + " Member [Measures].[*ZERO] as '0', SOLVE_ORDER=0 \n"
            + " Select \n"
            + " [*BASE_MEMBERS_Measures] on columns, \n"
            + " [*SORTED_ROW_AXIS] on rows \n"
            + " From [Sales Ragged]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[*ZERO]}\n"
            + "Axis #2:\n"
            + "{[Geography].[Geography].[Canada].[BC].[Vancouver]}\n"
            + "{[Geography].[Geography].[Canada].[BC].[Victoria]}\n"
            + "{[Geography].[Geography].[Israel].[Israel].[Haifa]}\n"
            + "{[Geography].[Geography].[Israel].[Israel].[Tel Aviv]}\n"
            + "{[Geography].[Geography].[Mexico].[DF].[Mexico City]}\n"
            + "{[Geography].[Geography].[Mexico].[DF].[San Andres]}\n"
            + "{[Geography].[Geography].[Mexico].[Guerrero].[Acapulco]}\n"
            + "{[Geography].[Geography].[Mexico].[Jalisco].[Guadalajara]}\n"
            + "{[Geography].[Geography].[Mexico].[Veracruz].[Orizaba]}\n"
            + "{[Geography].[Geography].[Mexico].[Yucatan].[Merida]}\n"
            + "{[Geography].[Geography].[Mexico].[Zacatecas].[Camacho]}\n"
            + "{[Geography].[Geography].[Mexico].[Zacatecas].[Hidalgo]}\n"
            + "{[Geography].[Geography].[USA].[USA].[Washington]}\n"
            + "{[Geography].[Geography].[USA].[CA].[Alameda]}\n"
            + "{[Geography].[Geography].[USA].[CA].[Beverly Hills]}\n"
            + "{[Geography].[Geography].[USA].[CA].[Los Angeles]}\n"
            + "{[Geography].[Geography].[USA].[CA].[San Francisco]}\n"
            + "{[Geography].[Geography].[USA].[OR].[Portland]}\n"
            + "{[Geography].[Geography].[USA].[OR].[Salem]}\n"
            + "{[Geography].[Geography].[USA].[WA].[Bellingham]}\n"
            + "{[Geography].[Geography].[USA].[WA].[Bremerton]}\n"
            + "{[Geography].[Geography].[USA].[WA].[Seattle]}\n"
            + "{[Geography].[Geography].[USA].[WA].[Spokane]}\n"
            + "Row #0: 0\n"
            + "Row #1: 0\n"
            + "Row #2: 0\n"
            + "Row #3: 0\n"
            + "Row #4: 0\n"
            + "Row #5: 0\n"
            + "Row #6: 0\n"
            + "Row #7: 0\n"
            + "Row #8: 0\n"
            + "Row #9: 0\n"
            + "Row #10: 0\n"
            + "Row #11: 0\n"
            + "Row #12: 0\n"
            + "Row #13: 0\n"
            + "Row #14: 0\n"
            + "Row #15: 0\n"
            + "Row #16: 0\n"
            + "Row #17: 0\n"
            + "Row #18: 0\n"
            + "Row #19: 0\n"
            + "Row #20: 0\n"
            + "Row #21: 0\n"
            + "Row #22: 0\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.RaggedHierarchyTestModifier1.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @RolapConfig(key = ConfigConstants.NULL_MEMBER_REPRESENTATION, value = "null", type = String.class)
    void testHideIfBlankHidesWhitespace(Connection connection) {
        if (getDatabaseProduct(getDialect(connection).name())
            != DatabaseProduct.ORACLE)
        {
            return;
        }
        /*
        ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
                "Sales",
                "<Dimension name=\"Gender4\" foreignKey=\"customer_id\">\n"
                + "    <Hierarchy hasAll=\"true\" allMemberName=\"All Gender\" primaryKey=\"customer_id\">\n"
                + "      <Table name=\"customer\"/>\n"
                + "      <Level name=\"Gender\" column=\"gender\" uniqueMembers=\"true\" hideMemberIf=\"IfBlankName\">\n"
                + "         <NameExpression> "
                + " <SQL dialect='generic'> "
                    +           "case \"gender\" "
                    +           "when 'F' then ' ' "
                    +           "when 'M' then 'M' "
                    + " end "
                    + "</SQL> "
                    + "</NameExpression>  "
                    + "      </Level>"
                    + "    </Hierarchy>\n"
                    + "  </Dimension>"));
         */
        assertThatQuery(connection,
            " select {[Gender4].[Gender].members} "
            + "on COLUMNS "
            + "from sales").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Gender4].[M]}\n"
            + "Row #0: 135,215\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.RaggedHierarchyTestModifier2.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @RolapConfig(key = ConfigConstants.NULL_MEMBER_REPRESENTATION, value = "null", type = String.class)
    void testNativeFilterWithHideMemberIfBlankOnLeaf(Connection connection) throws Exception {
        /*
        ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
            "Sales Ragged",
            "<Dimension name=\"Store\" foreignKey=\"store_id\">\n"
            + "    <Hierarchy hasAll=\"true\" primaryKey=\"store_id\">\n"
            + "      <Table name=\"store_ragged\"/>\n"
            + "      <Level name=\"Store Country\" column=\"store_country\" uniqueMembers=\"true\"\n"
            + "          hideMemberIf=\"Never\"/>\n"
            + "      <Level name=\"Store State\" column=\"store_state\" uniqueMembers=\"true\"\n"
            + "          hideMemberIf=\"IfParentsName\"/>\n"
            + "      <Level name=\"Store City\" column=\"store_city\" uniqueMembers=\"false\"\n"
            + "          hideMemberIf=\"IfBlankName\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>"));
         */
        assertThatQuery(connection,
            "SELECT\n"
            + "[Measures].[Unit Sales] ON COLUMNS\n"
            + ",FILTER([Store].[Store City].MEMBERS, NOT ISEMPTY ([Measures].[Unit Sales])) ON ROWS\n"
            + "FROM [Sales Ragged]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[Store].[Israel].[Israel].[Haifa]}\n"
            + "{[Store].[Store].[Israel].[Israel].[Tel Aviv]}\n"
            + "{[Store].[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Store].[Store].[USA].[OR].[Portland]}\n"
            + "{[Store].[Store].[USA].[OR].[Salem]}\n"
            + "{[Store].[Store].[USA].[USA].[Washington]}\n"
            + "{[Store].[Store].[USA].[WA].[Bellingham]}\n"
            + "{[Store].[Store].[USA].[WA].[Bremerton]}\n"
            + "{[Store].[Store].[USA].[WA].[Seattle]}\n"
            + "{[Store].[Store].[USA].[WA].[Spokane]}\n"
            + "Row #0: 2,203\n"
            + "Row #1: 11,491\n"
            + "Row #2: 21,333\n"
            + "Row #3: 25,663\n"
            + "Row #4: 2,117\n"
            + "Row #5: 26,079\n"
            + "Row #6: 41,580\n"
            + "Row #7: 25,635\n"
            + "Row #8: 2,237\n"
            + "Row #9: 24,576\n"
            + "Row #10: 25,011\n"
            + "Row #11: 23,591\n");
    }

    @Test
    @RolapContextTest(catalog = { CatalogSupplier.class, SchemaModifiersEmf.RaggedHierarchyTestModifier2.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    @RolapConfig(key = ConfigConstants.NULL_MEMBER_REPRESENTATION, value = "null", type = String.class)
    void testNativeCJWithHideMemberIfBlankOnLeaf(Connection connection) throws Exception {
        /*
        ((BaseTestContext)context).update(SchemaUpdater.createSubstitutingCube(
            "Sales Ragged",
            "<Dimension name=\"Store\" foreignKey=\"store_id\">\n"
            + "    <Hierarchy hasAll=\"true\" primaryKey=\"store_id\">\n"
            + "      <Table name=\"store_ragged\"/>\n"
            + "      <Level name=\"Store Country\" column=\"store_country\" uniqueMembers=\"true\"\n"
            + "          hideMemberIf=\"Never\"/>\n"
            + "      <Level name=\"Store State\" column=\"store_state\" uniqueMembers=\"true\"\n"
            + "          hideMemberIf=\"IfParentsName\"/>\n"
            + "      <Level name=\"Store City\" column=\"store_city\" uniqueMembers=\"false\"\n"
            + "          hideMemberIf=\"IfBlankName\"/>\n"
            + "    </Hierarchy>\n"
            + "  </Dimension>"));
         */
        assertThatQuery(connection,
            "SELECT\n"
            + "[Measures].[Unit Sales] ON COLUMNS\n"
            + ",non empty Crossjoin([Gender].[Gender].[Gender].members, [Store].[Store].[Store City].MEMBERS) ON ROWS\n"
            + "FROM [Sales Ragged]").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[Israel].[Israel].[Haifa]}\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[Israel].[Israel].[Tel Aviv]}\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[USA].[OR].[Portland]}\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[USA].[OR].[Salem]}\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[USA].[USA].[Washington]}\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[USA].[WA].[Bellingham]}\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[USA].[WA].[Bremerton]}\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[USA].[WA].[Seattle]}\n"
            + "{[Gender].[Gender].[F], [Store].[Store].[USA].[WA].[Spokane]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[Israel].[Israel].[Haifa]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[Israel].[Israel].[Tel Aviv]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[USA].[OR].[Portland]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[USA].[OR].[Salem]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[USA].[USA].[Washington]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[USA].[WA].[Bellingham]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[USA].[WA].[Bremerton]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[USA].[WA].[Seattle]}\n"
            + "{[Gender].[Gender].[M], [Store].[Store].[USA].[WA].[Spokane]}\n"
            + "Row #0: 1,019\n"
            + "Row #1: 5,007\n"
            + "Row #2: 10,771\n"
            + "Row #3: 12,089\n"
            + "Row #4: 1,064\n"
            + "Row #5: 12,488\n"
            + "Row #6: 20,548\n"
            + "Row #7: 12,835\n"
            + "Row #8: 1,096\n"
            + "Row #9: 11,640\n"
            + "Row #10: 13,513\n"
            + "Row #11: 12,068\n"
            + "Row #12: 1,184\n"
            + "Row #13: 6,484\n"
            + "Row #14: 10,562\n"
            + "Row #15: 13,574\n"
            + "Row #16: 1,053\n"
            + "Row #17: 13,591\n"
            + "Row #18: 21,032\n"
            + "Row #19: 12,800\n"
            + "Row #20: 1,141\n"
            + "Row #21: 12,936\n"
            + "Row #22: 11,498\n"
            + "Row #23: 11,523\n");
    }

    /** Named bridge onto the FoodMart CSVs (for the {@code data =} supplier form). */
}

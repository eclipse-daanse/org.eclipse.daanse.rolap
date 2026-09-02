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
package org.eclipse.daanse.olap.function.def.order;

import static org.eclipse.daanse.olap.function.TestResources.assertAxisCompilesTo;
import static org.eclipse.daanse.olap.function.TestResources.hiersExcept;
import static org.eclipse.daanse.rolap.testkit.assertions.FunDependencies.assertThatSetExpr;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatAxis;
import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;

import java.net.URL;
import java.util.Map;

import org.eclipse.daanse.cwm.testkit.api.DataSupplier;
import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.common.ConfigConstants;
import org.eclipse.daanse.rolap.itests.utils.EmfUtil;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.CatalogSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartDatabaseSupplier;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.catalog.impl.CatalogImpl;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.CubeFactory;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.VirtualCube;
import org.eclipse.daanse.rolap.mapping.model.olap.cube.measure.BaseMeasure;
import org.eclipse.daanse.rolap.mapping.model.olap.dimension.DimensionConnector;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapConfig;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;

@RolapContextTest(FoodmartTestInstance.class)
class OrderFunDefTest {

    public static class FoodmartData implements DataSupplier {
        @Override
        public Map<String, URL> csvResources() {
            return new FoodmartTestInstance().dataSupplier().csvResources();
        }
    }

    @Test
    void testBug715177c(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Order(TopCount({[Store].[USA].[CA].children},"
                + " [Measures].[Unit Sales], 2), [Measures].[Unit Sales])")
            .returns(
            "[Store].[Store].[USA].[CA].[Alameda]\n"
                + "[Store].[Store].[USA].[CA].[San Francisco]\n"
                + "[Store].[Store].[USA].[CA].[Beverly Hills]\n"
                + "[Store].[Store].[USA].[CA].[San Diego]\n"
                + "[Store].[Store].[USA].[CA].[Los Angeles]" );
    }



    @Test
    void testOrderDepends(Context<?> context) {
        // Order(<Set>, <Value Expression>) depends upon everything
        // <Value Expression> depends upon, except the dimensions of <Set>.

        // Depends upon everything EXCEPT [Product], [Measures],
        // [Marital Status], [Gender].
        assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Order("
                + " Crossjoin([Gender].[Gender].MEMBERS, [Product].[Product].MEMBERS),"
                + " ([Measures].[Unit Sales], [Marital Status].[S]),"
                + " ASC)")
            .dependsOn( hiersExcept(
                "[Product].[Product]", "[Measures]", "[Marital Status].[Marital Status]", "[Gender].[Gender]" ) );

        // Depends upon everything EXCEPT [Product], [Measures],
        // [Marital Status]. Does depend upon [Gender].
        assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Order("
                + " Crossjoin({[Gender].[Gender].CurrentMember}, [Product].[Product].MEMBERS),"
                + " ([Measures].[Unit Sales], [Marital Status].[S]),"
                + " ASC)")
            .dependsOn( hiersExcept(
                "[Product].[Product]", "[Measures]", "[Marital Status].[Marital Status]" ) );

        // Depends upon everything except [Measures].
        assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales",
            "Order("
                + "  Crossjoin("
                + "    [Gender].[Gender].CurrentMember.Children, "
                + "    [Marital Status].CurrentMember.Children), "
                + "  [Measures].[Unit Sales], "
                + "  BDESC)")
            .dependsOn( hiersExcept( "[Measures]" ) );

        assertThatSetExpr(context.getConnectionWithDefaultRole(), "Sales",
            "  Order(\n"
                + "    CrossJoin(\n"
                + "      {[Product].[Product].[All Products].[Food].[Eggs],\n"
                + "       [Product].[Product].[All Products].[Food].[Seafood],\n"
                + "       [Product].[Product].[All Products].[Drink].[Alcoholic Beverages]},\n"
                + "      {[Store].[Store].[USA].[WA].[Seattle],\n"
                + "       [Store].[Store].[USA].[CA],\n"
                + "       [Store].[Store].[USA].[OR]}),\n"
                + "    ([Time].[Time].[1997].[Q1], [Measures].[Unit Sales]),\n"
                + "    ASC)")
            .dependsOn( hiersExcept(
                "[Measures]", "[Store].[Store]", "[Product].[Product]", "[Time].[Time]" ) );
    }

    @Test
    void testOrderCalc1(Context<?> context) {

        // [Measures].[Unit Sales] is a constant member, so it is evaluated in
        // a ContextCalc.
        Connection connection = context.getConnectionWithDefaultRole();

        String expr = "order([Product].children, [Measures].[Unit Sales])";
        String expected = """
org.eclipse.daanse.olap.function.def.order.OrderContextCalc(type=SetType<MemberType<hierarchy=[Product].[Product]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=0)
    org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)
    org.eclipse.daanse.olap.function.def.order.OrderCurrentMemberCalc(type=SetType<MemberType<hierarchy=[Product].[Product]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=0, direction=ASC)
        org.eclipse.daanse.olap.function.def.set.children.ChildrenCalc(type=SetType<MemberType<hierarchy=[Product].[Product]>>, resultStyle=LIST, callCount=0, callMillis=0)
            org.eclipse.daanse.olap.function.def.hierarchy.member.HierarchyCurrentMemberFixedCalc(type=MemberType<hierarchy=[Product].[Product]>, resultStyle=VALUE, callCount=0, callMillis=0)
        org.eclipse.daanse.olap.calc.base.value.CurrentValueUnknownCalc(type=SCALAR, resultStyle=VALUE, callCount=0, callMillis=0)
					""";
        assertAxisCompilesTo(connection, expr, expected);
    }

    @Test
    void testOrderCalc2(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();

        String expr = "order([Product].children, ([Time].[1997], [Product].CurrentMember.Parent))";
        String expected = """
org.eclipse.daanse.olap.function.def.order.OrderContextCalc(type=SetType<MemberType<hierarchy=[Product].[Product]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=0)
    org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Time].[Time].[1997]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)
    org.eclipse.daanse.olap.function.def.order.OrderCurrentMemberCalc(type=SetType<MemberType<hierarchy=[Product].[Product]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=0, direction=ASC)
        org.eclipse.daanse.olap.function.def.set.children.ChildrenCalc(type=SetType<MemberType<hierarchy=[Product].[Product]>>, resultStyle=LIST, callCount=0, callMillis=0)
            org.eclipse.daanse.olap.function.def.hierarchy.member.HierarchyCurrentMemberFixedCalc(type=MemberType<hierarchy=[Product].[Product]>, resultStyle=VALUE, callCount=0, callMillis=0)
        org.eclipse.daanse.olap.calc.base.type.tuplebase.MemberValueCalc(type=SCALAR, resultStyle=VALUE, callCount=0, callMillis=0)
            org.eclipse.daanse.olap.function.def.member.parentcalc.ParentFunDef$1(type=MemberType<hierarchy=[Product].[Product]>, resultStyle=VALUE, callCount=0, callMillis=0)
                org.eclipse.daanse.olap.function.def.hierarchy.member.HierarchyCurrentMemberFixedCalc(type=MemberType<hierarchy=[Product].[Product]>, resultStyle=VALUE, callCount=0, callMillis=0)
				""";
        // [Time].[1997] is constant, and is evaluated in a ContextCalc.
        // [Product].Parent is variable, and is evaluated inside the loop.
        assertAxisCompilesTo(connection, expr, expected);
    }

    @Test
    void testOrderCalc3(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        // No ContextCalc this time. All members are non-variable.
        String expr = "order([Product].children, [Product].CurrentMember.Parent)";
        String expected = """
org.eclipse.daanse.olap.function.def.order.OrderCurrentMemberCalc(type=SetType<MemberType<hierarchy=[Product].[Product]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=0, direction=ASC)
    org.eclipse.daanse.olap.function.def.set.children.ChildrenCalc(type=SetType<MemberType<hierarchy=[Product].[Product]>>, resultStyle=LIST, callCount=0, callMillis=0)
        org.eclipse.daanse.olap.function.def.hierarchy.member.HierarchyCurrentMemberFixedCalc(type=MemberType<hierarchy=[Product].[Product]>, resultStyle=VALUE, callCount=0, callMillis=0)
    org.eclipse.daanse.olap.calc.base.type.tuplebase.MemberValueCalc(type=SCALAR, resultStyle=VALUE, callCount=0, callMillis=0)
        org.eclipse.daanse.olap.function.def.member.parentcalc.ParentFunDef$1(type=MemberType<hierarchy=[Product].[Product]>, resultStyle=VALUE, callCount=0, callMillis=0)
            org.eclipse.daanse.olap.function.def.hierarchy.member.HierarchyCurrentMemberFixedCalc(type=MemberType<hierarchy=[Product].[Product]>, resultStyle=VALUE, callCount=0, callMillis=0)
						""";
        assertAxisCompilesTo(connection, expr, expected);
    }

    @Test
    void testOrderCalc4(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();

        String expected = """
org.eclipse.daanse.olap.function.def.order.OrderContextCalc(type=SetType<MemberType<hierarchy=[Product].[Product]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=0)
    org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Measures].[Store Sales]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)
    org.eclipse.daanse.olap.function.def.order.OrderCurrentMemberCalc(type=SetType<MemberType<hierarchy=[Product].[Product]>>, resultStyle=MUTABLE_LIST, callCount=0, callMillis=0, direction=ASC)
        org.eclipse.daanse.olap.function.def.set.filter.ImmutableIterFilterCalc(type=SetType<MemberType<hierarchy=[Product].[Product]>>, resultStyle=ITERABLE, callCount=0, callMillis=0)
            org.eclipse.daanse.olap.function.def.set.children.ChildrenCalc(type=SetType<MemberType<hierarchy=[Product].[Product]>>, resultStyle=LIST, callCount=0, callMillis=0)
                org.eclipse.daanse.olap.function.def.hierarchy.member.HierarchyCurrentMemberFixedCalc(type=MemberType<hierarchy=[Product].[Product]>, resultStyle=VALUE, callCount=0, callMillis=0)
            org.eclipse.daanse.olap.function.def.operators.greater.GreaterCalc(type=BOOLEAN, resultStyle=VALUE, callCount=0, callMillis=0)
                org.eclipse.daanse.olap.calc.base.type.doublex.UnknownToDoubleCalc(type=NUMERIC, resultStyle=VALUE, callCount=0, callMillis=0)
                    org.eclipse.daanse.olap.calc.base.type.tuplebase.MemberValueCalc(type=SCALAR, resultStyle=VALUE, callCount=0, callMillis=0)
                        org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Measures].[Unit Sales]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)
                org.eclipse.daanse.olap.calc.base.constant.ConstantDoubleCalc(type=NUMERIC, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)
        org.eclipse.daanse.olap.calc.base.type.tuplebase.MemberValueCalc(type=SCALAR, resultStyle=VALUE, callCount=0, callMillis=0)
            org.eclipse.daanse.olap.calc.base.constant.ConstantMemberCalc(type=MemberType<member=[Gender].[Gender].[M]>, resultStyle=VALUE_NOT_NULL, callCount=0, callMillis=0)
								""";
        String expr = "order(filter([Product].children, [Measures].[Unit Sales] > 1000), ([Gender].[M], [Measures].[Store Sales]))";
        assertAxisCompilesTo(connection, expr, expected);
    }

    /**
     * Verifies that the order function works with a defined member. See this forum post for additional information:
     * http://forums.pentaho.com/showthread.php?p=179473#post179473
     */
    @Test
    void testOrderWithMember(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member [Measures].[Product Name Length] as "
                + "'LEN([Product].CurrentMember.Name)'\n"
                + "select {[Measures].[Product Name Length]} ON COLUMNS,\n"
                + "Order([Product].[All Products].Children, "
                + "[Measures].[Product Name Length], BASC) ON ROWS\n"
                + "from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Product Name Length]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Food]}\n"
                + "{[Product].[Product].[Drink]}\n"
                + "{[Product].[Product].[Non-Consumable]}\n"
                + "Row #0: 4\n"
                + "Row #1: 5\n"
                + "Row #2: 14\n" );
    }

    /**
     * test case for bug # 1797159, Potential MDX Order Non Empty Problem
     */
    @Test
    void testOrderNonEmpty(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select NON EMPTY [Gender].Members ON COLUMNS,\n"
                + "NON EMPTY Order([Product].[All Products].[Drink].Children,\n"
                + "[Gender].[All Gender].[F], ASC) ON ROWS\n"
                + "from [Sales]\n"
                + "where ([Customers].[All Customers].[USA].[CA].[San Francisco],\n"
                + " [Time].[1997])")
            .returnsGrid(

            "Axis #0:\n"
                + "{[Customers].[Customers].[USA].[CA].[San Francisco], [Time].[Time].[1997]}\n"
                + "Axis #1:\n"
                + "{[Gender].[Gender].[All Gender]}\n"
                + "{[Gender].[Gender].[F]}\n"
                + "{[Gender].[Gender].[M]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Drink].[Beverages]}\n"
                + "{[Product].[Product].[Drink].[Alcoholic Beverages]}\n"
                + "Row #0: 2\n"
                + "Row #0: \n"
                + "Row #0: 2\n"
                + "Row #1: 4\n"
                + "Row #1: 2\n"
                + "Row #1: 2\n" );
    }

    @Test
    void testOrder(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns,\n"
                + " order({\n"
                + "  [Product].[All Products].[Drink],\n"
                + "  [Product].[All Products].[Drink].[Beverages],\n"
                + "  [Product].[All Products].[Drink].[Dairy],\n"
                + "  [Product].[All Products].[Food],\n"
                + "  [Product].[All Products].[Food].[Baked Goods],\n"
                + "  [Product].[All Products].[Food].[Eggs],\n"
                + "  [Product].[All Products]},\n"
                + " [Measures].[Unit Sales]) on rows\n"
                + "from Sales")
            .returnsGrid(

            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[All Products]}\n"
                + "{[Product].[Product].[Drink]}\n"
                + "{[Product].[Product].[Drink].[Dairy]}\n"
                + "{[Product].[Product].[Drink].[Beverages]}\n"
                + "{[Product].[Product].[Food]}\n"
                + "{[Product].[Product].[Food].[Eggs]}\n"
                + "{[Product].[Product].[Food].[Baked Goods]}\n"
                + "Row #0: 266,773\n"
                + "Row #1: 24,597\n"
                + "Row #2: 4,186\n"
                + "Row #3: 13,573\n"
                + "Row #4: 191,940\n"
                + "Row #5: 4,132\n"
                + "Row #6: 7,870\n" );
    }

    @Test
    void testOrderParentsMissing(Context<?> context) {
        // Paradoxically, [Alcoholic Beverages] comes before
        // [Eggs] even though it has a larger value, because
        // its parent [Drink] has a smaller value than [Food].
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns,"
                + " order({\n"
                + "  [Product].[All Products].[Drink].[Alcoholic Beverages],\n"
                + "  [Product].[All Products].[Food].[Eggs]},\n"
                + " [Measures].[Unit Sales], ASC) on rows\n"
                + "from Sales")
            .returnsGrid(

            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Drink].[Alcoholic Beverages]}\n"
                + "{[Product].[Product].[Food].[Eggs]}\n"
                + "Row #0: 6,838\n"
                + "Row #1: 4,132\n" );
    }

    @Test
    void testOrderCrossJoinBreak(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales]} on columns,\n"
                + "  Order(\n"
                + "    CrossJoin(\n"
                + "      [Gender].children,\n"
                + "      [Marital Status].children),\n"
                + "    [Measures].[Unit Sales],\n"
                + "    BDESC) on rows\n"
                + "from Sales\n"
                + "where [Time].[1997].[Q1]")
            .returnsGrid(

            "Axis #0:\n"
                + "{[Time].[Time].[1997].[Q1]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[S]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[M]}\n"
                + "{[Gender].[Gender].[M], [Marital Status].[Marital Status].[M]}\n"
                + "{[Gender].[Gender].[F], [Marital Status].[Marital Status].[S]}\n"
                + "Row #0: 17,070\n"
                + "Row #1: 16,790\n"
                + "Row #2: 16,311\n"
                + "Row #3: 16,120\n" );
    }

    @Test
    void testOrderCrossJoin(Context<?> context) {
        // Note:
        // 1. [Alcoholic Beverages] collates before [Eggs] and
        //    [Seafood] because its parent, [Drink], is less
        //    than [Food]
        // 2. [Seattle] generally sorts after [CA] and [OR]
        //    because invisible parent [WA] is greater.
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select CrossJoin(\n"
                + "    {[Time].[1997],\n"
                + "     [Time].[1997].[Q1]},\n"
                + "    {[Measures].[Unit Sales]}) on columns,\n"
                + "  Order(\n"
                + "    CrossJoin(\n"
                + "      {[Product].[All Products].[Food].[Eggs],\n"
                + "       [Product].[All Products].[Food].[Seafood],\n"
                + "       [Product].[All Products].[Drink].[Alcoholic Beverages]},\n"
                + "      {[Store].[USA].[WA].[Seattle],\n"
                + "       [Store].[USA].[CA],\n"
                + "       [Store].[USA].[OR]}),\n"
                + "    ([Time].[1997].[Q1], [Measures].[Unit Sales]),\n"
                + "    ASC) on rows\n"
                + "from Sales")
            .returnsGrid(

            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1997], [Measures].[Unit Sales]}\n"
                + "{[Time].[Time].[1997].[Q1], [Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Drink].[Alcoholic Beverages], [Store].[Store].[USA].[OR]}\n"
                + "{[Product].[Product].[Drink].[Alcoholic Beverages], [Store].[Store].[USA].[CA]}\n"
                + "{[Product].[Product].[Drink].[Alcoholic Beverages], [Store].[Store].[USA].[WA].[Seattle]}\n"
                + "{[Product].[Product].[Food].[Seafood], [Store].[Store].[USA].[CA]}\n"
                + "{[Product].[Product].[Food].[Seafood], [Store].[Store].[USA].[OR]}\n"
                + "{[Product].[Product].[Food].[Seafood], [Store].[Store].[USA].[WA].[Seattle]}\n"
                + "{[Product].[Product].[Food].[Eggs], [Store].[Store].[USA].[CA]}\n"
                + "{[Product].[Product].[Food].[Eggs], [Store].[Store].[USA].[OR]}\n"
                + "{[Product].[Product].[Food].[Eggs], [Store].[Store].[USA].[WA].[Seattle]}\n"
                + "Row #0: 1,680\n"
                + "Row #0: 393\n"
                + "Row #1: 1,936\n"
                + "Row #1: 431\n"
                + "Row #2: 635\n"
                + "Row #2: 142\n"
                + "Row #3: 441\n"
                + "Row #3: 91\n"
                + "Row #4: 451\n"
                + "Row #4: 107\n"
                + "Row #5: 217\n"
                + "Row #5: 44\n"
                + "Row #6: 1,116\n"
                + "Row #6: 240\n"
                + "Row #7: 1,119\n"
                + "Row #7: 251\n"
                + "Row #8: 373\n"
                + "Row #8: 57\n" );
    }

    @Test
    void testOrderHierarchicalDesc(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Order(\n"
                + "    {[Product].[All Products], "
                + "     [Product].[Food],\n"
                + "     [Product].[Drink],\n"
                + "     [Product].[Non-Consumable],\n"
                + "     [Product].[Food].[Eggs],\n"
                + "     [Product].[Drink].[Dairy]},\n"
                + "  [Measures].[Unit Sales],\n"
                + "  DESC)")
            .returns(

            "[Product].[Product].[All Products]\n"
                + "[Product].[Product].[Food]\n"
                + "[Product].[Product].[Food].[Eggs]\n"
                + "[Product].[Product].[Non-Consumable]\n"
                + "[Product].[Product].[Drink]\n"
                + "[Product].[Product].[Drink].[Dairy]" );
    }

    @Test
    void testOrderCrossJoinDesc(Context<?> context) {
        assertThatAxis(context.getConnectionWithDefaultRole(), "Sales",
            "Order(\n"
                + "  CrossJoin(\n"
                + "    {[Gender].[M], [Gender].[F]},\n"
                + "    {[Product].[All Products], "
                + "     [Product].[Food],\n"
                + "     [Product].[Drink],\n"
                + "     [Product].[Non-Consumable],\n"
                + "     [Product].[Food].[Eggs],\n"
                + "     [Product].[Drink].[Dairy]}),\n"
                + "  [Measures].[Unit Sales],\n"
                + "  DESC)")
            .returns(

            "{[Gender].[Gender].[M], [Product].[Product].[All Products]}\n"
                + "{[Gender].[Gender].[M], [Product].[Product].[Food]}\n"
                + "{[Gender].[Gender].[M], [Product].[Product].[Food].[Eggs]}\n"
                + "{[Gender].[Gender].[M], [Product].[Product].[Non-Consumable]}\n"
                + "{[Gender].[Gender].[M], [Product].[Product].[Drink]}\n"
                + "{[Gender].[Gender].[M], [Product].[Product].[Drink].[Dairy]}\n"
                + "{[Gender].[Gender].[F], [Product].[Product].[All Products]}\n"
                + "{[Gender].[Gender].[F], [Product].[Product].[Food]}\n"
                + "{[Gender].[Gender].[F], [Product].[Product].[Food].[Eggs]}\n"
                + "{[Gender].[Gender].[F], [Product].[Product].[Non-Consumable]}\n"
                + "{[Gender].[Gender].[F], [Product].[Product].[Drink]}\n"
                + "{[Gender].[Gender].[F], [Product].[Product].[Drink].[Dairy]}" );
    }

    @Test
    void testOrderBug656802(Context<?> context) {
        // Note:
        // 1. [Alcoholic Beverages] collates before [Eggs] and
        //    [Seafood] because its parent, [Drink], is less
        //    than [Food]
        // 2. [Seattle] generally sorts after [CA] and [OR]
        //    because invisible parent [WA] is greater.
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select {[Measures].[Unit Sales], [Measures].[Store Cost], [Measures].[Store Sales]} ON columns, \n"
                + "Order(\n"
                + "  ToggleDrillState(\n"
                + "    {([Promotion Media].[All Media], [Product].[All Products])},\n"
                + "    {[Product].[All Products]}), \n"
                + "  [Measures].[Unit Sales], DESC) ON rows \n"
                + "from [Sales] where ([Time].[1997])")
            .returnsGrid(

            "Axis #0:\n"
                + "{[Time].[Time].[1997]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "{[Measures].[Store Cost]}\n"
                + "{[Measures].[Store Sales]}\n"
                + "Axis #2:\n"
                + "{[Promotion Media].[Promotion Media].[All Media], [Product].[Product].[All Products]}\n"
                + "{[Promotion Media].[Promotion Media].[All Media], [Product].[Product].[Food]}\n"
                + "{[Promotion Media].[Promotion Media].[All Media], [Product].[Product].[Non-Consumable]}\n"
                + "{[Promotion Media].[Promotion Media].[All Media], [Product].[Product].[Drink]}\n"
                + "Row #0: 266,773\n"
                + "Row #0: 225,627.23\n"
                + "Row #0: 565,238.13\n"
                + "Row #1: 191,940\n"
                + "Row #1: 163,270.72\n"
                + "Row #1: 409,035.59\n"
                + "Row #2: 50,236\n"
                + "Row #2: 42,879.28\n"
                + "Row #2: 107,366.33\n"
                + "Row #3: 24,597\n"
                + "Row #3: 19,477.23\n"
                + "Row #3: 48,836.21\n" );
    }

    @Test
    void testOrderBug712702_Simplified(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "SELECT Order({[Time].[Year].members}, [Measures].[Unit Sales]) on columns\n"
                + "from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Time].[Time].[1998]}\n"
                + "{[Time].[Time].[1997]}\n"
                + "Row #0: \n"
                + "Row #0: 266,773\n" );
    }

    @Test
    void testOrderBug712702_Original(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with member [Measures].[Average Unit Sales] as 'Avg(Descendants([Time].[Time].CurrentMember, [Time].[Month]), \n"
                + "[Measures].[Unit Sales])' \n"
                + "member [Measures].[Max Unit Sales] as 'Max(Descendants([Time].[Time].CurrentMember, [Time].[Month]), "
                + "[Measures].[Unit Sales])' \n"
                + "select {[Measures].[Average Unit Sales], [Measures].[Max Unit Sales], [Measures].[Unit Sales]} ON columns,"
                + " \n"
                + "  NON EMPTY Order(\n"
                + "    Crossjoin(\n"
                + "      {[Store].[USA].[OR].[Portland],\n"
                + "       [Store].[USA].[OR].[Salem],\n"
                + "       [Store].[USA].[OR].[Salem].[Store 13],\n"
                + "       [Store].[USA].[CA].[San Francisco],\n"
                + "       [Store].[USA].[CA].[San Diego],\n"
                + "       [Store].[USA].[CA].[Beverly Hills],\n"
                + "       [Store].[USA].[CA].[Los Angeles],\n"
                + "       [Store].[USA].[WA].[Walla Walla],\n"
                + "       [Store].[USA].[WA].[Bellingham],\n"
                + "       [Store].[USA].[WA].[Yakima],\n"
                + "       [Store].[USA].[WA].[Spokane],\n"
                + "       [Store].[USA].[WA].[Seattle], \n"
                + "       [Store].[USA].[WA].[Bremerton],\n"
                + "       [Store].[USA].[WA].[Tacoma]},\n"
                + "     [Time].[Year].Members), \n"
                + "  [Measures].[Average Unit Sales], ASC) ON rows\n"
                + "from [Sales] ")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Average Unit Sales]}\n"
                + "{[Measures].[Max Unit Sales]}\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Store].[Store].[USA].[OR].[Portland], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[OR].[Salem], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[OR].[Salem].[Store 13], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[CA].[San Francisco], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[CA].[Beverly Hills], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[CA].[San Diego], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[CA].[Los Angeles], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[WA].[Walla Walla], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[WA].[Bellingham], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[WA].[Yakima], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[WA].[Spokane], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[WA].[Bremerton], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[WA].[Seattle], [Time].[Time].[1997]}\n"
                + "{[Store].[Store].[USA].[WA].[Tacoma], [Time].[Time].[1997]}\n"
                + "Row #0: 2,173\n"
                + "Row #0: 2,933\n"
                + "Row #0: 26,079\n"
                + "Row #1: 3,465\n"
                + "Row #1: 5,891\n"
                + "Row #1: 41,580\n"
                + "Row #2: 3,465\n"
                + "Row #2: 5,891\n"
                + "Row #2: 41,580\n"
                + "Row #3: 176\n"
                + "Row #3: 222\n"
                + "Row #3: 2,117\n"
                + "Row #4: 1,778\n"
                + "Row #4: 2,545\n"
                + "Row #4: 21,333\n"
                + "Row #5: 2,136\n"
                + "Row #5: 2,686\n"
                + "Row #5: 25,635\n"
                + "Row #6: 2,139\n"
                + "Row #6: 2,669\n"
                + "Row #6: 25,663\n"
                + "Row #7: 184\n"
                + "Row #7: 301\n"
                + "Row #7: 2,203\n"
                + "Row #8: 186\n"
                + "Row #8: 275\n"
                + "Row #8: 2,237\n"
                + "Row #9: 958\n"
                + "Row #9: 1,163\n"
                + "Row #9: 11,491\n"
                + "Row #10: 1,966\n"
                + "Row #10: 2,634\n"
                + "Row #10: 23,591\n"
                + "Row #11: 2,048\n"
                + "Row #11: 2,623\n"
                + "Row #11: 24,576\n"
                + "Row #12: 2,084\n"
                + "Row #12: 2,304\n"
                + "Row #12: 25,011\n"
                + "Row #13: 2,938\n"
                + "Row #13: 3,818\n"
                + "Row #13: 35,257\n" );
    }

    @Test
    void testOrderEmpty(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select \n"
                + "  Order("
                + "    {},"
                + "    [Customers].currentMember, BDESC) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n" );
    }

    @Test
    void testOrderOne(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select \n"
                + "  Order("
                + "    {[Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young]},"
                + "    [Customers].currentMember, BDESC) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "Row #0: 75\n" );
    }

    @Test
    void testOrderKeyEmpty(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select \n"
                + "  Order("
                + "    {},"
                + "    [Customers].currentMember.OrderKey, BDESC) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n" );
    }

    @Test
    void testOrderKeyOne(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select \n"
                + "  Order("
                + "    {[Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young]},"
                + "    [Customers].currentMember.OrderKey, BDESC) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "Row #0: 75\n" );
    }

    @Test
    void testOrderDesc(Context<?> context) {
        // based on olap4j's OlapTest.testSortDimension
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "SELECT\n"
                + "{[Measures].[Store Sales]} ON COLUMNS,\n"
                + "{Order(\n"
                + "  {{[Product].[Drink], [Product].[Drink].Children}},\n"
                + "  [Product].CurrentMember.Name,\n"
                + "  DESC)} ON ROWS\n"
                + "FROM [Sales]\n"
                + "WHERE {[Time].[1997].[Q3].[7]}")
            .returnsGrid(
            "Axis #0:\n"
                + "{[Time].[Time].[1997].[Q3].[7]}\n"
                + "Axis #1:\n"
                + "{[Measures].[Store Sales]}\n"
                + "Axis #2:\n"
                + "{[Product].[Product].[Drink]}\n"
                + "{[Product].[Product].[Drink].[Dairy]}\n"
                + "{[Product].[Product].[Drink].[Beverages]}\n"
                + "{[Product].[Product].[Drink].[Alcoholic Beverages]}\n"
                + "Row #0: 4,409.58\n"
                + "Row #1: 629.69\n"
                + "Row #2: 2,477.02\n"
                + "Row #3: 1,302.87\n" );
    }

    @Test
    @RolapConfig(key = ConfigConstants.COMPARE_SIBLINGS_BY_ORDER_KEY, value = "true", type = Boolean.class)
    void testOrderMemberMemberValueExpNew(Context<?> context) {

        // Use a fresh connection to make sure bad member ordinals haven't
        // been assigned by previous tests.
        //final Context<?> context = getTestContext().withFreshConnection();
        Connection connection = context.getConnectionWithDefaultRole();
        try {
            assertThatQuery(context.getConnectionWithDefaultRole(),
                "select \n"
                    + "  Order("
                    + "    {[Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                    + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]},"
                    + "    [Customers].currentMember.OrderKey, BDESC) \n"
                    + "on 0 from [Sales]")
            .returnsGrid(
                "Axis #0:\n"
                    + "{}\n"
                    + "Axis #1:\n"
                    + "{[Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                    + "Row #0: 33\n"
                    + "Row #0: 75\n");
        } finally {
            if ( connection != null ) {
                connection.close();
            }
        }
    }

    @Test
    @RolapConfig(key = ConfigConstants.COMPARE_SIBLINGS_BY_ORDER_KEY, value = "true", type = Boolean.class)
    void testOrderMemberMemberValueExpNew1(Context<?> context) {
        // sort by default measure

        // Use a fresh connection to make sure bad member ordinals haven't
        // been assigned by previous tests.
        Connection connection = context.getConnectionWithDefaultRole();
        try {
            assertThatQuery(context.getConnectionWithDefaultRole(),
                "select \n"
                    + "  Order("
                    + "    {[Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                    + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]},"
                    + "    [Customers].currentMember, BDESC) \n"
                    + "on 0 from [Sales]")
            .returnsGrid(
                "Axis #0:\n"
                    + "{}\n"
                    + "Axis #1:\n"
                    + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                    + "Row #0: 75\n"
                    + "Row #0: 33\n" );
        } finally {
            connection.close();
        }
    }

    @Test
    void testOrderMemberDefaultFlag1(Context<?> context) {
        // flags not specified default to ASC - sort by default measure
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with \n"
                + "  Member [Measures].[Zero] as '0' \n"
                + "select \n"
                + "  Order("
                + "    {[Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]},"
                + "    [Customers].currentMember.OrderKey) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "Row #0: 33\n"
                + "Row #0: 75\n" );
    }

    @Test
    void testOrderMemberDefaultFlag2(Context<?> context) {
        // flags not specified default to ASC
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with \n"
                + "  Member [Measures].[Zero] as '0' \n"
                + "select \n"
                + "  Order("
                + "    {[Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]},"
                + "    [Measures].[Store Cost]) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                + "Row #0: 75\n"
                + "Row #0: 33\n" );
    }

    @Test
    void testOrderMemberMemberValueExpHierarchy(Context<?> context) {
        // Santa Monica and Woodland Hills both don't have orderkey
        // members are sorted by the order of their keys
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select \n"
                + "  Order("
                + "    {[Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]},"
                + "    [Customers].currentMember.OrderKey, DESC) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                + "Row #0: 75\n"
                + "Row #0: 33\n" );
    }

    @Test
    void testOrderMemberMultiKeysMemberValueExp1(Context<?> context) {
        // sort by unit sales and then customer id (Adeline = 6442, Abe = 570)
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select \n"
                + "  Order("
                + "    {[Customers].[USA].[WA].[Issaquah].[Abe Tramel],"
                + "     [Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]},"
                + "    [Measures].[Unit Sales], BDESC, [Customers].currentMember.OrderKey, BDESC) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                + "{[Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel]}\n"
                + "Row #0: 75\n"
                + "Row #0: 33\n"
                + "Row #0: 33\n" );
    }

    @Test
    @RolapConfig(key = ConfigConstants.COMPARE_SIBLINGS_BY_ORDER_KEY, value = "true", type = Boolean.class)
    void testOrderMemberMultiKeysMemberValueExp2(Context<?> context) {

        // Use a fresh connection to make sure bad member ordinals haven't
        // been assigned by previous tests.
        Connection connection = context.getConnectionWithDefaultRole();
        try {
            assertThatQuery(context.getConnectionWithDefaultRole(),
                "select \n"
                    + "  Order("
                    + "    {[Customers].[USA].[WA].[Issaquah].[Abe Tramel],"
                    + "     [Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                    + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]},"
                    + "    [Customers].currentMember.Parent.Parent.OrderKey, BASC, [Customers].currentMember.OrderKey, BDESC) \n"
                    + "on 0 from [Sales]")
            .returnsGrid(
                "Axis #0:\n"
                    + "{}\n"
                    + "Axis #1:\n"
                    + "{[Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                    + "{[Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel]}\n"
                    + "Row #0: 33\n"
                    + "Row #0: 75\n"
                    + "Row #0: 33\n" );
        } finally {
            connection.close();
        }
    }

    @Test
    void testOrderMemberMultiKeysMemberValueExpDepends(Context<?> context) {
        // should preserve order of Abe and Adeline (note second key is [Time])
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select \n"
                + "  Order("
                + "    {[Customers].[USA].[WA].[Issaquah].[Abe Tramel],"
                + "     [Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]},"
                + "    [Measures].[Unit Sales], BDESC, [Time].[Time].currentMember, BDESC) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "{[Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                + "Row #0: 75\n"
                + "Row #0: 33\n"
                + "Row #0: 33\n" );
    }

    @Test
    @RolapConfig(key = ConfigConstants.COMPARE_SIBLINGS_BY_ORDER_KEY, value = "true", type = Boolean.class)
    void testOrderTupleSingleKeysNew(Context<?> context) {

        // Use a fresh connection to make sure bad member ordinals haven't
        // been assigned by previous tests.
        final Connection connection = context.getConnectionWithDefaultRole();
        try {
            assertThatQuery(context.getConnectionWithDefaultRole(),
                "with \n"
                    + "  set [NECJ] as \n"
                    + "    'NonEmptyCrossJoin( \n"
                    + "    {[Customers].[USA].[WA].[Issaquah].[Abe Tramel],"
                    + "     [Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                    + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]},"
                    + "    {[Store].[USA].[WA].[Seattle],\n"
                    + "     [Store].[USA].[CA],\n"
                    + "     [Store].[USA].[OR]})'\n"
                    + "select \n"
                    + " Order([NECJ], [Customers].currentMember.OrderKey, BDESC) \n"
                    + "on 0 from [Sales]")
            .returnsGrid(
                "Axis #0:\n"
                    + "{}\n"
                    + "Axis #1:\n"
                    + "{[Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun], [Store].[Store].[USA].[CA]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young], [Store].[Store].[USA].[CA]}\n"
                    + "{[Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel], [Store].[Store].[USA].[WA].[Seattle]}\n"
                    + "Row #0: 33\n"
                    + "Row #0: 75\n"
                    + "Row #0: 33\n" );
        } finally {
            connection.close();
        }
    }

    @Test
    @RolapConfig(key = ConfigConstants.COMPARE_SIBLINGS_BY_ORDER_KEY, value = "true", type = Boolean.class)
    void testOrderTupleSingleKeysNew1(Context<?> context) {

        // Use a fresh connection to make sure bad member ordinals haven't
        // been assigned by previous tests.
        Connection connection = context.getConnectionWithDefaultRole();
        try {
            assertThatQuery(context.getConnectionWithDefaultRole(),
                "with \n"
                    + "  set [NECJ] as \n"
                    + "    'NonEmptyCrossJoin( \n"
                    + "    {[Customers].[USA].[WA].[Issaquah].[Abe Tramel],"
                    + "     [Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                    + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]},"
                    + "    {[Store].[USA].[WA].[Seattle],\n"
                    + "     [Store].[USA].[CA],\n"
                    + "     [Store].[USA].[OR]})'\n"
                    + "select \n"
                    + " Order([NECJ], [Store].currentMember.OrderKey, DESC) \n"
                    + "on 0 from [Sales]")
            .returnsGrid(
                "Axis #0:\n"
                    + "{}\n"
                    + "Axis #1:\n"
                    + "{[Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel], [Store].[Store].[USA].[WA].[Seattle]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young], [Store].[Store].[USA].[CA]}\n"
                    + "{[Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun], [Store].[Store].[USA].[CA]}\n"
                    + "Row #0: 33\n"
                    + "Row #0: 75\n"
                    + "Row #0: 33\n" );
        } finally {
            connection.close();
        }
    }

    @Test
    void testOrderTupleMultiKeys1(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with \n"
                + "  set [NECJ] as \n"
                + "    'NonEmptyCrossJoin( \n"
                + "    {[Store].[USA].[CA],\n"
                + "     [Store].[USA].[WA]},\n"
                + "    {[Customers].[USA].[WA].[Issaquah].[Abe Tramel],"
                + "     [Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]})' \n"
                + "select \n"
                + " Order([NECJ], [Store].currentMember.OrderKey, BDESC, [Measures].[Unit Sales], BDESC) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA].[WA], [Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel]}\n"
                + "{[Store].[Store].[USA].[CA], [Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "{[Store].[Store].[USA].[CA], [Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                + "Row #0: 33\n"
                + "Row #0: 75\n"
                + "Row #0: 33\n" );
    }

    @Test
    void testOrderTupleMultiKeys2(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with \n"
                + "  set [NECJ] as \n"
                + "    'NonEmptyCrossJoin( \n"
                + "    {[Store].[USA].[CA],\n"
                + "     [Store].[USA].[WA]},\n"
                + "    {[Customers].[USA].[WA].[Issaquah].[Abe Tramel],"
                + "     [Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]})' \n"
                + "select \n"
                + " Order([NECJ], [Measures].[Unit Sales], BDESC, Ancestor([Customers].currentMember, [Customers].[Name])"
                + ".OrderKey, BDESC) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA].[CA], [Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "{[Store].[Store].[USA].[CA], [Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                + "{[Store].[Store].[USA].[WA], [Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel]}\n"
                + "Row #0: 75\n"
                + "Row #0: 33\n"
                + "Row #0: 33\n" );
    }

    @Test
    void testOrderTupleMultiKeys3(Context<?> context) {
        // WA unit sales is greater than CA unit sales
        // Santa Monica unit sales (2660) is greater that Woodland hills (2516)
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with \n"
                + "  set [NECJ] as \n"
                + "    'NonEmptyCrossJoin( \n"
                + "    {[Store].[USA].[CA],\n"
                + "     [Store].[USA].[WA]},\n"
                + "    {[Customers].[USA].[WA].[Issaquah].[Abe Tramel],"
                + "     [Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]})' \n"
                + "select \n"
                + " Order([NECJ], [Measures].[Unit Sales], DESC, Ancestor([Customers].currentMember, [Customers].[Name]), "
                + "BDESC) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Store].[Store].[USA].[WA], [Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel]}\n"
                + "{[Store].[Store].[USA].[CA], [Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                + "{[Store].[Store].[USA].[CA], [Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "Row #0: 33\n"
                + "Row #0: 33\n"
                + "Row #0: 75\n" );
    }

    /**
     * EMF version of TestOrderTupleMultiKeyswithVCubeModifier
     * Creates a virtual cube for testing order functionality
     */
    public static class TestOrderTupleMultiKeyswithVCubeModifierEmf implements CatalogMappingSupplier {

        private CatalogImpl catalog;

        public TestOrderTupleMultiKeyswithVCubeModifierEmf(Catalog cat) {
            // Copy catalog using EcoreUtil
            EcoreUtil.Copier copier = EmfUtil.copier((CatalogImpl) cat);
            catalog = (CatalogImpl) copier.get(cat);


            // Create virtual cube
            VirtualCube virtualCube =
                CubeFactory.eINSTANCE.createVirtualCube();
            virtualCube.setName("Sales vs HR");

            // Create dimension connector for Customers from Sales cube
            //org.eclipse.daanse.rolap.mapping.emf.rolapmapping.DimensionConnector customersDimConnector =
            //    org.eclipse.daanse.rolap.mapping.emf.rolapmapping.DimensionFactory.eINSTANCE.createDimensionConnector();
            //customersDimConnector.setOverrideDimensionName("Customers");
            //customersDimConnector.setPhysicalCube(CatalogSupplier.CUBE_SALES);

            // Create dimension connector for Position from HR cube
            //org.eclipse.daanse.rolap.mapping.emf.rolapmapping.DimensionConnector positionDimConnector =
            //    org.eclipse.daanse.rolap.mapping.emf.rolapmapping.DimensionFactory.eINSTANCE.createDimensionConnector();
            //positionDimConnector.setOverrideDimensionName("Position");
            //positionDimConnector.setPhysicalCube(CatalogSupplier.CUBE_HR);

            // Add dimension connectors to virtual cube
            virtualCube.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_CUSTOMER));
            virtualCube.getDimensionConnectors().add((DimensionConnector) copier.get(CatalogSupplier.CONNECTOR_HR_POSITION));

            virtualCube.getReferencedMeasures().add((BaseMeasure) copier.get(CatalogSupplier.MEASURE_ORG_SALARY));

            // Add virtual cube to catalog
            catalog.getImportedElement().add(virtualCube);
        }

        @Override
        public Catalog get() {
            return catalog;
        }
    }

    @Test
    @RolapConfig(key = ConfigConstants.COMPARE_SIBLINGS_BY_ORDER_KEY, value = "true", type = Boolean.class)
    @RolapContextTest(catalog = { CatalogSupplier.class, TestOrderTupleMultiKeyswithVCubeModifierEmf.class },
        database = FoodmartDatabaseSupplier.class, data = FoodmartData.class)
    void testOrderTupleMultiKeyswithVCube(Context<?> context) {
        // WA unit sales is greater than CA unit sales

        // Use a fresh connection to make sure bad member ordinals haven't
        // been assigned by previous tests.
        // a non-sense cube just to test ordering by order key

        /*
        class TestOrderTupleMultiKeyswithVCubeModifier extends PojoMappingModifier {

            public TestOrderTupleMultiKeyswithVCubeModifier(CatalogMapping catalog) {
                super(catalog);
            }
            protected List<CubeMapping> cubes(List<? extends CubeMapping> cubes) {
                List<CubeMapping> result = new ArrayList<>();
                result.addAll(super.cubes(cubes));
                result.add(VirtualCubeMappingImpl.builder()
                    .withName("Sales vs HR")
                    .withDimensionConnectors(List.of(
                        DimensionConnectorMappingImpl.builder()
                            .withPhysicalCube((PhysicalCubeMappingImpl) look(FoodmartMappingSupplier.CUBE_SALES))
                            .withOverrideDimensionName("Customers")
                            .build(),
                        DimensionConnectorMappingImpl.builder()
                            .withPhysicalCube((PhysicalCubeMappingImpl) look(FoodmartMappingSupplier.CUBE_HR))
                            .withOverrideDimensionName("Position")
                            .build()
                    ))
                    .withReferencedMeasures(List.of(look(FoodmartMappingSupplier.MEASURE_ORG_SALARY)))
                    .build());
                return result;
            }
        }
        */
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "with \n"
                + "  set [CJ] as \n"
                + "    'CrossJoin( \n"
                + "    {[Position].[Store Management].children},\n"
                + "    {[Customers].[USA].[WA].[Issaquah].[Abe Tramel],"
                + "     [Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]})' \n"
                + "select \n"
                + "  [Measures].[Org Salary] on columns, \n"
                + "  Order([CJ], [Position].currentMember.OrderKey, BASC, Ancestor([Customers].currentMember, [Customers]"
                + ".[Name]).OrderKey, BDESC) \n"
                + "on rows \n"
                + "from [Sales vs HR]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Org Salary]}\n"
                + "Axis #2:\n"
                + "{[Position].[Position].[Store Management].[Store Manager], [Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                + "{[Position].[Position].[Store Management].[Store Manager], [Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "{[Position].[Position].[Store Management].[Store Manager], [Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel]}\n"
                + "{[Position].[Position].[Store Management].[Store Assistant Manager], [Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline "
                + "Chun]}\n"
                + "{[Position].[Position].[Store Management].[Store Assistant Manager], [Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel "
                + "Young]}\n"
                + "{[Position].[Position].[Store Management].[Store Assistant Manager], [Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel]}\n"
                + "{[Position].[Position].[Store Management].[Store Shift Supervisor], [Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline "
                + "Chun]}\n"
                + "{[Position].[Position].[Store Management].[Store Shift Supervisor], [Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel "
                + "Young]}\n"
                + "{[Position].[Position].[Store Management].[Store Shift Supervisor], [Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel]}\n"
                + "Row #0: \n"
                + "Row #1: \n"
                + "Row #2: \n"
                + "Row #3: \n"
                + "Row #4: \n"
                + "Row #5: \n"
                + "Row #6: \n"
                + "Row #7: \n"
                + "Row #8: \n" );
    }

    @Test
    @RolapConfig(key = ConfigConstants.COMPARE_SIBLINGS_BY_ORDER_KEY, value = "true", type = Boolean.class)
    void testOrderConstant1(Context<?> context) {
        // sort by customerId (Abel = 7851, Adeline = 6442, Abe = 570)
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select \n"
                + "  Order("
                + "    {[Customers].[USA].[WA].[Issaquah].[Abe Tramel],"
                + "     [Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]},"
                + "    [Customers].[USA].OrderKey, BDESC, [Customers].currentMember.OrderKey, BASC) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                + "Row #0: 33\n"
                + "Row #0: 75\n"
                + "Row #0: 33\n");
    }

    @Test
    void testOrderDiffrentDim(Context<?> context) {
        assertThatQuery(context.getConnectionWithDefaultRole(),
            "select \n"
                + "  Order("
                + "    {[Customers].[USA].[WA].[Issaquah].[Abe Tramel],"
                + "     [Customers].[All Customers].[USA].[CA].[Woodland Hills].[Abel Young],"
                + "     [Customers].[All Customers].[USA].[CA].[Santa Monica].[Adeline Chun]},"
                + "    [Product].currentMember.OrderKey, BDESC, [Gender].currentMember.OrderKey, BDESC) \n"
                + "on 0 from [Sales]")
            .returnsGrid(
            "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Customers].[Customers].[USA].[WA].[Issaquah].[Abe Tramel]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Woodland Hills].[Abel Young]}\n"
                + "{[Customers].[Customers].[USA].[CA].[Santa Monica].[Adeline Chun]}\n"
                + "Row #0: 33\n"
                + "Row #0: 75\n"
                + "Row #0: 33\n" );
    }

}

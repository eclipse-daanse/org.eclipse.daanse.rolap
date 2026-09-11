/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2002-2017 Hitachi Vantara and others
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

import static org.eclipse.daanse.rolap.testkit.assertions.MdxAssert.assertThatQuery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.catalog.CatalogReader;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.olap.api.element.Catalog;
import org.eclipse.daanse.olap.api.element.Cube;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.execution.ExecutionContext;
import org.eclipse.daanse.olap.api.execution.ExecutionMetadata;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.ArrayTupleList;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.DelegatingTupleList;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.TupleCollections;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.UnaryTupleList;
import org.eclipse.daanse.olap.common.Util;
import org.eclipse.daanse.olap.execution.ExecutionImpl;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.foodmart.FoodmartTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link TupleList} and common implementations.
 *
 * @author jhyde
 */
@RolapContextTest(FoodmartTestInstance.class)
class TupleListTest {

    @Test
    void testUnaryTupleList(Context<?> context) {
        // empty list
        final TupleList list0 = new UnaryTupleList();
        assertTrue(list0.isEmpty());
        assertEquals(0, list0.size());

        assertEquals(list0, TupleCollections.emptyList(1));

        TupleList list1 = new UnaryTupleList();
        assertEquals(list0, list1);
        final Member storeUsaMember = xxx(context.getConnectionWithDefaultRole(),"[Store].[USA]");
        list1.add(Collections.singletonList(storeUsaMember));
        assertFalse(list1.isEmpty());
        assertEquals(1, list1.size());
        assertNotSame(list0, list1);

        TupleList list2 = new UnaryTupleList();
        list2.addTuple(new Member[]{storeUsaMember});
        assertFalse(list2.isEmpty());
        assertEquals(1, list2.size());
        assertEquals(list1, list2);

        list2.clear();
        assertEquals(list0, list2);
        assertEquals(list2, list0);

        // For various lists, sublist returns the whole thing.
        for (TupleList list : Arrays.asList(list0, list1, list2)) {
            assertEquals(list, list.subList(0, list.size()));
            assertNotSame(list, list.subList(0, list.size()));
        }

        // Null members OK (at least for TupleList).
        list1.addTuple(new Member[]{null});
        list1.add(Collections.<Member>singletonList(null));
    }

    @Test
    void testArrayTupleList(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        final Member genderFMember = xxx(connection, "[Gender].[F]");
        final Member genderMMember = xxx(connection,"[Gender].[M]");

        // empty list
        final TupleList list0 = new ArrayTupleList(2);
        assertTrue(list0.isEmpty());
        assertEquals(0, list0.size());

        assertIterableEquals(list0, TupleCollections.emptyList(2));

        TupleList list1 = new ArrayTupleList(2);
        assertEquals(list0, list1);
        final Member storeUsaMember = xxx(connection,"[Store].[USA]");
        list1.add(Arrays.asList(storeUsaMember, genderFMember));
        assertFalse(list1.isEmpty());
        assertEquals(1, list1.size());
        assertNotSame(list0, list1);

        try {
            list1.add(Arrays.asList(storeUsaMember));
            fail("expected error");
        } catch (IllegalArgumentException e) {
            assertEquals("Tuple length does not match arity", e.getMessage());
        }
        try {
            list1.addTuple(new Member[] {storeUsaMember});
            fail("expected error");
        } catch (IllegalArgumentException e) {
            assertEquals("Tuple length does not match arity", e.getMessage());
        }
        try {
            list1.add(
                Arrays.asList(storeUsaMember, genderFMember, genderFMember));
            fail("expected error");
        } catch (IllegalArgumentException e) {
            assertEquals("Tuple length does not match arity", e.getMessage());
        }
        try {
            list1.addTuple(
                new Member[]{storeUsaMember, genderFMember, genderFMember});
            fail("expected error");
        } catch (IllegalArgumentException e) {
            assertEquals("Tuple length does not match arity", e.getMessage());
        }

        TupleList list2 = new ArrayTupleList(2);
        list2.addTuple(new Member[]{storeUsaMember, genderFMember});
        assertFalse(list2.isEmpty());
        assertEquals(1, list2.size());
        assertEquals(list1, list2);

        list2.clear();
        assertEquals(list0, list2);
        assertEquals(list2, list0);

        assertEquals("[]", list0.toString());
        assertEquals("[[[Store].[Store].[USA], [Gender].[Gender].[F]]]", list1.toString());
        assertEquals("[]", list2.toString());

        // For various lists, sublist returns the whole thing.
        for (TupleList list : Arrays.asList(list0, list1, list2)) {
            final TupleList sublist = list.subList(0, list.size());
            assertNotNull(sublist);
            assertNotNull(sublist.toString());
            assertEquals(sublist.isEmpty(), list.isEmpty());
            assertIterableEquals(list, sublist);
            assertNotSame(list, sublist);
        }

        // Null members OK (at least for TupleList).
        list1.addTuple(storeUsaMember, null);
        list1.add(Arrays.asList(storeUsaMember, null));

        TupleList fm = new ArrayTupleList(2);
        fm.addTuple(genderFMember, storeUsaMember);
        fm.addTuple(genderMMember, storeUsaMember);
        checkProject(fm);
    }

    @Test
    void testDelegatingTupleList(Context<?> context) {
        Connection connection = context.getConnectionWithDefaultRole();
        final Member genderFMember = xxx(connection, "[Gender].[F]");
        final Member genderMMember = xxx(connection, "[Gender].[M]");
        final Member storeUsaMember = xxx(connection, "[Store].[USA]");

        final List<List<Member>> arrayList = new ArrayList<>();
        TupleList fm = new DelegatingTupleList(2, arrayList);

        fm.addTuple(genderFMember, storeUsaMember);
        fm.addTuple(genderMMember, storeUsaMember);

        assertEquals(2, fm.size());
        assertEquals(2, fm.getArity());
        assertEquals(
            "[[[Gender].[Gender].[F], [Store].[Store].[USA]], [[Gender].[Gender].[M], [Store].[Store].[USA]]]",
            fm.toString());

        checkProject(fm);
    }

    /**
     * This is a test for MONDRIAN-1040. The DelegatingTupleList.slice()
     * method was mixing up the column and index variables.
     */
    @Test
    void testDelegatingTupleListSlice(Context<?> context) {
        // Functional test.
        Connection connection = context.getConnectionWithDefaultRole();
        assertThatQuery(connection,
            "select {[Measures].[Store Sales]} ON COLUMNS, Hierarchize(Except({[Customers].[All Customers], [Customers].[All Customers].Children}, {[Customers].[All Customers]})) ON ROWS from [Sales] ").returnsGrid(
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Store Sales]}\n"
            + "Axis #2:\n"
            + "{[Customers].[Customers].[Canada]}\n"
            + "{[Customers].[Customers].[Mexico]}\n"
            + "{[Customers].[Customers].[USA]}\n"
            + "Row #0: \n"
            + "Row #1: \n"
            + "Row #2: 565,238.13\n");
        ExecutionImpl execution = new ExecutionImpl(
            ((Connection) connection).getInternalStatement(),
            Optional.of(Duration.ofMinutes(5)));
        ExecutionMetadata metadata = ExecutionMetadata.of("testDelegatingTupleListSlice", "testDelegatingTupleListSlice", null, 0);
        ExecutionContext.where(
                execution.asContext().createChild(metadata, Optional.empty()),
                () -> {
                    // Unit test
                    final Member genderFMember = xxx(connection, "[Gender].[F]");
                    final Member storeUsaMember = xxx(connection, "[Store].[USA]");
                    final List<List<Member>> arrayList =
                        new ArrayList<>();
                    final TupleList fm =
                        new DelegatingTupleList(2, arrayList);
                    fm.addTuple(genderFMember, storeUsaMember);
                    final List<Member> sliced = fm.slice(0);
                    assertEquals(1, sliced.size());
                    assertEquals(1, fm.size());
                    return null;
                });
    }

    private void checkProject(TupleList fm) {
        assertEquals(2, fm.size());
        assertEquals(2, fm.getArity());
        assertEquals(2, fm.project(new int[] {0}).size());
        assertEquals(fm.slice(0), fm.project(new int[] {0}).slice(0));
        assertEquals(fm.slice(1), fm.project(new int[] {1}).slice(0));
        assertEquals(
            "[[[Gender].[Gender].[F]], [[Gender].[Gender].[M]]]",
            fm.project(new int[] {0}).toString());
        assertEquals(
            "[[[Store].[Store].[USA]], [[Store].[Store].[USA]]]",
            fm.project(new int[] {1}).toString());

        // Also check cloneList.
        assertEquals(0, fm.copyList(100).size());
        assertEquals(fm.size(), fm.copyList(-1).size());
        assertEquals(fm, fm.copyList(-1));
        assertNotSame(fm, fm.copyList(-1));
    }

    /**
     * Queries a member of the Sales cube.
     *
     * @param memberName Unique name of member
     * @return The member
     */
    private Member xxx(Connection connection, String memberName) {
        Catalog schema = connection.getCatalog();
        Cube salesCube = schema.lookupCube("Sales").orElseThrow();
        final CatalogReader schemaReader =
            salesCube.getCatalogReader(null).withLocus(); // unrestricted
        return schemaReader.getMemberByUniqueName(
            Util.parseIdentifier(memberName), true);
    }
}

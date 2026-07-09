/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2003-2005 Julian Hyde
 * Copyright (C) 2005-2020 Hitachi Vantara and others
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
package org.eclipse.daanse.rolap.common.constraint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.evaluator.Evaluator;
import org.eclipse.daanse.rolap.common.evaluator.RolapEvaluator;
import org.eclipse.daanse.rolap.element.CompoundSlicerRolapMember;
import org.eclipse.daanse.rolap.element.RolapHierarchy;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests the compound-slicer-placeholder handling of {@link CalculatedMemberExpander} against fully mocked
 * collaborators — no database, no catalog context. Rehomed from the mondrian TCK
 * ({@code mondrian/rolap/CalculatedMemberExpanderTest}) so the {@code common.constraint}
 * package needs no OSGi export for it; the former foodmart-backed slicer setup is replaced by a
 * mocked {@link RolapEvaluator} slicer map (the tested code reads only
 * {@code getSlicerMembersByHierarchy()}). The expander's calc-member expansion itself is covered
 * by {@code org.eclipse.daanse.rolap.common.CalculatedMemberExpanderTest}.
 */
class CalculatedMemberExpanderTest {

    private void assertSameContent(
        String msg, Collection<Member> expected, Collection<Member> actual)
    {
        assertEquals(expected.size(), actual.size(), msg + " size");
        Iterator<Member> itExpected = expected.iterator();
        Iterator<Member> itActual = actual.iterator();
        for (int i = 0; itExpected.hasNext(); i++) {
            assertEquals(itActual.next(), itExpected.next(), msg + " [" + i + "]");
        }
    }

    private void assertApartExpandSupportedCalculatedMembers(
        String msg,
        Member[] expectedByDefault,
        Member[] expectedOnDisjoint,
        Member[] argMembersArray,
        Evaluator evaluator)
    {
        assertSameContent(
            msg + " - (list, eval)",
            List.of(expectedByDefault),
            CalculatedMemberExpander.expandSupportedCalculatedMembers(
                List.of(argMembersArray), evaluator).getMembers());
        assertSameContent(
            msg + " - (list, eval, false)",
            List.of(expectedByDefault),
            CalculatedMemberExpander.expandSupportedCalculatedMembers(
                List.of(argMembersArray), evaluator, false).getMembers());
        assertSameContent(
            msg + " - (list, eval, true)",
            List.of(expectedOnDisjoint),
            CalculatedMemberExpander.expandSupportedCalculatedMembers(
                List.of(argMembersArray), evaluator, true).getMembers());
    }

    private Member makeNoncalculatedMember(String toString) {
        Member member = Mockito.mock(Member.class);
        assertEquals(false, member.isCalculated());
        Mockito.doReturn("mock[" + toString + "]").when(member).toString();
        return member;
    }

    /** A mocked evaluator whose slicer map resolves {@code hierarchy} to {@code slicerMember}. */
    private RolapEvaluator evaluatorWithSlicer(RolapHierarchy hierarchy, Member slicerMember) {
        RolapEvaluator evaluator = mock(RolapEvaluator.class);
        when(evaluator.getSlicerMembersByHierarchy())
            .thenReturn(Map.of(hierarchy, new LinkedHashSet<>(List.of(slicerMember))));
        return evaluator;
    }

    private CompoundSlicerRolapMember placeholderFor(RolapHierarchy hierarchy) {
        CompoundSlicerRolapMember placeHolderMember = mock(CompoundSlicerRolapMember.class);
        Mockito.doReturn(hierarchy).when(placeHolderMember).getHierarchy();
        return placeHolderMember;
    }

    @Test
    void testReplaceCompoundSlicerPlaceholder() {
        RolapHierarchy hierarchy = mock(RolapHierarchy.class);
        Member slicerMember = makeNoncalculatedMember("slicer");
        RolapEvaluator evaluator = evaluatorWithSlicer(hierarchy, slicerMember);

        Member r = CalculatedMemberExpander.replaceCompoundSlicerPlaceholder(
            placeholderFor(hierarchy), evaluator);

        assertSame(slicerMember, r);
    }

    // placeholder expansion: replaced by the slicer member by default, dropped on disjoint tuples
    @Test
    void testExpandSupportedCalculatedMembersPlaceholder() {
        RolapHierarchy hierarchy = mock(RolapHierarchy.class);
        Member slicerMember = makeNoncalculatedMember("slicer");
        RolapEvaluator evaluator = evaluatorWithSlicer(hierarchy, slicerMember);

        Member endMember0 = makeNoncalculatedMember("0");

        // (0, placeholder)
        Member[] argMembers = new Member[] {endMember0, placeholderFor(hierarchy)};
        Member[] expectedMembers = new Member[] {endMember0, slicerMember};
        Member[] expectedMembersOnDisjoin = new Member[] {endMember0};
        assertApartExpandSupportedCalculatedMembers(
            "(0, placeholder)",
            expectedMembers, expectedMembersOnDisjoin, argMembers,
            evaluator);
    }

}

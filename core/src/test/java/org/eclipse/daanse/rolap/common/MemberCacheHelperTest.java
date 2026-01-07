/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2006-2017 Hitachi Vantara and others
 * All Rights Reserved.
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

package org.eclipse.daanse.rolap.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.daanse.rolap.common.sql.MemberChildrenConstraint;
import org.eclipse.daanse.rolap.element.RolapLevel;
import org.eclipse.daanse.rolap.element.RolapMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;


class MemberCacheHelperTest {

    @Mock
    private RolapMember parentMember;

    @Mock
    private TestPublicChildByNameConstraint childByNameConstraint;

    private MemberChildrenConstraint defMemChildrenConstraint =
        DefaultMemberChildrenConstraint.instance();

    private List<RolapMember> children = new ArrayList<>();

    private MemberCacheHelper cacheHelper = new MemberCacheHelper(null);

    @BeforeEach void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void roundtripChildrenUsingChildByNameConstraint() {
        List<String> childNames = fillChildren(children, 3);
        when(childByNameConstraint.getChildNames()).thenReturn(childNames);

        cacheHelper.putChildren(parentMember, childByNameConstraint, children);

        List<RolapMember> retrievedChildren =
            cacheHelper.getChildrenFromCache(
                parentMember, childByNameConstraint);

        assertThat(retrievedChildren).isEqualTo(children);
    }

    @Test
    void cachedByDefaultConstraint() {
        List<String> childNames = fillChildren(children, 5);
        when(childByNameConstraint.getChildNames()).thenReturn(childNames);

        // cached under default constraint, but subsequent
        // retrieval with childByName should work since all children present.
        cacheHelper.putChildren(
            parentMember,
            defMemChildrenConstraint, children);

        List<RolapMember> retrievedChildren =
            cacheHelper.getChildrenFromCache(
                parentMember,
                childByNameConstraint);

        assertThat(retrievedChildren).isEqualTo(children);
    }

    @Test
    void onlyRequestedChildrenRetrieved() {
        // tests retrieval of a subset of children from
        // the cache with keyed with DefaultMemberChildrenConstraint
        List<String> childNames = fillChildren(children, 5);

        int FROM = 2;
        int TO = 5;
        // childByName constraint defined with member names in sublist
        when(childByNameConstraint.getChildNames()).thenReturn(
            childNames.subList(FROM, TO));

        // cached under default constraint, but subsequent
        // retrieval with childByName should work since all children present.
        cacheHelper.putChildren(
            parentMember,
            defMemChildrenConstraint, children);

        List<RolapMember> retrievedChildren =
            cacheHelper.getChildrenFromCache(
                parentMember,
                childByNameConstraint);

        assertThat(retrievedChildren).as("Expected children were not retrieved from cache.").isEqualTo(children.subList(FROM, TO));
    }

    @Test
    void missingChildrenNotRetrievedDefaultConst() {
        runMissingChildrenNotRetrievedTest(defMemChildrenConstraint);
    }

    @Test
    void missingChildrenNotRetrievedChildByName() {
        runMissingChildrenNotRetrievedTest(childByNameConstraint);
    }

    public void runMissingChildrenNotRetrievedTest(
        MemberChildrenConstraint constraint)
    {
        fillChildren(children, 5);
        when(childByNameConstraint.getChildNames()).thenReturn(
            Arrays.asList(new String[]{ "Other Name", "Other Name2" }));

        cacheHelper.putChildren(
            parentMember, constraint, children);

        List<RolapMember> retrievedChildren =
            cacheHelper.getChildrenFromCache(
                parentMember, childByNameConstraint);

        assertThat(retrievedChildren).as("Not expecting to retrieve anything from cache").isNull();
    }


    @Test
    void removeChildMemberPresentInNamedChildrenMap() {
        List<String> childNames = fillChildren(children, 3);
        when(childByNameConstraint.getChildNames()).thenReturn(
            childNames.subList(1, 3));
        List<MemberKeyR> childKeys = new ArrayList<>();

        for (RolapMember member : children) {
            when(member.getParentMember()).thenReturn(parentMember);
            MemberKeyR key = mockMemberKey();
            childKeys.add(key);
            cacheHelper.putMember(key, member);
        }
        MemberKeyR parentKey = new MemberKeyR(null, "parentValue");
        cacheHelper.putMember(parentKey, parentMember);
        cacheHelper.putChildren(parentMember, childByNameConstraint, children);

        cacheHelper.removeMember(childKeys.getFirst());

        List<RolapMember> members =
            cacheHelper.getChildrenFromCache(
                parentMember, childByNameConstraint);

        assertThat(members).as("Retrieved children should not include the removed member").isEqualTo(children.subList(1, 3));
    }

    private static int keyCounter = 0;

    private MemberKeyR mockMemberKey() {
        RolapMember parent = mock(RolapMember.class);
        RolapLevel mockLevel = mock(RolapLevel.class);
        when(mockLevel.isParentChild()).thenReturn(true);
        when(parent.getLevel()).thenReturn(mockLevel);
        return new MemberKeyR(parent, "key-" + (keyCounter++));
    }

    private List<String> fillChildren(List<RolapMember> children, int count) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RolapMember member = mock(RolapMember.class);
            String name = "Member-" + i;
            names.add(name);
            when(member.getName()).thenReturn(name);
            // there's a bug in mockito which causes mock objects
            // .compareTo to always return 1
            // https://code.google.com/p/mockito/issues/detail?id=467
            // here's a workaround.
            when(member.compareTo(any(RolapMember.class))).thenAnswer(
                new Answer<Object>() {
                    @Override
					public Object answer(InvocationOnMock invocation)
                        throws Throwable
                    {
                        return  ((RolapMember)invocation.getMock()).getName()
                            .compareTo(
                                ((RolapMember) invocation.getArguments()[0])
                                    .getName());
                    }
                }
            );
            children.add(member);
        }
        return names;
    }
}

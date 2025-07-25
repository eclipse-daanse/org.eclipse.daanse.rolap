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

package org.eclipse.daanse.rolap.common;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.olap.api.element.Member;

public class TupleConstraintStruct {

    private List<Member> members;
    private List<TupleList> disjoinedTupleLists;

    public TupleConstraintStruct() {
        members = new ArrayList<>();
        disjoinedTupleLists = new ArrayList<>();
    }

    public List<Member> getMembers() {
        return members;
    }

    public void setMembers(List<Member> members) {
        if (members == null) {
            throw new IllegalArgumentException("members should not be null");
        }
        this.members = members;
    }

    public List<TupleList> getDisjoinedTupleLists() {
        return disjoinedTupleLists;
    }

    public void setDisjoinedTupleLists(List<TupleList> disjoinedTupleLists) {
        if (disjoinedTupleLists == null) {
            throw new IllegalArgumentException(
                "disjoinedTupleLists should not be null");
        }
        this.disjoinedTupleLists = disjoinedTupleLists;
    }

    public void addMember(Member member) {
        members.add(member);
    }

    public void addMembers(List<Member> members) {
        this.members.addAll(members);
    }

    public void addTupleList(TupleList tupleList) {
        disjoinedTupleLists.add(tupleList);
    }

    public Member[] getMembersArray() {
        return members.toArray(new Member[members.size()]);
    }
}

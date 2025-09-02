/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.result.Position;
import org.eclipse.daanse.olap.calc.base.type.tuplebase.TupleCollections;
import org.junit.jupiter.api.Test;


/**
 * Unit test for lists and iterators over members and tuples.
 */
class RolapAxisTest {

    @Test
    void testMemberArrayList() {
        TupleList list = TupleCollections.createList(3);
        list.add(
            Arrays.<Member>asList(
                new TestMember("a"),
                new TestMember("b"),
                new TestMember("c")));
        list.add(
            Arrays.<Member>asList(
                new TestMember("d"),
                new TestMember("e"),
                new TestMember("f")));
        list.add(
            Arrays.<Member>asList(
                new TestMember("g"),
                new TestMember("h"),
                new TestMember("i")));

        StringBuilder buf = new StringBuilder(100);

        RolapAxis axis = new RolapAxis(list);
        List<Position> positions = axis.getPositions();
        boolean firstTimeInner = true;
        for (Position position : positions) {
            if (! firstTimeInner) {
                buf.append(',');
            }
            buf.append(toString(position));
            firstTimeInner = false;
        }
        String s = buf.toString();
        String e = "{a,b,c},{d,e,f},{g,h,i}";
        assertEquals(s, e);

        positions = axis.getPositions();
        int size = positions.size();
        assertEquals(size, 3);

        buf.setLength(0);
        for (int i = 0; i < size; i++) {
            Position position = positions.get(i);
            if (i > 0) {
                buf.append(',');
            }
            buf.append(toString(position));
        }
        s = buf.toString();
        e = "{a,b,c},{d,e,f},{g,h,i}";
        assertEquals(s, e);
    }

    protected String toString(List<Member> position) {
        StringBuffer buf = new StringBuffer(100);
        buf.append('{');
        boolean firstTimeInner = true;
        for (Member m : position) {
            if (! firstTimeInner) {
                buf.append(',');
            }
            buf.append(m);
            firstTimeInner = false;
        }
        buf.append('}');
        return buf.toString();
    }
}

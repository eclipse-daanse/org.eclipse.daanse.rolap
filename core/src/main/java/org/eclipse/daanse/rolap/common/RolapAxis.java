/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (C) 2005-2005 Julian Hyde
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


package org.eclipse.daanse.rolap.common;

import java.util.AbstractList;
import java.util.List;

import org.eclipse.daanse.olap.api.calc.todo.TupleList;
import org.eclipse.daanse.olap.api.element.Member;
import org.eclipse.daanse.olap.api.result.Axis;
import org.eclipse.daanse.olap.api.result.Position;

/**
 * Implementation of the Axis interface.
 *
 * @author Richard M. Emberson
 * @author Julian Hyde
 */
public class RolapAxis implements Axis {
    private final TupleList list;

    public RolapAxis(TupleList list) {
        this.list = list;
    }

    public TupleList getTupleList() {
        return list;
    }

    @Override
	public List<Position> getPositions() {
        return new PositionList(list);
    }

    public static String toString(Axis axis) {
        List<Position> pl = axis.getPositions();
        return toString(pl);
    }

    public static String toString(List<Position> pl) {
        StringBuilder buf = new StringBuilder();
        for (Position p : pl) {
            buf.append('{');
            boolean firstTime = true;
            for (Member m : p) {
                if (! firstTime) {
                    buf.append(", ");
                }
                buf.append(m.getUniqueName());
                firstTime = false;
            }
            buf.append('}');
            buf.append('\n');
        }
        return buf.toString();
    }

    /**
     * List of positions.
     */
    private static class PositionList extends AbstractList<Position> {
        private final TupleList list;

        PositionList(TupleList list) {
            this.list = list;
        }

        @Override
		public boolean isEmpty() {
            // may be considerably cheaper than computing size
            return list.isEmpty();
        }

        @Override
		public int size() {
            return list.size();
        }

        @Override
		public Position get(int index) {
            return new PositionImpl(list, index);
        }
    }

    /**
     * Implementation of {@link Position} that reads from a given location in
     * a {@link TupleList}.
     */
    private static class PositionImpl
        extends AbstractList<Member>
        implements Position
    {
        private final TupleList tupleList;
        private final int offset;

        PositionImpl(TupleList tupleList, int offset) {
            this.tupleList = tupleList;
            this.offset = offset;
        }

        @Override
		public Member get(int index) {
            return tupleList.get(index, offset);
        }

        @Override
		public int size() {
            return tupleList.getArity();
        }

        @Override
        public List<Member> getMembers() {
            return null;
            //TODO
        }
    }
}

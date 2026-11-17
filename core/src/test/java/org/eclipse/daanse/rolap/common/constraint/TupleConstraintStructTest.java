/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
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
package org.eclipse.daanse.rolap.common.constraint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.eclipse.daanse.olap.api.calc.tuple.TupleList;
import org.eclipse.daanse.olap.api.element.Member;
import org.junit.jupiter.api.Test;

/** What the native seam collects as constraint: members and tuple lists, in order, and never null. */
class TupleConstraintStructTest {

    @Test
    void nullIsRefusedWithAMessage() {
        TupleConstraintStruct struct = new TupleConstraintStruct();

        assertThatThrownBy(() -> struct.setMembers(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("members should not be null");
        assertThatThrownBy(() -> struct.setDisjoinedTupleLists(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disjoinedTupleLists should not be null");
    }

    @Test
    void membersAndListsKeepTheirOrderAndTheArrayIsACopy() {
        Member first = mock(Member.class, "first");
        Member second = mock(Member.class, "second");
        TupleList list = mock(TupleList.class);
        TupleConstraintStruct struct = new TupleConstraintStruct();

        struct.addMember(first);
        struct.addMembers(List.of(second));
        struct.addTupleList(list);
        Member[] array = struct.getMembersArray();
        array[0] = second;

        assertThat(struct.getMembers()).containsExactly(first, second);
        assertThat(struct.getDisjoinedTupleLists()).containsExactly(list);
        assertThat(struct.getMembersArray()).containsExactly(first, second);
    }
}

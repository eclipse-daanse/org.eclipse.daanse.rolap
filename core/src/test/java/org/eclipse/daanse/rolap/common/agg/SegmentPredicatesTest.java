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
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.rolap.common.agg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.eclipse.daanse.olap.spi.SegmentPredicate;
import org.junit.jupiter.api.Test;

class SegmentPredicatesTest {

    @Test
    void canonicalTextIsDeterministic() {
        SegmentPredicate values = new SegmentPredicate.Values("t.c", Arrays.asList(null, "a", "b"));
        assertThat(values.canonical()).isEqualTo("t.c in (null,a,b)");

        SegmentPredicate range = new SegmentPredicate.Range("t.c", 1, true, 9, false);
        assertThat(range.canonical()).isEqualTo("t.c [1;9)");

        SegmentPredicate and = new SegmentPredicate.And(List.of(values, range));
        assertThat(and.canonical()).isEqualTo("(t.c in (null,a,b) and t.c [1;9))");

        assertThat(new SegmentPredicate.Not(new SegmentPredicate.Literal(true)).canonical())
                .isEqualTo("not (true)");
    }

    @Test
    void equalStructuresAreEqualValues() {
        SegmentPredicate one = new SegmentPredicate.Values("t.c", List.of("a", "b"));
        SegmentPredicate two = new SegmentPredicate.Values("t.c", List.of("a", "b"));
        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
        assertThat(new SegmentPredicate.Or(List.of(one)))
                .isEqualTo(new SegmentPredicate.Or(List.of(two)));
    }
}

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
package org.eclipse.daanse.rolap.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** A filtered list shows the accepted elements only, and it asks the filter no more often than it must. */
class FilteredIterableListReadTest {

    private static final List<Integer> ZERO_TO_NINE = IntStream.range(0, 10).boxed().toList();
    private static final List<Integer> THE_EVEN_ONES = List.of(0, 2, 4, 6, 8);

    private final AtomicInteger asked = new AtomicInteger();

    private FilteredIterableList<Integer> evenOnesOf(List<Integer> source) {
        return new FilteredIterableList<>(source, number -> {
            asked.incrementAndGet();
            return number % 2 == 0;
        });
    }

    private static int walkOver(FilteredIterableList<Integer> numbers) {
        int seen = 0;
        for (Iterator<Integer> walk = numbers.iterator(); walk.hasNext(); walk.next()) {
            seen++;
        }
        return seen;
    }

    @Nested
    class WhenTheFilteredListIsRead {

        @Test
        void onlyTheAcceptedElementsArriveAndInTheirOldOrder() {
            assertThat(evenOnesOf(ZERO_TO_NINE)).containsExactlyElementsOf(THE_EVEN_ONES);
        }

        @Test
        void theSizeAndTheArrayAgreeWithTheIteration() {
            assertThat(evenOnesOf(ZERO_TO_NINE)).hasSize(THE_EVEN_ONES.size());
            assertThat(evenOnesOf(ZERO_TO_NINE).toArray()).containsExactlyElementsOf(THE_EVEN_ONES);
        }

        @Test
        void aPartOfTheListHoldsTheElementsFromItsFirstPositionUpToItsLast() {
            assertThat(evenOnesOf(ZERO_TO_NINE).subList(2, 4)).containsExactly(4, 6);
        }
    }

    @Nested
    class WhenTheFilteredListIsBuilt {

        @Test
        void theFilterIsAskedOnlyUpToTheFirstAcceptedElement() {
            evenOnesOf(ZERO_TO_NINE);

            assertThat(asked).hasValue(1);
        }
    }

    @Nested
    class WhenAPositionHasAlreadyBeenVisited {

        @Test
        void readingItAgainAsksTheFilterNoFurtherQuestion() {
            FilteredIterableList<Integer> evenOnes = evenOnesOf(ZERO_TO_NINE);
            assertThat(walkOver(evenOnes)).isEqualTo(THE_EVEN_ONES.size());
            int askedWhileVisiting = asked.get();

            assertThat(evenOnes.get(THE_EVEN_ONES.size() - 1)).isEqualTo(8);
            assertThat(asked).hasValue(askedWhileVisiting);
        }
    }

    @Nested
    class WhenTheWholeListHasBeenVisitedAndThePositionsAreForgottenAgain {

        @Test
        void readingAnEarlyPositionGivesTheElementThatSitsThere() {
            FilteredIterableList<Integer> evenOnes = evenOnesOf(IntStream.range(0, 1000).boxed().toList());
            assertThat(walkOver(evenOnes)).isEqualTo(500);

            assertThat(evenOnes.get(1)).isEqualTo(2);
        }
    }
}

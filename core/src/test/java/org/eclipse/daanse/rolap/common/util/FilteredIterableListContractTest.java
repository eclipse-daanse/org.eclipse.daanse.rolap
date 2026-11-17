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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** A filtered list answers like any other list, and it walks forwards only. */
class FilteredIterableListContractTest {

    private static final List<String> BERLIN_AND_JENA = List.of("Berlin", "Jena");

    private static FilteredIterableList<String> everythingOf(List<String> source) {
        return new FilteredIterableList<>(source, town -> true);
    }

    @Nested
    class WhenAnAcceptedElementIsNull {

        @Test
        void theWalkCarriesOnBehindIt() {
            assertThat(everythingOf(Arrays.asList("Berlin", null, "Jena"))).containsExactly("Berlin", null, "Jena");
        }
    }

    @Nested
    class WhenTheFilteredListIsComparedWithAnotherList {

        @Test
        void bothDirectionsCallThemEqualWhenTheElementsMatch() {
            FilteredIterableList<String> towns = everythingOf(BERLIN_AND_JENA);

            assertThat(towns).isEqualTo(BERLIN_AND_JENA);
            assertThat(BERLIN_AND_JENA.equals(towns)).as("the plain list sees the filtered one as its equal").isTrue();
        }

        @Test
        void theyHashAlike() {
            assertThat(everythingOf(BERLIN_AND_JENA).hashCode()).isEqualTo(BERLIN_AND_JENA.hashCode());
        }
    }

    @Nested
    class WhenTheWalkIsAskedToGoBackOrToChangeSomething {

        @Test
        void everySuchRequestIsRefused() {
            ListIterator<String> towns = everythingOf(BERLIN_AND_JENA).listIterator(0);

            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(towns::previous);
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(towns::remove);
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> towns.set("Weimar"));
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> towns.add("Weimar"));
        }
    }
}

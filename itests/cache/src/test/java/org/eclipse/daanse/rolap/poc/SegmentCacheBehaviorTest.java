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
 */
package org.eclipse.daanse.rolap.poc;

import static org.assertj.core.api.Assertions.assertThat;

import static org.eclipse.daanse.rolap.testkit.assertions.Mdx.executeQuery;
import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.instance.emf.complex.school.SchoolTestInstance;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.eclipse.daanse.rolap.testkit.junit.modifier.CacheProbe;
import org.junit.jupiter.api.Test;

@RolapContextTest(value = SchoolTestInstance.class, modifiers = CacheProbe.class)
class SegmentCacheBehaviorTest {

    private static final String MDX =
            "SELECT {[Measures].[Anzahl Schulen]} ON COLUMNS FROM [Schulen in Jena (Institutionen)]";

    @Test
    void secondExecutionIsServedFromTheSegmentCache(Connection conn, CacheProbe cache) {
        executeQuery(conn, MDX);
        assertThat(cache.segmentLoads())
                .as("kalter Lauf lädt mindestens ein Segment aus der DB")
                .isPositive();

        cache.reset();
        executeQuery(conn, MDX);
        assertThat(cache.segmentLoads())
                .as("warmer Lauf lädt nichts nach")
                .isZero();
        assertThat(cache.hits())
                .as("warmer Lauf wird aus dem Segment-Cache bedient")
                .isPositive();
    }
}

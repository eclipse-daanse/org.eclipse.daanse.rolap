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
package org.eclipse.daanse.rolap.common.star;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.element.RolapCatalog;
import org.eclipse.daanse.rolap.common.agg.CellRequest;
import org.eclipse.daanse.rolap.common.agg.SegmentWithData;
import org.eclipse.daanse.rolap.mapping.model.database.source.RelationalSource;
import org.junit.jupiter.api.Test;
import org.mockito.quality.Strictness;

/**
 * A flush invalidates the thread-local working store: after
 * {@link RolapStar#invalidateWorkingStores()} a registered segment is no
 * longer served, a re-registered one is.
 */
class WorkingStoreCoherenceTest {

    private static final class TestStar extends RolapStar {
        TestStar(RolapCatalog catalog, Context<?> context, RelationalSource fact) {
            super(catalog, context, fact);
        }
    }

    @Test
    void invalidateWorkingStoresDropsRegisteredSegments() {
        RelationalSource fact = mock(RelationalSource.class,
                withSettings().strictness(Strictness.LENIENT));

        RolapStar star = new TestStar(
                mock(RolapCatalog.class), mock(Context.class), fact);

        RolapStar.Measure measure = mock(RolapStar.Measure.class,
                withSettings().strictness(Strictness.LENIENT));
        when(measure.getStar()).thenReturn(star);
        CellRequest request = new CellRequest(measure, false, false);

        SegmentWithData segment = mock(SegmentWithData.class,
                withSettings().strictness(Strictness.LENIENT));
        when(segment.getConstrainedColumnsBitKey())
                .thenReturn(request.getConstrainedColumnsBitKey());
        when(segment.matches(any(), any())).thenReturn(true);
        when(segment.getCellValue(any())).thenReturn("42");

        star.register(segment);
        assertThat(star.getCellFromCache(request)).isEqualTo("42");

        star.invalidateWorkingStores();
        assertThat(star.getCellFromCache(request)).isNull();

        star.register(segment);
        assertThat(star.getCellFromCache(request)).isEqualTo("42");
    }
}

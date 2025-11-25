/*
 * This software is subject to the terms of the Eclipse Public License v1.0
 * Agreement, available at the following URL:
 * http://www.eclipse.org/legal/epl-v10.html.
 * You must accept the terms of that agreement to use this software.
 *
 * Copyright (c) 2019-2019 Hitachi Vantara.
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

package org.eclipse.daanse.rolap.common.cache;

import static org.mockito.Mockito.mock;

import org.eclipse.daanse.olap.spi.SegmentBody;
import org.eclipse.daanse.olap.spi.SegmentHeader;
import org.junit.jupiter.api.Test;

class SegmentCacheIndexImplTest {
	@Test
    void noHeaderOnLoad() {
        final SegmentCacheIndexImpl index =
            new SegmentCacheIndexImpl(Thread.currentThread());

        final SegmentHeader header = mock(SegmentHeader.class);
        final SegmentBody body = mock(SegmentBody.class);

        // This should not fail.
        index.loadSucceeded(header, body);
    }
}

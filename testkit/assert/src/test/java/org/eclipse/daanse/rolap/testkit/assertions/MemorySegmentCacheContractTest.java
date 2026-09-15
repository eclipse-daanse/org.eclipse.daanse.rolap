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
package org.eclipse.daanse.rolap.testkit.assertions;

import org.eclipse.daanse.olap.spi.SegmentCache;
import org.eclipse.daanse.rolap.common.cache.MemorySegmentCache;

class MemorySegmentCacheContractTest extends SegmentCacheContract {

    @Override
    protected SegmentCache createCache() {
        return new MemorySegmentCache();
    }
}

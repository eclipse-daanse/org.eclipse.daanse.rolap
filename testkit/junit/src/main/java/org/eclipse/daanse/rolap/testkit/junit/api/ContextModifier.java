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
 *   SmartCity Jena, Stefan Bischof - initial
 */
package org.eclipse.daanse.rolap.testkit.junit.api;

import org.eclipse.daanse.olap.api.Context;

/**
 * Modifies or instruments the freshly built context before the test runs.
 *
 * <p>Declared via {@link RolapContextTest#modifiers()}, instantiated once per
 * context lifetime, applied after the context is built, and closed in reverse
 * order after the test — guaranteed by the extension's store, no try/finally
 * needed. Injectable into test parameters and {@link InjectRolap} fields by
 * concrete type, so a modifier can expose what it observed.
 */
public interface ContextModifier extends AutoCloseable {

    /** Runs after the context is built, before the test. */
    void apply(Context<?> context) throws Exception;

    /** Guaranteed cleanup after the test; default does nothing. */
    @Override
    default void close() throws Exception {
        // nothing to clean up by default
    }
}

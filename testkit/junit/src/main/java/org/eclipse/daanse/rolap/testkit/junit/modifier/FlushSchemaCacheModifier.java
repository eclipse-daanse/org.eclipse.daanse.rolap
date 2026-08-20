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
package org.eclipse.daanse.rolap.testkit.junit.modifier;

import org.eclipse.daanse.olap.api.Context;
import org.eclipse.daanse.rolap.testkit.junit.api.ContextModifier;

/**
 * Clears the catalog cache when the context is built, forcing a fresh read
 * and real SQL on the next query.
 *
 * <p>Useful under {@code ContextScope.PER_CLASS}, where the default
 * {@code PER_TEST} fresh-context behavior doesn't apply.
 */
public final class FlushSchemaCacheModifier implements ContextModifier {

    private Context<?> context;

    @Override
    public void apply(Context<?> context) {
        this.context = context;
        flush();
    }

    /** Flush again mid-test (inject the modifier to call this). */
    public void flush() {
        context.getCatalogCache().clear();
    }
}

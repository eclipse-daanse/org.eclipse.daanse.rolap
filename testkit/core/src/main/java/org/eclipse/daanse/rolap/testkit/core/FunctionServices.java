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
package org.eclipse.daanse.rolap.testkit.core;

import org.eclipse.daanse.olap.api.function.FunctionService;
import org.eclipse.daanse.rolap.function.def.intersect.IntersectResolver;
import org.eclipse.daanse.rolap.function.def.visualtotals.VisualTotalsResolver;

/**
 * Thin wrapper around
 * {@link org.eclipse.daanse.olap.function.services.standard.StandardFunctions}
 * that additionally registers the two ROLAP-specific resolvers
 * ({@code IntersectResolver}, {@code VisualTotalsResolver}) that live in
 * {@code rolap.core} and are therefore not visible to the olap-level standard
 * registry.
 */
public final class FunctionServices {

    private FunctionServices() {
    }

    public static FunctionService standard() {
        FunctionService svc = org.eclipse.daanse.olap.function.services.standard.StandardFunctions.standard();
        svc.addResolver(new IntersectResolver());
        svc.addResolver(new VisualTotalsResolver());
        return svc;
    }
}

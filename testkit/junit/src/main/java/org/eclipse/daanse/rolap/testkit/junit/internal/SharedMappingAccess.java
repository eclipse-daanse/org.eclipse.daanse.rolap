/*********************************************************************
* Copyright (c) 2026 Contributors to the Eclipse Foundation.
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.daanse.rolap.testkit.junit.internal;

import java.util.function.Supplier;

import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;

/**
 * Serialises access to the generated mapping models.
 *
 * <p>The generated suppliers hand out a JVM-shared model and mutate it on the
 * way out: {@code CatalogSupplier.get()} returns the same static
 * {@code CATALOG_*} instance every call and first runs {@code Naming.complete}
 * over it, which walks {@code eAllContents()} calling {@code setName(...)}.
 * The SteelWheels supplier additionally does several
 * {@code getOwnedElement().addAll(...)} per call. EMF models are not
 * thread-safe, so two test threads resolving a fixture at the same time crash:
 *
 * <pre>
 * NullPointerException: Cannot invoke "EObject.eContents()" because "object" is null
 *   at AbstractTreeIterator.next
 *   at Naming.complete
 *   at steelwheels.CatalogSupplier.get
 *   at DatabaseProvisioner.load
 * </pre>
 *
 * <p>Because that completion is idempotent, serialising resolution is enough --
 * once the first caller has completed the graph, later callers only read it.
 * This is deliberately a single process-wide monitor: resolution happens once
 * per fixture, not per query, so the contention is negligible compared with
 * opting whole test classes out of parallel execution.
 */
final class SharedMappingAccess {

    private static final Object LOCK = new Object();

    private SharedMappingAccess() {
    }

    /** Resolves a shared mapping model, serialised against all other resolutions. */
    static <T> T resolve(Supplier<T> resolution) {
        synchronized (LOCK) {
            return resolution.get();
        }
    }

    /** Wraps a supplier so that every {@code get()} resolves under the lock. */
    static CatalogMappingSupplier guarded(CatalogMappingSupplier delegate) {
        if (delegate == null) {
            return null;
        }
        return () -> resolve(delegate::get);
    }
}

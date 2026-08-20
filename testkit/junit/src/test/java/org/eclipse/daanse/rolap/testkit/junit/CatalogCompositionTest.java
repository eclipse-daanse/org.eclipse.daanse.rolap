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
package org.eclipse.daanse.rolap.testkit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.daanse.olap.api.connection.Connection;
import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.eclipse.daanse.rolap.testkit.junit.api.RolapContextTest;
import org.junit.jupiter.api.Test;

/**
 * The catalog array composes left-to-right: the second supplier receives the
 * first supplier's catalog via its {@code (Catalog)} constructor — the legacy
 * {@code withSchemaEmf} chain, declared.
 */
@RolapContextTest(catalog = { CatalogCompositionTest.MinimalBase.class, CatalogCompositionTest.Renamer.class },
        database = org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.cube.minimal.MinimalCubeDatabaseSupplier.class)
class CatalogCompositionTest {

    static final String COMPOSED_NAME = "Composed Catalog";

    @Test
    void secondSupplierWrapsTheFirst(Connection connection) {
        assertThat(connection.getCatalog().getName()).isEqualTo(COMPOSED_NAME);
    }

    /** Base catalog: the minimal tutorial cube. */
    public static class MinimalBase implements CatalogMappingSupplier {
        @Override
        public Catalog get() {
            return new org.eclipse.daanse.rolap.mapping.instance.emf.tutorial.cube.minimal.CatalogSupplier().get();
        }
    }

    /** Modifier-style supplier: wraps the previous catalog (Catalog constructor). */
    public static class Renamer implements CatalogMappingSupplier {
        private final Catalog previous;

        public Renamer(Catalog previous) {
            this.previous = previous;
        }

        @Override
        public Catalog get() {
            previous.setName(COMPOSED_NAME);
            return previous;
        }
    }
}

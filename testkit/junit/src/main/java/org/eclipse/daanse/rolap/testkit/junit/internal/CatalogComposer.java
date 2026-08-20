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
package org.eclipse.daanse.rolap.testkit.junit.internal;

import java.lang.reflect.Constructor;
import java.util.List;

import org.eclipse.daanse.rolap.mapping.model.catalog.Catalog;
import org.eclipse.daanse.rolap.mapping.model.provider.CatalogMappingSupplier;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

/**
 * Composes the {@code catalog = {A.class, B.class, ...}} array left-to-right:
 * each class after the first is built via a constructor taking the previous
 * result's {@link Catalog}, or its no-arg constructor if none exists.
 */
final class CatalogComposer {

    private CatalogComposer() {
    }

    static CatalogMappingSupplier compose(List<Class<? extends CatalogMappingSupplier>> classes,
            String describedLocation) {
        CatalogMappingSupplier current = RolapFixture.instantiate(classes.get(0), describedLocation);
        for (Class<? extends CatalogMappingSupplier> next : classes.subList(1, classes.size())) {
            Constructor<? extends CatalogMappingSupplier> wrapping = catalogConstructor(next);
            if (wrapping != null) {
                try {
                    current = wrapping.newInstance(current.get());
                } catch (ReflectiveOperationException e) {
                    throw new ExtensionConfigurationException("@RolapContextTest at " + describedLocation
                            + ": cannot compose " + next.getName() + " over the previous catalog", e);
                }
            } else {
                current = RolapFixture.instantiate(next, describedLocation);
            }
        }
        return current;
    }

    private static Constructor<? extends CatalogMappingSupplier> catalogConstructor(
            Class<? extends CatalogMappingSupplier> type) {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 1
                    && constructor.getParameterTypes()[0].isAssignableFrom(Catalog.class)) {
                @SuppressWarnings("unchecked")
                Constructor<? extends CatalogMappingSupplier> typed =
                        (Constructor<? extends CatalogMappingSupplier>) constructor;
                return typed;
            }
        }
        return null;
    }
}
